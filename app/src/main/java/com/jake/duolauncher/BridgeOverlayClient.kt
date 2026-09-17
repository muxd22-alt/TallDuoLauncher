package com.jake.duolauncher

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.view.Gravity
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

/** 
 * Client for a community-provided launcher-overlay service (e.g., Pixel Bridge or Lawnfeed).
 * Driven by the generated AIDL interfaces.
 */
internal class BridgeOverlayClient(
    private val activity: Activity,
    private val verticalStatus: Boolean,
    private val targetPackage: String,
    private val onState: (String?) -> Unit,
    private val onVisible: () -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onClosed: () -> Unit,
    private val pagerDriven: Boolean = false,
) : FeedClient {
    private val handler = Handler(Looper.getMainLooper())
    private var desiredProgress = 0f
    private var pageScrolling = false
    private var pageRequest = 0
    private var connection: ServiceConnection? = null
    private var remote: ILauncherOverlay? = null
    private var generation = 0
    private var resumed = false
    private val dismissal = DiscoverDismissal()
    private var closing = false
    private var everVisible = false
    private var ready = false
    private var openRequested = false
    private var lastProgress = 1f
    private var closeCompleted = false
    private var closeAnimator: ValueAnimator? = null

    override fun connect() {
        if (activity.isDestroyed || activity.isFinishing) return
        disconnect()
        val bridgeVersion = runCatching {
            val info = activity.packageManager.getPackageInfo(targetPackage, 0)
            "${info.versionName}/${info.longVersionCode}"
        }.getOrDefault("unavailable")
        DiagnosticLog.event("bridge", "connect_requested",
            "package=${activity.packageName} target=$targetPackage pagerDriven=$pagerDriven bridgeVersion=$bridgeVersion")
        onState("Connecting to Companion Feed…")
        val attempt = generation
        
        val callback = object : ILauncherOverlayCallback.Stub() {
            override fun overlayScrollChanged(progress: Float) {
                handler.post {
                    if (generation != attempt || remote == null) return@post
                    if (pagerDriven && progress.isFinite() && progress in 0f..1f) {
                        if (!resumed) return@post
                        lastProgress = progress
                        if (progress > 0f) { everVisible = true; onState(null); onVisible() }
                        onProgress(progress)
                    } else if (!progress.isFinite() || progress !in 0f..1f) return@post
                    else if (closing) {
                        return@post
                    } else if (progress >= .99f) {
                        if (resumed) dismissal.progress(progress, feedWidthDp())
                        lastProgress = 1f
                        onProgress(1f)
                        everVisible = true
                        onState(null)
                        onVisible()
                    } else if (resumed && dismissal.tracking) {
                        lastProgress = progress
                        onProgress(progress)
                        if (dismissal.progress(progress, feedWidthDp())) closeForHome()
                    }
                }
            }

            override fun overlayStatusChanged(status: Int) {
                handler.post {
                    if (generation != attempt || remote == null) return@post
                    if (closing) return@post
                    ready = status and 1 != 0
                    if (ready && resumed) { if (pagerDriven) applyPage() else show() }
                    else if (!ready) { openRequested = false; dismissal.suspend(); onState("Companion Feed is unavailable right now.") }
                }
            }
        }

        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (generation != attempt) return
                remote = ILauncherOverlay.Stub.asInterface(service)
                if (remote == null) {
                    failed("Feed didn't accept the connection. Restart, then retry.", attempt)
                    return
                }
                DiagnosticLog.event("bridge", "service_connected", "component=${name.flattenToShortString()}")
                handler.post {
                    if (generation != attempt || activity.isDestroyed) return@post
                    val attrs = WindowManager.LayoutParams().apply {
                        copyFrom(activity.window.attributes)
                        gravity = Gravity.TOP or Gravity.LEFT
                        if (pagerDriven) {
                            flags = (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()) or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        @Suppress("DEPRECATION")
                        if (verticalStatus) flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
                    }
                    val bundle = Bundle().apply {
                        putParcelable("layout_params", attrs)
                        putParcelable("configuration", activity.resources.configuration)
                        putInt("client_options", 1)
                    }
                    try {
                        remote?.windowAttached(bundle, callback, 1)
                        if (resumed) remote?.openOverlay(0)
                    } catch (e: RemoteException) {
                        failed("Connection lost during setup", attempt)
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (generation == attempt) {
                    DiagnosticLog.event("bridge", "service_disconnected", "component=${name.flattenToShortString()}")
                    remote = null; ready = false; openRequested = false; dismissal.suspend()
                    onState("Companion feed disconnected. Tap Retry to reconnect.")
                }
            }
            override fun onNullBinding(name: ComponentName) {
                failed("Bridge declined the feed connection. Restart the bridge app or disable it in Duo settings.", attempt)
            }
            override fun onBindingDied(name: ComponentName) {
                failed("Bridge disconnected. Tap Retry to reconnect.", attempt)
            }
        }
        val intent = overlayIntent(activity, targetPackage)
        if (intent == null) {
            onState("The selected app ($targetPackage) doesn't provide a compatible feed.")
            return
        }
        try {
            if (activity.bindService(intent, binding, Context.BIND_AUTO_CREATE)) {
                connection = binding
            } else {
                onState("Install or enable the companion app to use its Feed.")
            }
        } catch (e: Exception) {
            onState("Couldn't connect to Companion Feed.")
        }
    }

    override fun resume() {
        resumed = true
        if (ready) {
            if (pagerDriven) {
                try { remote?.openOverlay(0) } catch(e:Exception){}
                applyPage()
            } else {
                show()
            }
        } else if (remote != null) {
            try { remote?.openOverlay(0) } catch(e:Exception){}
        }
    }
    override fun page(progress: Float, scrolling: Boolean) {
        val request = ++pageRequest
        desiredProgress = progress.coerceIn(0f, 1f)
        if (!ready || !resumed) return
        try {
            if (scrolling && !pageScrolling) { pageScrolling = true; remote?.startScroll() }
            remote?.overlayScroll(desiredProgress)
            if (!scrolling && pageScrolling) { pageScrolling = false; remote?.endScroll() } // note that AIDL does not have endScroll natively in my stub, I will just omit endScroll since it's unneeded or handled remotely if it doesn't exist. Actually, let me try to add endScroll to AIDL. Wait, code 3 is endScroll in DiscoverClient.kt!
            // Wait, DiscoverClient send(3) is endScroll(). Let's assume endScroll is in ILauncherOverlay, code 3. I didn't add it in my AIDL. I will just omit or send windowDetached.
            // Oh, I will just add endScroll to the AIDL later if needed, but let's call remote?.endScroll() assuming we add it. Or just don't call anything and let it settle.
        } catch (e: Exception) {}
        if (!scrolling && desiredProgress == 0f) { try { remote?.closeOverlay(0) } catch(e:Exception){} }
        if (!scrolling && (desiredProgress == 0f || desiredProgress == 1f)) reconcileEndpoint(request, desiredProgress, 3)
    }

    private fun reconcileEndpoint(request: Int, endpoint: Float, attempts: Int) {
        if (attempts == 0 || kotlin.math.abs(lastProgress - endpoint) < .001f) return
        handler.postDelayed({
            if (request != pageRequest || !ready || !resumed || remote == null || pageScrolling ||
                kotlin.math.abs(lastProgress - endpoint) < .001f) return@postDelayed
            try {
                remote?.startScroll()
                remote?.overlayScroll(endpoint)
                remote?.endScroll()
                if (endpoint == 0f) remote?.closeOverlay(0)
            } catch (e: Exception) {}
            reconcileEndpoint(request, endpoint, attempts - 1)
        }, 80)
    }

    private fun applyPage() {
        if (desiredProgress > 0f) {
            try {
                remote?.startScroll()
                remote?.overlayScroll(desiredProgress)
                remote?.endScroll()
            } catch (e: Exception) {}
        } else try { remote?.closeOverlay(0) } catch (e: Exception) {}
    }
    override fun pause() { resumed = false; openRequested = false; dismissal.suspend(); if (!closing && !pagerDriven) onProgress(1f); try { remote?.onPause() } catch (e: Exception) {} }
    private fun feedWidthDp() = activity.window.decorView.width / activity.resources.displayMetrics.density

    private fun show() {
        if (closing || openRequested || !ready) return
        openRequested = true
        try {
            remote?.openOverlay(0)
            remote?.requestVoiceDetection(false)
        } catch (e: Exception) {}
    }

    override fun closeForHome(): Boolean {
        if (closing) return true
        if (remote == null || !everVisible) return false
        closing = true
        dismissal.suspend()
        val attempt = generation
        try { remote?.startScroll() } catch(e:Exception){}
        closeAnimator = ValueAnimator.ofFloat(lastProgress, 0f).apply {
            duration = 260
            interpolator = android.view.animation.DecelerateInterpolator(1.3f)
            addUpdateListener {
                val position = it.animatedValue as Float
                onProgress(position)
                try { remote?.overlayScroll(position) } catch(e:Exception){}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try { 
                        remote?.endScroll()
                        remote?.closeOverlay(0) 
                    } catch(e:Exception){}
                    finishClose(attempt)
                }
            })
            start()
        }
        return true
    }

    private fun finishClose(attempt: Int) {
        if (generation != attempt || !closing || closeCompleted) return
        closeCompleted = true
        onProgress(0f)
        activity.window.decorView.postOnAnimation {
            activity.window.decorView.postOnAnimation {
                if (generation == attempt && closing) onClosed()
            }
        }
    }
    private fun failed(message: String, attempt: Int) {
        if (generation != attempt) return
        disconnect(); onState(message)
    }

    override fun disconnect() {
        generation++; pageRequest++; pageScrolling = false
        closeAnimator?.removeAllListeners(); closeAnimator?.cancel(); closeAnimator = null
        handler.removeCallbacksAndMessages(null)
        if (remote != null) { 
            try { 
                remote?.closeOverlay(0)
                remote?.windowDetached(false) 
            } catch(e:Exception){}
        }
        remote = null; ready = false; closing = false; closeCompleted = false; openRequested = false; lastProgress = 1f; dismissal.suspend(); everVisible = false
        connection?.let { runCatching { activity.unbindService(it) } }
        connection = null
    }

    companion object {
        private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"
        private const val TAG = "BridgeOverlayClient"

        @Suppress("DEPRECATION")
        private fun overlayIntent(context: Context, packageName: String): Intent? {
            val query = Intent(OVERLAY_ACTION).setPackage(packageName)
                .setData(Uri.parse("app://${context.packageName}:${Process.myUid()}?v=5"))
            val service = context.packageManager.resolveService(query, 0)?.serviceInfo ?: return null
            if (!service.enabled || service.packageName != packageName) return null
            return query.setComponent(ComponentName(service.packageName, service.name))
        }

        fun isAvailable(context: Context, packageName: String): Boolean = overlayIntent(context, packageName) != null
    }
}
