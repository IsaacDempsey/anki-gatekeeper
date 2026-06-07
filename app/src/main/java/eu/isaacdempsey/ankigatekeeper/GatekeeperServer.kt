package eu.isaacdempsey.ankigatekeeper

import android.content.Context
import android.content.Intent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

class GatekeeperServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1)
            }.also { serverSocket = it }

            executor.execute {
                while (!socket.isClosed) {
                    try {
                        socket.accept().use { client ->
                            client.soTimeout = 2000
                            var requestPath = "/"
                            try {
                                val reader = BufferedReader(
                                    InputStreamReader(client.getInputStream(), Charsets.UTF_8)
                                )

                                val requestLine = reader.readLine()
                                if (requestLine != null) {
                                    val parts = requestLine.split(" ")
                                    if (parts.size >= 2) requestPath = parts[1].substringBefore('?')
                                }

                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            } catch (_: IOException) { }

                            when (requestPath) {
                                "/watchman.jpg" -> serveAsset(client.getOutputStream(), "watchman.jpg", "image/jpeg")
                                "/icon.png", "/favicon.ico" -> serveAsset(client.getOutputStream(), "icon.png", "image/png")
                                else -> {
                                    client.getOutputStream().apply {
                                        write(httpHeader("text/html; charset=utf-8", responseHtmlBytes.size).toByteArray())
                                        write(responseHtmlBytes)
                                        flush()
                                    }
                                    context.startActivity(
                                        Intent(context, MainActivity::class.java).apply {
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            )
                                            putExtra(MainActivity.EXTRA_FROM_WEB, true)
                                        }
                                    )
                                }
                            }
                        }
                    } catch (_: IOException) { }
                }
            }
        } catch (_: IOException) { }
    }

    private fun httpHeader(contentType: String, bodyLen: Int, status: String = "200 OK") =
        "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: $bodyLen\r\nConnection: close\r\n\r\n"

    private fun serveAsset(out: OutputStream, assetName: String, contentType: String) {
        try {
            context.assets.open(assetName).use { stream ->
                val body = stream.readBytes()
                out.write(httpHeader(contentType, body.size).toByteArray())
                out.write(body)
                out.flush()
            }
        } catch (_: IOException) {
            val body = "404 Not Found".toByteArray()
            out.write(httpHeader("text/plain", body.size, "404 Not Found").toByteArray())
            out.write(body)
            out.flush()
        }
    }

    fun stop() {
        serverSocket?.close()
        executor.shutdown()
    }

    private val responseHtmlBytes: ByteArray by lazy {
        context.assets.open("blocked.html").use { it.readBytes() }
    }

    companion object {
        const val PORT = 8765
    }
}
