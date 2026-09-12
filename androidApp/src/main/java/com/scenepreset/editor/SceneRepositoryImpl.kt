// Copyright 2026, Thread Editor contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.scenepreset.editor

import android.content.SharedPreferences
import android.util.Base64
import com.scenepreset.editor.data.PresetFile
import com.scenepreset.editor.data.SETTING_SCENE_PATH
import com.scenepreset.editor.data.SceneRepository
import com.scenepreset.editor.model.CategoriesCodec
import com.scenepreset.editor.model.CategoryRule
import com.scenepreset.editor.model.PresetRule
import com.scenepreset.editor.model.SceneVersion
import com.scenepreset.editor.model.ThreadsCodec
import java.io.File

private const val TAG = "SceneRepository"

class SceneRepositoryImpl(
    private val prefs: SharedPreferences,
    private val backupDir: File? = null,
) : SceneRepository {

    private val defaultBase = "/data/user/0/com.omarea.vtools/files"
    @Volatile private var lockedCache: Boolean? = null

    // In-process caches: every load() used to shell out 4 su commands (~400ms+);
    // the file only changes through this app (save/reset/import), so caching the
    // last read is safe and makes re-entering pages instant.
    @Volatile private var loadCache: PresetFile? = null
    @Volatile private var categoriesCache: List<CategoryRule>? = null

    private fun storedBase(): String {
        val raw = prefs.getString(SETTING_SCENE_PATH, null) ?: defaultBase
        val trimmed = raw.trimEnd('/')
        return if (trimmed.endsWith("/threads.json")) trimmed.substringBeforeLast('/') else trimmed
    }

    override val path: String
        get() = "${storedBase()}/threads.json"

    override val categoriesPath: String
        get() = "${storedBase()}/categories.json"

    override fun updatePath(path: String) {
        lockedCache = null
        loadCache = null
        categoriesCache = null
        val base = path.trimEnd('/').let {
            if (it.endsWith("/threads.json")) it.substringBeforeLast('/') else it
        }
        prefs.edit().putString(SETTING_SCENE_PATH, base).apply()
    }

    override fun exists(): Boolean {
        val script = "if [ -e '$path' ]; then echo yes; else echo no; fi"
        val (ok, out) = runSu(script)
        if (ok) {
            val exists = out.trim().equals("yes", ignoreCase = true)
            EditorLog.i("scene.repo", "exists ok=$exists path=$path")
            return exists
        }
        val fb = runCatching { File(path).exists() }.getOrDefault(false)
        EditorLog.i("scene.repo", "exists fallback=$fb path=$path")
        return fb
    }

    override fun load(): PresetFile {
        loadCache?.let {
            EditorLog.i("scene.repo", "load cache hit rules=${it.rules.size} path=${it.path}")
            return it
        }
        val current = path
        val fileExists = exists()
        val locked = isLocked()
        val content = if (fileExists) {
            val script = "if [ -f \"$current\" ]; then cat \"$current\"; else echo '__SCENE_MISSING__'; fi"
            val (ok, out) = runSu(script)
            if (ok && out.trim() != "__SCENE_MISSING__" && out.isNotBlank()) {
                EditorLog.i("scene.repo", "load cat ok size=${out.length} head=${out.take(80)}")
                out
            } else {
                EditorLog.i("scene.repo", "load cat fallback path=$current")
                runCatching { File(current).readText() }.getOrDefault("[]")
            }
        } else {
            EditorLog.i("scene.repo", "load missing path=$current")
            "[]"
        }
        val size = runSu("stat -c %s '$current'")
            .let { if (it.first) it.second.trim().toLongOrNull() ?: 0L else 0L }
        val rules = runCatching { ThreadsCodec.decode(content) }.getOrDefault(emptyList())
        EditorLog.i("scene.repo", "load decoded=${rules.size} rules path=$current exists=$fileExists locked=$locked")
        val result = PresetFile(current, fileExists, locked, size, rules)
        loadCache = result
        return result
    }

    override fun save(rules: List<PresetRule>, version: SceneVersion): String? {
        val current = path
        val content = ThreadsCodec.encode(rules, version)
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        EditorLog.i("scene.repo", "save begin path=$current contentLen=${content.length}")
        val script = writeJsonFile(b64, current)
        val (ok, out) = runSu(script)
        EditorLog.i("scene.repo", "save result ok=$ok out=${out.take(200)}")
        if (ok && out.contains("__DONE__")) {
            loadCache = null
            return null
        }
        val reason = out.lineSequence()
            .firstOrNull { it.startsWith("__FAIL__") }
            ?.let {
                when (it) {
                    "__FAIL__DECODE" -> "内容编码错误"
                    "__FAIL__MOVE" -> "写入失败：请检查文件是否被 Scene 占用或当前用户是否可写"
                    else -> it
                }
            }
            ?: "保存失败：${out.ifBlank { "root 不可用或路径无权限" }}"
        return reason
    }

    override fun isLocked(): Boolean {
        lockedCache?.let { return it }
        val script = """
            LSATTR="lsattr"
            if ! command -v lsattr >/dev/null 2>&1; then
              if command -v busybox >/dev/null 2>&1; then LSATTR="busybox lsattr"; else LSATTR="/system/bin/lsattr"; fi
            fi
            A=`${'$'}LSATTR -d '$path' 2>/dev/null | awk '{print ${'$'}1}'`
            if echo "${'$'}A" | grep -q i; then echo LOCKED; else echo UNLOCKED; fi
        """.trimIndent()
        val (ok, out) = runSu(script)
        val locked = ok && out.trim() == "LOCKED"
        lockedCache = locked
        EditorLog.i("scene.repo", "isLocked=$locked path=$path")
        return locked
    }

    override fun setLocked(locked: Boolean): String? {
        return setLockedFor(path, locked) { lockedCache = it }
    }

    override fun isCategoriesLocked(): Boolean = isLockedFor(categoriesPath)

    override fun setCategoriesLocked(locked: Boolean): String? = setLockedFor(categoriesPath, locked) { }

    private fun isLockedFor(target: String): Boolean {
        val script = """
            LSATTR="lsattr"
            if ! command -v lsattr >/dev/null 2>&1; then
              if command -v busybox >/dev/null 2>&1; then LSATTR="busybox lsattr"; else LSATTR="/system/bin/lsattr"; fi
            fi
            A=`${'$'}LSATTR -d '$target' 2>/dev/null | awk '{print ${'$'}1}'`
            if echo "${'$'}A" | grep -q i; then echo LOCKED; else echo UNLOCKED; fi
        """.trimIndent()
        val (ok, out) = runSu(script)
        return ok && out.trim() == "LOCKED"
    }

    private fun setLockedFor(target: String, locked: Boolean, onDone: (Boolean) -> Unit): String? {
        val op = if (locked) "+i" else "-i"
        val script = """
            CHATTR="chattr"
            if ! command -v chattr >/dev/null 2>&1; then
              if command -v busybox >/dev/null 2>&1; then CHATTR="busybox chattr"; else CHATTR="/system/bin/chattr"; fi
            fi
            ${'$'}CHATTR $op '$target' 2>/dev/null && echo '__OK__' || echo '__FAIL__'
        """.trimIndent()
        val (ok, out) = runSu(script)
        EditorLog.i("scene.repo", "setLocked locked=$locked ok=$ok out=$out path=$target")
        if (ok && out.trim() == "__OK__") {
            onDone(locked)
            return null
        }
        return "设置锁定失败：chattr 不可用或文件系统不支持"
    }

    override fun ensureExists(): Boolean {
        if (exists()) return true
        val current = path
        val script = """
            mkdir -p "${'$'}(dirname '$current')" 2>/dev/null
            if [ -f '$current' ]; then echo '__OK__'; else printf '[]' > '$current' 2>/dev/null && echo '__OK__' || echo '__FAIL__'; fi
        """.trimIndent()
        val (ok, out) = runSu(script)
        if (ok && out.trim() == "__OK__") return true
        return runCatching {
            File(current).apply { parentFile?.mkdirs() }.writeText("[]")
            true
        }.getOrDefault(false)
    }

    override fun reset(): String? {
        val current = path
        lockedCache = null
        loadCache = null
        val script = "chattr -i '$current' 2>/dev/null; rm -f '$current' && echo '__DONE__' || echo '__FAIL__'"
        val (ok, out) = runSu(script)
        EditorLog.i("scene.repo", "reset ok=$ok out=$out path=$current")
        if (ok && out.contains("__DONE__")) return null
        return "重置失败：${out.ifBlank { "root 不可用" }}"
    }

    /**
     * Atomically replaces Scene's threads.json:
     *   1. unlock the file if needed,
     *   2. write content to a temp file in the same directory (via base64),
     *   3. restore owner/group/mode/SELinux context,
     *   4. rename over the original,
     *   5. restore the immutable flag when previously set.
     */
    override fun categoriesExists(): Boolean {
        val script = "if [ -e '$categoriesPath' ]; then echo yes; else echo no; fi"
        val (ok, out) = runSu(script)
        return ok && out.trim().equals("yes", ignoreCase = true)
    }

    override fun loadCategories(): List<CategoryRule> {
        categoriesCache?.let { return it }
        val current = categoriesPath
        val exists = categoriesExists()
        val content = if (exists) {
            val script = "if [ -f \"$current\" ]; then cat \"$current\"; else echo '__SCENE_MISSING__'; fi"
            val (ok, out) = runSu(script)
            if (ok && out.trim() != "__SCENE_MISSING__" && out.isNotBlank()) out
            else runCatching { File(current).readText() }.getOrDefault("[]")
        } else "[]"
        val decoded = runCatching { CategoriesCodec.decode(content) }.getOrDefault(emptyList())
        categoriesCache = decoded
        return decoded
    }

    /**
     * Captures the pristine categories.json into app-private storage exactly once
     * (first launch after the file becomes readable). Later edits never touch the
     * backup, so "restore" always returns the official first-seen configuration.
     */
    override fun captureCategoriesBackupIfMissing() {
        val backup = categoriesBackupFile() ?: return
        if (backup.exists()) return
        if (!categoriesExists()) return
        val current = categoriesPath
        val script = "if [ -f '$current' ]; then cat '$current'; else echo '__MISS__'; fi"
        val (ok, out) = runSu(script)
        if (!ok || out.isBlank() || out.contains("__MISS__")) return
        // Sanity check: only persist parseable JSON.
        runCatching { CategoriesCodec.decode(out) }.getOrElse { return }
        runCatching {
            backup.parentFile?.mkdirs()
            backup.writeText(out)
        }
    }

    override fun saveCategories(rules: List<CategoryRule>): String? {
        val current = categoriesPath
        val content = CategoriesCodec.encode(rules)
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val script = writeJsonFile(b64, current)
        val (ok, out) = runSu(script)
        if (ok && out.contains("__DONE__")) {
            categoriesCache = null
            return null
        }
        return "保存类目失败：${out.ifBlank { "root 不可用或路径无权限" }}"
    }

    override fun ensureCategories(): Boolean {
        if (categoriesExists()) return true
        val current = categoriesPath
        val script = """
            mkdir -p "${'$'}(dirname '$current')" 2>/dev/null
            if [ -f '$current' ]; then echo '__OK__'; else printf '[]' > '$current' 2>/dev/null && echo '__OK__' || echo '__FAIL__'; fi
        """.trimIndent()
        val (ok, out) = runSu(script)
        return ok && out.trim() == "__OK__"
    }

    /** App-private backup of categories.json captured on first launch. */
    private fun categoriesBackupFile(): File? {
        val dir = backupDir ?: return null
        // Encode the Scene base path so custom paths back up separately.
        val safe = storedBase().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "categories_backup_$safe.json")
    }

    override fun restoreCategoriesBackup(): String? {
        val backup = categoriesBackupFile()
            ?: return "无法创建备份目录"
        if (!backup.exists()) {
            return "没有可用的备份。categories.json 无法通过切换性能调度重新拉取官方配置，\n" +
                "如需完全重置请清除 Scene 的全部数据（会同时清空 Scene 的其它设置）。"
        }
        val content = runCatching { backup.readText() }.getOrNull()
            ?: return "读取备份失败"
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val script = writeJsonFile(b64, categoriesPath)
        val (ok, out) = runSu(script)
        return if (ok && out.contains("__DONE__")) null
        else "恢复备份失败：${out.ifBlank { "root 不可用" }}"
    }

    override fun resetCategories(): String? {
        categoriesCache = null
        val current = categoriesPath
        loadCache = null
        val script = "chattr -i '$current' 2>/dev/null; rm -f '$current' && echo '__DONE__' || echo '__FAIL__'"
        val (ok, out) = runSu(script)
        return if (ok && out.contains("__DONE__")) null else "重置类目失败：${out.ifBlank { "root 不可用" }}"
    }

    private fun writeJsonFile(base64Content: String, path: String): String {
        val dir = path.substringBeforeLast('/', "/")
        val tmp = "$dir/.threads_editor_tmp"
        return """
            F='$path'
            TMP='$tmp'
            LOCKED=0
            mkdir -p "${'$'}(dirname "${'$'}F")" 2>/dev/null
            LSATTR="lsattr"
            if ! command -v lsattr >/dev/null 2>&1; then
              if command -v busybox >/dev/null 2>&1; then LSATTR="busybox lsattr"; else LSATTR="/system/bin/lsattr"; fi
            fi
            A=`${'$'}LSATTR -d "${'$'}F" 2>/dev/null | awk '{print ${'$'}1}'`
            if echo "${'$'}A" | grep -q i; then
              LOCKED=1
              CHATTR="chattr"
              if ! command -v chattr >/dev/null 2>&1; then
                if command -v busybox >/dev/null 2>&1; then CHATTR="busybox chattr"; else CHATTR="/system/bin/chattr"; fi
              fi
              ${'$'}CHATTR -i "${'$'}F" 2>/dev/null
            fi
            rm -f "${'$'}TMP"
            printf '%s' '$base64Content' | base64 -d > "${'$'}TMP" 2>/dev/null || { echo '__FAIL__DECODE'; exit 0; }
            if [ -e "${'$'}F" ]; then
              OWNER=`stat -c %u "${'$'}F" 2>/dev/null`
              GROUP=`stat -c %g "${'$'}F" 2>/dev/null`
              MODE=`stat -c %a "${'$'}F" 2>/dev/null`
              CTX=`ls -Z "${'$'}F" 2>/dev/null | awk '{print ${'$'}1}'`
            else
              OWNER=`stat -c %u "${'$'}(dirname "${'$'}F")" 2>/dev/null`
              GROUP=`stat -c %g "${'$'}(dirname "${'$'}F")" 2>/dev/null`
              MODE='660'
              CTX=''
            fi
            [ -n "${'$'}OWNER" ] && chown "${'$'}OWNER:${'$'}GROUP" "${'$'}TMP" 2>/dev/null
            [ -n "${'$'}MODE" ] && chmod "${'$'}MODE" "${'$'}TMP" 2>/dev/null
            if [ -n "${'$'}CTX" ] && [ "${'$'}CTX" != "?" ]; then chcon "${'$'}CTX" "${'$'}TMP" 2>/dev/null; fi
            if mv -f "${'$'}TMP" "${'$'}F" 2>/dev/null; then
              if [ "${'$'}LOCKED" = "1" ]; then
                CHATTR="chattr"
                if ! command -v chattr >/dev/null 2>&1; then
                  if command -v busybox >/dev/null 2>&1; then CHATTR="busybox chattr"; else CHATTR="/system/bin/chattr"; fi
                fi
                ${'$'}CHATTR +i "${'$'}F" 2>/dev/null
              fi
              echo '__DONE__'
            else
              rm -f "${'$'}TMP"
              if [ "${'$'}LOCKED" = "1" ]; then
                CHATTR="chattr"
                if ! command -v chattr >/dev/null 2>&1; then
                  if command -v busybox >/dev/null 2>&1; then CHATTR="busybox chattr"; else CHATTR="/system/bin/chattr"; fi
                fi
                ${'$'}CHATTR +i "${'$'}F" 2>/dev/null
              fi
              echo '__FAIL__MOVE'
            fi
        """.trimIndent()
    }
}
