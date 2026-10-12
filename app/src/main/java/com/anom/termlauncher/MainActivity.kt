package com.anom.termlauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import java.io.File
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
const val PS = "atif@anom:"

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

val HELP = listOf(
    "-- shell --",
    "anything else runs in sh",
    "files: /sdcard/atif@anom",
    "grant = allow file access",
    "cd <dir>  ls  cat  grep  ps ...",
    "tab = autocomplete",
    "ctrl+c stop  ctrl+l clear",
    "ctrl+u wipe line  ctrl+w word",
    "-- apps --",
    "apps | app -l     app grid",
    "app -o <name>     open app",
    "app -s <q>        search apps",
    "app -r            rescan apps",
    "info <app>  uninstall <app>",
    "-- quick open --",
    "call phone sms contacts camera mail",
    "(other apps: app -o <name>)",
    "-- system --",
    "fastfetch battery ram storage cpu",
    "net myip ver clock cal",
    "torch [on|off]",
    "vol [up|down|0-100|mute|max]",
    "settings wifi bt display",
    "statusbar [show|hide|toggle]",
    "-- tools --",
    "calc <expr>  timer <sec> [label]",
    "note [text|-d n|-c]",
    "copy <text>  paste",
    "weather [city]  roll [n]  coin",
    "-- fun --",
    "cmatrix (tap to exit)",
    "fortune  cowsay <t>  8ball <q>",
    "pass [len]  uuid  hack",
    "-- web --",
    "web <url>  google <q>  yt <q>",
    "call <number>",
    "-- shell --",
    "theme [name]  history  clear",
    "",
    "grid: tap app, or arrows + enter",
    "back button closes the grid"
).joinToString("\n")

val COMMANDS = listOf(
    "help", "clear", "cls", "clock", "fastfetch", "neofetch", "battery", "ram", "storage", "cpu",
    "myip", "net", "ver", "version", "sudo", "cal", "calc", "bc", "coin", "roll", "torch", "flash",
    "flashlight", "vol", "volume", "timer", "note", "notes", "copy", "paste", "weather", "wttr",
    "web", "url", "google", "search", "yt", "youtube", "dial", "call", "phone", "camera", "cam",
    "mail", "gmail", "email", "sms", "messages", "msg", "contacts", "settings",
    "wifi", "bt", "bluetooth", "display", "info", "uninstall", "theme", "history", "apps", "app",
    "statusbar", "status", "cd", "grant", "cmatrix", "fortune", "cowsay", "8ball", "pass", "uuid", "hack",
    "ls", "cat", "pwd", "echo", "grep", "ps", "top", "df", "free", "date", "uname", "uptime",
    "whoami", "id", "env", "ping", "getprop", "mkdir", "rm", "cp", "mv", "touch", "head", "tail",
    "wc", "find", "du", "sleep", "stat", "ip", "ifconfig", "netstat", "hostname", "nproc"
).distinct().sorted()

@Stable
class Term(private val ctx: Context) {
    val lines = mutableStateListOf("atif@anom launcher v0.3", "type 'help' for commands", "")
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
    private val fallbackHome: File = ctx.filesDir
    var home by mutableStateOf(fallbackHome)
    var cwd by mutableStateOf(fallbackHome)
    private var oldCwd: File = fallbackHome
    var hasStorage by mutableStateOf(false)
    var fx by mutableStateOf<String?>(null)
    @Volatile private var proc: Process? = null
    private var running = false
    var statusHidden by mutableStateOf(prefs.getBoolean("hide_status", true))
    private val shellPath = listOf("/system/bin/bash", "/system/xbin/bash")
        .firstOrNull { File(it).canExecute() } ?: "/system/bin/sh"

    init {
        applyTheme(prefs.getString("theme", "charcoal") ?: "charcoal")
        refreshHome()
        if (!hasStorage) out("no file access yet - type 'grant' to use /sdcard/atif@anom")
    }

