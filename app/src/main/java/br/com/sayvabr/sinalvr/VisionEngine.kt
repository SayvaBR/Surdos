package br.com.sayvabr.sinalvr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

data class ScanItem(
    val id: Int,
    val label: String,
    val confidence: Float,
    val bounds: Rect,
    val crop: Bitmap,
    val needsReview: Boolean = false
)

private data class Candidate(
    val label: String,
    val group: String,
    val score: Float,
    val source: String
)

object VisionEngine {
    private const val UNKNOWN = "Objeto não identificado"

    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.10f)
                .build()
        )
    }

    private val segmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    @Volatile
    private var efficientNet: ImageClassifier? = null

    @Synchronized
    private fun classifier(context: Context): ImageClassifier {
        efficientNet?.let { return it }

        val base = BaseOptions.builder()
            .setModelAssetPath("efficientnet_lite0.tflite")
            .build()

        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(8)
            .setScoreThreshold(0.01f)
            .build()

        return ImageClassifier.createFromOptions(context.applicationContext, options)
            .also { efficientNet = it }
    }

    fun scan(
        context: Context,
        bitmap: Bitmap,
        onSuccess: (List<ScanItem>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                val regions = if (objects.isEmpty()) {
                    listOf(Rect(0, 0, bitmap.width, bitmap.height))
                } else {
                    objects.map { paddedRect(it.boundingBox, bitmap.width, bitmap.height) }
                }

                val results = mutableListOf<ScanItem>()
                val pending = AtomicInteger(regions.size)

                regions.forEachIndexed { index, rect ->
                    val crop = Bitmap.createBitmap(
                        bitmap,
                        rect.left,
                        rect.top,
                        rect.width(),
                        rect.height()
                    )

                    val efficientCandidates = try {
                        classifyEfficientNet(context, crop)
                    } catch (_: Throwable) {
                        emptyList()
                    }

                    labeler.process(InputImage.fromBitmap(crop, 0))
                        .addOnSuccessListener { labels ->
                            val mlCandidates = labels.take(6).mapNotNull {
                                mapCandidate(it.text, it.confidence, "mlkit")
                            }

                            val decision = decide(efficientCandidates, mlCandidates)
                            results += ScanItem(
                                id = index,
                                label = decision.first,
                                confidence = decision.second,
                                bounds = rect,
                                crop = crop,
                                needsReview = decision.first == UNKNOWN || decision.second < 0.35f
                            )
                        }
                        .addOnFailureListener {
                            val decision = decide(efficientCandidates, emptyList())
                            results += ScanItem(
                                id = index,
                                label = decision.first,
                                confidence = decision.second,
                                bounds = rect,
                                crop = crop,
                                needsReview = decision.first == UNKNOWN || decision.second < 0.35f
                            )
                        }
                        .addOnCompleteListener {
                            if (pending.decrementAndGet() == 0) {
                                onSuccess(results.sortedBy { it.id })
                            }
                        }
                }
            }
            .addOnFailureListener(onError)
    }

    private fun classifyEfficientNet(context: Context, crop: Bitmap): List<Candidate> {
        val mpImage = BitmapImageBuilder(
            if (crop.config == Bitmap.Config.ARGB_8888) crop
            else crop.copy(Bitmap.Config.ARGB_8888, false)
        ).build()

        val categories = classifier(context)
            .classify(mpImage)
            .classificationResult()
            .classifications()
            .firstOrNull()
            ?.categories()
            .orEmpty()

        return categories.mapNotNull {
            mapCandidate(it.categoryName(), it.score(), "efficientnet")
        }
    }

    private fun decide(
        efficient: List<Candidate>,
        mlkit: List<Candidate>
    ): Pair<String, Float> {
        if (efficient.isEmpty() && mlkit.isEmpty()) return UNKNOWN to 0f

        val all = efficient + mlkit
        val labels = all.map { it.label }.distinct()
        val scored = labels.map { label ->
            val e = efficient.filter { it.label == label }.maxOfOrNull { it.score } ?: 0f
            val m = mlkit.filter { it.label == label }.maxOfOrNull { it.score } ?: 0f
            val groups = all.filter { it.label == label }.map { it.group }.toSet()

            var score = e + (m * 0.18f)
            if (e > 0f && m > 0f) score += 0.12f

            // O modelo genérico funciona melhor como confirmação de família.
            groups.forEach { group ->
                val eGroup = efficient.filter { it.group == group }.maxOfOrNull { it.score } ?: 0f
                val mGroup = mlkit.filter { it.group == group }.maxOfOrNull { it.score } ?: 0f
                if (eGroup > 0f && mGroup > 0.35f) score += 0.12f
            }
            label to score
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return UNKNOWN to 0f
        val second = scored.getOrNull(1)?.second ?: 0f
        val originalEfficient = efficient.firstOrNull { it.label == best.first }?.score ?: 0f
        val hasGroupAgreement = all.any { a ->
            a.label == best.first && mlkit.any { it.group == a.group && it.score >= 0.35f }
        }

        // Em dúvida, preferimos admitir que não sabemos a ensinar uma palavra errada.
        val reliable =
            (originalEfficient >= 0.22f && best.second - second >= 0.055f) ||
            (originalEfficient >= 0.14f && hasGroupAgreement) ||
            best.second >= 0.50f

        return if (reliable) {
            best.first to best.second.coerceAtMost(1f)
        } else {
            UNKNOWN to best.second.coerceAtMost(1f)
        }
    }

    private fun mapCandidate(raw: String?, score: Float, source: String): Candidate? {
        if (raw.isNullOrBlank()) return null
        val s = raw.lowercase().replace('_', ' ')

        fun c(label: String, group: String) = Candidate(label, group, score, source)

        return when {
            hasAny(s, "ballpoint", "ballpen", "biro", "pen", "writing implement") ->
                c("Caneta", "writing")
            hasAny(s, "pencil") ->
                c("Lápis", "writing")
            hasAny(s, "eraser", "rubber eraser") ->
                c("Borracha", "writing")
            hasAny(s, "notebook", "notepad") && !hasAny(s, "computer", "laptop") ->
                c("Caderno", "school")
            hasAny(s, "book", "book jacket", "comic book") ->
                c("Livro", "school")
            hasAny(s, "backpack", "knapsack", "rucksack") ->
                c("Mochila", "school")

            hasAny(s, "flip flop", "flip-flop", "slipper", "sandal") ->
                c("Chinelo", "footwear")
            hasAny(s, "running shoe", "sneaker") ->
                c("Tênis", "footwear")
            hasAny(s, "loafer", "shoe", "footwear", "footgear") ->
                c("Sapato", "footwear")
            hasAny(s, "cowboy boot", "boot") ->
                c("Bota", "footwear")
            hasAny(s, "clog") ->
                c("Tamanco", "footwear")

            hasAny(s, "chair", "seat") ->
                c("Cadeira", "furniture")
            hasAny(s, "desk") ->
                c("Mesa", "furniture")
            hasAny(s, "dining table", "table") ->
                c("Mesa", "furniture")
            hasAny(s, "sofa", "couch") ->
                c("Sofá", "furniture")
            hasAny(s, "bed") ->
                c("Cama", "furniture")
            hasAny(s, "bookcase", "bookshelf") ->
                c("Estante", "furniture")

            hasAny(s, "cellular telephone", "mobile phone", "cell phone", "smartphone") ->
                c("Celular", "electronics")
            hasAny(s, "laptop", "notebook computer") ->
                c("Notebook", "electronics")
            hasAny(s, "computer keyboard", "keyboard") ->
                c("Teclado", "electronics")
            hasAny(s, "computer mouse", "mouse") ->
                c("Mouse", "electronics")
            hasAny(s, "television", "tv") ->
                c("Televisão", "electronics")
            hasAny(s, "remote control") ->
                c("Controle remoto", "electronics")
            hasAny(s, "digital clock", "analog clock", "clock") ->
                c("Relógio", "electronics")

            hasAny(s, "bottle", "water bottle") ->
                c("Garrafa", "container")
            hasAny(s, "coffee mug", "mug") ->
                c("Caneca", "container")
            hasAny(s, "cup") ->
                c("Copo", "container")
            hasAny(s, "plate") ->
                c("Prato", "container")
            hasAny(s, "bowl") ->
                c("Tigela", "container")

            hasAny(s, "person", "people", "human face", "face") ->
                c("Pessoa", "person")
            hasAny(s, "plant", "potted plant") ->
                c("Planta", "nature")
            hasAny(s, "flower") ->
                c("Flor", "nature")
            hasAny(s, "dog") ->
                c("Cachorro", "animal")
            hasAny(s, "cat") ->
                c("Gato", "animal")

            hasAny(s, "handbag", "purse") ->
                c("Bolsa", "accessory")
            hasAny(s, "eyeglasses", "glasses", "sunglasses") ->
                c("Óculos", "accessory")
            hasAny(s, "hat", "sombrero", "cowboy hat") ->
                c("Chapéu", "accessory")
            hasAny(s, "umbrella") ->
                c("Guarda-chuva", "accessory")

            hasAny(s, "door") ->
                c("Porta", "room")
            hasAny(s, "window") ->
                c("Janela", "room")
            hasAny(s, "lamp", "lampshade") ->
                c("Luminária", "room")

            else -> null
        }
    }

    private fun hasAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(it) }

    fun removeBackground(
        bitmap: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                onSuccess(result.foregroundBitmap ?: bitmap)
            }
            .addOnFailureListener(onError)
    }

    fun savePng(context: Context, bitmap: Bitmap, label: String): String? {
        val safeLabel = label
            .replace("Objeto não identificado", "objeto")
            .replace(" ", "_")
        val filename =
            "SinalLens_" + safeLabel + "_" + System.currentTimeMillis() + ".png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SinalLens"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return null

            resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            val dir = context.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            ) ?: return null
            val folder = File(dir, "SinalLens").apply { mkdirs() }
            val file = File(folder, filename)
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            file.absolutePath
        }
    }

    private fun paddedRect(rect: Rect, width: Int, height: Int): Rect {
        val padX = max(8, (rect.width() * 0.10f).toInt())
        val padY = max(8, (rect.height() * 0.10f).toInt())
        val left = (rect.left - padX).coerceIn(0, width - 1)
        val top = (rect.top - padY).coerceIn(0, height - 1)
        val right = (rect.right + padX).coerceIn(left + 1, width)
        val bottom = (rect.bottom + padY).coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }
}
