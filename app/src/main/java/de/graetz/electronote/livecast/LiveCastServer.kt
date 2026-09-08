package de.graetz.electronote.livecast

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Streams whatever is currently on screen to any browser on the same Wi-Fi network —
 * a hand-rolled HTTP + WebSocket server (Android has no equivalent to a ready-made local
 * server, so this mirrors the design used for the same feature on iOS: NWListener there,
 * plain java.net.ServerSocket here).
 */
object LiveCastServer {
    var isStreaming by mutableStateOf(false)
        private set
    var viewerCount by mutableIntStateOf(0)
        private set
    var localIp by mutableStateOf("")
        private set
    var port by mutableIntStateOf(8080)
    var lastError by mutableStateOf<String?>(null)
        private set

    var targetFps: Int = 15

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val wsClients = CopyOnWriteArrayList<Socket>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var targetViewRef: WeakReference<View>? = null

    /** The view whose current contents get captured — set once from the app's UI root. */
    fun setTargetView(view: View?) {
        targetViewRef = view?.let { WeakReference(it) }
    }

    fun toggle() {
        if (isStreaming) stop() else start()
    }

    fun start() {
        if (isStreaming) return
        refreshIp()
        try {
            val socket = ServerSocket(port)
            serverSocket = socket
            running.set(true)
            isStreaming = true
            lastError = null

            thread(name = "LiveCastAccept") {
                while (running.get()) {
                    try {
                        val client = socket.accept()
                        thread(name = "LiveCastClient") { handleClient(client) }
                    } catch (e: Exception) {
                        if (running.get()) {
                            // Listener itself died unexpectedly.
                        }
                    }
                }
            }

            thread(name = "LiveCastCapture") {
                while (running.get()) {
                    if (wsClients.isNotEmpty()) {
                        broadcastFrame()
                    }
                    val fps = targetFps.coerceIn(5, 30)
                    Thread.sleep(1000L / fps)
                }
            }
        } catch (e: Exception) {
            lastError = "Konnte Port $port nicht binden: ${e.message}"
            isStreaming = false
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        for (c in wsClients) {
            try { c.close() } catch (_: Exception) { }
        }
        wsClients.clear()
        viewerCount = 0
        isStreaming = false
    }

    fun refreshIp() {
        localIp = getLocalIpAddress() ?: "127.0.0.1"
    }

    fun serverUrl(): String = "http://${localIp.ifEmpty { "127.0.0.1" }}:$port"

    // MARK: - Connection handling

    private fun handleClient(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val requestLine = readLine(input)
            if (requestLine.isNullOrEmpty()) {
                socket.close()
                return
            }
            val headers = StringBuilder()
            var line: String?
            do {
                line = readLine(input)
                if (!line.isNullOrEmpty()) headers.append(line).append("\r\n")
            } while (!line.isNullOrEmpty())

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }
            val path = parts[1].substringBefore("?")
            val headerText = headers.toString()

            when {
                headerText.contains("Upgrade: websocket", ignoreCase = true) || path == "/ws" ->
                    upgradeWebSocket(socket, headerText)
                path == "/snapshot.jpg" -> serveSnapshot(socket)
                path == "/api/status" -> serveStatus(socket)
                path == "/favicon.ico" -> {
                    val resp = "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(resp.toByteArray())
                    socket.close()
                }
                else -> serveHtml(socket)
            }
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (prev == '\r'.code && b == '\n'.code) {
                sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    // MARK: - WebSocket (RFC 6455)

    private fun upgradeWebSocket(socket: Socket, headerText: String) {
        val key = Regex("(?i)sec-websocket-key:\\s*(.+)").find(headerText)?.groupValues?.get(1)?.trim()
        if (key == null) {
            socket.close()
            return
        }
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val accept = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + magic).toByteArray()),
            Base64.NO_WRAP
        )
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n"
        socket.getOutputStream().write(response.toByteArray())
        socket.getOutputStream().flush()

