package com.xc.air3upgrader

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

class ApkDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
            val result = MatrixCursor(projection ?: arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_FLAGS
            ))

            val context = context ?: return result
            val privateDir = context.getExternalFilesDir(null) ?: return result

            result.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, "apk_root")
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, privateDir.absolutePath)
                add(DocumentsContract.Root.COLUMN_TITLE, "AIR³ Upgrader APKs")
                add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                            DocumentsContract.Root.FLAG_LOCAL_ONLY or
                            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                )
            }

            return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
            val result = MatrixCursor(projection ?: arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ))

            val file = File(documentId)
            if (!file.exists()) return result

            result.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.absolutePath)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                    if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
                    else "application/vnd.android.package-archive"
                )
                add(DocumentsContract.Document.COLUMN_FLAGS,
                    if (file.isDirectory) DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED
                    else DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                )
                add(DocumentsContract.Document.COLUMN_SIZE, file.length())
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            }

            return result
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): Cursor {
            val result = MatrixCursor(projection ?: arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ))

            val parentFile = File(parentDocumentId)
            if (!parentFile.exists() || !parentFile.isDirectory) return result

            parentFile.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
                result.newRow().apply {
                    add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.absolutePath)
                    add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
                    add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                        if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR
                        else "application/vnd.android.package-archive"
                    )
                    add(DocumentsContract.Document.COLUMN_FLAGS,
                        if (file.isDirectory) DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED
                        else DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    )
                    add(DocumentsContract.Document.COLUMN_SIZE, file.length())
                    add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
                }
            }

            return result
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
            val file = File(documentId)
            if (!file.exists()) throw FileNotFoundException("File not found: $documentId")

            val fileMode = when {
                "r" in mode -> ParcelFileDescriptor.MODE_READ_ONLY
                "w" in mode -> ParcelFileDescriptor.MODE_WRITE_ONLY
                else -> throw IllegalArgumentException("Invalid mode: $mode")
            }

            return ParcelFileDescriptor.open(file, fileMode)
    }
}
