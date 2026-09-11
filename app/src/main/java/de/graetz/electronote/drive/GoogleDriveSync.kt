package de.graetz.electronote.drive

import android.content.Context
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import org.json.JSONObject
import java.io.File

data class DriveNotebookEntry(val folderId: String, val name: String)

/**
 * Manual, explicit-action Drive sync — mirrors NextcloudSync's shape (no background/live
 * sync, matching the user's earlier stated preference for Nextcloud). The whole document
 * folder (document.json + background images + videos/ subfolder) round-trips recursively.
 */
object GoogleDriveSync {
    private const val ROOT_FOLDER_NAME = "ElectroNote"

    private fun mimeTypeFor(filename: String): String = when {
        filename.endsWith(".json") -> "application/json"
        filename.endsWith(".png") -> "image/png"
        filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> "image/jpeg"
        filename.endsWith(".mp4") -> "video/mp4"
        filename.endsWith(".pdf") -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun uploadRecursive(token: String, localDir: File, remoteFolderId: String): Boolean {
        val files = localDir.listFiles() ?: return true
        for (file in files) {
            if (file.isDirectory) {
                val subFolderId = GoogleDriveApi.ensureFolder(token, remoteFolderId, file.name) ?: return false
                if (!uploadRecursive(token, file, subFolderId)) return false
            } else {
                val ok = GoogleDriveApi.uploadFile(token, remoteFolderId, file.name, mimeTypeFor(file.name), file.readBytes())
                if (!ok) return false
            }
        }
        return true
    }

    private fun downloadRecursive(token: String, remoteFolderId: String, localDir: File) {
        localDir.mkdirs()
        for (entry in GoogleDriveApi.listChildren(token, remoteFolderId)) {
            if (entry.isFolder) {
                downloadRecursive(token, entry.id, File(localDir, entry.name))
            } else {
                val bytes = GoogleDriveApi.downloadFile(token, entry.id) ?: continue
                File(localDir, entry.name).writeBytes(bytes)
            }
        }
    }

    /** Returns true on success. Requires a signed-in Google account (see GoogleDriveAuth). */
    fun upload(context: Context, token: String, document: NotebookDocument): Boolean {
        val rootId = GoogleDriveApi.ensureRootFolder(token, ROOT_FOLDER_NAME) ?: return false
        val existingId = document.driveFolderId
        val docFolderId = if (existingId != null && GoogleDriveApi.listChildren(token, rootId).any { it.id == existingId }) {
            existingId
        } else {
            GoogleDriveApi.ensureFolder(token, rootId, document.name) ?: return false
        }
        document.driveFolderId = docFolderId
        NotebookStore.saveDocument(context, document)
        val localDir = NotebookStore.documentDir(context, document.id)
        return uploadRecursive(token, localDir, docFolderId)
    }

    fun listRemoteNotebooks(context: Context, token: String): List<DriveNotebookEntry> {
        val rootId = GoogleDriveApi.ensureRootFolder(token, ROOT_FOLDER_NAME) ?: return emptyList()
        return GoogleDriveApi.listChildren(token, rootId)
            .filter { it.isFolder }
            .map { DriveNotebookEntry(it.id, it.name) }
    }

    fun download(context: Context, token: String, entry: DriveNotebookEntry): NotebookDocument? {
        val jsonEntry = GoogleDriveApi.listChildren(token, entry.folderId).find { it.name == "document.json" } ?: return null
        val jsonBytes = GoogleDriveApi.downloadFile(token, jsonEntry.id) ?: return null
        val document = runCatching { NotebookDocument.fromJson(JSONObject(String(jsonBytes))) }.getOrNull() ?: return null
        document.driveFolderId = entry.folderId
        val localDir = NotebookStore.documentDir(context, document.id)
        downloadRecursive(token, entry.folderId, localDir)
        NotebookStore.saveDocument(context, document)
        return document
    }
}
