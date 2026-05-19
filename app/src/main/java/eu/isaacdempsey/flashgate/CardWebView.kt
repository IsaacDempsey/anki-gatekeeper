package eu.isaacdempsey.flashgate

import android.os.Build
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.IOException

private val SOUND_REGEX = Regex("""\[sound:([^\]]+)\]""")
private val VIDEO_EXT = Regex(""".*\.(mp4|mov|avi|mkv|webm)$""", RegexOption.IGNORE_CASE)

private val mediaDir: String
    get() = "${Environment.getExternalStorageDirectory().absolutePath}/AnkiDroid/collection.media"

private fun preprocessHtml(html: String, autoPlayFirst: Boolean = false): String {
    var isFirst = true
    return SOUND_REGEX.replace(html) { m ->
        val name = m.groupValues[1]
        if (VIDEO_EXT.matches(name)) {
            "<video src=\"$name\" controls style=\"max-width:100%\"></video>"
        } else {
            val autoplay = if (autoPlayFirst && isFirst) " autoplay" else ""
            isFirst = false
            "<audio src=\"$name\" controls$autoplay></audio>"
        }
    }
}

private fun wrapHtml(body: String, css: String): String = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
$css
img{max-width:100%;height:auto;display:block;margin:8px auto}
audio,video{max-width:100%;display:block;margin:8px auto}
</style>
</head><body class="card">$body</body></html>"""

private fun guessMime(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png"         -> "image/png"
    "gif"         -> "image/gif"
    "webp"        -> "image/webp"
    "svg"         -> "image/svg+xml"
    "mp3"         -> "audio/mpeg"
    "ogg"         -> "audio/ogg"
    "wav"         -> "audio/wav"
    "mp4"         -> "video/mp4"
    "webm"        -> "video/webm"
    else          -> "application/octet-stream"
}

@Composable
fun CardWebView(html: String, css: String, autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/media/") { path ->
                    try {
                        WebResourceResponse(guessMime(path), null, File(mediaDir, path).inputStream())
                    } catch (_: IOException) {
                        null
                    }
                }
                .build()

            WebView(context).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    settings.isAlgorithmicDarkeningAllowed = true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    settings.forceDark = WebSettings.FORCE_DARK_AUTO
                }
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        assetLoader.shouldInterceptRequest(request.url)
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/media/",
                wrapHtml(preprocessHtml(html, autoPlay), css),
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = modifier,
    )
}