    fun out(s: String) {
        s.split("\n").forEach { lines.add(it) }
        if (lines.size > 1100) repeat(lines.size - 1000) { lines.removeAt(0) }
    }
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

    fun prompt(): String {
        val p = cwd.path
        val h = home.path
        val shown = when {
            p == h -> "~"
            p.startsWith("$h/") -> "~" + p.removePrefix(h)
            else -> p
        }
        return "$PS$shown\$"
    }

    private fun submit() {
        refreshHome()
        val raw = input.trim()
        lines.add("${prompt()} $raw")
        input = ""
        if (raw.isEmpty()) return
        history.add(raw)
        hIdx = history.size
        val p = raw.split(Regex("\\s+"))
        val c = p[0].lowercase()
        val a = p.drop(1)
        grid = null
        when (c) {
            "help" -> out(HELP)
            "clear", "cls" -> lines.clear()
            "clock" -> out(fmt("EEEE, d MMMM yyyy  HH:mm:ss"))
            "fastfetch", "neofetch" -> fetch()
            "battery" -> out(battery())
            "ram" -> { val m = mem(); out("${gb(m.totalMem - m.availMem)} / ${gb(m.totalMem)}") }
            "storage" -> out(disk())
            "cpu" -> out("${Runtime.getRuntime().availableProcessors()} cores, ${Build.HARDWARE}, ${Build.SUPPORTED_ABIS.joinToString(",")}")
            "myip" -> out(ip())
            "net" -> out(net())
            "ver", "version" -> out("anomsh 0.3 - Android ${Build.VERSION.RELEASE}")
            "sudo" -> out("atif is not in the sudoers file. This incident will be reported.")
            "cal" -> out(cal())
            "calc", "bc" -> out(calc(a.joinToString(" ")))
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
            "yt", "youtube" ->
                if (a.isEmpty()) out("usage: yt <query>")
                else view("https://www.youtube.com/results?search_query=" + Uri.encode(a.joinToString(" ")))

            // direct app commands
            "dial", "call", "phone" ->
                if (a.isEmpty()) go(Intent(Intent.ACTION_DIAL))
                else go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a[0]))))
            "camera", "cam" -> go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "mail", "gmail", "email" -> openPkg("com.google.android.gm") { openCat(Intent.CATEGORY_APP_EMAIL) }
            "sms", "messages", "msg" -> openCat(Intent.CATEGORY_APP_MESSAGING)
            "contacts" -> openCat(Intent.CATEGORY_APP_CONTACTS)

            "grant" -> grant()
            "cmatrix" -> fx = "matrix"
            "fortune" -> out(FORTUNES.random())
            "cowsay" -> out(cowsay(a.joinToString(" ")))
            "8ball" -> out(if (a.isEmpty()) "ask a question: 8ball <q>" else EIGHT.random())
            "pass" -> out(pass(a.firstOrNull()?.toIntOrNull() ?: 16))
            "uuid" -> out(java.util.UUID.randomUUID().toString())
            "hack" -> hack()

            "settings" -> sys(Settings.ACTION_SETTINGS)
            "wifi" -> sys(Settings.ACTION_WIFI_SETTINGS)
            "bt", "bluetooth" -> sys(Settings.ACTION_BLUETOOTH_SETTINGS)
            "display" -> sys(Settings.ACTION_DISPLAY_SETTINGS)
            "info" -> find(a)?.let { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.pkg}"))) }
            "uninstall" -> find(a)?.let { go(Intent(Intent.ACTION_DELETE, Uri.parse("package:${it.pkg}"))) }
            "theme" -> theme(a)
            "statusbar", "status" -> statusbar(a)
            "history" -> history.forEachIndexed { i, h -> out("${i + 1}  $h") }
            "apps" -> app(listOf("-l"))
            "app" -> app(a)
            "cd" -> cd(a)
            else -> sh(raw)
        }
    }

    private fun app(a: List<String>) {
        if (a.isEmpty()) { out("usage: app -l | -o <name> | -s <q> | -r | <name>"); return }
        when (a[0]) {
            "-l", "--list" -> { grid = apps; sel = 0; out("${apps.size} apps") }
            "-r", "--rescan" -> { reloadTick++; out("rescanning...") }
            "-o", "--open" -> {
                val m = find(a.drop(1))
                if (m != null) { out("launching ${m.label}"); launch(m) }
            }
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

    // ---- real shell ----

    private fun cd(a: List<String>) {
        val arg = a.joinToString(" ")
        val target = when {
            arg.isEmpty() || arg == "~" -> home
            arg == "-" -> oldCwd
            arg.startsWith("~/") -> File(home, arg.drop(2))
            arg.startsWith("/") -> File(arg)
            else -> File(cwd, arg)
        }
        val d = try { target.canonicalFile } catch (e: Exception) { target }
        if (d.isDirectory) { oldCwd = cwd; cwd = d } else out("cd: $arg: no such directory")
    }

    private fun sh(cmd: String) {
        if (running) { out("busy - ctrl+c stops the running command"); return }
        running = true
        val dir = cwd
        Thread {
            var count = 0
            try {
                val pb = ProcessBuilder(shellPath, "-c", cmd)
                pb.directory(dir)
                pb.redirectErrorStream(true)
                pb.environment().apply {
                    put("HOME", home.path)
                    put("USER", "atif")
                    put("TERM", "dumb")
                    put("PWD", dir.path)
                    put("TMPDIR", ctx.cacheDir.path)
                    if (get("PATH").isNullOrEmpty()) put("PATH", "/system/bin:/system/xbin")
                }
                val p = pb.start()
                proc = p
                p.outputStream.close()
                val rd = p.inputStream.bufferedReader()
                val pending = ArrayList<String>()
                fun flush() {
                    if (pending.isEmpty()) return
                    val chunk = pending.joinToString("\n")
                    pending.clear()
                    ui.post { out(chunk) }
                }
                while (true) {
                    val l = rd.readLine() ?: break
                    count++
                    if (count > 5000) {
                        p.destroyForcibly()
                        pending.add("[output truncated]")
                        break
                    }
                    pending.add(l.replace("\t", "    "))
                    if (pending.size >= 100 || !rd.ready()) flush()
                }
                flush()
                rd.close()
                val code = p.waitFor()
                ui.post { if (code != 0 && code != 143 && code != 137) out("[exit $code]") }
            } catch (e: Exception) {
                ui.post { out("sh: ${e.message}") }
            } finally {
                proc = null
                ui.post { running = false }
            }
        }.start()
    }

    fun ctrl(c: Char) {
        when (c.lowercaseChar()) {
            'c' -> {
                if (running) { proc?.destroyForcibly(); out("^C") }
                else { lines.add("${prompt()} $input^C"); input = "" }
            }
            'l' -> lines.clear()
            'u' -> input = ""
            'w' -> {
                val s = input.trimEnd().substringBeforeLast(' ', "")
                input = if (s.isEmpty()) "" else "$s "
            }
            'p' -> arrow(0, -1)
            'n' -> arrow(0, 1)
        }
    }

    // ---- tab completion ----

    fun tab() {
        val raw = input
        val sp = raw.indexOf(' ')
        if (sp < 0) { complete("", raw, COMMANDS); return }
        val head0 = raw.substring(0, sp + 1)
        val cmd = raw.substring(0, sp).lowercase()
        val rest = raw.substring(sp + 1)
        val labels = apps.map { it.label.lowercase() }
        when (cmd) {
            "info", "uninstall" -> complete(head0, rest, labels)
            "app" -> when {
                rest.isEmpty() || (rest.startsWith("-") && !rest.contains(' ')) ->
                    complete(head0, rest, listOf("-l", "-o", "-s", "-r"))
                rest.startsWith("-o ") || rest.startsWith("-s ") ->
                    complete(head0 + rest.take(3), rest.drop(3), labels)
                else -> complete(head0, rest, labels)
            }
            "theme" -> complete(head0, rest, THEMES.keys.toList())
            "statusbar", "status" -> complete(head0, rest, listOf("show", "hide", "toggle"))
            "torch", "flash", "flashlight" -> complete(head0, rest, listOf("on", "off"))
            "vol", "volume" -> complete(head0, rest, listOf("up", "down", "mute", "max"))
            else -> completePath(raw)
        }
    }

    private fun complete(head: String, token: String, cands: List<String>) {
        val m = cands.filter { it.startsWith(token, ignoreCase = true) }.distinct()
        if (m.isEmpty()) return
        if (m.size == 1) {
            input = head + m[0] + (if (m[0].endsWith("/")) "" else " ")
            return
        }
        val lcp = m.reduce { x, y -> x.commonPrefixWith(y, ignoreCase = true) }
        if (lcp.length > token.length) input = head + lcp
        else { lines.add("${prompt()} $input"); out(m.take(60).joinToString("  ")) }
    }

    private fun completePath(raw: String) {
        val head = raw.substringBeforeLast(' ', "") + " "
        val tok = raw.substringAfterLast(' ')
        val slash = tok.lastIndexOf('/')
        val dirPart = if (slash >= 0) tok.substring(0, slash + 1) else ""
        val namePart = tok.substring(slash + 1)
        val dir = when {
            dirPart.startsWith("/") -> File(dirPart)
            dirPart.startsWith("~") -> File(home, dirPart.removePrefix("~").trimStart('/'))
            else -> File(cwd, dirPart)
        }
        val names = dir.listFiles()
            ?.map { if (it.isDirectory) it.name + "/" else it.name }
            ?.filter { !it.startsWith(".") || namePart.startsWith(".") }
            ?.sorted() ?: return
        complete(head + dirPart, namePart, names)
    }

    // ---- storage ----

    private fun storageGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun refreshHome() {
        if (!storageGranted()) { hasStorage = false; return }
        val d = File(Environment.getExternalStorageDirectory(), "atif@anom")
        if (!d.isDirectory && !d.mkdirs()) { hasStorage = false; return }
        hasStorage = true
        if (home.path == d.path) return
        val atHome = cwd.path == home.path
        home = d
        oldCwd = d
        if (atHome) cwd = d
        out("storage ready: ${d.path}")
    }

    private fun grant() {
        if (hasStorage) { out("file access already allowed: ${home.path}"); return }
        if (Build.VERSION.SDK_INT >= 30)
            go(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        else
            go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        out("allow 'All files access' (or Storage), then come back")
    }

    // ---- fun ----

    private fun cowsay(text: String): String {
        val words = text.ifBlank { "moo" }.split(Regex("\\s+"))
        val ls = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            if (cur.isEmpty()) cur = w
            else if (cur.length + 1 + w.length <= 24) cur += " $w"
            else { ls.add(cur); cur = w }
        }
        if (cur.isNotEmpty()) ls.add(cur)
        val width = ls.maxOf { it.length }
        val sb = StringBuilder(" " + "_".repeat(width + 2) + "\n")
        if (ls.size == 1) sb.append("< ${ls[0]} >\n")
        else ls.forEachIndexed { i, l ->
            val (x, y) = when (i) {
                0 -> "/" to "\\"
                ls.size - 1 -> "\\" to "/"
                else -> "|" to "|"
            }
            sb.append("$x ${l.padEnd(width)} $y\n")
        }
        sb.append(" " + "-".repeat(width + 2) + "\n")
        sb.append("        \\   ^__^\n")
        sb.append("         \\  (oo)\\_______\n")
        sb.append("            (__)\\       )\\/\\\n")
        sb.append("                ||----w |\n")
        sb.append("                ||     ||")
        return sb.toString()
    }

    private fun pass(n: Int): String {
        val len = n.coerceIn(4, 64)
        val set = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#%^&*-_=+"
        val r = java.security.SecureRandom()
        return (1..len).map { set[r.nextInt(set.length)] }.joinToString("")
    }

    private fun hack() {
        val steps = listOf(
            "[*] scanning 192.168.0.0/24 ...",
            "[+] host found: 192.168.0.1",
            "[*] bypassing firewall ...",
            "[+] firewall bypassed",
            "[*] injecting payload ...",
            "[+] root access granted",
            "[*] downloading the mainframe ...",
            "[!] just kidding. nothing happened."
        )
        steps.forEachIndexed { i, l -> ui.postDelayed({ out(l) }, i * 350L) }
    }

    // ---- misc ----

    private fun statusbar(a: List<String>) {
        statusHidden = when (a.firstOrNull()?.lowercase()) {
            "show", "on" -> false
            "hide", "off" -> true
            null, "toggle" -> !statusHidden
            else -> { out("usage: statusbar [show|hide|toggle]"); return }
        }
        prefs.edit().putBoolean("hide_status", statusHidden).apply()
        out("status bar " + if (statusHidden) "hidden" else "shown")
    }

    private fun openCat(category: String) =
        go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category))

    private fun openPkg(pkg: String, fallback: (() -> Unit)? = null) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        when {
            i != null -> go(i)
            fallback != null -> fallback()
            else -> out("not installed: $pkg")
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
    private var term: Term? = null

    private fun applyBars(hidden: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) c.hide(WindowInsetsCompat.Type.statusBars())
        else c.show(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) term?.let { applyBars(it.statusHidden); it.refreshHome() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val t = remember { Term(applicationContext) }
            term = t
            LaunchedEffect(t.reloadTick) { t.apps = loadApps(applicationContext) }
            LaunchedEffect(t.statusHidden) { applyBars(t.statusHidden) }
            Screen(t)
        }
    }
}

