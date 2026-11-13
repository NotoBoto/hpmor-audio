package io.github.notoboto.unison.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import io.github.notoboto.unison.app
import io.github.notoboto.unison.sources.PluginException
import io.github.notoboto.unison.sources.PluginInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// WebView screens of sources (SOURCES_DESIGN.md §8.2, §19.2): login and browser checks.
// They live inside MainActivity, so the app lock (appLock) covers them like any other screen.

fun loginRoute(pluginId: String) = "srclogin/$pluginId"
fun browserRoute(pluginId: String, url: String) = "srcweb/$pluginId?url=${Uri.encode(url)}"

@SuppressLint("SetJavaScriptEnabled")
private fun sourceWebView(ctx: android.content.Context, ua: String?, onPage: (WebView, String) -> Unit, onProgress: (Boolean) -> Unit) =
    WebView(ctx).apply {
        // AndroidView gives views WRAP_CONTENT: a WebView then sizes the page to its content, and pages that take the
        // window's height (html { height: 100% }, Author.Today's mobile site) come out 0 px tall and blank
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // sites treat "; wv" (an in-app WebView) differently, Author.Today's mobile pages break on it: look like Chrome
        settings.userAgentString = (ua ?: settings.userAgentString).replace("; wv)", ")")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)   // OAuth pages on another domain
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { onProgress(true) }
            override fun onPageFinished(view: WebView, url: String) { onProgress(false); onPage(view, url) }
        }
    }

/** Login in a WebView: cookies or a token from localStorage (§19.2). Closes itself once the token is there. */
@Composable
fun SourceLoginScreen(nav: NavController, pluginId: String) {
    val app = LocalContext.current.app()
    val p = remember { app.sources.repos.installed()[pluginId]?.info } ?: run { EmptyState("🧩", "Расширение не установлено"); return }
    val login = p.login ?: run { EmptyState("🔑", "Этому источнику вход не нужен"); return }
    var loading by remember { mutableStateOf(true) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }
    val siteHost = remember { Uri.parse(p.site).host.orEmpty().removePrefix("www.") }

    fun finish() {
        if (done) return
        done = true
        CookieManager.getInstance().flush()
        nav.popBackStack()
    }

    // localStorage: read the key on the site's own pages; SPAs write it without a page load, so poll as well
    fun probe(view: WebView) {
        if (login.kind != "localStorage" || done) return
        val host = Uri.parse(view.url ?: return).host.orEmpty().removePrefix("www.")
        if (host != siteHost && !host.endsWith(".$siteHost")) return
        view.evaluateJavascript("(function(){try{return localStorage.getItem(${JsonPrimitive(login.key ?: "")})}catch(e){return null}})()") { res ->
            val raw = runCatching { (Json.parseToJsonElement(res) as? JsonPrimitive)?.contentOrNull }.getOrNull()
            if (app.sources.auth.saveLocalStorage(p, raw)) finish()
        }
    }
    LaunchedEffect(web) { while (web != null && !done) { delay(1500); web?.let { probe(it) } } }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    Scaffold(topBar = {
        BackBar(nav, "Вход: ${p.name}") {
            if (login.kind == "cookies") TextButton(onClick = { app.sources.auth.saveCookieLogin(p); finish() }) { Text("Готово") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (login.kind == "cookies") "Войдите на сайте и нажмите «Готово»." else "Войдите на сайте — окно закроется само.",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AndroidView({ ctx -> sourceWebView(ctx, null, { v, _ -> probe(v) }, { loading = it }).also { web = it; it.loadUrl(login.url) } },
                Modifier.fillMaxSize())
        }
    }
}

/** A browser check (Cloudflare, DDoS-Guard, …): the user passes it, the app keeps the WebView's UA and cookies (§8.2). */
@Composable
fun SourceBrowserScreen(nav: NavController, pluginId: String, url: String) {
    val app = LocalContext.current.app()
    val p = remember { app.sources.repos.installed()[pluginId]?.info } ?: run { EmptyState("🧩", "Расширение не установлено"); return }
    var loading by remember { mutableStateOf(true) }
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    Scaffold(topBar = {
        BackBar(nav, p.name) {
            TextButton(onClick = {
                web?.let { app.sources.auth.saveBrowser(p.id, it.settings.userAgentString) }
                CookieManager.getInstance().flush()
                nav.popBackStack()
            }) { Text("Готово") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Пройдите проверку сайта, дождитесь его страницы и нажмите «Готово».",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AndroidView({ ctx -> sourceWebView(ctx, null, { _, _ -> }, { loading = it }).also { web = it; it.loadUrl(url.ifBlank { p.site }) } },
                Modifier.fillMaxSize())
        }
    }
}

/** Forgets the login: token, cookies and the site's localStorage in the WebView. */
fun sourceLogout(ctx: android.content.Context, p: PluginInfo) {
    ctx.app().sources.auth.logout(p)
    runCatching { WebStorage.getInstance().deleteOrigin(p.site.trimEnd('/')) }
}

/** An error of a source with the action it calls for (§8.2): log in, pass a browser check, retry, open the site. */
@Composable
fun SourceError(nav: NavController, p: PluginInfo?, e: Throwable, retry: () -> Unit) {
    val info = (e as? PluginException)?.info
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(errorText(e), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                info?.code == "NEEDS_LOGIN" && p?.login != null -> Button(onClick = { nav.navigate(loginRoute(p.id)) }) { Text("Войти") }
                info?.code == "NEEDS_BROWSER" && p != null -> Button(onClick = { nav.navigate(browserRoute(p.id, info.url ?: p.site)) }) { Text("Пройти проверку") }
                else -> OutlinedButton(onClick = retry) { Text("Повторить") }
            }
            val open = info?.url ?: p?.site
            if (open != null) OutlinedButton(onClick = { nav.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(open))) }) { Text("Открыть в браузере") }
        }
    }
}
