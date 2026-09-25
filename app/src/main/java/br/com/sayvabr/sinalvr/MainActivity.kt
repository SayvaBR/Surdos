package br.com.sayvabr.sinalvr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.min

private val Ink = Color(0xFF121820)
private val Orange = Color(0xFFF36C21)
private val OrangeSoft = Color(0xFFFFE5D5)
private val Cream = Color(0xFFFFFAF5)
private val Card = Color.White
private val Muted = Color(0xFF66727A)
private val Green = Color(0xFF169B86)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SinalLensApp() }
    }
}

@Composable
fun SinalLensApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Orange,
            secondary = Green,
            background = Cream,
            surface = Card,
            onBackground = Ink,
            onSurface = Ink
        )
    ) {
        var librasWord by remember { mutableStateOf<String?>(null) }

        Surface(Modifier.fillMaxSize(), color = Cream) {
            if (librasWord != null) {
                LibrasScreen(
                    word = librasWord!!,
                    onBack = { librasWord = null }
                )
            } else {
                ScannerScreen(onOpenLibras = { librasWord = it })
            }
        }
    }
}

@Composable
private fun ScannerScreen(onOpenLibras: (String) -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frozen by remember { mutableStateOf<Bitmap?>(null) }
    val detected = remember { mutableStateListOf<ScanItem>() }
    var selected by remember { mutableStateOf<ScanItem?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Aponte a câmera para objetos do cotidiano") }
    var loading by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (!permissionGranted) {
            PermissionScreen(onAsk = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        } else if (frozen == null) {
            CameraPreview(
                onReady = { previewView = it },
                modifier = Modifier.fillMaxSize()
            )

            ScannerChrome(
                status = status,
                loading = loading,
                onScan = {
                    val bitmap = previewView?.bitmap
                    if (bitmap == null) {
                        status = "A câmera ainda está preparando a imagem"
                    } else {
                        loading = true
                        status = "Reconhecendo objetos..."
                        VisionEngine.scan(
                            bitmap = bitmap,
                            onSuccess = { items ->
                                frozen = bitmap
                                detected.clear()
                                detected.addAll(items)
                                selected = items.firstOrNull()
                                cutout = null
                                loading = false
                                status = if (items.isEmpty()) {
                                    "Não encontrei um objeto. Tente aproximar."
                                } else {
                                    "Toque em uma marcação para aprender"
                                }
                            },
                            onError = {
                                loading = false
                                status = "Não consegui analisar. Tente novamente."
                            }
                        )
                    }
                }
            )
        } else {
            ScanResult(
                bitmap = frozen!!,
                items = detected,
                selected = selected,
                cutout = cutout,
                status = status,
                loading = loading,
                onSelect = {
                    selected = it
                    cutout = null
                },
                onCut = {
                    val item = selected ?: return@ScanResult
                    loading = true
                    status = "Recortando o objeto..."
                    VisionEngine.removeBackground(
                        item.crop,
                        onSuccess = {
                            cutout = it
                            loading = false
                            status = "Fundo removido"
                        },
                        onError = {
                            loading = false
                            status = "Não foi possível remover o fundo agora"
                        }
                    )
                },
                onSave = {
                    val item = selected ?: return@ScanResult
                    val image = cutout ?: item.crop
                    val saved = VisionEngine.savePng(context, image, item.label)
                    Toast.makeText(
                        context,
                        if (saved != null) "Imagem salva em Pictures/SinalLens" else "Não foi possível salvar",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLibras = {
                    selected?.let { onOpenLibras(it.label) }
                },
                onRescan = {
                    frozen = null
                    detected.clear()
                    selected = null
                    cutout = null
                    status = "Aponte a câmera para objetos do cotidiano"
                }
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onReady(this)

                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(surfaceProvider)

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                }, ContextCompat.getMainExecutor(context))
            }
        }
    )
}

@Composable
private fun ScannerChrome(
    status: String,
    loading: Boolean,
    onScan: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp)
    ) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter),
            shape = RoundedCornerShape(18.dp),
            color = Ink.copy(alpha = .78f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = Orange) {
                    Text("L", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("SinalLens AR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(status, color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                }
            }
        }

        Canvas(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(270.dp)
                .padding(horizontal = 22.dp)
        ) {
            val w = size.width
            val h = size.height
            val c = Orange
            val length = 42f
            val stroke = 5f

            drawLine(c, Offset(0f, 0f), Offset(length, 0f), stroke)
            drawLine(c, Offset(0f, 0f), Offset(0f, length), stroke)
            drawLine(c, Offset(w, 0f), Offset(w - length, 0f), stroke)
            drawLine(c, Offset(w, 0f), Offset(w, length), stroke)
            drawLine(c, Offset(0f, h), Offset(length, h), stroke)
            drawLine(c, Offset(0f, h), Offset(0f, h - length), stroke)
            drawLine(c, Offset(w, h), Offset(w - length, h), stroke)
            drawLine(c, Offset(w, h), Offset(w, h - length), stroke)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Ink.copy(alpha = .72f)
            ) {
                Text(
                    "cadeira • mesa • livro • pessoa • mochila...",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .clickable(enabled = !loading, onClick = onScan),
                shape = CircleShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(5.dp, Orange)
            ) {
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Orange) {
                        Box(Modifier.padding(23.dp))
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(34.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("ESCANEAR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
        }
    }
}

@Composable
private fun ScanResult(
    bitmap: Bitmap,
    items: List<ScanItem>,
    selected: ScanItem?,
    cutout: Bitmap?,
    status: String,
    loading: Boolean,
    onSelect: (ScanItem) -> Unit,
    onCut: () -> Unit,
    onSave: () -> Unit,
    onLibras: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Ink)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Imagem escaneada",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items, selected) {
                        detectTapGestures { tap ->
                            val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                            val drawnW = bitmap.width * scale
                            val drawnH = bitmap.height * scale
                            val dx = (size.width - drawnW) / 2f
                            val dy = (size.height - drawnH) / 2f
                            val x = ((tap.x - dx) / scale).toInt()
                            val y = ((tap.y - dy) / scale).toInt()
                            items.firstOrNull { it.bounds.contains(x, y) }?.let(onSelect)
                        }
                    }
            ) {
                val scale = min(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
                val drawnW = bitmap.width * scale
                val drawnH = bitmap.height * scale
                val dx = (size.width - drawnW) / 2f
                val dy = (size.height - drawnH) / 2f

                items.forEach { item ->
                    val rect = item.bounds
                    val left = dx + rect.left * scale
                    val top = dy + rect.top * scale
                    val width = rect.width() * scale
                    val height = rect.height() * scale
                    val color = if (item == selected) Orange else Color.White

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                        style = Stroke(width = if (item == selected) 7f else 4f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(14.dp)
                    .background(Ink.copy(alpha = .80f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp),
                        color = Orange,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(status, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                items.take(5).forEach { item ->
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clickable { onSelect(item) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (item == selected) Orange else Ink.copy(alpha = .78f)
                    ) {
                        Text(
                            item.label,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clickable(onClick = onRescan),
                shape = CircleShape,
                color = Ink.copy(alpha = .82f)
            ) {
                Text("‹", modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp), color = Color.White, fontSize = 28.sp)
            }
        }

        selected?.let { item ->
            ObjectSheet(
                item = item,
                cutout = cutout,
                loading = loading,
                onCut = onCut,
                onSave = onSave,
                onLibras = onLibras,
                onRescan = onRescan
            )
        }
    }
}

@Composable
private fun ObjectSheet(
    item: ScanItem,
    cutout: Bitmap?,
    loading: Boolean,
    onCut: () -> Unit,
    onSave: () -> Unit,
    onLibras: () -> Unit,
    onRescan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = OrangeSoft) {
                Image(
                    bitmap = (cutout ?: item.crop).asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier
                        .width(74.dp)
                        .height(74.dp)
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    if (item.confidence > 0f) "Objeto reconhecido pela câmera" else "Objeto detectado",
                    color = Muted,
                    fontSize = 12.sp
                )
                Text("Português  ↔  Libras", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(13.dp))

        Button(
            onClick = onLibras,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
        ) {
            Text("🤟  VER SINAL EM LIBRAS", fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(8.dp))

        Row {
            ActionPill(
                text = if (cutout == null) "✂  Recortar fundo" else "✓  Fundo recortado",
                enabled = !loading,
                modifier = Modifier.weight(1f),
                onClick = onCut
            )
            Spacer(Modifier.width(8.dp))
            ActionPill(
                text = "↓  Salvar PNG",
                enabled = !loading,
                modifier = Modifier.weight(1f),
                onClick = onSave
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Escaneie outro objeto",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onRescan)
                .padding(8.dp),
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionPill(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Cream,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5DED7))
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 13.dp),
            color = Ink,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LibrasScreen(word: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.clickable(onClick = onBack),
                shape = CircleShape,
                color = OrangeSoft
            ) {
                Text("‹", modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = Orange, fontSize = 28.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(word, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("Sinal em Libras • VLibras", color = Muted, fontSize = 12.sp)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            color = OrangeSoft,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "O avatar abaixo é do VLibras, software público brasileiro. Se o sinal não iniciar sozinho, toque na palavra “$word” dentro do tradutor.",
                modifier = Modifier.padding(12.dp),
                color = Ink,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    setBackgroundColor(android.graphics.Color.WHITE)
                    loadDataWithBaseURL(
                        "https://vlibras.gov.br/",
                        vlibrasHtml(word),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                webView.evaluateJavascript(
                    "if(window.__sinalLensTranslate){window.__sinalLensTranslate(" + jsString(word) + ");}",
                    null
                )
            }
        )
    }
}

private fun vlibrasHtml(word: String): String {
    val safe = word
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace(""", "&quot;")

    return """
        <!doctype html>
        <html lang="pt-BR">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <style>
            html,body{margin:0;padding:0;background:#fff;font-family:system-ui,-apple-system,sans-serif;height:100%;overflow:hidden}
            #lesson{position:absolute;left:16px;right:16px;top:14px;z-index:4;background:#fff7f0;border:1px solid #ffd2b7;border-radius:18px;padding:14px}
            #lesson small{display:block;color:#66727a;font-weight:700;letter-spacing:.08em;margin-bottom:5px}
            #word{font-size:30px;font-weight:900;color:#121820;cursor:pointer}
            #hint{font-size:12px;color:#66727a;margin-top:6px}
            [vw]{z-index:99!important}
          </style>
        </head>
        <body>
          <div id="lesson">
            <small>OBJETO RECONHECIDO</small>
            <div id="word">$safe</div>
            <div id="hint">Toque na palavra para repetir o sinal.</div>
          </div>

          <div vw class="enabled">
            <div vw-access-button class="active"></div>
            <div vw-plugin-wrapper>
              <div class="vw-plugin-top-wrapper"></div>
            </div>
          </div>

          <script src="https://vlibras.gov.br/app/vlibras-plugin.js"></script>
          <script>
            new window.VLibras.Widget({
              rootPath:'https://vlibras.gov.br/app',
              avatar:'random',
              position:'R'
            });

            window.__sinalLensTranslate = function(text){
              var tries = 0;
              var timer = setInterval(function(){
                tries++;
                try{
                  if(window.plugin && window.plugin.player && window.plugin.player.translate){
                    window.plugin.player.translate(text);
                    clearInterval(timer);
                  }
                }catch(e){}
                if(tries > 20) clearInterval(timer);
              },500);
            };

            document.getElementById('word').onclick=function(){
              window.__sinalLensTranslate(this.innerText);
            };

            setTimeout(function(){
              window.__sinalLensTranslate(document.getElementById('word').innerText);
            },1200);
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsString(value: String): String {
    return """ + value
        .replace("\\", "\\\\")
        .replace(""", "\\"")
        .replace("\n", "\\n") + """
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Surface(shape = CircleShape, color = OrangeSoft) {
            Text("⌾", modifier = Modifier.padding(26.dp), color = Orange, fontSize = 42.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("A câmera é o material didático", color = Ink, fontWeight = FontWeight.Black, fontSize = 27.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(9.dp))
        Text(
            "O SinalLens usa a câmera para reconhecer objetos reais e conectar cada um ao português escrito e à Libras.",
            color = Muted,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAsk,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
        ) {
            Text("PERMITIR CÂMERA", modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.weight(1f))
    }
}