@Composable
fun Screen(t: Term) {
    BackHandler(enabled = t.fx != null) { t.fx = null }
    if (t.fx == "matrix") MatrixRain { t.fx = null } else Terminal(t)
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Text(
        SimpleDateFormat("EEE d MMM  HH:mm:ss", Locale.getDefault()).format(now),
        color = DIM, fontFamily = Mono, fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun InputLine(t: Term) {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(530); blink = !blink } }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = ACC)) { append(t.prompt() + " ") }
            append(t.input)
            withStyle(SpanStyle(color = ACC)) { append(if (blink) "█" else " ") }
        },
        color = FG, fontFamily = Mono, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun Terminal(t: Term) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    BackHandler { t.grid = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            .systemBarsPadding()
            .displayCutoutPadding()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.isCtrlPressed) {
                    val cc = when (e.key) {
                        Key.C -> 'c'; Key.L -> 'l'; Key.U -> 'u'; Key.W -> 'w'; Key.P -> 'p'; Key.N -> 'n'
                        else -> null
                    }
                    if (cc != null) { t.ctrl(cc); return@onPreviewKeyEvent true }
                }
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> t.enter()
                    Key.Backspace -> t.back()
                    Key.DirectionLeft -> t.arrow(-1, 0)
                    Key.DirectionRight -> t.arrow(1, 0)
                    Key.DirectionUp -> t.arrow(0, -1)
                    Key.DirectionDown -> t.arrow(0, 1)
                    Key.Escape -> t.grid = null
                    Key.Tab -> t.tab()
                    else -> {
                        val cp = e.utf16CodePoint
                        if (cp >= 32 && !e.isCtrlPressed) t.type(String(Character.toChars(cp)))
                        else return@onPreviewKeyEvent false
                    }
                }
                true
            }
    ) {
        Clock()

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

        InputLine(t)
        Keyboard(t)
        Spacer(Modifier.height(12.dp))
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
                                    val shape = RectangleShape
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
    var ctrl by remember { mutableStateOf(false) }
    var sym by remember { mutableStateOf(false) }
    fun ch(c: Char) {
        if (ctrl) { t.ctrl(c); ctrl = false; return }
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
        if (!sym) {
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
        } else {
            Row { "!@#\$%^&*()".forEach { c -> KBtn(c.toString()) { t.type(c.toString()) } } }
            Row { "|;:<>?~`_,".forEach { c -> KBtn(c.toString()) { t.type(c.toString()) } } }
            Row {
                "=+[]{}'\"\\".forEach { c -> KBtn(c.toString()) { t.type(c.toString()) } }
                KBtn("⌫", 1.5f, onLong = { t.input = "" }) { t.back() }
            }
        }
        Row {
            KBtn("ctrl", hl = ctrl) { ctrl = !ctrl }
            KBtn("tab") { t.tab() }
            KBtn(if (sym) "abc" else "sym", hl = sym) { sym = !sym }
            KBtn("-") { t.type("-") }
            KBtn(".") { t.type(".") }
            KBtn("/") { t.type("/") }
            KBtn("←") { t.arrow(-1, 0) }
            KBtn("↑") { t.arrow(0, -1) }
            KBtn("↓") { t.arrow(0, 1) }
            KBtn("→") { t.arrow(1, 0) }
        }
        Row {
            KBtn("space", 8f) { t.type(" ") }
            KBtn("⏎", 2f, hl = true) { t.enter() }
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
            .height(40.dp)
            .padding(2.dp)
            .clip(RectangleShape)
            .background(if (hl) ACC else KEYBG)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (hl) BG else FG, fontFamily = Mono, fontSize = if (label.length >= 4) 11.sp else 15.sp)
    }
}