        wsClients.add(socket)
        viewerCount = wsClients.size
        captureCurrentFrame()?.let { sendWsFrame(socket, it) }

        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(2048)
            while (running.get()) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read >= 1) {
                    val opcode = buffer[0].toInt() and 0x0F
                    if (opcode == 0x08) break // close frame
                    if (opcode == 0x09) { // ping -> pong
                        try {
                            socket.getOutputStream().write(byteArrayOf(0x8A.toByte(), 0x00))
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            wsClients.remove(socket)
            viewerCount = wsClients.size
            try { socket.close() } catch (_: Exception) { }
        }
    }

    private fun sendWsFrame(socket: Socket, payload: ByteArray) {
        val header = ByteArrayOutputStream()
        header.write(0x82) // FIN + binary opcode
        val len = payload.size
        when {
            len < 126 -> header.write(len)
            len <= 0xFFFF -> {
                header.write(126)
                header.write((len shr 8) and 0xFF)
                header.write(len and 0xFF)
            }
            else -> {
                header.write(127)
                for (i in 7 downTo 0) header.write(((len.toLong() shr (i * 8)) and 0xFF).toInt())
            }
        }
        synchronized(socket) {
            val out = socket.getOutputStream()
            out.write(header.toByteArray())
            out.write(payload)
            out.flush()
        }
    }

    private fun broadcastFrame() {
        val frame = captureCurrentFrame() ?: return
        for (client in wsClients.toList()) {
            try {
                sendWsFrame(client, frame)
            } catch (e: Exception) {
                wsClients.remove(client)
                viewerCount = wsClients.size
                try { client.close() } catch (_: Exception) { }
            }
        }
    }

    // MARK: - Frame capture (main thread, since it draws a live View)

    private fun captureCurrentFrame(): ByteArray? {
        val view = targetViewRef?.get() ?: return null
        if (view.width <= 0 || view.height <= 0) return null

        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        mainHandler.post {
            try {
                val targetWidth = minOf(1280, view.width)
                val scale = targetWidth.toFloat() / view.width
                val w = targetWidth
                val h = (view.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)
                view.draw(canvas)
                bitmap = bmp
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }
        latch.await(200, TimeUnit.MILLISECONDS)
        val bmp = bitmap ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        return out.toByteArray()
    }

    // MARK: - HTTP endpoints

    private fun serveSnapshot(socket: Socket) {
        val jpeg = captureCurrentFrame()
        if (jpeg == null) {
            socket.close()
            return
        }
        val header = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: image/jpeg\r\n" +
            "Content-Length: ${jpeg.size}\r\n" +
            "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray())
        out.write(jpeg)
        out.flush()
        socket.close()
    }

    private fun serveStatus(socket: Socket) {
        val json = "{\"streaming\": true, \"viewers\": $viewerCount}"
        val bytes = json.toByteArray()
        val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
        socket.close()
    }

    private fun serveHtml(socket: Socket) {
        val bytes = buildHtmlViewer().toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
        socket.close()
    }

    // MARK: - Local IP

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip != null && !ip.startsWith("169.254.")) return ip
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    // MARK: - HTML5 viewer

    private fun buildHtmlViewer(): String = """
        <!DOCTYPE html>
        <html lang="de">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>ElectroNote — Live-Übertragung</title>
        <style>
            * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif; }
            body { background:#0b0e14; color:#f0f6fc; display:flex; flex-direction:column; align-items:center; min-height:100vh; }
            header { width:100%; max-width:1400px; padding:14px 20px; display:flex; align-items:center; justify-content:space-between; background:rgba(18,22,31,0.9); }
            .title { font-size:18px; font-weight:700; }
            .badge { display:inline-flex; align-items:center; gap:6px; background:rgba(255,71,87,0.15); color:#ff4757; border:1px solid rgba(255,71,87,0.35); padding:4px 10px; border-radius:20px; font-size:12px; font-weight:700; }
            .dot { width:8px; height:8px; background:currentColor; border-radius:50%; }
            .stream-container { flex:1; width:100%; max-width:1240px; padding:16px; display:flex; justify-content:center; align-items:center; }
            #stream { width:100%; height:auto; max-height:85vh; object-fit:contain; border-radius:12px; background:#000; }
            footer { padding:10px; font-size:12px; color:#8b949e; }
        </style>
        </head>
        <body>
            <header>
                <div class="title">ElectroNote</div>
                <div class="badge" id="status"><div class="dot"></div><span id="status-text">VERBINDET...</span></div>
            </header>
            <div class="stream-container"><img id="stream" alt="Live-Übertragung lädt..."></div>
            <footer>Echtzeitübertragung über lokales WLAN</footer>
            <script>
                const img = document.getElementById("stream");
                const status = document.getElementById("status");
                const statusText = document.getElementById("status-text");
                let ws = null, currentBlobUrl = null, reconnectTimer = null, firstFrame = false, fallbackTimer = null;

                function startWs() {
                    const proto = location.protocol === "https:" ? "wss:" : "ws:";
                    ws = new WebSocket(proto + "//" + location.host + "/ws");
                    ws.binaryType = "blob";
                    ws.onopen = () => { statusText.innerText = "LIVE"; };
                    ws.onmessage = (e) => {
                        firstFrame = true;
                        const url = URL.createObjectURL(e.data);
                        const old = currentBlobUrl;
                        currentBlobUrl = url;
                        img.src = url;
                        if (old) setTimeout(() => URL.revokeObjectURL(old), 100);
                    };
                    ws.onclose = () => {
                        statusText.innerText = "VERBINDET...";
                        if (!reconnectTimer) reconnectTimer = setTimeout(() => { reconnectTimer = null; startWs(); }, 1000);
                    };
                    ws.onerror = () => {};
                }

                setTimeout(() => { if (!firstFrame) startPolling(); }, 2500);

                function startPolling() {
                    if (fallbackTimer) return;
                    fallbackTimer = setInterval(() => {
                        const t = new Image();
                        t.onload = () => { img.src = t.src; statusText.innerText = "LIVE (HTTP)"; };
                        t.src = "/snapshot.jpg?t=" + Date.now();
                    }, 100);
                }

                startWs();
            </script>
        </body>
        </html>
    """.trimIndent()
}
