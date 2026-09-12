// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.scenepreset.editor.data.AppEntry
import com.scenepreset.editor.data.CpuClusterInfo
import com.scenepreset.editor.data.HistoryCodec
import com.scenepreset.editor.data.HistoryEntry
import com.scenepreset.editor.data.HistoryKind
import com.scenepreset.editor.data.CoreTopology
import com.scenepreset.editor.data.FILE_CUSTOM_TOPOLOGY
import com.scenepreset.editor.data.SETTING_LOG_CAP_KB
import com.scenepreset.editor.data.buildCoreMask
import com.scenepreset.editor.data.parseCoreSpec
import com.scenepreset.editor.data.SETTING_SCENE_VERSION
import com.scenepreset.editor.data.SETTING_IGNORE_VERSION_PROMPT
import com.scenepreset.editor.data.SceneRepository
import com.scenepreset.editor.data.ThreadEditorContext
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.ThreadRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Date
import java.util.Locale
import java.io.File

private const val PREFS_NAME = "thread_editor_prefs"
private const val DEFAULT_SCENE_BASE = "/data/user/0/com.omarea.vtools/files"
private const val SCENE_PACKAGE = "com.omarea.vtools"
private const val TAG = "ThreadEditor"

/**
 * Consent-based on-device logger. Nothing is captured unless the user turns
 * on "log recording" in Settings; writes to the app-private log file.
 */
internal object EditorLog {
    private var file: File? = null
    @Volatile private var maxBytes = 0L
    private val sessionId = java.util.UUID.randomUUID().toString().take(8)

    fun attach(f: File?) {
        synchronized(this) { file = f }
    }

    fun setMaxBytes(bytes: Long) {
        maxBytes = bytes.coerceAtLeast(0L)
        // Disabling logging must also remove the previous log file.
        if (maxBytes == 0L) clear()
    }

    /** Empties the log file in place, keeping it attached for future writes. */
    fun clear() {
        synchronized(this) {
            runCatching {
                file?.let { f ->
                    if (f.exists()) f.writeText("")
                }
            }
        }
    }

    fun isEnabled(): Boolean = maxBytes > 0L

    fun i(domain: String, message: String) = write("INFO", domain, message, null)

    fun w(domain: String, message: String) = write("WARN", domain, message, null)

    fun e(domain: String, message: String) = write("ERROR", domain, message, null)

    fun op(domain: String, message: String, durationMs: Long) = write("INFO", domain, message, durationMs)

    private fun write(level: String, domain: String, message: String, durationMs: Long?) {
        if (!isEnabled()) return
        val f = file ?: return
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val sb = StringBuilder()
            .append('[').append(ts).append("] ")
            .append(level).append(' ').append(domain).append(' ')
            .append("session=").append(sessionId)
        if (durationMs != null) sb.append(" duration_ms=").append(durationMs)
        sb.append(' ').append(message)
        try {
            f.parentFile?.mkdirs()
            synchronized(this) {
                f.appendText(sb.toString() + "\n")
                prune(f)
            }
        } catch (_: Exception) {
            // Logging must never break the editor.
        }
    }

    private fun prune(f: File) {
        if (maxBytes <= 0L) return
        if (f.length() <= maxBytes) return
        runCatching {
            val bytes = f.readBytes()
            if (bytes.size <= maxBytes) return
            val tail = String(bytes, bytes.size - maxBytes.toInt(), maxBytes.toInt(), Charsets.UTF_8)
                .substringAfter('\n')
            f.writeText(tail)
        }
    }

    fun content(): String = runCatching { file?.takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")

    fun currentSize(): Long = runCatching { file?.takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L)
}

@Composable
fun rememberThreadEditorContext(): ThreadEditorContext {
    val appContext = LocalContext.current.applicationContext
    val activity = LocalContext.current as? Activity
    val prefs = remember { appContext.getSharedPreferences(PREFS_NAME, 0) }

    val pendingImport = remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val pendingExport = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val pendingLogExport = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val pendingCategoriesImport = remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val pendingCategoriesExport = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val lastExportContent = remember { mutableStateOf<String?>(null) }
    val lastLogContent = remember { mutableStateOf<String?>(null) }
    val lastCategoriesContent = remember { mutableStateOf<String?>(null) }

    // Attach the consent-based logger to the app-private log file.
    remember {
        EditorLog.attach(File(appContext.cacheDir, "logs/thread_editor.log"))
        EditorLog.setMaxBytes(prefs.getInt(SETTING_LOG_CAP_KB, 0) * 1024L)
        Unit
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val callback = pendingImport.value
        pendingImport.value = null
        callback?.invoke(uri?.let { readUriText(appContext, it) })
    }

    val logExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        val callback = pendingLogExport.value
        val content = lastLogContent.value
        pendingLogExport.value = null
        lastLogContent.value = null
        val ok = uri != null && content != null && writeUriText(appContext, uri, content)
        callback?.invoke(ok)
    }

