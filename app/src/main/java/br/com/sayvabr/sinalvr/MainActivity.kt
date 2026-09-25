package br.com.sayvabr.sinalvr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val Night = Color(0xFF07131C)
private val Panel = Color(0xFF10232E)
private val Cyan = Color(0xFF65E6FF)
private val Mint = Color(0xFF7CF2C8)
private val Soft = Color(0xFFB7CBD4)
private val Alert = Color(0xFFFFD166)

data class HeadPose(val yaw: Float = 0f, val pitch: Float = 0f)

data class LearningPoint(
    val icon: String,
    val title: String,
    val subtitle: String,
    val explanation: String
)

private val points = listOf(
    LearningPoint(
        "🌱",
        "Fotossíntese",
        "Ciências • experiência visual",
        "O conteúdo aparece em camadas visuais: luz, água, gás carbônico e produção de alimento pela planta. Na versão final, o estudante também acessa uma explicação em Libras gravada e validada por pessoa fluente."
    ),
    LearningPoint(
        "⚠",
        "Alerta visual",
        "Acessibilidade • situação real",
        "O protótipo transforma um evento que normalmente seria apenas sonoro em luz, texto e vibração. A ideia é ensinar situações de segurança sem depender da audição."
    ),
    LearningPoint(
        "💧",
        "Ciclo da água",
        "Ciências • exploração 360°",
        "O estudante explora o cenário no próprio ritmo e acompanha evaporação, condensação e precipitação por pistas visuais. O conceito pode ser revisto quantas vezes forem necessárias."
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SinalVrApp() }
    }
}

@Composable
fun SinalVrApp() {
    val colors = darkColorScheme(
        primary = Cyan,
        secondary = Mint,
        background = Night,
        surface = Panel,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = colors) {
        var page by rememberSaveable { mutableStateOf("home") }
        Surface(modifier = Modifier.fillMaxSize(), color = Night) {
            if (page == "home") {
                HomeScreen(onEnter = { page = "experience" })
            } else {
                ExperienceScreen(onBack = { page = "home" })
            }
        }
    }
}

