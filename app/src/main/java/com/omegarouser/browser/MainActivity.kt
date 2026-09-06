package com.omegarouser.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnHome: ImageButton

    private val homeUrl = "file:///android_asset/start_page.html"

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

        setupWebView()
        setupControls()

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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.max = 100
                progressBar.progress = 0
                progressBar.visibility = ProgressBar.VISIBLE
                url?.let { addressBar.setText(displayUrl(it)) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = ProgressBar.GONE
                swipeRefresh.isRefreshing = false
                updateNavButtons()
                url?.let { addressBar.setText(displayUrl(it)) }
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
        }

        swipeRefresh.setOnRefreshListener {
            webView.reload()
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
