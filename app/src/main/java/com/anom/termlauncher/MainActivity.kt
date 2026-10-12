package com.anom.termlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Calendar
import kotlin.random.Random
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

var BG by mutableStateOf(Color(0xFF181818))
var FG by mutableStateOf(Color(0xFFD4CBB8))
var DIM by mutableStateOf(Color(0xFF7A7468))
var ACC by mutableStateOf(Color(0xFFC8A15A))
var KEYBG by mutableStateOf(Color(0xFF262626))
val Mono = FontFamily.Monospace
const val PS = "atif@anom:~$"

data class App(val label: String, val pkg: String, val icon: ImageBitmap)

suspend fun loadApps(ctx: Context): List<App> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            App(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

class Term(private val ctx: Context) {
    val lines = mutableStateListOf("atif@anom launcher v0.2", "type 'help' for commands", "")
    var input by mutableStateOf("")
    var grid by mutableStateOf<List<App>?>(null)
    var sel by mutableIntStateOf(0)
    var apps by mutableStateOf(listOf<App>())
    var reloadTick by mutableIntStateOf(0)
    private val history = mutableListOf<String>()
    private var hIdx = 0
    private val prefs = ctx.getSharedPreferences("term", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    private var torch = false

    init { applyTheme(prefs.getString("theme", "charcoal") ?: "charcoal") }

    fun out(s: String) { s.split("\n").forEach { lines.add(it) } }
    fun type(s: String) { input += s }
    fun back() { input = input.dropLast(1) }

    fun enter() {
        if (grid != null && input.isEmpty()) launchSel() else submit()
    }

    fun arrow(dx: Int, dy: Int) {
        val g = grid
        if (g != null) {
            if (g.isNotEmpty()) sel = (sel + dx + dy * 4).coerceIn(0, g.size - 1)
        } else if (dy != 0 && history.isNotEmpty()) {
            hIdx = (hIdx + dy).coerceIn(0, history.size)
            input = history.getOrElse(hIdx) { "" }
        }
    }

    fun launch(a: App) {
        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it)
        }
    }

    private fun launchSel() { grid?.getOrNull(sel)?.let { launch(it) } }

    private fun submit() {
        val raw = input.trim()
        lines.add("$PS $raw")
        input = ""
        if (raw.isEmpty()) return
        history.add(raw)
        hIdx = history.size
        val p = raw.split(Regex("\\s+"))
        val c = p[0].lowercase()
        val a = p.drop(1)
        grid = null
        when (c) {
            "help" -> out(
                listOf(
                    "-- apps --",
                    "ls | app -l       app grid (4x4)",
                    "app -s <q>        search apps",
                    "app <name>        launch app",
                    "app -r            rescan apps",
                    "info <app>        app settings page",
                    "uninstall <app>",
                    "-- system --",
                    "fastfetch battery ram storage cpu",
                    "uptime uname ip net ver",
                    "torch [on|off]    flashlight",
                    "vol [up|down|0-100|mute|max]",
                    "settings wifi bt display camera",
                    "-- tools --",
                    "date time cal",
                    "calc <expr>       calc 2*(3+4)^2",
                    "timer <sec> [label]",
                    "note [text|-d n|-c]",
                    "copy <text>  paste",
                    "weather [city]",
                    "roll [n]  coin",
                    "-- web --",
                    "web <url>  google <q>  yt <q>",
                    "dial <number>",
                    "-- shell --",
                    "theme [name]  (charcoal green amber",
                    "               blue mono)",
                    "history echo clear whoami hostname pwd",
                    "",
                    "grid: tap app, or arrows + enter",
                    "back button closes the grid"
                ).joinToString("\n")
            )
            "clear", "cls" -> lines.clear()
            "date" -> out(fmt("EEEE, d MMMM yyyy"))
            "time", "clock" -> out(fmt("HH:mm:ss"))
            "fastfetch", "neofetch" -> fetch()
            "battery" -> out(battery())
            "ram", "free" -> { val m = mem(); out("${gb(m.totalMem - m.availMem)} / ${gb(m.totalMem)}") }
            "storage", "df" -> out(disk())
            "uptime" -> out(uptime())
            "uname" -> out("Linux ${System.getProperty("os.version")} ${Build.SUPPORTED_ABIS.firstOrNull()}")
            "whoami" -> out("atif")
            "hostname" -> out("anom")
            "echo" -> out(a.joinToString(" "))
            "pwd" -> out("/home/atif")
            "ver", "version" -> out("anomsh 0.2 - Android ${Build.VERSION.RELEASE}")
            "sudo" -> out("atif is not in the sudoers file. This incident will be reported.")
            "cal" -> out(cal())
            "calc", "bc" -> out(calc(a.joinToString(" ")))
            "ip" -> out(ip())
            "net" -> out(net())
            "cpu", "nproc" -> out("${Runtime.getRuntime().availableProcessors()} cores, ${Build.HARDWARE}, ${Build.SUPPORTED_ABIS.joinToString(",")}")
            "coin" -> out(if (Random.nextBoolean()) "heads" else "tails")
            "roll" -> { val n = a.firstOrNull()?.toIntOrNull()?.coerceAtLeast(2) ?: 6; out("d$n -> ${Random.nextInt(1, n + 1)}") }
            "torch", "flash", "flashlight" -> flash(a.firstOrNull())
            "vol", "volume" -> vol(a.firstOrNull())
            "timer" -> timer(a)
            "note", "notes" -> note(a)
            "copy" -> { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("term", a.joinToString(" "))); out("copied") }
            "paste" -> out(ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() ?: "clipboard empty")
            "weather", "wttr" -> weather(a.joinToString(" "))
            "web", "url" -> if (a.isEmpty()) out("usage: web <url>") else view(if (a[0].startsWith("http")) a[0] else "https://" + a[0])
            "google", "search" -> view("https://www.google.com/search?q=" + Uri.encode(a.joinToString(" ")))
            "yt", "youtube" -> view("https://www.youtube.com/results?search_query=" + Uri.encode(a.joinToString(" ")))
            "dial", "call" -> if (a.isEmpty()) out("usage: dial <number>") else go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a[0]))))
            "camera" -> go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "settings" -> sys(Settings.ACTION_SETTINGS)
            "wifi" -> sys(Settings.ACTION_WIFI_SETTINGS)
            "bt", "bluetooth" -> sys(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> sys(Settings.ACTION_DISPLAY_SETTINGS)
            "info" -> find(a)?.let { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.pkg}"))) }
            "uninstall" -> find(a)?.let { go(Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.pkg}"))) }
            "theme" -> theme(a)
            "history" -> history.forEachIndexed { i, h -> out("${i + 1}  $h") }
            "ls" -> app(listOf("-l"))
            "app" -> app(a)
            else -> out("$c: command not found")
        }
    }

    private fun app(a: List<String>) {
        if (a.isEmpty()) { out("usage: app -l | -s <q> | -r | <name>"); return }
        when (a[0]) {
            "-l", "--list" -> { grid = apps; sel = 0; out("${apps.size} apps") }
            "-r", "--rescan" -> { reloadTick++; out("rescanning...") }
            "-s", "--search" -> {
                val q = a.drop(1).joinToString(" ").lowercase()
                val m = apps.filter { it.label.lowercase().contains(q) }
                if (m.isEmpty()) out("no match for '$q'") else { grid = m; sel = 0 }
            }
            else -> {
                val q = a.joinToString(" ").lowercase()
                val exact = apps.firstOrNull { it.label.lowercase() == q }
                val m = apps.filter { it.label.lowercase().contains(q) }
                when {
                    exact != null -> { out("launching ${exact.label}"); launch(exact) }
                    m.size == 1 -> { out("launching ${m[0].label}"); launch(m[0]) }
                    m.isEmpty() -> out("app: '$q' not found")
                    else -> { grid = m; sel = 0; out("${m.size} matches") }
                }
            }
        }
    }

    private fun go(i: Intent) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
        catch (e: Exception) { out("error: ${e.message}") }
    }
    private fun sys(action: String) = go(Intent(action))
    private fun view(url: String) = go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun find(a: List<String>): App? {
        val q = a.joinToString(" ").lowercase()
        if (q.isEmpty()) { out("usage: <cmd> <app name>"); return null }
        val m = apps.filter { it.label.lowercase().contains(q) }
        val exact = m.firstOrNull { it.label.lowercase() == q }
        return when {
            exact != null -> exact
            m.isEmpty() -> { out("not found: $q"); null }
            m.size > 1 -> { out("ambiguous: " + m.take(5).joinToString(", ") { it.label }); null }
            else -> m[0]
        }
    }

    private fun cal(): String {
        val c = Calendar.getInstance()
        val today = c.get(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, 1)
        val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)
        val sb = StringBuilder(title.padStart((20 + title.length) / 2)).append("\nMo Tu We Th Fr Sa Su\n")
        val lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        repeat(lead) { sb.append("   ") }
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..days) {
            sb.append("%2d".format(d)).append(if (d == today) '*' else ' ')
            if ((lead + d) % 7 == 0) sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun calc(s: String): String {
        if (s.isBlank()) return "usage: calc 2*(3+4)^2   (+ - * / % ^ sqrt sin cos tan log ln abs pi e)"
        return try {
            val v = Expr(s.lowercase().replace(" ", "")).parse()
            if (v == v.toLong().toDouble() && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        } catch (x: Exception) { "calc: ${x.message ?: "bad expression"}" }
    }

    private fun ip(): String {
        val sb = StringBuilder()
        try {
            for (n in NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()) {
                if (!n.isUp || n.isLoopback) continue
                for (ad in n.inetAddresses.toList()) {
                    val h = ad.hostAddress ?: continue
                    if (!ad.isLoopbackAddress && !h.contains(":")) sb.append("${n.name}  $h\n")
                }
            }
        } catch (e: Exception) { return "ip: ${e.message}" }
        return sb.toString().trimEnd().ifEmpty { "no network" }
    }

    private fun net(): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "online"
        }
    }

    private fun flash(arg: String?) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull()
            if (id == null) { out("no camera"); return }
            torch = when (arg) { "on" -> true; "off" -> false; else -> !torch }
            cm.setTorchMode(id, torch)
            out("torch " + if (torch) "on" else "off")
        } catch (e: Exception) { out("torch: ${e.message}") }
    }

    private fun vol(arg: String?) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val n = when (arg) {
            null -> cur
            "up", "+" -> (cur + 1).coerceAtMost(max)
            "down", "-" -> (cur - 1).coerceAtLeast(0)
            "mute" -> 0
            "max" -> max
            else -> {
                val pct = arg.removeSuffix("%").toIntOrNull()
                if (pct == null) { out("usage: vol [up|down|mute|max|0-100]"); return }
                pct.coerceIn(0, 100) * max / 100
            }
        }
        try {
            if (arg != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0)
            out("volume ${n * 100 / max}%")
        } catch (e: Exception) { out("vol: ${e.message}") }
    }

    private fun timer(a: List<String>) {
        val sec = a.firstOrNull()?.toLongOrNull()
        if (sec == null || sec <= 0) { out("usage: timer <seconds> [label]"); return }
        val label = a.drop(1).joinToString(" ").ifEmpty { "timer" }
        out("[$label] set for ${sec}s")
        ui.postDelayed({
            out("[$label] time's up")
            try {
                ctx.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }, sec * 1000)
    }

    private fun note(a: List<String>) {
        val notes = (prefs.getString("notes", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        when {
            a.isEmpty() ->
                if (notes.isEmpty()) out("no notes. 'note <text>' to add")
                else notes.forEachIndexed { i, n -> out("${i + 1}. $n") }
            a[0] == "-c" -> { notes.clear(); out("notes cleared") }
            a[0] == "-d" -> {
                val i = (a.getOrNull(1)?.toIntOrNull() ?: 0) - 1
                if (i in notes.indices) { notes.removeAt(i); out("deleted") } else out("usage: note -d <number>")
            }
            else -> { notes.add(a.joinToString(" ")); out("saved") }
        }
        prefs.edit().putString("notes", notes.joinToString("\n")).apply()
    }

    private fun weather(city: String) {
        out("fetching...")
        Thread {
            val r = try {
                val c = URL("https://wttr.in/" + Uri.encode(city) + "?format=3").openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.setRequestProperty("User-Agent", "curl/8.0")
                c.inputStream.bufferedReader().use { it.readText() }.trim()
            } catch (e: Exception) { "weather: ${e.message}" }
            ui.post { out(r) }
        }.start()
    }

    private fun theme(a: List<String>) {
        val n = a.firstOrNull()?.lowercase()
        if (n == null) { out("themes: " + THEMES.keys.joinToString(" ")); return }
        if (applyTheme(n)) { prefs.edit().putString("theme", n).apply(); out("theme -> $n") }
        else out("unknown theme '$n'")
    }

    private fun fmt(p: String) = SimpleDateFormat(p, Locale.getDefault()).format(Date())
    private fun mem() = ActivityManager.MemoryInfo().also {
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }
    private fun gb(b: Long) = "%.1f GB".format(b / 1073741824.0)
    private fun disk(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        return "${gb(s.totalBytes - s.availableBytes)} / ${gb(s.totalBytes)}"
    }
    private fun uptime(): String {
        val s = SystemClock.elapsedRealtime() / 1000
        return "${s / 86400}d ${s % 86400 / 3600}h ${s % 3600 / 60}m"
    }
    private fun battery(): String {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "n/a"
        val scale = b.getIntExtra("scale", 100).coerceAtLeast(1)
        val lvl = b.getIntExtra("level", -1) * 100 / scale
        val st = b.getIntExtra("status", -1)
        val chg = st == 2 || st == 5
        return "$lvl%" + if (chg) " (charging)" else ""
    }

    private fun fetch() {
        val logo = listOf(
            "    .--.    ",
            "   |o_o |   ",
            "   |:_/ |   ",
            "  //   \\ \\  ",
            " (|     | ) ",
            "/'\\_   _/`\\ ",
            "\\___)=(___/ "
        )
        val dm = ctx.resources.displayMetrics
        val m = mem()
        val info = listOf(
            "atif@anom",
            "---------",
            "OS: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Host: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Kernel: ${System.getProperty("os.version")}",
            "Uptime: ${uptime()}",
            "Shell: anomsh",
            "Res: ${dm.widthPixels}x${dm.heightPixels}",
            "CPU: ${Build.HARDWARE}",
            "RAM: ${gb(m.totalMem - m.availMem)} / ${gb(m.totalMem)}",
            "Disk: ${disk()}",
            "Battery: ${battery()}",
            "Apps: ${apps.size}"
        )
        for (i in 0 until maxOf(logo.size, info.size)) {
            out((logo.getOrNull(i) ?: "").padEnd(14) + (info.getOrNull(i) ?: ""))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val t = remember { Term(applicationContext) }
            LaunchedEffect(t.reloadTick) { t.apps = loadApps(applicationContext) }
            Screen(t)
        }
    }
}

@Composable
fun Screen(t: Term) {
    val fr = remember { FocusRequester() }
    var blink by remember { mutableStateOf(true) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(Unit) { while (true) { delay(530); blink = !blink } }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    BackHandler { t.grid = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .systemBarsPadding()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> t.enter()
                    Key.Backspace -> t.back()
                    Key.DirectionLeft -> t.arrow(-1, 0)
                    Key.DirectionRight -> t.arrow(1, 0)
                    Key.DirectionUp -> t.arrow(0, -1)
                    Key.DirectionDown -> t.arrow(0, 1)
                    Key.Escape -> t.grid = null
                    else -> {
                        val cp = e.utf16CodePoint
                        if (cp >= 32 && !e.isCtrlPressed) t.type(String(Character.toChars(cp)))
                        else return@onPreviewKeyEvent false
                    }
                }
                true
            }
    ) {
        Text(
            SimpleDateFormat("EEE d MMM  HH:mm:ss", Locale.getDefault()).format(now),
            color = DIM, fontFamily = Mono, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        )

        val g = t.grid
        if (g != null) {
            AppGrid(t, g, Modifier.weight(1f))
        } else {
            val ls = rememberLazyListState()
            LaunchedEffect(t.lines.size) {
                if (t.lines.isNotEmpty()) ls.scrollToItem(t.lines.size - 1)
            }
            LazyColumn(
                state = ls,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                items(t.lines.size) { i ->
                    val l = t.lines[i]
                    Text(
                        if (l.isEmpty()) " " else l,
                        color = if (l.startsWith(PS)) ACC else FG,
                        fontFamily = Mono, fontSize = 12.sp, lineHeight = 15.sp
                    )
                }
            }
        }

        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = ACC)) { append("$PS ") }
                append(t.input)
                withStyle(SpanStyle(color = ACC)) { append(if (blink) "█" else " ") }
            },
            color = FG, fontFamily = Mono, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Keyboard(t)
    }
}

