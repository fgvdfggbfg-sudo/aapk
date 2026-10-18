package com.smartcalc.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    @Volatile private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the native splash until the first page has rendered (max 1.5 s)
        val start = System.currentTimeMillis()
        splash.setKeepOnScreenCondition { !pageReady && System.currentTimeMillis() - start < 1500 }

        // Animated exit: icon springs back, then zooms and spins away while the background fades
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val screen = provider.view
            val set = AnimatorSet()
            set.playTogether(
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 3f),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 3f),
                ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 30f),
                ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(screen, View.ALPHA, 1f, 0f)
            )
            set.duration = 650L
            set.interpolator = AnticipateInterpolator(1.4f)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    provider.remove()
                }
            })
            set.start()
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#111827"))
        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#111827"))
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        root.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        // Edge-to-edge: pad content away from status bar, nav bar, notch and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // Serve the bundled web app from assets/www over a secure https origin.
        // Clean links like "/menu" are mapped to "menu.html".
        val assetsHandler = WebViewAssetLoader.AssetsPathHandler(this)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.PathHandler { path ->
                var p = path.substringBefore('?').trimEnd('/')
                if (p.isEmpty()) p = "index"
                if (!p.substringAfterLast('/').contains('.')) p += ".html"
                val file = if (assetExists("www/$p")) "www/$p" else "www/menu.html"
                assetsHandler.handle(file)
            })
            .build()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = true
        }
        webView.addJavascriptInterface(Bridge(), "AndroidApp")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: Exception) {
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(window.appBack&&window.appBack())?'1':'0'") { r ->
                    if (r == "\"1\"") return@evaluateJavascript
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            }
        })
    }

    private fun assetExists(path: String): Boolean = try {
        assets.open(path).close(); true
    } catch (e: Exception) {
        false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /** Methods callable from JavaScript as window.AndroidApp.* */
    inner class Bridge {

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Calculator", text))
            }
        }

        @JavascriptInterface
        fun shareText(title: String, text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, title.ifEmpty { "Share" }))
            }
        }

        @Suppress("DEPRECATION")
        @JavascriptInterface
        fun vibrate(ms: Int) {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(1, 200), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(ms.toLong().coerceIn(1, 200))
            }
        }

        /** Saves a base64 file to Downloads/Calculator. Returns true on success. */
        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String): Boolean {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Calculator")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
                } else {
                    val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Calculator")
                    dir.mkdirs()
                    File(dir, safe).writeBytes(bytes)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        /** Matches system bar icon colours to the app's light / dark theme. */
        @JavascriptInterface
        fun setTheme(light: Boolean) {
            runOnUiThread {
                val bg = Color.parseColor(if (light) "#F8FAFC" else "#111827")
                root.setBackgroundColor(bg)
                webView.setBackgroundColor(bg)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
    }
}