class Theme(val bg: Color, val fg: Color, val dim: Color, val acc: Color, val key: Color)

val THEMES = linkedMapOf(
    "charcoal" to Theme(Color(0xFF181818), Color(0xFFD4CBB8), Color(0xFF7A7468), Color(0xFFC8A15A), Color(0xFF262626)),
    "green" to Theme(Color(0xFF0A0F0A), Color(0xFF9BE59B), Color(0xFF4F7A4F), Color(0xFF33FF66), Color(0xFF142014)),
    "amber" to Theme(Color(0xFF120D00), Color(0xFFFFB000), Color(0xFF7A5600), Color(0xFFFFD060), Color(0xFF201800)),
    "blue" to Theme(Color(0xFF1B1F2A), Color(0xFFC8D3F5), Color(0xFF5A6788), Color(0xFF82AAFF), Color(0xFF252B3B)),
    "mono" to Theme(Color(0xFF000000), Color(0xFFE0E0E0), Color(0xFF777777), Color(0xFFFFFFFF), Color(0xFF1C1C1C)),
    "dracula" to Theme(Color(0xFF282A36), Color(0xFFF8F8F2), Color(0xFF6272A4), Color(0xFFBD93F9), Color(0xFF343746)),
    "nord" to Theme(Color(0xFF2E3440), Color(0xFFD8DEE9), Color(0xFF616E88), Color(0xFF88C0D0), Color(0xFF3B4252)),
    "gruvbox" to Theme(Color(0xFF282828), Color(0xFFEBDBB2), Color(0xFF928374), Color(0xFFFABD2F), Color(0xFF3C3836)),
    "solarized" to Theme(Color(0xFF002B36), Color(0xFF93A1A1), Color(0xFF586E75), Color(0xFFB58900), Color(0xFF073642)),
    "monokai" to Theme(Color(0xFF272822), Color(0xFFF8F8F2), Color(0xFF75715E), Color(0xFFA6E22E), Color(0xFF3E3D32)),
    "tokyonight" to Theme(Color(0xFF1A1B26), Color(0xFFC0CAF5), Color(0xFF565F89), Color(0xFF7AA2F7), Color(0xFF24283B)),
    "catppuccin" to Theme(Color(0xFF1E1E2E), Color(0xFFCDD6F4), Color(0xFF6C7086), Color(0xFFCBA6F7), Color(0xFF313244)),
    "onedark" to Theme(Color(0xFF282C34), Color(0xFFABB2BF), Color(0xFF5C6370), Color(0xFF61AFEF), Color(0xFF3E4451)),
    "rosepine" to Theme(Color(0xFF191724), Color(0xFFE0DEF4), Color(0xFF6E6A86), Color(0xFFEBBCBA), Color(0xFF26233A)),
    "everforest" to Theme(Color(0xFF2D353B), Color(0xFFD3C6AA), Color(0xFF859289), Color(0xFFA7C080), Color(0xFF343F44)),
    "kanagawa" to Theme(Color(0xFF1F1F28), Color(0xFFDCD7BA), Color(0xFF727169), Color(0xFFE6C384), Color(0xFF2A2A37)),
    "ayu" to Theme(Color(0xFF0B0E14), Color(0xFFBFBDB6), Color(0xFF565B66), Color(0xFFFFB454), Color(0xFF131721))
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

val FORTUNES = listOf(
    "It works on my machine.",
    "There is no cloud, only someone else's computer.",
    "Have you tried turning it off and on again?",
    "A watched compile never finishes.",
    "Weeks of coding can save you hours of planning.",
    "rm -rf is forever. Think twice.",
    "The best code is the code you didn't write.",
    "Today's bug is tomorrow's feature.",
    "sudo make me a sandwich.",
    "Commit early, commit often, push carefully."
)

val EIGHT = listOf(
    "It is certain.", "Without a doubt.", "Yes, definitely.", "Most likely.", "Signs point to yes.",
    "Reply hazy, try again.", "Ask again later.", "Cannot predict now.",
    "Don't count on it.", "My sources say no.", "Very doubtful."
)

const val GLYPHS = "ｦｱｳｴｵｶｷｹｺｻｼｽｾﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ0123456789:.=*+-<>|"

class Rain(val cols: Int, val rows: Int) {
    private val rnd = java.util.Random()
    val head = IntArray(cols) { -rnd.nextInt(rows) }
    val len = IntArray(cols) { 6 + rnd.nextInt(20) }
    val chars = CharArray(cols * rows) { pick() }

    private fun pick() = GLYPHS[rnd.nextInt(GLYPHS.length)]

    fun step() {
        for (c in 0 until cols) {
            head[c]++
            val h = head[c]
            if (h in 0 until rows) chars[c * rows + h] = pick()
            if (h - len[c] >= rows) {
                head[c] = -rnd.nextInt(rows / 2 + 1)
                len[c] = 6 + rnd.nextInt(20)
            }
            if (rnd.nextInt(4) == 0) {
                val rr = h - rnd.nextInt(len[c])
                if (rr in 0 until rows) chars[c * rows + rr] = pick()
            }
        }
    }
}

@Composable
fun MatrixRain(onExit: () -> Unit) {
    val fr = remember { FocusRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    val d = LocalDensity.current.density
    val cw = 12f * d
    val chh = 17f * d
    val rain = remember(size) {
        if (size.width > 0 && size.height > 0)
            Rain((size.width / cw).toInt().coerceAtLeast(1), (size.height / chh).toInt().coerceAtLeast(1))
        else null
    }
    val paint = remember {
        android.graphics.Paint().apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    paint.textSize = chh * 0.85f
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(rain) {
        while (rain != null) { delay(60); rain.step(); tick++ }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) { onExit(); true } else false }
            .pointerInput(Unit) { detectTapGestures { onExit() } }
    ) {
        val r = rain
        if (r == null || tick < 0) return@Canvas
        drawRect(Color.Black)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            for (col in 0 until r.cols) {
                val h = r.head[col]
                val l = r.len[col]
                val x = col * cw + cw / 2
                for (k in 0 until l) {
                    val row = h - k
                    if (row < 0 || row >= r.rows) continue
                    paint.color =
                        if (k == 0) android.graphics.Color.rgb(215, 255, 215)
                        else android.graphics.Color.argb((230 * (l - k) / l).coerceAtLeast(25), 0, 255, 65)
                    nc.drawText(r.chars, col * r.rows + row, 1, x, row * chh + chh * 0.85f, paint)
                }
            }
        }
    }
}
