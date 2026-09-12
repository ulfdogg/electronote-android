package de.graetz.electronote.ai

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color

enum class AiProvider(val label: String, val url: String, val brandColor: Color) {
    ChatGPT("ChatGPT", "https://chatgpt.com", Color(0xFF10A37F)),
    Claude("Claude", "https://claude.ai/new", Color(0xFFD97757)),
    Gemini("Gemini", "https://gemini.google.com/app", Color(0xFF4285F4))
}

/**
 * Opens an AI provider's chat site in Chrome Custom Tabs rather than an in-app WebView.
 * Google (and others) refuse to complete "Sign in with Google" inside embedded WebViews —
 * Custom Tabs runs in the real, installed Chrome's session/cookie jar, so login works
 * normally, including for accounts that only ever registered via Google. Same reasoning
 * as using SFSafariViewController instead of WKWebView on iOS.
 */
fun openUrlInBrowser(context: Context, url: String) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}

fun openAiProvider(context: Context, provider: AiProvider) = openUrlInBrowser(context, provider.url)