    val categoriesImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val callback = pendingCategoriesImport.value
        pendingCategoriesImport.value = null
        callback?.invoke(uri?.let { readUriText(appContext, it) })
    }

    val categoriesExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val callback = pendingCategoriesExport.value
        val content = lastCategoriesContent.value
        pendingCategoriesExport.value = null
        lastCategoriesContent.value = null
        val ok = uri != null && content != null && writeUriText(appContext, uri, content)
        callback?.invoke(ok)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val callback = pendingExport.value
        val content = lastExportContent.value
        pendingExport.value = null
        lastExportContent.value = null
        val ok = uri != null && content != null && writeUriText(appContext, uri, content)
        callback?.invoke(ok)
    }

    return remember {
        object : ThreadEditorContext {
            private val appCache = object : LinkedHashMap<String, AppEntry>(128, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AppEntry>?): Boolean = size > 1024
            }
            private val appCacheLock = Any()
            private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

            override val sceneRepository: SceneRepository = SceneRepositoryImpl(prefs, File(appContext.filesDir, "backups"))

            override fun defaultSceneBasePath(): String = DEFAULT_SCENE_BASE

            override fun sceneVersion(): SceneVersion =
                SceneVersion.fromId(prefs.getString(SETTING_SCENE_VERSION, null))

            override fun setSceneVersion(version: SceneVersion) {
                prefs.edit().putString(SETTING_SCENE_VERSION, version.id).apply()
            }

            override fun installedSceneVersion(): SceneVersion? = runCatching {
                val pm = appContext.packageManager
                val info = pm.getPackageInfo(SCENE_PACKAGE, 0)
                SceneVersion.fromVersionName(info.versionName ?: "")
            }.getOrNull()

            override fun isSceneInstalled(): Boolean = runCatching {
                appContext.packageManager.getPackageInfo(SCENE_PACKAGE, 0)
                true
            }.getOrDefault(false)

            override fun ignoreVersionPrompt(): Boolean =
                prefs.getBoolean(SETTING_IGNORE_VERSION_PROMPT, false)

            override fun setIgnoreVersionPrompt(ignored: Boolean) {
                prefs.edit().putBoolean(SETTING_IGNORE_VERSION_PROMPT, ignored).apply()
            }

            override fun reloadApp() {
                activity?.recreate()
            }

            override fun importJson(onResult: (String?) -> Unit) {
                pendingImport.value = onResult
                importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*"))
            }

            override fun exportJson(defaultName: String, content: String, onResult: (Boolean) -> Unit) {
                pendingExport.value = onResult
                lastExportContent.value = content
                exportLauncher.launch(defaultName)
            }

            override fun importCategories(onResult: (String?) -> Unit) {
                pendingCategoriesImport.value = onResult
                categoriesImportLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*"))
            }

            override fun exportCategories(defaultName: String, content: String, onResult: (Boolean) -> Unit) {
                pendingCategoriesExport.value = onResult
                lastCategoriesContent.value = content
                categoriesExportLauncher.launch(defaultName)
            }

            override fun readSetting(key: String): String? = prefs.getString(key, null)

            override fun writeSetting(key: String, value: String) {
                prefs.edit().putString(key, value).apply()
            }

            override fun log(message: String) {
                EditorLog.i("ui", message)
            }

            override fun logCapKb(): Int = prefs.getInt(SETTING_LOG_CAP_KB, 0)

            override fun setLogCapKb(kb: Int) {
                val capped = kb.coerceIn(0, 1024)
                prefs.edit().putInt(SETTING_LOG_CAP_KB, capped).apply()
                EditorLog.setMaxBytes(capped * 1024L)
            }

            override fun clearLogs() {
                EditorLog.clear()
            }

            override fun exportLogs(onResult: (Boolean) -> Unit) {
                pendingLogExport.value = onResult
                lastLogContent.value = EditorLog.content()
                logExportLauncher.launch("thread_editor_log.txt")
            }

            override fun warmAppCache(packageNames: List<String>) {
                val missing = packageNames.filter { pkg ->
                    synchronized(appCacheLock) { appCache[pkg] }?.icon != null
                }
                if (missing.isEmpty()) return
                appScope.launch(Dispatchers.Default) {
                    missing.forEach { resolveApp(it) }
                }
            }

            override fun resolveApp(packageName: String): AppEntry {
                synchronized(appCacheLock) { appCache[packageName] }?.let { return it }
                val entry = try {
                    val pm = appContext.packageManager
                    val ai = pm.getApplicationInfo(packageName, 0)
                    val label = pm.getApplicationLabel(ai).toString()
                    val icon = runCatching {
                        drawableToBitmap(pm.getApplicationIcon(ai), 96).asImageBitmap()
                    }.getOrNull()
                    AppEntry(packageName, label, firstLetter(label, packageName), icon)
                } catch (e: Exception) {
                    AppEntry(packageName, packageName, firstLetter(packageName, packageName), null)
                }
                synchronized(appCacheLock) { appCache[packageName] = entry }
                return entry
            }

            // Full app-list scans (icon decode for hundreds of packages) cost seconds;
            // cache per filter flag. resolveApp() keeps its own per-package LRU.
            @Volatile private var userAppsCache: List<AppEntry>? = null
            @Volatile private var allAppsCache: List<AppEntry>? = null

            override suspend fun installedApps(includeSystem: Boolean): List<AppEntry> =
                withContext(Dispatchers.IO) {
                    (if (includeSystem) allAppsCache else userAppsCache)?.let { return@withContext it }
                    val pm = appContext.packageManager
                    try {
                        val all = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        val visible = if (includeSystem) all else all.filter {
                            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        }
                        val result = visible.map { info ->
                            val label = pm.getApplicationLabel(info).toString()
                            val icon = runCatching {
                                drawableToBitmap(pm.getApplicationIcon(info), 96).asImageBitmap()
                            }.getOrNull()
                            AppEntry(info.packageName, label, firstLetter(label, info.packageName), icon)
                        }.sortedBy { it.label.lowercase() }
                        if (includeSystem) allAppsCache = result else userAppsCache = result
                        result
                    } catch (e: Exception) {
                        Log.w(TAG, "installedApps failed", e)
                        emptyList()
                    }
                }

            override suspend fun cpuClusters(): List<CpuClusterInfo> =
                withContext(Dispatchers.IO) {
                    clustersCache?.let { return@withContext it }
                    val result = effectiveTopology().clusters.map { cluster ->
                        CpuClusterInfo(cluster.label, parseCoreSpec(cluster.cores, customTopology()?.coreCount ?: Int.MAX_VALUE).sorted())
                    }
                    clustersCache = result
                    result
                }

            @Volatile private var clustersCache: List<CpuClusterInfo>? = null

            private fun topologyFile(): File = File(appContext.filesDir, FILE_CUSTOM_TOPOLOGY)

            @Volatile private var customCache: CoreTopology? = null

            private fun customTopology(): CoreTopology? {
                customCache?.let { return it }
                val f = topologyFile()
                if (!f.exists()) return null
                return runCatching { CoreTopology.decode(f.readText()) }
                    .getOrNull()
                    ?.also { customCache = it }
            }

            @Volatile private var detectedCache: CoreTopology? = null

            override suspend fun detectedTopology(): CoreTopology =
                withContext(Dispatchers.IO) {
                    detectedCache?.let { return@withContext it }
                    val clusters = detectCpuClusters()
                    val topo = CoreTopology(
                        coreCount = clusters.flatMap { it.cpus }.maxOrNull()?.plus(1) ?: 1,
                        clusters = clusters.map { CoreTopology.CoreCluster(it.label, buildCoreMask(it.cpus.toSet())) },
                    )
                    detectedCache = topo
                    topo
                }

            override suspend fun effectiveTopology(): CoreTopology {
                customTopology()?.let { return it }
                return detectedTopology()
            }

            override fun setCustomTopology(topology: CoreTopology?) {
                customCache = null
                clustersCache = null
                val f = topologyFile()
                if (topology == null) {
                    runCatching { f.delete() }
                } else {
                    runCatching {
                        f.parentFile?.mkdirs()
                        f.writeText(CoreTopology.encode(topology.normalized()))
                    }
                }
            }

            override fun buildHistoryEntry(name: String, note: String, rules: List<ThreadRule>, version: SceneVersion): HistoryEntry {
                val id = System.currentTimeMillis().toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                return HistoryEntry(id = id, timestamp = timestamp, name = name, note = note, rules = rules, sceneVersion = version.id, kind = HistoryKind.Threads)
            }

            override fun buildCategoryHistoryEntry(name: String, note: String, rules: List<CategoryRule>): HistoryEntry {
                val id = System.currentTimeMillis().toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                return HistoryEntry(id = id, timestamp = timestamp, name = name, note = note, kind = HistoryKind.Categories, categoryRules = rules)
            }

            override fun loadHistory(kind: HistoryKind?): List<HistoryEntry> {
                val dir = File(appContext.filesDir, "history")
                return dir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".json") }
                    ?.mapNotNull { f -> runCatching { HistoryCodec.decode(f.readText()) }.getOrNull() }
                    ?.filter { kind == null || it.kind == kind }
                    ?.sortedByDescending { it.timestamp }
                    .orEmpty()
            }

            override fun appendHistory(entry: HistoryEntry) {
                val dir = File(appContext.filesDir, "history")
                runCatching {
                    dir.mkdirs()
                    File(dir, "${entry.id}.json").writeText(HistoryCodec.encode(entry))
                }
            }

            override fun deleteHistory(id: String) {
                val dir = File(appContext.filesDir, "history")
                runCatching { File(dir, "$id.json").delete() }
            }

            override fun openUrl(url: String) {
                val host = activity
                if (host == null) {
                    EditorLog.w("app.about", "openUrl skipped: no activity")
                    return
                }
                runCatching {
                    host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure {
                    EditorLog.w("app.about", "openUrl failed: ${it.message}")
                }
            }
        }
    }
}

