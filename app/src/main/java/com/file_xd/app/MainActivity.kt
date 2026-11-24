package com.filebox.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private var mode = "C"
    private lateinit var cur: File
    private var items: List<File> = emptyList()
    private var clip: File? = null
    private var clipMove = false

    private lateinit var page: LinearLayout
    private lateinit var titleTv: TextView
    private lateinit var pathTv: TextView
    private lateinit var list: ListView
    private lateinit var pasteBar: LinearLayout
    private lateinit var tabC: Button
    private lateinit var tabE: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun rootOf(m: String): File =
        if (m == "C") Environment.getExternalStorageDirectory()
        else File(filesDir, "storage").apply { mkdirs() }

    private fun hasAccess() =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun askAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            requestPermissions(arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(i: Int) = items[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val f = items[i]
            val ctx = this@MainActivity
            val fg = P.fg(ctx); val sz = P.textSize(ctx)
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.lastModified()))
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            row.addView(TextView(ctx).apply {
                text = (if (f.isDirectory) "📁 " else "📄 ") + f.name
                textSize = sz; setTextColor(fg)
            })
            row.addView(TextView(ctx).apply {
                text = (if (f.isDirectory) "${f.listFiles()?.size ?: 0} items" else Fx.human(f.length())) + " · " + date
                textSize = sz - 4; setTextColor(fg); alpha = 0.65f
            })
            return row
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        cur = rootOf("C")

        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(32), dp(8), dp(8))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleTv = TextView(this).apply {
            text = "FileBox"; textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        bar.addView(titleTv)
        bar.addView(btn("🔍") { searchDialog() })
        bar.addView(btn("＋") { createDialog() })
        bar.addView(btn("⚙") { startActivity(Intent(this, SettingsActivity::class.java)) })
        page.addView(bar)

        val tabs = LinearLayout(this)
        tabC = btn("C: Files") { switchMode("C") }
        tabE = btn("E: App folder") { switchMode("E") }
        tabC.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tabE.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tabs.addView(tabC); tabs.addView(tabE)
        page.addView(tabs)

        pathTv = TextView(this).apply {
            textSize = 13f; setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnClickListener { goUp() }
        }
        page.addView(pathTv)

        list = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, i, _ ->
                val f = items[i]
                if (f.isDirectory) { cur = f; load() } else openFile(f)
            }
            setOnItemLongClickListener { _, _, i, _ -> menu(items[i]); true }
        }
        page.addView(list)

        pasteBar = LinearLayout(this).apply { visibility = View.GONE }
        pasteBar.addView(btn("Paste here") { paste() }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        pasteBar.addView(btn("Cancel") { clip = null; load() }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        page.addView(pasteBar)

        setContentView(page)
        if (!hasAccess()) askDialog()
        load()
    }

    override fun onResume() { super.onResume(); if (::page.isInitialized) load() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { load() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!goUp()) super.onBackPressed()
    }

    private fun goUp(): Boolean {
        val root = rootOf(mode)
        if (cur.canonicalPath != root.canonicalPath) {
            cur = cur.parentFile ?: root; load(); return true
        }
        return false
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { f() }
    }

    private fun askDialog() {
        AlertDialog.Builder(this)
            .setTitle("Give access")
            .setMessage("FileBox needs file access to browse and create folders in your phone storage (C:).")
            .setPositiveButton("Give access") { _, _ -> askAccess() }
            .setNegativeButton("Later", null).show()
    }

    private fun switchMode(m: String) {
        if (m == "C" && !hasAccess()) askDialog()
        mode = m; cur = rootOf(m); load()
    }

    private fun load() {
        val bg = P.bg(this); val fg = P.fg(this); val sp = P.sp(this)
        page.setBackgroundColor(bg)
        titleTv.setTextColor(fg); pathTv.setTextColor(fg)
        tabC.alpha = if (mode == "C") 1f else 0.5f
        tabE.alpha = if (mode == "E") 1f else 0.5f

        val showHidden = sp.getBoolean("hidden", false)
        val sort = sp.getInt("sort", 0)
        items = (cur.listFiles()?.toList() ?: emptyList())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(Comparator { a, c ->
                if (a.isDirectory != c.isDirectory) { if (a.isDirectory) -1 else 1 }
                else when (sort) {
                    1 -> c.length().compareTo(a.length())
                    2 -> c.lastModified().compareTo(a.lastModified())
                    else -> a.name.compareTo(c.name, true)
                }
            })
        val rel = cur.path.removePrefix(rootOf(mode).path)
        pathTv.text = "$mode:" + rel.ifEmpty { "/" } + "   (tap to go up)"
        pasteBar.visibility = if (clip != null) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun ask(title: String, hint: String, def: String = "", ok: (String) -> Unit) {
        val et = EditText(this).apply { setText(def); setHint(hint); setSingleLine() }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun work(done: String, task: () -> Unit) {
        toast("Working…")
        Thread {
            try { task(); runOnUiThread { toast(done); load() } }
            catch (e: Exception) { runOnUiThread { toast("Error: ${e.message}") } }
        }.start()
    }

    private fun inside(f: File) = f.canonicalPath.startsWith(cur.canonicalPath)

    private fun createDialog() {
        val ops = arrayOf("New folder (a/b/c makes nested)", "New file (.txt .xml .html .zip …)")
        AlertDialog.Builder(this).setTitle("Create").setItems(ops) { _, i ->
            if (i == 0) ask("New folder", "name") { n ->
                val f = File(cur, n)
                if (n.isEmpty() || !inside(f)) toast("Bad name")
                else { toast(if (f.mkdirs()) "Created" else "Failed / exists"); load() }
            } else ask("New file", "name.ext") { n ->
                val f = File(cur, n)
                if (n.isEmpty() || !inside(f)) toast("Bad name")
                else try {
                    f.parentFile?.mkdirs()
                    toast(if (f.createNewFile()) "Created" else "Already exists"); load()
                } catch (e: Exception) { toast("Error: ${e.message}") }
            }
        }.show()
    }

    private fun searchDialog() {
        ask("Search in ${cur.name.ifEmpty { "/" }}", "file or folder name") { q ->
            if (q.isEmpty()) return@ask
            toast("Searching…")
            Thread {
                val r = Fx.search(cur, q)
                runOnUiThread { showResults(r) }
            }.start()
        }
    }

    private fun showResults(r: List<File>) {
        if (r.isEmpty()) { toast("Nothing found"); return }
        val names = r.map { it.path.removePrefix(cur.path).trimStart('/') + if (it.isDirectory) "/" else "" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("${r.size} found").setItems(names) { _, i ->
            val f = r[i]
            if (f.isDirectory) { cur = f; load() } else { cur = f.parentFile ?: cur; load(); menu(f) }
        }.show()
    }

    private fun menu(f: File) {
        val ops = mutableListOf("Rename", "Copy", "Move", "Delete", "Zip")
        if (f.extension.equals("zip", true)) ops.add("Unzip here")
        if (f.isFile) { ops.add("Edit as text"); ops.add("Open with…"); ops.add("Share") }
        ops.add("Properties")
        AlertDialog.Builder(this).setTitle(f.name).setItems(ops.toTypedArray()) { _, i ->
            when (ops[i]) {
                "Rename" -> ask("Rename (change .txt → .html too)", "name", f.name) { n ->
                    val t = File(f.parentFile, n)
                    when {
                        n.isEmpty() || n.contains('/') -> toast("Bad name")
                        t.exists() -> toast("Name already used")
                        else -> { toast(if (f.renameTo(t)) "Renamed" else "Failed"); load() }
                    }
                }
                "Copy" -> { clip = f; clipMove = false; load() }
                "Move" -> { clip = f; clipMove = true; load() }
                "Delete" -> AlertDialog.Builder(this).setTitle("Delete ${f.name}?")
                    .setPositiveButton("Delete") { _, _ -> work("Deleted") { f.deleteRecursively() } }
                    .setNegativeButton("Cancel", null).show()
                "Zip" -> work("Zipped") { Fx.zip(f, Fx.unique(cur, f.nameWithoutExtension + ".zip")) }
                "Unzip here" -> work("Unzipped") { Fx.unzip(f, Fx.unique(cur, f.nameWithoutExtension)) }
                "Edit as text" -> editor(f)
                "Open with…" -> openFile(f)
                "Share" -> share(f)
                "Properties" -> Thread {
                    val s = Fx.size(f)
                    runOnUiThread {
                        AlertDialog.Builder(this).setTitle("Properties").setMessage(
                            "Name: ${f.name}\nPath: ${f.path}\nSize: ${Fx.human(s)}\nModified: ${Date(f.lastModified())}"
                        ).setPositiveButton("OK", null).show()
                    }
                }.start()
            }
        }.show()
    }

    private fun paste() {
        val src = clip ?: return
        if (src.isDirectory && cur.canonicalPath.startsWith(src.canonicalPath)) { toast("Can't paste into itself"); return }
        val dst = Fx.unique(cur, src.name)
        work("Done") {
            if (clipMove) { if (!src.renameTo(dst)) { Fx.copy(src, dst); src.deleteRecursively() } }
            else Fx.copy(src, dst)
            clip = null
        }
    }

    private fun editor(f: File) {
        if (f.length() > 1_000_000) { toast("File too large to edit"); return }
        val et = EditText(this).apply {
            setText(f.readText()); gravity = Gravity.TOP; minLines = 12
        }
        AlertDialog.Builder(this).setTitle(f.name).setView(ScrollView(this).apply { addView(et) })
            .setPositiveButton("Save") { _, _ -> f.writeText(et.text.toString()); toast("Saved"); load() }
            .setNegativeButton("Close", null).show()
    }

    private fun uriOf(f: File) = FileProvider.getUriForFile(this, "$packageName.fp", f)
    private fun mimeOf(f: File) =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    private fun openFile(f: File) {
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Open with"))
        } catch (e: Exception) { toast("No app can open this") }
    }

    private fun share(f: File) {
        try {
            val i = Intent(Intent.ACTION_SEND).setType(mimeOf(f))
                .putExtra(Intent.EXTRA_STREAM, uriOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share"))
        } catch (e: Exception) { toast("Can't share") }
    }
}
