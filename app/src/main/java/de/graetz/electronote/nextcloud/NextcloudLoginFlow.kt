package de.graetz.electronote.nextcloud

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LoginPoll(val token: String, val endpoint: String, val loginUrl: String)

/**
 * Nextcloud's "Login Flow v2" — the same browser-based login every official Nextcloud app
 * uses: the user enters their password on their own server's real login page (never inside
 * this app), and Nextcloud hands back a scoped app-password automatically. Avoids ever
 * asking the user to generate/paste a WebDAV app-password by hand.
 * https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html
 */
object NextcloudLoginFlow {

    fun initiate(serverUrl: String): LoginPoll? {
        val base = serverUrl.trimEnd('/')
        return try {
            val conn = (URL("$base/index.php/login/v2").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            conn.outputStream.use { it.write(ByteArray(0)) }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            val poll = obj.getJSONObject("poll")
            LoginPoll(
                token = poll.getString("token"),
                endpoint = poll.getString("endpoint"),
                loginUrl = obj.getString("login")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** One poll attempt. Returns null while the user hasn't finished logging in yet. */
    fun poll(pollInfo: LoginPoll): NextcloudCredentials? {
        return try {
            val conn = (URL(pollInfo.endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }
            val payload = "token=" + URLEncoder.encode(pollInfo.token, "UTF-8")
            conn.outputStream.use { it.write(payload.toByteArray()) }

            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(body)
            NextcloudCredentials(
                serverUrl = obj.getString("server").trimEnd('/'),
                loginName = obj.getString("loginName"),
                appPassword = obj.getString("appPassword")
            )
        } catch (e: Exception) {
            null
        }
    }
}
