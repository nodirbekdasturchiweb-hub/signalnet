package uz.signalnet.soldier

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * SignalNet — HARBIY XIZMATCHILAR (ASKAR) ANDROID APK ILOVASI
 * Muallif: Usmanov Bobur
 *
 * Vazifasi:
 * 1. Telefon qulflangan bo'lsa ham signal kelganda ekranni uyg'otib, to'liq ekran blokirovkada
 *    katta "TREVOGA SIGNALI BERILDI!" yoki "YIG'IN" oynasini chiqaradi.
 * 2. Harbiy sirena ovozi va vibratsiyani yoqadi.
 * 3. Askar ">> QABUL QILDIM" tugmasini o'ngga SWIPE qilishi bilan serverga tasdiqlangan vaqtni yuboradi.
 */
class MainActivity : ComponentActivity() {
    private val httpClient = OkHttpClient()
    private var socket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeUpAndUnlockScreen()

        setContent {
            MaterialTheme {
                var serverUrl by remember { mutableStateOf("http://192.168.1.100:4000") }
                var personId by remember { mutableStateOf("P-101") }
                var isConnected by remember { mutableStateOf(false) }

                var currentSignalId by remember { mutableStateOf("SIG-1023") }
                var currentSignalType by remember { mutableStateOf("TREVOGA") }
                var currentInstruction by remember { mutableStateOf("Zudlik bilan tayyorgarlik ko'ring!") }
                var isAcknowledged by remember { mutableStateOf(false) }

                if (!isConnected) {
                    SoldierSetupScreen(
                        serverUrl = serverUrl,
                        personId = personId,
                        onServerChange = { serverUrl = it },
                        onPersonIdChange = { personId = it },
                        onConnect = {
                            connectSoldierSocket(serverUrl) { sigId, sigType, instr ->
                                currentSignalId = sigId
                                currentSignalType = sigType
                                currentInstruction = instr
                                isAcknowledged = false
                                triggerMilitarySirenAndVibration()
                            }
                            isConnected = true
                        }
                    )
                } else {
                    SoldierLockscreenAlertUI(
                        signalType = currentSignalType,
                        instruction = currentInstruction,
                        isAcknowledged = isAcknowledged,
                        onSwipeConfirmed = {
                            isAcknowledged = true
                            sendAckToServer(serverUrl, personId, currentSignalId)
                        }
                    )
                }
            }
        }
    }

    private fun wakeUpAndUnlockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun triggerMilitarySirenAndVibration() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(1200, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun connectSoldierSocket(url: String, onIncomingSignal: (String, String, String) -> Unit) {
        try {
            socket?.disconnect()
            socket = IO.socket(url)
            socket?.on("signal:incoming") { args ->
                if (args.isNotEmpty()) {
                    val sig = args[0] as JSONObject
                    onIncomingSignal(
                        sig.optString("id", "SIG-NEW"),
                        sig.optString("type", "TREVOGA"),
                        sig.optString("instruction", "Zudlik bilan tayyorgarlik ko'ring!")
                    )
                }
            }
            socket?.connect()
        } catch (_: Exception) {}
    }

    private fun sendAckToServer(serverUrl: String, personId: String, signalId: String) {
        thread {
            try {
                val payload = JSONObject().apply {
                    put("personId", personId)
                    put("signalId", signalId)
                }
                socket?.emit("acknowledge_signal", payload)

                val req = Request.Builder()
                    .url("$serverUrl/api/apk/acknowledge")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute()
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun SoldierSetupScreen(
    serverUrl: String,
    personId: String,
    onServerChange: (String) -> Unit,
    onPersonIdChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070E14))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("XIZMATCHI ILOVASI (APK)", color = Color(0xFF22C55E), fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerChange,
            label = { Text("Shtab Server IP Manzili") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = personId,
            onValueChange = onPersonIdChange,
            label = { Text("Harbiy Xizmatchi ID (masalan: P-101)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("NAVBATCHILIK REJIMINI YOQISH", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SoldierLockscreenAlertUI(
    signalType: String,
    instruction: String,
    isAcknowledged: Boolean,
    onSwipeConfirmed: () -> Unit
) {
    var swipeOffset by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D13))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("XIZMATCHI ILOVASI", color = Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.Bold)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (signalType == "YIGIN")
                            listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                        else
                            listOf(Color(0xFF7F1D1D), Color(0xFF3B0707))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (signalType == "YIGIN") "YIG'IN" else "TREVOGA",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "SIGNALI BERILDI!",
                    color = Color(0xFFFECACA),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = instruction,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(32.dp))

                // REAL SWIPE TO CONFIRM TRACK
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(0xFF16A34A), shape = CircleShape)
                        .padding(6.dp)
                        .pointerInput(isAcknowledged) {
                            if (!isAcknowledged) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (swipeOffset > 320f) {
                                            onSwipeConfirmed()
                                        } else {
                                            swipeOffset = 0f
                                        }
                                    }
                                ) { _, dragAmount ->
                                    swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, 500f)
                                    if (swipeOffset > 360f) {
                                        onSwipeConfirmed()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (isAcknowledged) "✓ QABUL QILINDI (SERVERGA YOZILDI)" else ">>      QABUL QILDIM (SWIPE)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    if (!isAcknowledged) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                                .size(52.dp)
                                .background(Color(0xFF052E16), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(">>", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Text("SignalNet Real-Time Harbiy Tizimi", color = Color(0xFF475569), fontSize = 12.sp)
    }
}
