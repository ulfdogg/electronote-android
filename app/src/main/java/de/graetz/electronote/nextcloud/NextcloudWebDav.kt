package de.graetz.electronote.nextcloud

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class RemoteDocumentEntry(val folderName: String)

/**
 * Minimal WebDAV client against Nextcloud's `remote.php/dav/files/<user>/...` endpoint,
 * authenticated with the app-password obtained via [NextcloudLoginFlow]. Only what's
 * needed for "save this one document here / load that one document from there" — no
 * background sync, matching the "no automatic reconciliation" requirement.
 */
object NextcloudWebDav {

    private const val ROOT_FOLDER = "ElectroNote"

    private fun davBase(credentials: NextcloudCredentials): String =
        "${credentials.serverUrl}/remote.php/dav/files/${encodeSegment(credentials.loginName)}"

    private fun encodeSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun authHeader(credentials: NextcloudCredentials): String {
        val raw = "${credentials.loginName}:${credentials.appPassword}"
        return "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
    }

    private fun connection(credentials: NextcloudCredentials, encodedPath: String, method: String): HttpURLConnection {
        val url = URL(davBase(credentials) + encodedPath)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", authHeader(credentials))
            connectTimeout = 20000
            readTimeout = 20000
        }
    }

    private fun ensureDirectory(credentials: NextcloudCredentials, encodedPath: String): Boolean {
        val conn = connection(credentials, encodedPath, "MKCOL")
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299 || code == 405 // 405 = already exists
    }

    fun ensureDocumentFolder(credentials: NextcloudCredentials, folderName: String): Boolean {
        ensureDirectory(credentials, "/${encodeSegment(ROOT_FOLDER)}")
        return ensureDirectory(credentials, "/${encodeSegment(ROOT_FOLDER)}/${encodeSegment(folderName)}")
    }

    /**
     * Creates any intermediate subdirectories of [relativeFilePath] (e.g. "images/foo.png"
     * → creates ".../<folderName>/images") so files can live in iOS-style subfolders
     * (pdfs/, images/, videos/) instead of flat inside the document folder.
     */
    private fun ensureParentDirs(credentials: NextcloudCredentials, folderName: String, relativeFilePath: String) {
        val segments = relativeFilePath.split("/").dropLast(1)
        var path = "/${encodeSegment(ROOT_FOLDER)}/${encodeSegment(folderName)}"
        for (seg in segments) {
            path += "/${encodeSegment(seg)}"
            ensureDirectory(credentials, path)
        }
    }

    private fun encodedFilePath(folderName: String, relativeFilePath: String): String =
        "/${encodeSegment(ROOT_FOLDER)}/${encodeSegment(folderName)}/" +
            relativeFilePath.split("/").joinToString("/") { encodeSegment(it) }

    /** [relativeFilePath] may contain '/' to place the file in an iOS-style subfolder. */
    fun uploadFile(credentials: NextcloudCredentials, folderName: String, relativeFilePath: String, bytes: ByteArray): Boolean {
        if (relativeFilePath.contains("/")) ensureParentDirs(credentials, folderName, relativeFilePath)
        val path = encodedFilePath(folderName, relativeFilePath)
        val conn = connection(credentials, path, "PUT")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        return try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    fun downloadFile(credentials: NextcloudCredentials, folderName: String, relativeFilePath: String): ByteArray? {
        val path = encodedFilePath(folderName, relativeFilePath)
        val conn = connection(credentials, path, "GET")
        return try {
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /** Lists filenames (not full paths) directly inside `<folderName>/<subPath>`. */
    fun listFiles(credentials: NextcloudCredentials, folderName: String, subPath: String): List<String> {
        val dirPath = if (subPath.isEmpty()) {
            "/${encodeSegment(ROOT_FOLDER)}/${encodeSegment(folderName)}/"
        } else {
            "/${encodeSegment(ROOT_FOLDER)}/${encodeSegment(folderName)}/" +
                subPath.split("/").joinToString("/") { encodeSegment(it) } + "/"
        }
        val conn = connection(credentials, dirPath, "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.doOutput = true
        val requestBody = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent()
        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseFileEntries(xml)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFileEntries(xml: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))

            var currentHref: String? = null
            var isCollection = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response" -> { currentHref = null; isCollection = false }
                        "href" -> currentHref = parser.nextText()
                        "collection" -> isCollection = true
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "response") {
                        val href = currentHref
                        if (!isCollection && href != null) {
                            val decoded = URLDecoder.decode(href, "UTF-8")
                            val name = decoded.trimEnd('/').substringAfterLast('/')
                            if (name.isNotEmpty()) results.add(name)
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return results
    }

    /** Lists document folders under /ElectroNote/ so the user can pick one to download. */
    fun listDocumentFolders(credentials: NextcloudCredentials): List<RemoteDocumentEntry> {
        ensureDirectory(credentials, "/${encodeSegment(ROOT_FOLDER)}")
        val conn = connection(credentials, "/${encodeSegment(ROOT_FOLDER)}/", "PROPFIND")
        conn.setRequestProperty("Depth", "1")
        conn.doOutput = true
        val requestBody = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent()
        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }
            val xml = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseFolderEntries(xml)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseFolderEntries(xml: String): List<RemoteDocumentEntry> {
        val results = mutableListOf<RemoteDocumentEntry>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))

            var currentHref: String? = null
            var isCollection = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response" -> { currentHref = null; isCollection = false }
                        "href" -> currentHref = parser.nextText()
                        "collection" -> isCollection = true
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "response") {
                        val href = currentHref
                        if (isCollection && href != null) {
                            val decoded = URLDecoder.decode(href, "UTF-8").trimEnd('/')
                            val folderName = decoded.substringAfterLast('/')
                            if (folderName.isNotEmpty() && decoded.contains("/$ROOT_FOLDER/")) {
                                results.add(RemoteDocumentEntry(folderName))
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return results
    }
}
