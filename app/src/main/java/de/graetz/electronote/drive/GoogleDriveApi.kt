package de.graetz.electronote.drive

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveFileEntry(val id: String, val name: String, val isFolder: Boolean)

/** Raw Drive REST v3 calls over HttpURLConnection — same house style as NextcloudWebDav,
 * no google-api-client dependency needed for this small a surface area. */
object GoogleDriveApi {
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    private fun connect(urlStr: String, method: String, token: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return conn
    }

    fun findChild(token: String, parentId: String, name: String, foldersOnly: Boolean = false): DriveFileEntry? {
        val mimeClause = if (foldersOnly) " and mimeType='$FOLDER_MIME'" else ""
        val q = "name='${escapeQuery(name)}' and '$parentId' in parents and trashed=false$mimeClause"
        val url = "$FILES_URL?q=${URLEncoder.encode(q, "UTF-8")}&fields=${URLEncoder.encode("files(id,name,mimeType)", "UTF-8")}"
        val conn = connect(url, "GET", token)
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            DriveFileEntry(obj.getString("id"), obj.getString("name"), obj.optString("mimeType") == FOLDER_MIME)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun listChildren(token: String, parentId: String): List<DriveFileEntry> {
        val q = "'$parentId' in parents and trashed=false"
        val url = "$FILES_URL?q=${URLEncoder.encode(q, "UTF-8")}&fields=${URLEncoder.encode("files(id,name,mimeType)", "UTF-8")}&pageSize=200"
        val conn = connect(url, "GET", token)
        return try {
            if (conn.responseCode != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("files") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DriveFileEntry(obj.getString("id"), obj.getString("name"), obj.optString("mimeType") == FOLDER_MIME)
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    fun ensureFolder(token: String, parentId: String, name: String): String? {
        findChild(token, parentId, name, foldersOnly = true)?.let { return it.id }
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            put("parents", JSONArray().put(parentId))
        }
        val conn = connect(FILES_URL, "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        return try {
            conn.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().readText()
            JSONObject(body).getString("id")
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** "root" is Drive's documented alias for the user's My Drive top level. */
    fun ensureRootFolder(token: String, name: String): String? =
        findChild(token, "root", name, foldersOnly = true)?.id ?: ensureFolder(token, "root", name)

    /** Creates or overwrites (matched by name within parentId) a file's content. */
    fun uploadFile(token: String, parentId: String, name: String, mimeType: String, bytes: ByteArray): Boolean {
        val existing = findChild(token, parentId, name)
        return if (existing != null) {
            updateFileContent(token, existing.id, mimeType, bytes)
        } else {
            createFile(token, parentId, name, mimeType, bytes)
        }
    }

    private fun createFile(token: String, parentId: String, name: String, mimeType: String, bytes: ByteArray): Boolean {
        val boundary = "electronote-${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", JSONArray().put(parentId))
        }
        val body = ("--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata.toString() +
            "\r\n--$boundary\r\n" +
            "Content-Type: $mimeType\r\n\r\n").toByteArray(Charsets.UTF_8) +
            bytes +
            "\r\n--$boundary--".toByteArray(Charsets.UTF_8)

        val conn = connect("$UPLOAD_URL?uploadType=multipart", "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        return try {
            conn.outputStream.use { it.write(body) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    // HttpURLConnection rejects "PATCH" outright on some Android/JDK builds (validates
    // against a fixed method list). Google's API explicitly documents this override header
    // as the workaround for clients that can only send GET/POST.
    private fun updateFileContent(token: String, fileId: String, mimeType: String, bytes: ByteArray): Boolean {
        val conn = connect("$UPLOAD_URL/$fileId?uploadType=media", "POST", token)
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", mimeType)
        return try {
            conn.outputStream.use { it.write(bytes) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    fun downloadFile(token: String, fileId: String): ByteArray? {
        val conn = connect("$FILES_URL/$fileId?alt=media", "GET", token)
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun escapeQuery(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}
