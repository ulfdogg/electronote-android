package de.graetz.electronote.nextcloud

import android.content.Context
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import org.json.JSONObject
import java.io.File

/**
 * "Same document, either device" — explicit, manual upload/download against a Nextcloud
 * folder, no background/automatic reconciliation. Each notebook lives at
 * /ElectroNote/<name>-<shortId>/ on the server: document.json plus any background images,
 * mirroring the local on-device layout exactly.
 */
object NextcloudSync {

    fun remoteFolderNameFor(document: NotebookDocument): String {
        val safe = document.name
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
            .ifEmpty { "Notizbuch" }
        return "$safe-${document.id.take(8)}"
    }

    /** Returns true on success. */
    fun upload(context: Context, credentials: NextcloudCredentials, document: NotebookDocument): Boolean {
        val folderName = document.remoteFolderName ?: remoteFolderNameFor(document).also {
            document.remoteFolderName = it
        }
        if (!NextcloudWebDav.ensureDocumentFolder(credentials, folderName)) return false

        val jsonBytes = document.toJson().toString().toByteArray()
        if (!NextcloudWebDav.uploadFile(credentials, folderName, "document.json", jsonBytes)) return false

        for (bg in document.backgrounds) {
            val file = File(NotebookStore.documentDir(context, document.id), bg.imageFile)
            if (file.exists()) {
                NextcloudWebDav.uploadFile(credentials, folderName, bg.imageFile, file.readBytes())
            }
        }

        NotebookStore.saveDocument(context, document)
        return true
    }

    /** Downloads a remote document folder and saves it locally (same id as on the server). */
    fun download(context: Context, credentials: NextcloudCredentials, folderName: String): NotebookDocument? {
        val jsonBytes = NextcloudWebDav.downloadFile(credentials, folderName, "document.json") ?: return null
        val document = try {
            NotebookDocument.fromJson(JSONObject(String(jsonBytes)))
        } catch (e: Exception) {
            return null
        }
        document.remoteFolderName = folderName

        for (bg in document.backgrounds) {
            val bytes = NextcloudWebDav.downloadFile(credentials, folderName, bg.imageFile) ?: continue
            val file = File(NotebookStore.documentDir(context, document.id), bg.imageFile)
            file.writeBytes(bytes)
        }

        NotebookStore.saveDocument(context, document)
        return document
    }
}