private fun firstLetter(label: String, packageName: String): String {
    val c = label.trim().firstOrNull { it.isLetter() } ?: packageName.trim().firstOrNull()
    return (c ?: '?').uppercase()
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, size: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    // GPU-resident copy: faster to draw in lists, lower per-frame memory bandwidth.
    return if (android.os.Build.VERSION.SDK_INT >= 26) {
        runCatching { bitmap.copy(Bitmap.Config.HARDWARE, false) }.getOrDefault(bitmap)
    } else bitmap
}

private val SU_BINARIES = listOf(
    "su",
    "/system/bin/su",
    "/sbin/su",
    "/system/xbin/su",
    "/data/adb/ksu/bin/su",
    "/data/adb/ap/bin/su",
)

/**
 * Runs a shell command as root. Uses `-m`/`--mount-master` so the command runs
 * in the GLOBAL mount namespace; KernelSU otherwise isolates `su -c` to a
 * namespace that cannot see other apps' real files (which made Scene's
 * threads.json "invisible"). Falls back across common su paths.
 */
internal fun runSu(cmd: String): Pair<Boolean, String> {
    val variants = listOf(
        listOf("-mm", "-c", cmd),
        listOf("-m", "-c", cmd),
        listOf("--mount-master", "-c", cmd),
        listOf("-c", cmd),
    )
    var last: Pair<Boolean, String>? = null
    for (bin in SU_BINARIES) {
        for (v in variants) {
            val r = execRaw(listOf(bin) + v)
            last = r
            if (r.first) return r
        }
    }
    // Last resort: already-rooted shell (adb root / su-less root).
    val fb = execRaw(listOf("sh", "-c", cmd))
    return if (fb.first) fb else (last ?: (false to "root 不可用"))
}

