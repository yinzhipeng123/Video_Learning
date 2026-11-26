package com.yzp.videolearning

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    course: Course,
    onBack: () -> Unit,
    onPosition: (Long) -> Unit,
    initialPositionMs: Long,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val originalOrientation = remember(activity, course.courseId) { activity?.requestedOrientation }
    val positionCallback by rememberUpdatedState(onPosition)
    var attempt by remember(course.courseId) { mutableStateOf(0) }
    val controller = remember(course.courseId, attempt) {
        PlaybackController(context, activity, course.playerUrl, initialPositionMs) { positionCallback(it) }
    }
    val latestController by rememberUpdatedState(controller)
    DisposableEffect(activity, course.courseId) {
        onDispose {
            latestController.release()
            originalOrientation?.let { activity?.requestedOrientation = it }
        }
    }
    val leave = { if (!controller.exitFullscreen()) { controller.flushPosition(); onBack() } }
    BackHandler(onBack = leave)

    DisposableEffect(controller, activity) {
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.resume()
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                Lifecycle.Event.ON_DESTROY -> controller.release()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        if (lifecycle == null || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            controller.resume()
        } else {
            controller.pause()
        }
        onDispose {
            lifecycle?.removeObserver(observer)
            controller.release()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(course.title.ifBlank { "视频学习" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            actions = {
                IconButton(onClick = { controller.rotate() }) { Text("横/竖", style = MaterialTheme.typography.labelMedium) }
                IconButton(onClick = { controller.flushPosition(); attempt++ }) { Icon(Icons.Default.Refresh, "重新加载") }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            key(controller) {
                AndroidView(factory = { controller.createView() }, modifier = Modifier.fillMaxSize())
            }
            if (controller.loading && controller.error == null) {
                Surface(modifier = Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载视频…")
                    }
                }
            }
            controller.error?.let { message ->
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { controller.flushPosition(); attempt++ }) { Text("重新加载") }
                        Button(onClick = onBack) { Text("返回课程详情") }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.findActivity()
    else -> null
}

private class PlaybackController(
    private val context: Context,
    private val activity: Activity?,
    private val playerUrl: String,
    initialPositionMs: Long,
    private val onPosition: (Long) -> Unit,
) {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val fullscreen = PlaybackChromeClient(activity) {
        webView?.takeIf { PlaybackPolicy.allows(it.url) }?.evaluateJavascript(
            "(function(){if(document.fullscreenElement && document.exitFullscreen){document.exitFullscreen().catch(function(){});}})();", null,
        )
    }
    private var webView: WebView? = null
    private var released = false
    private var resumed = false
    private var pageReady = false
    private var sampleEpoch = 0L
    private var lastPositionMs: Long? = null
    private val restoreSeconds = initialPositionMs.coerceAtLeast(0L) / 1000.0
    private val timeout = Runnable { fail("视频页面加载超时，请检查网络后重新加载。") }
    private val poll = object : Runnable {
        override fun run() {
            val view = webView ?: return
            if (released || !resumed || !pageReady || error != null || !PlaybackPolicy.allows(view.url)) return
            repairCollapsedLayout(view)
            val epoch = sampleEpoch
            view.evaluateJavascript(positionScript) { result ->
                // A callback from an earlier document or lifecycle session must never
                // overwrite the synchronously flushed position after pause/navigation.
                if (!released && resumed && pageReady && sampleEpoch == epoch &&
                    webView === view && error == null && PlaybackPolicy.allows(view.url)
                ) {
                    PlaybackPolicy.positionFromJavascript(result)?.let { position ->
                        lastPositionMs = position
                        onPosition(position)
                    }
                }
            }
            handler.postDelayed(this, 2000L)
        }
    }

    // Page-only adapter: no native JavaScript interface or media URL access.
    private val pageBridgeScript = context.assets.open("player_bridge.js").bufferedReader().use { it.readText() }
    private val positionScript = pageBridgeScript + "\nwindow.__coursePlaybackBridge && window.__coursePlaybackBridge.position($restoreSeconds);"

    @SuppressLint("SetJavaScriptEnabled")
    fun createView(): WebView = WebView(context).also { view ->
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView = view
        // WebView uses LayoutParams when resolving CSS viewport/percentage heights.
        // Its default wrap-content mode can collapse the provider's 100% root height.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.setBackgroundColor(Color.BLACK)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        view.webChromeClient = fullscreen
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val allowed = PlaybackPolicy.allows(request.url.toString())
                if (!allowed) fail("该链接不在课程播放范围内，请返回课程详情。")
                return !allowed
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (released || error != null) return
                if (!PlaybackPolicy.allows(url)) {
                    fail("课程播放链接无效，请联系课程提供方更新。")
                    return
                }
                sampleEpoch++
                pageReady = false
                loading = true
                handler.removeCallbacks(poll)
                handler.removeCallbacks(timeout)
                if (resumed) handler.postDelayed(timeout, 30000L)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (released || error != null || !PlaybackPolicy.allows(url)) return
                handler.removeCallbacks(timeout)
                loading = false
                pageReady = true
                view.evaluateJavascript(pageBridgeScript, null)
                repairCollapsedLayout(view)
                startPolling()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, problem: WebResourceError) {
                if (request.isForMainFrame) fail("视频页面加载失败，请检查网络后重新加载。")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    fail(if (response.statusCode == 401 || response.statusCode == 403) {
                        "课程暂时无法访问，链接可能已过期或需要授权，请联系课程提供方。"
                    } else "课程服务暂时不可用，请稍后重新加载。")
                }
            }

            override fun onReceivedSslError(view: WebView, sslHandler: SslErrorHandler, problem: SslError) {
                sslHandler.cancel()
                fail("无法安全连接课程服务，请检查手机日期和网络后重试。")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (webView === view) {
                    webView = null
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    fail("播放器已停止，请重新加载课程。")
                }
                return true
            }
        }
        view.setDownloadListener { _, _, _, _, _ -> fail("此课程链接暂时不能在线播放，请联系课程提供方。") }
        if (PlaybackPolicy.allows(playerUrl)) {
            if (resumed) handler.postDelayed(timeout, 30000L)
            view.loadUrl(playerUrl.trim())
        } else fail("课程播放链接无效，请联系课程提供方更新。")
    }

    private fun repairCollapsedLayout(view: WebView) {
        if (released || error != null || !PlaybackPolicy.allows(view.url)) return
        // The official canvas player was observed decoding into a 1080 x 0 canvas when
        // html/body/#app percentage heights collapsed. Only that layout gets adapted;
        // player APIs, authorization, media sources and normal page layouts stay intact.
        view.evaluateJavascript("""
            (function() {
                if (!document.querySelector('#app .bplayer-wrap')) return;
                function resizeRoot() {
                    var root = document.documentElement;
                    var style = document.getElementById('course-webview-layout');
                    if (!style && root.getBoundingClientRect().height > 0) return;
                    var height = Math.round(window.innerHeight);
                    if (height <= 0) return;
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'course-webview-layout';
                        document.head.appendChild(style);
                    }
                    var css = 'html { height: ' + height + 'px !important; }';
                    if (style.textContent === css) return;
                    style.textContent = css;
                    requestAnimationFrame(function() { window.dispatchEvent(new Event('resize')); });
                }
                if (!window.__courseLayoutResizeInstalled) {
                    window.__courseLayoutResizeInstalled = true;
                    window.addEventListener('resize', resizeRoot);
                }
                resizeRoot();
            })();
        """.trimIndent(), null)
    }

    private fun startPolling() {
        handler.removeCallbacks(poll)
        if (resumed && pageReady && error == null && !released) handler.post(poll)
    }

    private fun fail(message: String) {
        if (released || error != null) return
        sampleEpoch++
        error = message
        loading = false
        pageReady = false
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(poll)
        fullscreen.hide()
        webView?.stopLoading()
        pauseVideo()
        webView?.onPause()
        flushPosition()
    }

    private fun pauseVideo() {
        webView?.takeIf { PlaybackPolicy.allows(it.url) }?.evaluateJavascript(
            pageBridgeScript + "\nwindow.__coursePlaybackBridge && window.__coursePlaybackBridge.pause();", null,
        )
    }

    fun resume() {
        if (released) return
        resumed = true
        if (error == null) {
            webView?.onResume()
            if (loading) {
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, 30000L)
            }
        }
        startPolling()
    }

    fun pause() {
        if (released) return
        resumed = false
        sampleEpoch++
        handler.removeCallbacks(poll)
        handler.removeCallbacks(timeout)
        pauseVideo()
        webView?.onPause()
        flushPosition()
    }

    // Use the last confirmed sample; never await JavaScript while leaving the screen.
    // This deliberately keeps the documented two-second sampling precision.
    fun flushPosition() { lastPositionMs?.let(onPosition) }

    fun rotate() {
        if (fullscreen.showing) return
        activity?.requestedOrientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun exitFullscreen(): Boolean = fullscreen.showing.also { if (it) fullscreen.exitFromBack() }

    fun release() {
        if (released) return
        pause()
        released = true
        handler.removeCallbacksAndMessages(null)
        fullscreen.hide()
        webView?.let { view ->
            view.stopLoading()
            (view.parent as? ViewGroup)?.removeView(view)
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
    }
}

@Suppress("DEPRECATION")
private class PlaybackChromeClient(
    private val activity: Activity?,
    private val exitDomFullscreen: () -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var callback: CustomViewCallback? = null
    private var container: FrameLayout? = null
    private var orientation: Int? = null
    private var systemUi: Int? = null
    private var hadFullscreenFlag = false
    private var backCallback: OnBackPressedCallback? = null
    val showing: Boolean get() = customView != null

    override fun onShowCustomView(view: View, customViewCallback: CustomViewCallback) {
        val currentActivity = activity
        val decor = currentActivity?.window?.decorView as? ViewGroup
        if (showing || currentActivity == null || decor == null) {
            customViewCallback.onCustomViewHidden()
            return
        }
        hadFullscreenFlag = currentActivity.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        currentActivity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        orientation = currentActivity.requestedOrientation
        systemUi = decor.systemUiVisibility
        customView = view
        callback = customViewCallback
        container = object : FrameLayout(currentActivity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && showing) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) exitFromBack()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            decor.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            requestFocus()
        }
        val dispatcher = (currentActivity as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showing) exitFromBack()
                else {
                    remove()
                    dispatcher?.onBackPressed()
                }
            }
        }.also { dispatcher?.addCallback(it) }
        decor.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() = hide()

    fun exitFromBack() {
        if (!showing) return
        exitDomFullscreen()
        hide()
    }

    fun hide() {
        backCallback?.remove()
        backCallback = null
        if (!showing) return
        val hiddenCallback = callback
        callback = null
        customView = null
        container?.let {
            it.removeAllViews()
            (it.parent as? ViewGroup)?.removeView(it)
        }
        container = null
        if (!hadFullscreenFlag) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        systemUi?.let { activity?.window?.decorView?.systemUiVisibility = it }
        orientation?.let { activity?.requestedOrientation = it }
        hiddenCallback?.onCustomViewHidden()
    }
}