@Composable
private fun HomeScreen(onEnter: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = Cyan.copy(alpha = .14f)) {
                Text(
                    "SINAL VR • PROTÓTIPO",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Cyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Aprender com os olhos.\nExplorar com o corpo.",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 35.sp,
            lineHeight = 39.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Um material didático imersivo pensado para estudantes surdos: visual, explorável e acessível no próprio celular.",
            color = Soft,
            fontSize = 16.sp,
            lineHeight = 23.sp
        )

        Spacer(Modifier.height(24.dp))
        PreviewCard()

        Spacer(Modifier.height(22.dp))
        Text("A ideia em 4 respostas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        InfoCard("PARA QUEM?", "Estudantes surdos da Educação Básica e turmas bilíngues.", "◎")
        InfoCard("O QUE É?", "Ambientes virtuais com conteúdos visuais, Libras e desafios interativos.", "◇")
        InfoCard("POR QUÊ?", "Para reduzir a dependência de explicações sonoras e tornar conceitos mais visuais e espaciais.", "!")
        InfoCard("COMO?", "Celular Android + sensores de movimento. Pode ser usado na mão ou em visor VR simples tipo Cardboard.", "↻")

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onEnter,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Night),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("ENTRAR NA EXPERIÊNCIA  →", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Protótipo acadêmico • Libras deve ser gravada e validada por pessoas fluentes e pela comunidade surda.",
            color = Soft.copy(alpha = .72f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PreviewCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.65f)
            .background(
                Brush.linearGradient(listOf(Color(0xFF143743), Color(0xFF0A1B25))),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, Cyan.copy(alpha = .24f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = Color.White.copy(alpha = .08f)
            repeat(6) { i ->
                val x = size.width * i / 5f
                drawLine(grid, Offset(size.width / 2, size.height * .38f), Offset(x, size.height), 1.2f)
            }
            repeat(4) { i ->
                val y = size.height * (.55f + i * .12f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.2f)
            }
        }
        Column {
            Text("SALA DE CIÊNCIAS • 360°", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Explore. Observe.\nDescubra.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd),
            color = Mint,
            shape = CircleShape
        ) {
            Text("⌁", modifier = Modifier.padding(14.dp), color = Night, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun InfoCard(label: String, body: String, mark: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Panel, RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = .05f), RoundedCornerShape(18.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = CircleShape, color = Cyan.copy(alpha = .12f)) {
            Text(mark, modifier = Modifier.padding(10.dp), color = Cyan, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ExperienceScreen(onBack: () -> Unit) {
    var vrMode by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LearningPoint?>(points.first()) }
    val pose = rememberHeadPose()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = if (vrMode) 8.dp else 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(8.dp),
                color = Color.White,
                fontSize = 32.sp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("SALA IMERSIVA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    if (abs(pose.yaw) + abs(pose.pitch) > .05f) "sensor de movimento ativo" else "mova o celular para explorar",
                    color = Mint,
                    fontSize = 11.sp
                )
            }
            Surface(
                modifier = Modifier.clickable { vrMode = !vrMode },
                color = if (vrMode) Cyan else Panel,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (vrMode) "VR ATIVO" else "MODO VR",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    color = if (vrMode) Night else Cyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (vrMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ScenePane(
                    pose = pose,
                    selected = selected,
                    onSelect = {
                        selected = it
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                ScenePane(
                    pose = pose,
                    selected = selected,
                    onSelect = {
                        selected = it
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Text(
                "Modo Cardboard: coloque o celular na horizontal em um visor simples. O cenário reage ao movimento.",
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
                color = Soft,
                fontSize = 10.sp
            )
        } else {
            ScenePane(
                pose = pose,
                selected = selected,
                onSelect = {
                    selected = it
                    if (it.title == "Alerta visual") {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(visible = selected != null) {
                selected?.let { DetailCard(it) }
            }
        }
    }
}

@Composable
private fun ScenePane(
    pose: HeadPose,
    selected: LearningPoint?,
    onSelect: (LearningPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF173847), Color(0xFF0A202B), Color(0xFF07131C))
                ),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, Cyan.copy(alpha = .18f), RoundedCornerShape(24.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * .46f
            drawLine(Color.White.copy(alpha = .09f), Offset(0f, horizon), Offset(size.width, horizon), 2f)
            repeat(7) { i ->
                val x = size.width * i / 6f
                drawLine(
                    Color.White.copy(alpha = .06f),
                    Offset(size.width / 2f, horizon),
                    Offset(x, size.height),
                    1.5f
                )
            }
            repeat(5) { i ->
                val y = horizon + (size.height - horizon) * i / 4f
                drawLine(Color.White.copy(alpha = .05f), Offset(0f, y), Offset(size.width, y), 1.2f)
            }

            val boardLeft = size.width * .20f + (-pose.yaw * 18f)
            val boardTop = size.height * .12f + (pose.pitch * 18f)
            drawRoundRect(
                color = Color(0xFF0B2630),
                topLeft = Offset(boardLeft, boardTop),
                size = androidx.compose.ui.geometry.Size(size.width * .60f, size.height * .23f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
            )
            drawRoundRect(
                color = Cyan.copy(alpha = .22f),
                topLeft = Offset(boardLeft + 5f, boardTop + 5f),
                size = androidx.compose.ui.geometry.Size(size.width * .60f - 10f, size.height * .23f - 10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
                .graphicsLayer {
                    translationX = -pose.yaw * 28f
                    translationY = pose.pitch * 24f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CIÊNCIAS VISUAIS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Explore os pontos luminosos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Hotspot(
            point = points[0],
            selected = selected == points[0],
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 18.dp, y = (-10).dp)
                .graphicsLayer { translationX = -pose.yaw * 55f; translationY = pose.pitch * 35f },
            onClick = { onSelect(points[0]) }
        )

        Hotspot(
            point = points[1],
            selected = selected == points[1],
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp, y = 30.dp)
                .graphicsLayer { translationX = -pose.yaw * 45f; translationY = pose.pitch * 30f },
            onClick = { onSelect(points[1]) }
        )

        Hotspot(
            point = points[2],
            selected = selected == points[2],
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-38).dp)
                .graphicsLayer { translationX = -pose.yaw * 70f; translationY = pose.pitch * 44f },
            onClick = { onSelect(points[2]) }
        )

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = Night.copy(alpha = .72f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "↔ mova o celular",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                color = Soft,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun Hotspot(
    point: LearningPoint,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        color = if (selected) Cyan else Night.copy(alpha = .83f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (selected) 8.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(point.icon, fontSize = 19.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                point.title,
                color = if (selected) Night else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DetailCard(point: LearningPoint) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(22.dp))
            .padding(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(point.icon, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(point.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(point.subtitle, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Surface(color = Mint.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                Text("LIBRAS", modifier = Modifier.padding(9.dp), color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(point.explanation, color = Soft, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "▶ Área reservada para vídeo em Libras validado",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun rememberHeadPose(): HeadPose {
    val context = LocalContext.current
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val rotation = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                yaw = orientation[0].coerceIn(-1.2f, 1.2f)
                pitch = orientation[1].coerceIn(-1f, 1f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotation != null) {
            sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return HeadPose(yaw, pitch)
}
