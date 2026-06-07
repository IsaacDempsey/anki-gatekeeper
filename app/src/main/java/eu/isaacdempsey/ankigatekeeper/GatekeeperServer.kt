package eu.isaacdempsey.ankigatekeeper

import android.content.Context
import android.content.Intent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
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
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1)
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
                                    if (parts.size >= 2) requestPath = parts[1]
                                }

                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                            } catch (_: IOException) { }

                            if (requestPath == "/it-gets-easier.gif") {
                                context.assets.open("it-gets-easier.gif").use { stream ->
                                    val body = stream.readBytes()
                                    val header = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/gif\r\n" +
                                        "Content-Length: ${body.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    client.getOutputStream().apply {
                                        write(header.toByteArray())
                                        write(body)
                                        flush()
                                    }
                                }
                            } else {
                                val body = RESPONSE_HTML.toByteArray(Charsets.UTF_8)
                                val header = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${body.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                client.getOutputStream().apply {
                                    write(header.toByteArray())
                                    write(body)
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
                    } catch (_: IOException) { }
                }
            }
        } catch (_: IOException) { }
    }

    fun stop() {
        serverSocket?.close()
        executor.shutdown()
    }

    companion object {
        const val PORT = 8765

        private val RESPONSE_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Anki Gatekeeper</title>
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                  background: #000;
                  display: flex;
                  justify-content: center;
                  align-items: center;
                  min-height: 100vh;
                }
                img { max-width: 100%; max-height: 100vh; display: block; }
              </style>
            </head>
            <body>
              <img src="/it-gets-easier.gif" alt="">
            </body>
            </html>
        """.trimIndent()
    }
}
