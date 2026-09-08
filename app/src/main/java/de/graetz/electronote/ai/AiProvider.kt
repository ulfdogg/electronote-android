package de.graetz.electronote.ai

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

enum class AiProvider(val label: String, val url: String) {
    ChatGPT("ChatGPT", "https://chatgpt.com"),
    Claude("Claude", "https://claude.ai/new"),
    Gemini("Gemini", "https://gemini.google.com/app")
}

/**
 * Opens an AI provider's chat site in Chrome Custom Tabs rather than an in-app WebView.
 * Google (and others) refuse to complete "Sign in with Google" inside embedded WebViews —
 * Custom Tabs runs in the real, installed Chrome's session/cookie jar, so login works
 * normally, including for accounts that only ever registered via Google. Same reasoning
 * as using SFSafariViewController instead of WKWebView on iOS.
 */
fun openAiProvider(context: Context, provider: AiProvider) {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(provider.url))
}
