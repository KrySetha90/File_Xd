package com.filebox.app

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object P {
    fun sp(c: Context) = c.getSharedPreferences("fb", Context.MODE_PRIVATE)
    fun bg(c: Context) = sp(c).getInt("bg", Color.parseColor("#121212"))
    fun fg(c: Context) = if (ColorUtils.calculateLuminance(bg(c)) < 0.5) Color.WHITE else Color.BLACK
    fun textSize(c: Context) = when (sp(c).getInt("size", 1)) { 0 -> 14f; 2 -> 20f; else -> 16f }
}

object Fx {
    fun size(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)

    fun human(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return "%.1f %s".format(v, u[i])
    }

    fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        val base = f.nameWithoutExtension
        val ext = f.extension
        var n = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return f
    }

    fun copy(src: File, dst: File) {
        if (src.isDirectory) src.copyRecursively(dst, true) else src.copyTo(dst, true)
    }

    fun zip(src: File, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { z -> add(z, src, src.name) }
    }

    private fun add(z: ZipOutputStream, f: File, path: String) {
        if (f.isDirectory) {
            z.putNextEntry(ZipEntry("$path/")); z.closeEntry()
            f.listFiles()?.forEach { add(z, it, "$path/${it.name}") }
        } else {
            z.putNextEntry(ZipEntry(path))
            f.inputStream().use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val base = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(dest, e.name)
                if (!out.canonicalPath.startsWith(base)) throw SecurityException("Bad zip entry")
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
    }

    fun search(root: File, q: String, limit: Int = 500): List<File> =
        root.walkTopDown().filter { it != root && it.name.contains(q, true) }.take(limit).toList()
}
