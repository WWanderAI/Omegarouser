package com.omegarouser.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var sslIndicator: ImageView

    private val homeUrl = "file:///android_asset/start_page.html"

    // Простой список рекламных/трекинговых доменов для блокировки
    private val adBlockHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "adservice.google.com",
        "adsystem.com",
        "amazon-adsystem.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "moatads.com",
        "scorecardresearch.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "mc.yandex.ru",
        "mc.yandex.com",
        "an.yandex.ru",
        "yandexadexchange.net",
        "vk.com/rtrg",
        "top-fwz1.mail.ru",
        "top.mail.ru",
        "popads.net",
        "adcolony.com"
    )

    // Ожидающий геолокационный колбэк, пока запрашиваем системное разрешение
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    // Ожидающий запрос камеры/микрофона от WebView
    private var pendingPermissionRequest: PermissionRequest? = null

    private val requestGeoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        if (origin != null && callback != null) {
            callback.invoke(origin, granted, false)
        }
        pendingGeoOrigin = null
        pendingGeoCallback = null
    }

    private val requestMediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingPermissionRequest
        if (request != null) {
            val allGranted = results.values.all { it }
            if (allGranted) {
                request.grant(request.resources)
            } else {
                request.deny()
                Toast.makeText(this, "Доступ к камере/микрофону не предоставлен", Toast.LENGTH_SHORT).show()
            }
        }
        pendingPermissionRequest = null
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не критичен, загрузка всё равно начнётся */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        addressBar = findViewById(R.id.addressBar)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnReload = findViewById(R.id.btnReload)
        btnHome = findViewById(R.id.btnHome)
        sslIndicator = findViewById(R.id.sslIndicator)

        setupWebView()
        setupControls()
        maybeRequestNotificationPermission()

        webView.loadUrl(homeUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.setGeolocationEnabled(true)
        webView.settings.mediaPlaybackRequiresUserGesture = false

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val host = request?.url?.host?.lowercase()
                if (host != null && adBlockHosts.any { host.contains(it) }) {
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.max = 100
                progressBar.progress = 0
                progressBar.visibility = ProgressBar.VISIBLE
                url?.let {
                    addressBar.setText(displayUrl(it))
                    updateSslIndicator(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = ProgressBar.GONE
                swipeRefresh.isRefreshing = false
                updateNavButtons()
                url?.let {
                    addressBar.setText(displayUrl(it))
                    updateSslIndicator(it)
                }
                if (url != null && !url.startsWith("file:///android_asset")) {
                    applyGreenFilter(view)
                }
            }
        }

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val hasPermission = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    requestGeoPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val neededAndroidPermissions = mutableListOf<String>()
                for (resource in request.resources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            neededAndroidPermissions.add(Manifest.permission.CAMERA)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            neededAndroidPermissions.add(Manifest.permission.RECORD_AUDIO)
                    }
                }
                if (neededAndroidPermissions.isEmpty()) {
                    request.grant(request.resources)
                    return
                }
                val allGranted = neededAndroidPermissions.all {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    requestMediaPermissionsLauncher.launch(neededAndroidPermissions.toTypedArray())
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                request.addRequestHeader("User-Agent", userAgent)
                request.setDescription("Загрузка файла — Omegarouser")
                val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setTitle(fileName)
                request.allowScanningByMediaScanner()
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Скачивание начато: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show()
            }
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupControls() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnReload.setOnClickListener {
            webView.reload()
        }
        btnHome.setOnClickListener {
            webView.loadUrl(homeUrl)
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                navigateFromInput(addressBar.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun navigateFromInput(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return

        val url = when {
            Patterns.WEB_URL.matcher(query).matches() -> {
                if (!query.startsWith("http://") && !query.startsWith("https://")) {
                    "https://$query"
                } else {
                    query
                }
            }
            else -> {
                "https://www.google.com/search?q=" + Uri.encode(query)
            }
        }
        webView.loadUrl(url)
    }

    private fun applyGreenFilter(view: WebView?) {
        val js = """
            (function() {
                if (document.getElementById('omegarouser-green-filter')) return;
                var style = document.createElement('style');
                style.id = 'omegarouser-green-filter';
                style.type = 'text/css';
                style.appendChild(document.createTextNode(
                    'html { filter: hue-rotate(100deg) saturate(0.92) !important; -webkit-filter: hue-rotate(100deg) saturate(0.92) !important; }'
                ));
                document.head.appendChild(style);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun displayUrl(url: String): String {
        return if (url.startsWith("file:///android_asset/start_page.html")) "" else url
    }

    private fun updateSslIndicator(url: String) {
        when {
            url.startsWith("file:///android_asset") -> {
                sslIndicator.visibility = ImageView.GONE
            }
            url.startsWith("https://") -> {
                sslIndicator.visibility = ImageView.VISIBLE
                sslIndicator.setImageResource(R.drawable.ic_lock_secure)
            }
            url.startsWith("http://") -> {
                sslIndicator.visibility = ImageView.VISIBLE
                sslIndicator.setImageResource(R.drawable.ic_lock_insecure)
            }
            else -> {
                sslIndicator.visibility = ImageView.GONE
            }
        }
    }

    private fun updateNavButtons() {
        btnBack.isEnabled = webView.canGoBack()
        btnForward.isEnabled = webView.canGoForward()
        btnBack.alpha = if (webView.canGoBack()) 1.0f else 0.4f
        btnForward.alpha = if (webView.canGoForward()) 1.0f else 0.4f
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