@Composable
fun AppGrid(t: Term, list: List<App>, modifier: Modifier = Modifier) {
    val pages = maxOf(1, (list.size + 15) / 16)
    val pager = rememberPagerState { pages }
    LaunchedEffect(t.sel) { pager.animateScrollToPage(t.sel / 16) }
    LaunchedEffect(pager.settledPage) {
        if (t.sel / 16 != pager.settledPage) t.sel = pager.settledPage * 16
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalPager(pager, Modifier.weight(1f)) { p ->
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                for (r in 0 until 4) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until 4) {
                            val idx = p * 16 + r * 4 + c
                            val a = list.getOrNull(idx)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (a != null) {
                                    val shape = RoundedCornerShape(6.dp)
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(3.dp)
                                            .clip(shape)
                                            .border(1.dp, if (idx == t.sel) ACC else Color.Transparent, shape)
                                            .clickable { t.launch(a) }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Image(a.icon, null, Modifier.size(40.dp))
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            a.label, color = FG, fontFamily = Mono, fontSize = 10.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            "page ${pager.currentPage + 1}/$pages  ·  ${list.size} apps  ·  swipe / arrows / tap",
            color = DIM, fontFamily = Mono, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(4.dp), textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Keyboard(t: Term) {
    var caps by remember { mutableStateOf(false) }
    fun ch(c: Char) {
        t.type(if (caps) c.uppercase() else c.toString())
        caps = false
    }
    fun label(c: Char) = if (caps) c.uppercase() else c.toString()

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF101010))
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Row { "1234567890".forEach { c -> KBtn(c.toString()) { t.type(c.toString()) } } }
        Row { "qwertyuiop".forEach { c -> KBtn(label(c)) { ch(c) } } }
        Row {
            Spacer(Modifier.weight(0.5f))
            "asdfghjkl".forEach { c -> KBtn(label(c)) { ch(c) } }
            Spacer(Modifier.weight(0.5f))
        }
        Row {
            KBtn("⇧", 1.5f, hl = caps) { caps = !caps }
            "zxcvbnm".forEach { c -> KBtn(label(c)) { ch(c) } }
            KBtn("⌫", 1.5f, onLong = { t.input = "" }) { t.back() }
        }
        Row {
            KBtn("-") { t.type("-") }
            KBtn(".") { t.type(".") }
            KBtn("/") { t.type("/") }
            KBtn("space", 2.5f) { t.type(" ") }
            KBtn("←") { t.arrow(-1, 0) }
            KBtn("↑") { t.arrow(0, -1) }
            KBtn("↓") { t.arrow(0, 1) }
            KBtn("→") { t.arrow(1, 0) }
            KBtn("⏎", 1.5f, hl = true) { t.enter() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.KBtn(
    label: String,
    w: Float = 1f,
    hl: Boolean = false,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .weight(w)
            .height(42.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hl) ACC else KEYBG)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (hl) BG else FG, fontFamily = Mono, fontSize = 15.sp)
    }
}


class Theme(val bg: Color, val fg: Color, val dim: Color, val acc: Color, val key: Color)

val THEMES = mapOf(
    "charcoal" to Theme(Color(0xFF181818), Color(0xFFD4CBB8), Color(0xFF7A7468), Color(0xFFC8A15A), Color(0xFF262626)),
    "green" to Theme(Color(0xFF0A0F0A), Color(0xFF9BE59B), Color(0xFF4F7A4F), Color(0xFF33FF66), Color(0xFF142014)),
    "amber" to Theme(Color(0xFF120D00), Color(0xFFFFB000), Color(0xFF7A5600), Color(0xFFFFD060), Color(0xFF201800)),
    "blue" to Theme(Color(0xFF1B1F2A), Color(0xFFC8D3F5), Color(0xFF5A6788), Color(0xFF82AAFF), Color(0xFF252B3B)),
    "mono" to Theme(Color(0xFF000000), Color(0xFFE0E0E0), Color(0xFF777777), Color(0xFFFFFFFF), Color(0xFF1C1C1C))
)

fun applyTheme(n: String): Boolean {
    val t = THEMES[n] ?: return false
    BG = t.bg; FG = t.fg; DIM = t.dim; ACC = t.acc; KEYBG = t.key
    return true
}

class Expr(private val s: String) {
    private var i = 0

    fun parse(): Double {
        val v = expr()
        if (i < s.length) throw IllegalArgumentException("unexpected '${s[i]}'")
        return v
    }

    private fun expr(): Double {
        var v = term()
        while (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val op = s[i++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = factor()
        while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
            val op = s[i++]
            val r = factor()
            v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
        }
        return v
    }

    private fun factor(): Double {
        if (i < s.length && s[i] == '-') { i++; return -factor() }
        if (i < s.length && s[i] == '+') { i++; return factor() }
        val b = atom()
        if (i < s.length && s[i] == '^') { i++; return Math.pow(b, factor()) }
        return b
    }

    private fun atom(): Double {
        if (i >= s.length) throw IllegalArgumentException("unexpected end")
        if (s[i] == '(') {
            i++
            val v = expr()
            if (i >= s.length || s[i] != ')') throw IllegalArgumentException("missing )")
            i++
            return v
        }
        val st = i
        if (s[i].isDigit() || s[i] == '.') {
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
        while (i < s.length && s[i].isLetter()) i++
        val name = s.substring(st, i)
        if (name.isEmpty()) throw IllegalArgumentException("unexpected '${s[i]}'")
        if (name == "pi") return Math.PI
        if (name == "e") return Math.E
        if (i >= s.length || s[i] != '(') throw IllegalArgumentException("unknown '$name'")
        i++
        val v = expr()
        if (i >= s.length || s[i] != ')') throw IllegalArgumentException("missing )")
        i++
        return when (name) {
            "sqrt" -> Math.sqrt(v)
            "sin" -> Math.sin(v)
            "cos" -> Math.cos(v)
            "tan" -> Math.tan(v)
            "log" -> Math.log10(v)
            "ln" -> Math.log(v)
            "abs" -> Math.abs(v)
            else -> throw IllegalArgumentException("unknown '$name'")
        }
    }
}
