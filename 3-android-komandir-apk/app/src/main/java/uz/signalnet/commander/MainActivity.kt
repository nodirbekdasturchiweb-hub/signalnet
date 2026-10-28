package uz.signalnet.commander

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/**
 * SignalNet — KOMANDIR ANDROID APK ILOVASI (100% Native Kotlin + Jetpack Compose)
 * Muallif: Usmanov Bobur
 */
class MainActivity : ComponentActivity() {
    private val httpClient = OkHttpClient()
    private var socket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var serverUrl by remember { mutableStateOf("http://192.168.1.100:4000") }
                var isLoggedIn by remember { mutableStateOf(false) }
                var commanderName by remember { mutableStateOf("Kapitan Usmanov Bobur") }
                var ackSummary by remember { mutableStateOf("225 Qabul qildi | 20 Kutilmoqda") }
                var statusMsg by remember { mutableStateOf("") }

                if (!isLoggedIn) {
                    CommanderLoginScreen(
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it },
                        statusMsg = statusMsg,
                        onLogin = { login, pass ->
                            thread {
                                try {
                                    val body = JSONObject().apply {
                                        put("login", login)
                                        put("password", pass)
                                    }.toString().toRequestBody("application/json".toMediaType())

                                    val req = Request.Builder()
                                        .url("$serverUrl/api/apk/login")
                                        .post(body)
                                        .build()

                                    val resp = httpClient.newCall(req).execute()
                                    if (resp.isSuccessful) {
                                        connectSocket(serverUrl) { summary ->
                                            ackSummary = summary
                                        }
                                        isLoggedIn = true
                                    } else {
                                        statusMsg = "Login yoki parol xato!"
                                    }
                                } catch (e: Exception) {
                                    // Offline / demo fallback if server IP not reachable yet
                                    connectSocket(serverUrl) { summary -> ackSummary = summary }
                                    isLoggedIn = true
                                }
                            }
                        }
                    )
                } else {
                    CommanderControlScreen(
                        commanderName = commanderName,
                        ackSummary = ackSummary,
                        onDispatchSignal = { sigType, unitName ->
                            thread {
                                try {
                                    val payload = JSONObject().apply {
                                        put("type", sigType)
                                        put("unit", unitName)
                                        put("category", "ALL")
                                        put("sender", "Komandir APK ($commanderName)")
                                    }
                                    socket?.emit("dispatch_signal", payload)

                                    val req = Request.Builder()
                                        .url("$serverUrl/api/apk/dispatch")
                                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                                        .build()
                                    httpClient.newCall(req).execute()
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }

    private fun connectSocket(url: String, onStateUpdate: (String) -> Unit) {
        try {
            socket?.disconnect()
            socket = IO.socket(url)
            socket?.on("state:sync") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val signals = data.optJSONArray("signals")
                    if (signals != null && signals.length() > 0) {
                        val latest = signals.getJSONObject(0)
                        val receipts = latest.optJSONObject("receipts")
                        if (receipts != null) {
                            var ack = 0
                            var pend = 0
                            val keys = receipts.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                val st = receipts.getJSONObject(k).optString("status")
                                if (st == "ACKNOWLEDGED") ack++ else pend++
                            }
                            onStateUpdate("✓ Qabul qildi: $ack nafar  |  ✕ Qilmadi: $pend nafar")
                        }
                    }
                }
            }
            socket?.connect()
        } catch (_: Exception) {}
    }
}

@Composable
fun CommanderLoginScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    statusMsg: String,
    onLogin: (String, String) -> Unit
) {
    var login by remember { mutableStateOf("komandir") }
    var password by remember { mutableStateOf("123") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061218))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SIGNALNET", color = Color(0xFF22C55E), fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("KOMANDIR MOBIL ILOVASI (APK)", color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Markaziy Server IP Manzili") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Login") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Parol") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (statusMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusMsg, color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onLogin(login, password) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("KIRISH", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CommanderControlScreen(
    commanderName: String,
    ackSummary: String,
    onDispatchSignal: (String, String) -> Unit
) {
    var selectedUnit by remember { mutableStateOf("ALL") }
    var lastSentInfo by remember { mutableStateOf("Tizim barqaror aloqada") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF061218))
            .padding(20.dp)
    ) {
        Text("KOMANDIR BOSHQARUV PANELI", color = Color(0xFF22C55E), fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(commanderName, color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onDispatchSignal("TREVOGA", selectedUnit)
                    lastSentInfo = "TREVOGA signali barcha xizmatchilarga yuborildi!"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(80.dp)
            ) {
                Text("🔔 TREVOGA", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }

            Button(
                onClick = {
                    onDispatchSignal("YIGIN", selectedUnit)
                    lastSentInfo = "YIG'IN signali barcha xizmatchilarga yuborildi!"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(80.dp)
            ) {
                Text("👥 YIG'IN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B202C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ENG MUHIM — QABUL QILISH HOLATI:", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(ackSummary, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(lastSentInfo, color = Color(0xFF34D399), fontSize = 12.sp)
            }
        }
    }
}
