package com.omegarouser.browser

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Patterns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Данные одной вкладки браузера */
class TabData(
    val webView: WebView,
    var isPrivate: Boolean,
    var title: String = "",
    var url: String = ""
)

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_SEARCH = "focus_search"
        const val EXTRA_NEW_TAB = "new_tab"
    }


    private lateinit var addressBar: AutoCompleteTextView
    private lateinit var btnVoiceSearch: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnTabs: TextView
    private lateinit var sslIndicator: ImageView
    private lateinit var tabContainer: FrameLayout

    private lateinit var tabsOverlay: FrameLayout
    private lateinit var tabsRecyclerView: RecyclerView

    private lateinit var findBar: View
    private lateinit var findQuery: EditText
    private lateinit var findMatchCount: TextView

    private val homeUrl = "file:///android_asset/start_page.html"

    private val tabs = mutableListOf<TabData>()
    private var currentTabIndex = -1
    private val currentTab: TabData? get() = tabs.getOrNull(currentTabIndex)
    private val currentWebView: WebView? get() = currentTab?.webView

    // Список рекламных/трекинговых доменов для блокировки
    private val adBlockHosts = setOf(
        // Google Ads / Analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "adservice.google.com", "googletagmanager.com",
        "googletagservices.com", "googleoptimize.com", "google-analytics.l.google.com",
        "pagead2.googlesyndication.com", "adservice.google.ru",
        // Общие рекламные сети
        "adsystem.com", "amazon-adsystem.com", "taboola.com", "outbrain.com",
        "criteo.com", "criteo.net", "moatads.com", "scorecardresearch.com",
        "adnxs.com", "pubmatic.com", "rubiconproject.com", "popads.net",
        "adcolony.com", "advertising.com", "adroll.com", "bidswitch.net",
        "casalemedia.com", "contextweb.com", "openx.net", "smartadserver.com",
        "media.net", "yieldmo.com", "sharethrough.com", "sovrn.com",
        "adform.net", "adition.com", "quantserve.com", "chartbeat.com",
        "newrelic.com", "hotjar.com",
        // Яндекс/Mail.ru реклама и трекинг
        "mc.yandex.ru", "mc.yandex.com", "an.yandex.ru", "yandexadexchange.net",
        "adfox.ru", "ads.adfox.ru", "banners.adfox.ru",
        "top-fwz1.mail.ru", "top.mail.ru", "target.my.com",
        "ad.mail.ru", "an.yandex.com",
        // VK реклама и трекинг
        "ads.vk.com", "vk-portal.net",
        // Facebook/Meta трекинг
        "connect.facebook.net", "an.facebook.com",
        // Прочее
        "mgid.com", "propellerads.com", "popcash.net", "exoclick.com",
        "trafficjunky.net", "adsterra.com", "revcontent.com"
    )

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val requestGeoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        if (origin != null && callback != null) callback.invoke(origin, granted, false)
        pendingGeoOrigin = null
        pendingGeoCallback = null
    }

    private val requestMediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingPermissionRequest
        if (request != null) {
            if (results.values.all { it }) request.grant(request.resources) else request.deny()
        }
        pendingPermissionRequest = null
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback.onReceiveValue(results)
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                addressBar.setText(spoken)
                navigateFromInput(spoken)
            }
        }
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@registerForActivityResult
        if (Patterns.WEB_URL.matcher(scanned).matches()) {
            val url = if (scanned.startsWith("http://") || scanned.startsWith("https://")) scanned else "https://$scanned"
            currentWebView?.loadUrl(url)
        } else {
            navigateFromInput(scanned)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addressBar = findViewById(R.id.addressBar)
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnReload = findViewById(R.id.btnReload)
        btnMenu = findViewById(R.id.btnMenu)
        btnTabs = findViewById(R.id.btnTabs)
        sslIndicator = findViewById(R.id.sslIndicator)
        tabContainer = findViewById(R.id.tabContainer)
        tabsOverlay = findViewById(R.id.tabsOverlay)
        tabsRecyclerView = findViewById(R.id.tabsRecyclerView)
        findBar = findViewById(R.id.findBar)
        findQuery = findViewById(R.id.findQuery)
        findMatchCount = findViewById(R.id.findMatchCount)

        tabsRecyclerView.layoutManager = LinearLayoutManager(this)

        setupControls()
        maybeRequestNotificationPermission()

        val incomingUrl = extractUrlFromIntent(intent)
        createNewTab(incomingUrl ?: homeUrl, isPrivate = false)

        if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            focusAddressBar()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUrl = extractUrlFromIntent(intent)
        if (incomingUrl != null) {
            createNewTab(incomingUrl, isPrivate = false)
            hideTabsOverlay()
        }
        if (intent.getBooleanExtra(EXTRA_NEW_TAB, false)) {
            createNewTab(homeUrl, isPrivate = false)
            hideTabsOverlay()
        }
        if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            focusAddressBar()
        }
    }

    private fun focusAddressBar() {
        addressBar.post {
            addressBar.requestFocus()
            addressBar.selectAll()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // ------------------------- Вкладки -------------------------

    private fun createNewTab(url: String, isPrivate: Boolean) {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val tab = TabData(webView, isPrivate, title = getString(R.string.tabs_new_tab), url = url)
        configureWebView(tab)
        tabContainer.addView(webView)
        tabs.add(tab)
        switchToTab(tabs.lastIndex)
        webView.loadUrl(url)
        updateTabsButtonCount()
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.forEachIndexed { i, t -> t.webView.visibility = if (i == index) View.VISIBLE else View.GONE }
        currentTabIndex = index
        val tab = tabs[index]
        addressBar.setText(displayUrl(tab.url))
        updateSslIndicator(tab.url)
        updateNavButtons()
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        val closed = tabs[index]
        tabContainer.removeView(closed.webView)
        closed.webView.destroy()
        tabs.removeAt(index)

        val wasPrivate = closed.isPrivate
        if (wasPrivate && tabs.none { it.isPrivate }) {
            // Последняя приватная вкладка закрыта — подчищаем куки и данные сайтов.
            // Ограничение: это очищает куки для ВСЕХ вкладок, т.к. Android WebView
            // использует общее хранилище кук на всё приложение.
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }

        if (tabs.isEmpty()) {
            createNewTab(homeUrl, isPrivate = false)
        } else {
            val newIndex = (index - 1).coerceAtLeast(0).coerceAtMost(tabs.size - 1)
            switchToTab(newIndex)
        }
        updateTabsButtonCount()
    }

    private fun updateTabsButtonCount() {
        btnTabs.text = tabs.size.toString()
    }

    private fun showTabsOverlay() {
        tabsOverlay.visibility = View.VISIBLE
        tabsRecyclerView.adapter = TabsAdapter(
            tabs,
            onSelect = { index -> switchToTab(index); hideTabsOverlay() },
            onClose = { index -> closeTab(index); tabsRecyclerView.adapter?.notifyDataSetChanged() }
        )
    }

    private fun hideTabsOverlay() {
        tabsOverlay.visibility = View.GONE
    }

    private class TabsAdapter(
        private val items: List<TabData>,
        private val onSelect: (Int) -> Unit,
        private val onClose: (Int) -> Unit
    ) : RecyclerView.Adapter<TabsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tabTitle)
            val url: TextView = view.findViewById(R.id.tabUrl)
            val close: ImageButton = view.findViewById(R.id.tabClose)
            val privateIndicator: View = view.findViewById(R.id.tabPrivateIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tab = items[position]
            holder.title.text = if (tab.isPrivate) {
                "🔒 " + (tab.title.ifBlank { holder.itemView.context.getString(R.string.tabs_new_tab) })
            } else {
                tab.title.ifBlank { holder.itemView.context.getString(R.string.tabs_new_tab) }
            }
            holder.url.text = tab.url
            holder.privateIndicator.visibility = if (tab.isPrivate) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onSelect(position) }
            holder.close.setOnClickListener { onClose(position) }
        }

        override fun getItemCount(): Int = items.size
    }

    // ------------------------- Настройка WebView вкладки -------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(tab: TabData) {
        val webView = tab.webView
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
                if (!SettingsStore.isAdblockEnabled(this@MainActivity)) {
                    return super.shouldInterceptRequest(view, request)
                }
                val host = request?.url?.host?.lowercase()
                if (host != null && adBlockHosts.any { host.contains(it) }) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (isSuspiciousUrl(url) && !confirmedSuspiciousUrls.contains(url)) {
                    showPhishingWarning(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                tab.url = url ?: tab.url
                if (tab === currentTab) {
                    progressBar.max = 100
                    progressBar.progress = 0
                    progressBar.visibility = ProgressBar.VISIBLE
                    addressBar.setText(displayUrl(tab.url))
                    updateSslIndicator(tab.url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tab.url = url ?: tab.url
                tab.title = view?.title ?: tab.title

                if (tab === currentTab) {
                    progressBar.visibility = ProgressBar.GONE
                    swipeRefresh.isRefreshing = false
                    updateNavButtons()
                    addressBar.setText(displayUrl(tab.url))
                    updateSslIndicator(tab.url)
                }

                if (url != null && !url.startsWith("file:///android_asset")) {
                    replaceGoogleBranding(view)
                    recolorGoogleBlue(view)
                    if (SettingsStore.isAdblockEnabled(this@MainActivity)) {
                        hideAdElements(view)
                    }
                    if (!tab.isPrivate) {
                        HistoryStore.addEntry(this@MainActivity, url, tab.title)
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (tab === currentTab) {
                    ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, newProgress)
                        .setDuration(200).start()
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (title != null) tab.title = title
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
                val needed = mutableListOf<String>()
                for (resource in request.resources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> needed.add(Manifest.permission.CAMERA)
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> needed.add(Manifest.permission.RECORD_AUDIO)
                    }
                }
                if (needed.isEmpty()) { request.grant(request.resources); return }
                val allGranted = needed.all {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    requestMediaPermissionsLauncher.launch(needed.toTypedArray())
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                return try {
                    fileChooserLauncher.launch(
                        intent ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                    )
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
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
                val downloadId = dm.enqueue(request)
                DownloadStore.addEntry(this, downloadId, fileName, url, mimeType ?: "*/*")
                Toast.makeText(this, "Скачивание начато: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось начать скачивание", Toast.LENGTH_SHORT).show()
            }
        }

        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (tab === currentTab && isDoneCounting) {
                findMatchCount.text = if (numberOfMatches > 0) {
                    "${activeMatchOrdinal + 1}/$numberOfMatches"
                } else {
                    "0/0"
                }
            }
        }

        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val linkUrl = when (result.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> result.extra
                else -> null
            }
            if (linkUrl != null) {
                showLinkContextMenu(linkUrl, tab.isPrivate)
                true
            } else {
                false
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ------------------------- Панель управления -------------------------

    private fun setupControls() {
        btnBack.setOnClickListener { currentWebView?.let { if (it.canGoBack()) it.goBack() } }
        btnForward.setOnClickListener { currentWebView?.let { if (it.canGoForward()) it.goForward() } }
        btnReload.setOnClickListener { currentWebView?.reload() }
        btnTabs.setOnClickListener { showTabsOverlay() }
        btnMenu.setOnClickListener { showMainMenu(it) }

        findViewById<ImageButton>(R.id.btnCloseTabsOverlay).setOnClickListener { hideTabsOverlay() }
        findViewById<ImageButton>(R.id.btnNewTabFromOverlay).setOnClickListener {
            createNewTab(homeUrl, isPrivate = false)
            hideTabsOverlay()
        }

        findViewById<ImageButton>(R.id.findPrev).setOnClickListener { currentWebView?.findNext(false) }
        findViewById<ImageButton>(R.id.findNext).setOnClickListener { currentWebView?.findNext(true) }
        findViewById<ImageButton>(R.id.findClose).setOnClickListener { hideFindBar() }
        findQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentWebView?.findAllAsync(findQuery.text.toString())
                true
            } else false
        }

        swipeRefresh.setOnRefreshListener { currentWebView?.reload() }

        btnVoiceSearch.setOnClickListener { startVoiceSearch() }

        addressBar.threshold = 1
        addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) refreshAddressBarSuggestions()
        }
        addressBar.setOnItemClickListener { parent, _, position, _ ->
            val suggestion = (parent.adapter as? SuggestionAdapter)?.getItem(position)
            if (suggestion != null) {
                currentWebView?.loadUrl(suggestion.url)
                addressBar.setText(displayUrl(suggestion.url))
                addressBar.clearFocus()
            }
        }

        addressBar.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                navigateFromInput(addressBar.text.toString())
                true
            } else false
        }
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val isBookmarked = currentTab?.url?.let { BookmarkStore.isBookmarked(this, it) } ?: false

        popup.menu.add(0, 1, 0, R.string.menu_home)
        popup.menu.add(0, 2, 1, R.string.menu_bookmarks)
        popup.menu.add(0, 3, 2, if (isBookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark)
        popup.menu.add(0, 4, 3, R.string.menu_history)
        popup.menu.add(0, 5, 4, R.string.menu_downloads)
        popup.menu.add(0, 6, 5, R.string.menu_find_in_page)
        popup.menu.add(0, 7, 6, R.string.menu_share)
        popup.menu.add(0, 8, 7, R.string.menu_new_private_tab)
        popup.menu.add(0, 9, 8, R.string.menu_settings)
        popup.menu.add(0, 10, 9, R.string.menu_passwords)
        popup.menu.add(0, 11, 10, R.string.menu_autofill)
        popup.menu.add(0, 12, 11, R.string.menu_qr_scan)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> currentWebView?.loadUrl(homeUrl)
                2 -> openScreen(BookmarksActivity::class.java)
                3 -> toggleBookmarkForCurrentTab()
                4 -> openScreen(HistoryActivity::class.java)
                5 -> openScreen(DownloadsActivity::class.java)
                6 -> showFindBar()
                7 -> shareCurrentPage()
                8 -> createNewTab(homeUrl, isPrivate = true)
                9 -> openScreen(SettingsActivity::class.java)
                10 -> openScreen(PasswordsActivity::class.java)
                11 -> autofillPasswordForCurrentTab()
                12 -> startQrScan()
            }
            true
        }
        popup.show()
    }

    private fun <T> openScreen(activityClass: Class<T>) {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slight)
    }

    private fun toggleBookmarkForCurrentTab() {
        val tab = currentTab ?: return
        if (tab.url.startsWith("file:///android_asset")) return
        val added = BookmarkStore.toggle(this, tab.url, tab.title)
        OmegarouserBookmarksWidgetProvider.requestUpdate(this)
        val message = if (added) getString(R.string.bookmark_added) else getString(R.string.bookmark_removed)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun autofillPasswordForCurrentTab() {
        val tab = currentTab ?: return
        val host = try { Uri.parse(tab.url).host } catch (e: Exception) { null } ?: return
        val credential = PasswordStore.findForHost(this, host)
        if (credential == null) {
            Toast.makeText(this, getString(R.string.autofill_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val js = """
            (function() {
                var pw = document.querySelector('input[type=password]');
                if (!pw) return;
                var user = null;
                var inputs = document.querySelectorAll('input[type=text], input[type=email], input:not([type])');
                for (var i = 0; i < inputs.length; i++) {
                    if (inputs[i].compareDocumentPosition(pw) & Node.DOCUMENT_POSITION_FOLLOWING) {
                        user = inputs[i];
                    }
                }
                function setVal(el, val) {
                    var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                    nativeSetter.call(el, val);
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                if (user) setVal(user, ${JSONObject.quote(credential.username)});
                setVal(pw, ${JSONObject.quote(credential.password)});
            })();
        """.trimIndent()
        currentWebView?.evaluateJavascript(js) {
            Toast.makeText(this, getString(R.string.autofill_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startQrScan() {
        val options = ScanOptions()
        options.setPrompt(getString(R.string.qr_scan_prompt))
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        qrScanLauncher.launch(options)
    }

    private fun showLinkContextMenu(url: String, isPrivate: Boolean) {
        AlertDialog.Builder(this)
            .setItems(
                arrayOf(
                    getString(R.string.link_context_new_tab),
                    getString(R.string.link_context_copy),
                    getString(R.string.link_context_share)
                )
            ) { _, which ->
                when (which) {
                    0 -> createNewTab(url, isPrivate)
                    1 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("url", url))
                        Toast.makeText(this, getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_page_title)))
                    }
                }
            }
            .show()
    }

    /**
     * Простая эвристическая проверка на фишинг: сырой IP-адрес вместо домена,
     * или домен, маскирующийся под известный бренд (содержит его имя, но не
     * является официальным доменом). Это НЕ полноценная защита уровня Safe
     * Browsing — только базовая эвристика.
     */
    private val brandDomains = mapOf(
        "paypal" to "paypal.com", "google" to "google.com", "apple" to "apple.com",
        "microsoft" to "microsoft.com", "sberbank" to "sberbank.ru", "vk" to "vk.com",
        "gosuslugi" to "gosuslugi.ru", "instagram" to "instagram.com", "facebook" to "facebook.com",
        "whatsapp" to "whatsapp.com", "telegram" to "telegram.org", "steam" to "steampowered.com"
    )
    private val confirmedSuspiciousUrls = mutableSetOf<String>()

    private fun isSuspiciousUrl(url: String): Boolean {
        val host = try { Uri.parse(url).host?.lowercase() } catch (e: Exception) { null } ?: return false
        if (Patterns.IP_ADDRESS.matcher(host).matches()) return true
        for ((brand, officialDomain) in brandDomains) {
            if (host.contains(brand) && !host.endsWith(officialDomain)) return true
        }
        if (host.count { it == '-' } >= 4) return true
        return false
    }

    private fun showPhishingWarning(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.phishing_warning_title)
            .setMessage(getString(R.string.phishing_warning_message, url))
            .setPositiveButton(R.string.phishing_continue) { _, _ ->
                confirmedSuspiciousUrls.add(url)
                currentWebView?.loadUrl(url)
            }
            .setNegativeButton(R.string.phishing_go_back, null)
            .setCancelable(false)
            .show()
    }

    private fun shareCurrentPage() {
        val url = currentTab?.url ?: return
        if (url.startsWith("file:///android_asset")) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_page_title)))
    }

    private fun showFindBar() {
        findBar.visibility = View.VISIBLE
        findQuery.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(findQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideFindBar() {
        findBar.visibility = View.GONE
        currentWebView?.clearMatches()
        findQuery.setText("")
        findMatchCount.text = ""
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(findQuery.windowToken, 0)
    }

    // ------------------------- Навигация -------------------------

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_prompt))
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.voice_search_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAddressBarSuggestions() {
        val history = HistoryStore.getEntries(this).map { Suggestion(it.title, it.url) }
        val bookmarks = BookmarkStore.getEntries(this).map { Suggestion(it.title, it.url) }
        val combined = (bookmarks + history).distinctBy { it.url }
        addressBar.setAdapter(SuggestionAdapter(this, combined))
    }

    private fun navigateFromInput(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return
        val url = when {
            Patterns.WEB_URL.matcher(query).matches() -> {
                if (!query.startsWith("http://") && !query.startsWith("https://")) "https://$query" else query
            }
            else -> SettingsStore.searchUrl(this, query)
        }
        currentWebView?.loadUrl(url)
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                    if (text.isNullOrEmpty()) {
                        null
                    } else if (Patterns.WEB_URL.matcher(text).matches()) {
                        if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
                    } else {
                        SettingsStore.searchUrl(this, text)
                    }
                } else null
            }
            else -> null
        }
    }

    private fun displayUrl(url: String): String {
        return if (url.startsWith("file:///android_asset/start_page.html")) "" else url
    }

    private fun updateSslIndicator(url: String) {
        when {
            url.startsWith("file:///android_asset") -> sslIndicator.visibility = ImageView.GONE
            url.startsWith("https://") -> {
                sslIndicator.visibility = ImageView.VISIBLE
                sslIndicator.setImageResource(R.drawable.ic_lock_secure)
            }
            url.startsWith("http://") -> {
                sslIndicator.visibility = ImageView.VISIBLE
                sslIndicator.setImageResource(R.drawable.ic_lock_insecure)
            }
            else -> sslIndicator.visibility = ImageView.GONE
        }
    }

    private fun updateNavButtons() {
        val wv = currentWebView
        btnBack.isEnabled = wv?.canGoBack() == true
        btnForward.isEnabled = wv?.canGoForward() == true
        btnBack.alpha = if (wv?.canGoBack() == true) 1.0f else 0.4f
        btnForward.alpha = if (wv?.canGoForward() == true) 1.0f else 0.4f
    }

    /**
     * Точечно перекрашивает известные оттенки "гугловского синего" (кнопки типа "Войти",
     * ссылки результатов поиска) в зелёные тона нашего бренда, скругляет карточки/кнопки
     * и слегка тонирует белый фон в зелёный. В отличие от общего hue-rotate-фильтра,
     * который разворачивает ВСЕ цвета (и выглядел как инверсия), здесь меняются только
     * конкретные элементы — фото, логотипы других сервисов и основной текст не трогаются.
     *
     * Честное ограничение: это косметическая накладка поверх чужой вёрстки, а не
     * переписывание структуры страницы — расположение блоков задаёт сам сайт.
     */
    private fun recolorGoogleBlue(view: WebView?) {
        val js = """
            (function() {
                var targets = [[66,133,244],[26,115,232],[25,103,210],[11,87,208],[8,66,160],[26,13,171]];
                function dist(a, b) {
                    return Math.sqrt(Math.pow(a[0]-b[0],2) + Math.pow(a[1]-b[1],2) + Math.pow(a[2]-b[2],2));
                }
                function parseRgb(s) {
                    var m = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(s || '');
                    return m ? [parseInt(m[1]), parseInt(m[2]), parseInt(m[3])] : null;
                }
                function isCloseToTarget(rgb) {
                    if (!rgb) return false;
                    for (var i = 0; i < targets.length; i++) {
                        if (dist(rgb, targets[i]) < 22) return true;
                    }
                    return false;
                }
                function isNearWhite(rgb) {
                    return rgb && rgb[0] > 245 && rgb[1] > 245 && rgb[2] > 245;
                }
                function recolor(root) {
                    if (!root || !root.querySelectorAll) return;
                    var elements = root.querySelectorAll('*');
                    var limit = Math.min(elements.length, 4000);
                    for (var i = 0; i < limit; i++) {
                        var el = elements[i];
                        if (el.dataset && el.dataset.omegarouserRecolored) continue;
                        var cs = window.getComputedStyle(el);
                        var changed = false;

                        var bg = parseRgb(cs.backgroundColor);
                        if (isCloseToTarget(bg)) {
                            el.style.setProperty('background-color', '#34A853', 'important');
                            changed = true;
                        } else if (isNearWhite(bg) && (el.tagName === 'BODY' || el.tagName === 'HTML')) {
                            el.style.setProperty('background-color', '#F1F9F4', 'important');
                            changed = true;
                        }

                        if (isCloseToTarget(parseRgb(cs.color))) {
                            el.style.setProperty('color', '#0B8043', 'important');
                            changed = true;
                        }
                        if (isCloseToTarget(parseRgb(cs.borderColor))) {
                            el.style.setProperty('border-color', '#34A853', 'important');
                            changed = true;
                        }

                        // Скругляем карточки/кнопки без скруглений
                        var radius = parseFloat(cs.borderTopLeftRadius);
                        var hasBg = bg && !(bg[0] > 250 && bg[1] > 250 && bg[2] > 250) && cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.backgroundColor !== 'transparent';
                        if (hasBg && radius === 0 && el.tagName !== 'BODY' && el.tagName !== 'HTML') {
                            el.style.setProperty('border-radius', '14px', 'important');
                            changed = true;
                        }

                        if (changed && el.dataset) el.dataset.omegarouserRecolored = '1';
                    }
                }
                recolor(document);
                if (window.__omegarouserRecolorTimer) clearTimeout(window.__omegarouserRecolorTimer);
                if (!window.__omegarouserRecolorObserver) {
                    window.__omegarouserRecolorObserver = new MutationObserver(function() {
                        if (window.__omegarouserRecolorTimer) clearTimeout(window.__omegarouserRecolorTimer);
                        window.__omegarouserRecolorTimer = setTimeout(function() { recolor(document); }, 400);
                    });
                    if (document.body) {
                        window.__omegarouserRecolorObserver.observe(document.body, { childList: true, subtree: true });
                    }
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    /**
     * "Косметическая" блокировка: некоторые сайты (например, Lenta.ru) отдают рекламные
     * креативы напрямую со своего домена — блокировка по хосту такое не ловит.
     * Поэтому дополнительно скрываем DOM-элементы с типичной для рекламных блоков
     * разметкой (id/class с "ad", "advert", "reklama", "banner-ad", "adfox" и т.д.),
     * плюс следим за новыми элементами через MutationObserver.
     * Эвристика может иногда задеть что-то лишнее — это компромисс любого блокировщика.
     */
    private fun hideAdElements(view: WebView?) {
        val js = """
            (function() {
                var selectors = [
                    'ins.adsbygoogle',
                    'iframe[src*="doubleclick"]',
                    'iframe[src*="googlesyndication"]',
                    'iframe[id*="google_ads"]',
                    'iframe[src*="adfox"]',
                    '[id*="adfox"]',
                    '[class*="adfox"]',
                    '[id*="yandex_ad"]',
                    '[class*="yandex-ad"]',
                    '[class*="banner-ad"]',
                    '[class*="banner_ad"]',
                    '[class*="advert"]',
                    '[class*="reklama"]',
                    '[data-ad-slot]',
                    '[data-testid*="advert"]',
                    '[aria-label="Реклама"]',
                    '[class*="ad-place"]',
                    '[class*="ad_place"]',
                    '[class*="ad-container"]',
                    '[class*="ad_container"]'
                ];
                function hide(root) {
                    if (!root || !root.querySelectorAll) return;
                    selectors.forEach(function(sel) {
                        try {
                            root.querySelectorAll(sel).forEach(function(el) {
                                if (el.dataset && el.dataset.omegarouserAdHidden) return;
                                if (el.dataset) el.dataset.omegarouserAdHidden = '1';
                                el.style.setProperty('display', 'none', 'important');
                            });
                        } catch (e) {}
                    });
                }
                if (document.body) hide(document);
                if (!window.__omegarouserAdObserver) {
                    window.__omegarouserAdObserver = new MutationObserver(function() {
                        hide(document);
                    });
                    if (document.body) {
                        window.__omegarouserAdObserver.observe(document.body, { childList: true, subtree: true });
                    }
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun replaceGoogleBranding(view: WebView?) {
        val js = """
            (function() {
                function shouldSkip(node) {
                    var p = node.parentNode;
                    if (!p) return false;
                    var tag = p.nodeName;
                    return tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' ||
                           tag === 'TEXTAREA' || tag === 'INPUT';
                }
                function replaceIn(node) {
                    if (node.nodeType === 3) {
                        if (shouldSkip(node)) return;
                        var t = node.nodeValue;
                        if (/Gemini|Google/.test(t)) {
                            node.nodeValue = t.replace(/Gemini/g, 'Omegarouser').replace(/Google/g, 'Omegarouser');
                        }
                    } else if (node.nodeType === 1) {
                        for (var i = 0; i < node.childNodes.length; i++) {
                            replaceIn(node.childNodes[i]);
                        }
                    }
                }
                function replaceLogoImages(root) {
                    if (!root || !root.querySelectorAll) return;
                    var selector = 'img[alt*="Google"], img[title*="Google"], ' +
                        'svg[aria-label*="Google"], [aria-label="Google"]';
                    var nodes = root.querySelectorAll(selector);
                    nodes.forEach(function(el) {
                        if (el.dataset && el.dataset.omegarouserLogoDone) return;
                        if (el.dataset) el.dataset.omegarouserLogoDone = '1';

                        var rect = el.getBoundingClientRect();
                        var w = rect.width || el.offsetWidth || 90;
                        var h = rect.height || el.offsetHeight || 32;

                        var wrapper = document.createElement('span');
                        wrapper.style.position = 'relative';
                        wrapper.style.display = 'inline-block';
                        wrapper.style.width = w + 'px';
                        wrapper.style.height = h + 'px';
                        wrapper.style.verticalAlign = 'middle';
                        wrapper.style.overflow = 'visible';

                        var label = document.createElement('span');
                        label.textContent = 'Omegarouser';
                        label.style.position = 'absolute';
                        label.style.left = '0';
                        label.style.top = '50%';
                        label.style.transform = 'translateY(-50%)';
                        label.style.whiteSpace = 'nowrap';
                        label.style.fontFamily = 'inherit';
                        label.style.fontWeight = '700';
                        label.style.fontSize = Math.max(13, Math.min(h * 0.7, 28)) + 'px';
                        label.style.color = '#137333';
                        label.style.letterSpacing = '-0.4px';

                        wrapper.appendChild(label);
                        if (el.parentNode) el.parentNode.replaceChild(wrapper, el);
                    });
                }
                if (document.body) {
                    replaceIn(document.body);
                    replaceLogoImages(document);
                }
                if (!window.__omegarouserObserver) {
                    window.__omegarouserObserver = new MutationObserver(function(mutations) {
                        mutations.forEach(function(m) {
                            m.addedNodes.forEach(function(n) {
                                replaceIn(n);
                                if (n.nodeType === 1) replaceLogoImages(n);
                            });
                        });
                        replaceLogoImages(document);
                    });
                    if (document.body) {
                        window.__omegarouserObserver.observe(document.body, {
                            childList: true, subtree: true, characterData: true
                        });
                    }
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        when {
            tabsOverlay.visibility == View.VISIBLE -> hideTabsOverlay()
            findBar.visibility == View.VISIBLE -> hideFindBar()
            currentWebView?.canGoBack() == true -> currentWebView?.goBack()
            tabs.size > 1 -> closeTab(currentTabIndex)
            else -> super.onBackPressed()
        }
    }
}
