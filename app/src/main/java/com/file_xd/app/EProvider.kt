package com.filebox.app

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/** Exposes the E: folder to every app (system file picker, ZArchiver, editors, etc.). */
class EProvider : DocumentsProvider() {
    companion object {
        const val ROOT = "E"
        val ROOT_COLS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_SUMMARY)
        val DOC_COLS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS)
    }

    private fun base() = File(context!!.filesDir, "storage").apply { mkdirs() }

    private fun file(id: String): File {
        val f = if (id == ROOT) base() else File(id)
        if (!f.canonicalPath.startsWith(base().canonicalPath)) throw FileNotFoundException(id)
        return f
    }

    private fun idOf(f: File) = if (f.canonicalPath == base().canonicalPath) ROOT else f.canonicalPath

    private fun mime(f: File): String {
        if (f.isDirectory) return Document.MIME_TYPE_DIR
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun include(c: MatrixCursor, f: File) {
        val flags = (if (f.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE) or
            Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        val id = idOf(f)
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, if (id == ROOT) "E:" else f.name)
            .add(Document.COLUMN_SIZE, f.length())
            .add(Document.COLUMN_MIME_TYPE, mime(f))
            .add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(ROOT_COLS).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT)
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
                .add(Root.COLUMN_TITLE, "FileBox E:")
                .add(Root.COLUMN_DOCUMENT_ID, ROOT)
                .add(Root.COLUMN_ICON, android.R.drawable.ic_menu_save)
                .add(Root.COLUMN_SUMMARY, "App folder")
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(DOC_COLS).also { include(it, file(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(DOC_COLS).also { c -> file(parentDocumentId).listFiles()?.forEach { include(c, it) } }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val f = Fx.unique(file(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) f.mkdirs() else f.createNewFile()
        return idOf(f)
    }

    override fun deleteDocument(documentId: String) { file(documentId).deleteRecursively() }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val f = file(documentId)
        val t = File(f.parentFile, displayName)
        return if (f.renameTo(t)) idOf(t) else null
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        file(documentId).canonicalPath.startsWith(file(parentDocumentId).canonicalPath)
}
