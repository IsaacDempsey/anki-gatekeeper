package com.example.ankilauncher

import android.os.Build
import android.os.Environment
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private val SOUND_REGEX = Regex("""\[sound:([^\]]+)\]""")
private val VIDEO_EXT = Regex(""".*\.(mp4|mov|avi|mkv|webm)$""", RegexOption.IGNORE_CASE)

private fun ankiMediaBaseUrl(): String =
    "file://${Environment.getExternalStorageDirectory().absolutePath}/AnkiDroid/collection.media/"

private fun preprocessHtml(html: String): String = SOUND_REGEX.replace(html) { m ->
    val name = m.groupValues[1]
    if (VIDEO_EXT.matches(name)) "<video src=\"$name\" controls style=\"max-width:100%\"></video>"
    else "<audio src=\"$name\" controls></audio>"
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

@Composable
fun CardWebView(html: String, css: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    settings.isAlgorithmicDarkeningAllowed = true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    settings.forceDark = WebSettings.FORCE_DARK_AUTO
                }
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                ankiMediaBaseUrl(),
                wrapHtml(preprocessHtml(html), css),
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = modifier,
    )
}