private fun execRaw(cmdArgs: List<String>): Pair<Boolean, String> {
    val start = System.currentTimeMillis()
    return try {
        val proc = ProcessBuilder(cmdArgs).redirectErrorStream(false).start()
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread {
            runCatching { proc.inputStream.bufferedReader().use { r -> r.forEachLine { out.append(it).append('\n') } } }
        }
        val tErr = Thread {
            runCatching { proc.errorStream.bufferedReader().use { r -> r.forEachLine { err.append(it).append('\n') } } }
        }
        tOut.start()
        tErr.start()
        val finished = proc.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            EditorLog.w("scene.root", "runSu timeout args=${cmdArgs.joinToString(" ")} duration_ms=${System.currentTimeMillis() - start}")
            return false to "root 执行超时"
        }
        tOut.join(1000)
        tErr.join(1000)
        val o = out.toString().trim()
        val e = err.toString().trim()
        EditorLog.op("scene.root", "runSu code=${proc.exitValue()} args=${cmdArgs.joinToString(" ")} out=${o.take(160)} err=${e.take(160)}", System.currentTimeMillis() - start)
        (proc.exitValue() == 0) to (if (e.isNotBlank()) e else o)
    } catch (e: Exception) {
        EditorLog.e("scene.root", "runSu failed args=${cmdArgs.joinToString(" ")} ex=${e.message}")
        false to (e.message ?: "root 不可用")
    }
}

