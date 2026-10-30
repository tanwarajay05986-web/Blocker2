package com.akay.blocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class BlockService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var lastBlock = 0L
    private var lastScan = 0L
    private var registered = false
    private var browserPkgs: Set<String> = Builtin.BROWSERS

    private val ytIds = arrayOf("reel_recycler", "reel_player_page_container", "reel_watch_fragment_root", "shorts_player")
    private val igIds = arrayOf("clips_viewer", "clips_video", "reel_viewer", "reels_viewer")
    private val waIds = arrayOf("newsletter")
    private val settingsWords = arrayOf(
        "settings", "packageinstaller", "permissioncontroller", "securitycenter", "safecenter",
        "coloros", "oplus", "systemmanager", "phonemanager"
    )
    private val protectWords = arrayOf(
        "uninstall", "force stop", "deactivate", "device admin", "clear data", "use blocker", "stop blocker"
    )

    private class Snap {
        val ids = ArrayList<String>()
        val texts = StringBuilder()
        val edits = StringBuilder()
        val selected = ArrayList<String>()
        var url = ""
        var count = 0
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Store.on("newapps")) return
            if (i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val pk = i.data?.schemeSpecificPart ?: return
            if (pk == packageName) return
            if (Store.finApps().contains(pk)) return
            Store.addBlockedApp(pk)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        findBrowsers()
        if (!registered) {
            try {
                val f = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
                f.addDataScheme("package")
                ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
                registered = true
            } catch (e: Throwable) {
            }
        }
    }

    private fun findBrowsers() {
        val set = HashSet<String>(Builtin.BROWSERS)
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            for (r in packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)) {
                set.add(r.activityInfo.packageName)
            }
        } catch (e: Throwable) {
        }
        set.remove(packageName)
        browserPkgs = set
    }

    private fun isUnsupportedBrowser(pkg: String): Boolean =
        browserPkgs.contains(pkg) && !Builtin.BROWSERS.contains(pkg)

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (Store.on("finance") && Store.finApps().contains(pkg)) return
        val type = e.eventType

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (e.isPassword) return
            val sb = StringBuilder()
            val list = e.text
            if (list != null) for (t in list) sb.append(t).append(' ')
            val s = sb.toString()
            if (s.length >= 3 && Matcher.hit(s)) block(true)
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (Store.blockedApps().contains(pkg)) {
                block(true)
                return
            }
            if (Store.on("browsers") && isUnsupportedBrowser(pkg)) {
                block(true)
                return
            }
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 350) return
        lastScan = now
        scan(pkg)
    }

    // ---------- reading the screen ----------

    private fun isUrlId(id: String): Boolean {
        val l = id.lowercase()
        return l.contains("url_bar") || l.contains("url_field") || l.contains("url_view") ||
            l.contains("urlbar") || l.contains("location_bar") || l.contains("omnibar") ||
            l.contains("toolbar_url") || l.contains("address")
    }

    private fun walk(n: AccessibilityNodeInfo?, s: Snap, depth: Int) {
        if (n == null || depth > 30 || s.count > 700) return
        s.count++
        val id = n.viewIdResourceName
        if (id != null) s.ids.add(id)
        val t = n.text?.toString()
        val d = n.contentDescription?.toString()
        val cls = n.className?.toString() ?: ""
        if (!t.isNullOrEmpty() && !n.isPassword) {
            if (id != null && isUrlId(id)) {
                if (s.url.isEmpty()) s.url = t
            } else if (cls.endsWith("EditText")) {
                s.edits.append(t).append(' ')
            } else if (s.texts.length < 8000) {
                s.texts.append(t).append(' ')
            }
        }
        if (n.isSelected) {
            if (!t.isNullOrEmpty()) s.selected.add(t.lowercase())
            if (!d.isNullOrEmpty()) s.selected.add(d.lowercase())
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), s, depth + 1)
    }

    private fun idHit(s: Snap, keys: Array<String>): Boolean {
        for (id in s.ids) for (k in keys) if (id.contains(k)) return true
        return false
    }

    private fun isSettingsPkg(p: String): Boolean {
        for (w in settingsWords) if (p.contains(w)) return true
        return false
    }

    private fun protectHit(s: Snap): Boolean {
        val t = s.texts.toString().lowercase()
        if (!Regex("\\bblocker\\b").containsMatchIn(t)) return false
        for (w in protectWords) if (t.contains(w)) return true
        return false
    }

    private fun scan(evPkg: String) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: evPkg
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (Store.on("finance") && Store.finApps().contains(pkg)) return
        if (Store.blockedApps().contains(pkg)) {
            block(true)
            return
        }
        val protect = Store.on("uninstall") && isSettingsPkg(pkg)
        val yt = pkg == "com.google.android.youtube" && Store.on("ytshorts")
        val ig = pkg == "com.instagram.android" && Store.on("igreels")
        val sn = pkg == "com.snapchat.android" && Store.on("snap")
        val wa = (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") && Store.on("wachan")
        val br = browserPkgs.contains(pkg)
        if (!(protect || yt || ig || sn || wa || br)) return

        val s = Snap()
        walk(root, s, 0)

        if (protect && protectHit(s)) {
            block(true)
            return
        }
        if (yt && idHit(s, ytIds)) {
            block(false)
            return
        }
        if (ig && (idHit(s, igIds) || s.selected.contains("reels"))) {
            block(false)
            return
        }
        if (sn && (s.selected.contains("spotlight") || s.selected.contains("stories") || s.selected.contains("discover"))) {
            block(false)
            return
        }
        if (wa) {
            val t = s.texts.toString().lowercase()
            if (idHit(s, waIds) || t.contains("find channels") || t.contains("channel directory")) {
                block(false)
                return
            }
        }
        if (br) {
            val head = s.url + " " + s.edits
            if (head.isNotBlank() && Matcher.hit(head)) {
                block(true)
                return
            }
            if (Store.on("imgvid")) {
                if (Matcher.mediaHit(head) || s.selected.contains("images") || s.selected.contains("videos")) {
                    block(true)
                    return
                }
            }
            if (!Matcher.isWhiteUrl(head) && Matcher.pageHit(s.texts.toString())) block(true)
        }
    }

    // ---------- blocking ----------

    private fun block(home: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlock < 1200) return
        lastBlock = now
        performGlobalAction(if (home) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        showOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return
        val w = wm ?: return

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setBackgroundColor(C.coral)
        box.setPadding(dp(32), dp(32), dp(32), dp(32))

        val msg = TextView(this)
        msg.text = Store.message()
        msg.setTextColor(0xFFFFFFFF.toInt())
        msg.textSize = 26f
        msg.typeface = Typeface.DEFAULT_BOLD
        msg.gravity = Gravity.CENTER
        box.addView(msg, LinearLayout.LayoutParams(-1, -2))

        val cd = TextView(this)
        cd.setTextColor(0xFFFFE3E3.toInt())
        cd.textSize = 18f
        cd.gravity = Gravity.CENTER
        val clp = LinearLayout.LayoutParams(-1, -2)
        clp.topMargin = dp(18)
        box.addView(cd, clp)

        val type = if (Settings.canDrawOverlays(this)) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        try {
            w.addView(box, lp)
        } catch (e: Throwable) {
            try {
                lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                w.addView(box, lp)
            } catch (e2: Throwable) {
                return
            }
        }
        overlay = box

        var left = Store.countdown()
        cd.text = "Closing in $left"
        val tick = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    hideOverlay()
                    openRedirect()
                } else {
                    cd.text = "Closing in $left"
                    main.postDelayed(this, 1000)
                }
            }
        }
        main.postDelayed(tick, 1000)
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            wm?.removeView(v)
        } catch (e: Throwable) {
        }
    }

    private fun openRedirect() {
        val u = Store.redirect().trim()
        if (u.isEmpty()) return
        val url = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Throwable) {
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        if (registered) {
            try {
                unregisterReceiver(pkgReceiver)
            } catch (e: Throwable) {
            }
            registered = false
        }
        super.onDestroy()
    }
}
