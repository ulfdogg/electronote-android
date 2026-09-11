package de.graetz.electronote.nextcloud

import android.content.Context
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * "Same document, either device" — explicit, manual upload/download against a Nextcloud
 * folder, no background/automatic reconciliation. Each notebook lives at
 * /ElectroNote/<Name>.enote/ (or .ewb for whiteboards) on the server, in iOS's own
 * document.json schema (see [IosNotebookBridge]) — not Android's internal format — so the
 * same folder can be opened by either app. iOS's actual ink stays in drawing.pkdrawing,
 * untouched by Android; Android instead writes/reads a portable drawing.json mirror.
 */
object NextcloudSync {

    private const val IOS_EXTRAS_FILE = "ios_extras.json"

    private fun extension(document: NotebookDocument): String =
        if (document.docType == NotebookDocument.DOC_TYPE_WHITEBOARD) "ewb" else "enote"

    private fun loadCachedExtras(context: Context, documentId: String): IosNotebookBridge.IosExtras? {
        val file = File(NotebookStore.documentDir(context, documentId), IOS_EXTRAS_FILE)
        if (!file.exists()) return null
        return runCatching { IosNotebookBridge.IosExtras.fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    private fun saveCachedExtras(context: Context, documentId: String, extras: IosNotebookBridge.IosExtras) {
        File(NotebookStore.documentDir(context, documentId), IOS_EXTRAS_FILE).writeText(extras.toJson().toString())
    }

    /** Picks `<Name>.enote` (or the next free `<Name> 2.enote`, …), matching iOS's own
     * folder-uniqueness convention, so a brand-new upload never collides with an existing
     * document — iOS documents have no id field, the folder name IS their identity. */
    private fun chooseRemoteFolderName(credentials: NextcloudCredentials, document: NotebookDocument): String {
        val ext = extension(document)
        val base = document.name.trim().ifEmpty { "Notizbuch" }
        val existingNames = NextcloudWebDav.listDocumentFolders(credentials).map { it.folderName }.toSet()
        var candidate = "$base.$ext"
        var counter = 2
        while (candidate in existingNames) {
            candidate = "$base $counter.$ext"
            counter++
        }
        return candidate
    }

    /** Returns true on success. */
    fun upload(context: Context, credentials: NextcloudCredentials, document: NotebookDocument): Boolean {
        val folderName = document.remoteFolderName ?: chooseRemoteFolderName(credentials, document).also {
            document.remoteFolderName = it
        }
        if (!NextcloudWebDav.ensureDocumentFolder(credentials, folderName)) return false

        val extras = loadCachedExtras(context, document.id)
        val export = IosNotebookBridge.toIosJson(document, extras)
        if (!NextcloudWebDav.uploadFile(credentials, folderName, "document.json", export.documentJson.toString().toByteArray())) return false

        val strokesJson = IosNotebookBridge.strokesToPortableJson(document.strokes, export.scale)
        NextcloudWebDav.uploadFile(credentials, folderName, "drawing.json", strokesJson.toString().toByteArray())

        val docDir = NotebookStore.documentDir(context, document.id)
        for (png in export.textPngs) {
            NextcloudWebDav.uploadFile(credentials, folderName, "images/${png.filename}", png.bytes)
        }
        for (bg in document.backgrounds) {
            val file = File(docDir, bg.imageFile)
            if (file.exists()) NextcloudWebDav.uploadFile(credentials, folderName, "images/${bg.imageFile}", file.readBytes())
        }
        for (img in document.imageElements) {
            val file = File(docDir, img.filename)
            if (file.exists()) NextcloudWebDav.uploadFile(credentials, folderName, "images/${img.filename}", file.readBytes())
            img.videoFilename?.let { rel ->
                val videoFile = NotebookStore.videoFile(context, document.id, rel)
                if (videoFile.exists()) NextcloudWebDav.uploadFile(credentials, folderName, "videos/${videoFile.name}", videoFile.readBytes())
            }
        }

        NotebookStore.saveDocument(context, document)
        return true
    }

    /** Downloads a remote `.enote`/`.ewb` folder and saves it locally, reusing the same
     * local id if this folder was already linked to a document before (folder name is the
     * only identity iOS-authored documents have). */
    fun download(context: Context, credentials: NextcloudCredentials, folderName: String): NotebookDocument? {
        val jsonBytes = NextcloudWebDav.downloadFile(credentials, folderName, "document.json") ?: return null
        val json = try {
            JSONObject(String(jsonBytes))
        } catch (e: Exception) {
            return null
        }

        val existing = (NotebookStore.listDocuments(context) + NotebookStore.listTrash(context))
            .find { it.remoteFolderName == folderName }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val displayName = folderName.substringBeforeLast(".")

        val result = IosNotebookBridge.fromIosJson(json, id, displayName)
        val document = result.document
        document.remoteFolderName = folderName
        if (folderName.endsWith(".ewb")) document.docType = NotebookDocument.DOC_TYPE_WHITEBOARD

        val drawingJsonBytes = NextcloudWebDav.downloadFile(credentials, folderName, "drawing.json")
        if (drawingJsonBytes != null) {
            val arr = try { org.json.JSONArray(String(drawingJsonBytes)) } catch (e: Exception) { null }
            if (arr != null) document.strokes = IosNotebookBridge.portableJsonToStrokes(arr, IosNotebookBridge.PT_TO_PX)
        }

        val docDir = NotebookStore.documentDir(context, id)
        for (bg in document.backgrounds) {
            val bytes = NextcloudWebDav.downloadFile(credentials, folderName, "images/${bg.imageFile}") ?: continue
            File(docDir, bg.imageFile).writeBytes(bytes)
        }
        for (img in document.imageElements) {
            val bytes = NextcloudWebDav.downloadFile(credentials, folderName, "images/${img.filename}")
            if (bytes != null) File(docDir, img.filename).writeBytes(bytes)
            img.videoFilename?.let { rel ->
                val bareName = rel.substringAfterLast("/")
                val videoBytes = NextcloudWebDav.downloadFile(credentials, folderName, "videos/$bareName")
                if (videoBytes != null) NotebookStore.videoFile(context, id, rel).apply { parentFile?.mkdirs() }.writeBytes(videoBytes)
            }
        }

        saveCachedExtras(context, id, result.extras)
        NotebookStore.saveDocument(context, document)
        return document
    }
}