private fun readUriText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

private fun writeUriText(context: Context, uri: Uri, content: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        true
    }.getOrDefault(false)

private val CLUSTER_LABELS = listOf("Ultra_Core", "Prime_Core", "Mid_Core", "Little_Core")

/**
 * Detects CPU core clusters from `/sys/devices/system/cpu/cpufreq/policy*`
 * (best available source for cluster boundaries). Ordered by descending peak
 * frequency, labelled Ultra/Prime/Mid/Little then UnkownN_Core for extras.
 */
private fun detectCpuClusters(): List<CpuClusterInfo> {
    val script = """
        echo '--present--'
        cat /sys/devices/system/cpu/present 2>/dev/null
        echo '--freq--'
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
          [ -d "${'$'}p" ] || continue
          echo "${'$'}p|`cat "${'$'}p/related_cpus" 2>/dev/null`|`cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null`|`cat "${'$'}p/cpuinfo_min_freq" 2>/dev/null`"
        done
    """.trimIndent()
    val (ok, out) = runSu(script)
    if (!ok) return emptyList()

    val sections = out.lineSequence().filter { it.isNotBlank() }
        .fold(mutableListOf<MutableList<String>>()) { acc, line ->
            if (line.startsWith("--")) acc.add(mutableListOf(line)) else acc.lastOrNull()?.add(line)
            acc
        }
    fun section(name: String): List<String> =
        sections.firstOrNull { it.firstOrNull() == name }?.drop(1).orEmpty()

    data class Policy(val cpus: List<Int>, val maxFreq: Long)
    val policies = section("--freq--").mapNotNull { line ->
        val parts = line.split("|")
        if (parts.size < 4) return@mapNotNull null
        val cpus = parseCpuList(parts[1])
        if (cpus.isEmpty()) return@mapNotNull null
        Policy(cpus, parts[2].trim().toLongOrNull() ?: 0L)
    }

    val grouped = policies.groupBy { it.cpus.sorted().joinToString(",") }
        .values
        .map { pols -> pols.first().cpus.sorted() to (pols.maxOfOrNull { it.maxFreq } ?: 0L) }
        .sortedWith(
            compareByDescending<Pair<List<Int>, Long>> { it.second }
                .thenBy { it.first.minOrNull() ?: Int.MAX_VALUE },
        )
    if (grouped.isNotEmpty()) {
        return grouped.mapIndexed { idx, (cpus, _) -> labelCluster(idx, cpus) }
    }

    // No cpufreq policies exposed: infer from the present CPU range.
    val present = parseCpuList(section("--present--").firstOrNull() ?: "")
    val maxIndex = present.maxOrNull() ?: -1
    if (maxIndex < 0) return emptyList()
    return listOf(labelCluster(0, (0..maxIndex).toList()))
}

private fun labelCluster(idx: Int, cpus: List<Int>): CpuClusterInfo {
    val label = CLUSTER_LABELS.getOrNull(idx) ?: "Unkown${idx - 3}_Core"
    return CpuClusterInfo(label, cpus)
}

private fun parseCpuList(text: String): List<Int> {
    val set = sortedSetOf<Int>()
    text.trim().split(Regex("[,\\s]+")).forEach { part ->
        val m = Regex("^(\\d+)(?:-(\\d+))?$").matchEntire(part) ?: return@forEach
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: a
        for (i in minOf(a, b)..maxOf(a, b)) set += i
    }
    return set.toList()
}
