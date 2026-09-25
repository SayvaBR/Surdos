package br.com.sayvabr.sinalvr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

data class ScanItem(
    val id: Int,
    val label: String,
    val confidence: Float,
    val bounds: Rect,
    val crop: Bitmap
)

object VisionEngine {
    private val detector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    private val segmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    fun scan(bitmap: Bitmap, onSuccess: (List<ScanItem>) -> Unit, onError: (Throwable) -> Unit) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                if (objects.isEmpty()) {
                    labelWholeImage(bitmap, onSuccess, onError)
                    return@addOnSuccessListener
                }

                val results = mutableListOf<ScanItem>()
                val pending = AtomicInteger(objects.size)

                objects.forEachIndexed { index, detected ->
                    val safe = safeRect(detected.boundingBox, bitmap.width, bitmap.height)
                    val crop = Bitmap.createBitmap(bitmap, safe.left, safe.top, safe.width(), safe.height())

                    labeler.process(InputImage.fromBitmap(crop, 0))
                        .addOnSuccessListener { labels ->
                            val best = labels.maxByOrNull { it.confidence }
                            results += ScanItem(
                                id = index,
                                label = toPortuguese(best?.text ?: "Objeto"),
                                confidence = best?.confidence ?: 0f,
                                bounds = safe,
                                crop = crop
                            )
                        }
                        .addOnFailureListener {
                            results += ScanItem(index, "Objeto", 0f, safe, crop)
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

    private fun labelWholeImage(
        bitmap: Bitmap,
        onSuccess: (List<ScanItem>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                val best = labels.maxByOrNull { it.confidence }
                onSuccess(
                    listOf(
                        ScanItem(
                            id = 0,
                            label = toPortuguese(best?.text ?: "Objeto"),
                            confidence = best?.confidence ?: 0f,
                            bounds = Rect(0, 0, bitmap.width, bitmap.height),
                            crop = bitmap
                        )
                    )
                )
            }
            .addOnFailureListener(onError)
    }

    fun removeBackground(bitmap: Bitmap, onSuccess: (Bitmap) -> Unit, onError: (Throwable) -> Unit) {
        segmenter.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                onSuccess(result.foregroundBitmap ?: bitmap)
            }
            .addOnFailureListener(onError)
    }

    fun savePng(context: Context, bitmap: Bitmap, label: String): String? {
        val filename = "SinalLens_" + label.replace(" ", "_") + "_" + System.currentTimeMillis() + ".png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SinalLens")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            val folder = File(dir, "SinalLens").apply { mkdirs() }
            val file = File(folder, filename)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file.absolutePath
        }
    }

    private fun safeRect(rect: Rect, width: Int, height: Int): Rect {
        val left = rect.left.coerceIn(0, width - 1)
        val top = rect.top.coerceIn(0, height - 1)
        val right = rect.right.coerceIn(left + 1, width)
        val bottom = rect.bottom.coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun toPortuguese(raw: String): String {
        val key = raw.trim().lowercase()
        val dictionary = mapOf(
            "chair" to "Cadeira",
            "table" to "Mesa",
            "desk" to "Mesa",
            "book" to "Livro",
            "books" to "Livros",
            "person" to "Pessoa",
            "people" to "Pessoas",
            "human face" to "Pessoa",
            "plant" to "Planta",
            "flower" to "Flor",
            "food" to "Comida",
            "fruit" to "Fruta",
            "bottle" to "Garrafa",
            "cup" to "Copo",
            "mug" to "Caneca",
            "pen" to "Caneta",
            "pencil" to "Lápis",
            "notebook" to "Caderno",
            "laptop" to "Notebook",
            "computer" to "Computador",
            "mobile phone" to "Celular",
            "phone" to "Celular",
            "clock" to "Relógio",
            "door" to "Porta",
            "window" to "Janela",
            "backpack" to "Mochila",
            "bag" to "Bolsa",
            "shoe" to "Sapato",
            "glasses" to "Óculos",
            "furniture" to "Móvel",
            "home goods" to "Objeto doméstico"
        )
        return dictionary[key] ?: raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
