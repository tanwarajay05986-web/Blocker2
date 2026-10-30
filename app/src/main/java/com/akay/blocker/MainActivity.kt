package com.akay.blocker

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    internal val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    internal val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    internal var tab = 0
    internal var screen = "disc"

    // 0 = nothing, 1 = waiting for Accessibility, 2 = waiting for Overlay, 3 = waiting for Battery
    private var flow = 0

    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Perm.adminActive(this)) Store.set("uninstall", true) else toast("Not allowed")
        if (screen == "main") showTab(tab)
    }

    // ---------- helpers ----------

    internal fun lp(w: Int, h: Int, wt: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, wt)

    // size values above 0 are dp, MATCH / WRAP are kept as they are
    internal fun lpm(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(if (w > 0) dp(w) else w, if (h > 0) dp(h) else h)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    internal fun rr(color: Int, rDp: Int, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(rDp).toFloat()
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke)
        return d
    }

    // style: 0 normal, 1 medium, 2 bold
    internal fun tv(text: String, sp: Float, color: Int, style: Int = 0): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        t.typeface = when (style) {
            2 -> Typeface.DEFAULT_BOLD
            1 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        return t
    }

    internal fun info(title: String, text: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show()
    }

    // asks for the special code before something is turned off or deleted
    internal fun gate(onOk: () -> Unit) {
        if (!Config.CODE_ENFORCED || Store.code().isEmpty()) {
            onOk()
            return
        }
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.hint = "Special code"
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(e)
        AlertDialog.Builder(this)
            .setTitle("Special code")
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                if (e.text.toString() == Store.code()) onOk() else toast("Wrong code")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    internal fun toggleKey(key: String, sw: Sw) {
        if (!sw.on) {
            if (key == "uninstall") {
                if (Perm.adminActive(this)) {
                    Store.set(key, true)
                    sw.on = true
                } else {
                    adminLauncher.launch(Perm.adminIntent(this))
                }
                return
            }
            Store.set(key, true)
            sw.on = true
            if (!Perm.accEnabled(this)) toast("Turn on the Accessibility service first")
        } else {
            gate {
                Store.set(key, false)
                sw.on = false
                if (key == "uninstall") Perm.removeAdmin(this)
            }
        }
    }

    // shows a screen. `bottom` is the colour of the strip behind the phone's navigation bar
    internal fun setScreen(content: View, bottom: Int) {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(C.bg)
        val tp = View(this)
        val bp = View(this)
        bp.setBackgroundColor(bottom)
        wrap.addView(tp, lp(MATCH, 0))
        wrap.addView(content, lp(MATCH, 0, 1f))
        wrap.addView(bp, lp(MATCH, 0))
        ViewCompat.setOnApplyWindowInsetsListener(wrap) { _, ins ->
            val sb = ins.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = ins.getInsets(WindowInsetsCompat.Type.ime())
            (tp.layoutParams as LinearLayout.LayoutParams).height = sb.top
            (bp.layoutParams as LinearLayout.LayoutParams).height = maxOf(sb.bottom, ime.bottom)
            tp.requestLayout()
            bp.requestLayout()
            WindowInsetsCompat.CONSUMED
        }
        setContentView(wrap)
        val c = WindowInsetsControllerCompat(window, wrap)
        c.isAppearanceLightStatusBars = true
        c.isAppearanceLightNavigationBars = true
        ViewCompat.requestApplyInsets(wrap)
    }

    // ---------- lifecycle ----------

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        } catch (e: Throwable) {
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == "main" && tab != 0) showTab(0) else finish()
            }
        })
        if (Perm.accEnabled(this)) showTab(0) else showDisclosure()
    }

    override fun onResume() {
        super.onResume()
        if (screen == "disc" && flow == 0 && Perm.accEnabled(this)) flow = 1
        if (flow != 0) {
            advance()
        } else if (screen == "main") {
            showTab(tab)
        }
    }

    // goes through the permissions one after another
    private fun advance() {
        when (flow) {
            1 -> if (Perm.accEnabled(this)) {
                flow = 2
                if (!Perm.overlayOk(this)) {
                    Perm.openOverlay(this)
                } else {
                    advance()
                }
            }
            2 -> {
                flow = 3
                if (!Perm.batteryOk(this)) {
                    Perm.openBattery(this)
                } else {
                    advance()
                }
            }
            3 -> {
                flow = 0
                showTab(0)
            }
        }
    }

    // ---------- accessibility screen ----------

    internal fun showDisclosure() {
        screen = "disc"
        var agreed = true

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(26), dp(20), dp(18))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        top.addView(Glyph(this, G.ACCESS, C.green), lp(dp(66), dp(66)))
        top.addView(tv("Accessibility Service", 28f, C.text), lpm(WRAP, WRAP, 0, 10, 0, 0))
        root.addView(top, lp(MATCH, WRAP))

        root.addView(tv("Usage", 24f, C.text, 1), lpm(WRAP, WRAP, 0, 24, 0, 0))
        root.addView(
            tv(
                "\u25CF Blocker leverages accessibility features to block adult content and other unwanted websites/apps on your device. It also allows you to customize your blocklist with specific sites and apps you choose to block.",
                16f, C.text
            ),
            lpm(MATCH, WRAP, 0, 14, 0, 0)
        )
        root.addView(
            tv(
                "\u25CF Rest assured, Blocker maintains your privacy: no data from your phone is collected or transmitted. All your information stays securely on your device, ensuring nothing is sent to our servers.",
                16f, C.text
            ),
            lpm(MATCH, WRAP, 0, 18, 0, 0)
        )
        root.addView(View(this), lp(MATCH, 0, 1f))

        // check box
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val box = FrameLayout(this)
        val tick = Glyph(this, G.CHECK, Color.WHITE)
        box.addView(tick, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        row.addView(box, lpm(30, 30, 8, 0, 0, 0))
        val rt = tv("By checking this box, I agree to allow Blocker to use the Accessibility service for effective functioning.", 16f, C.text)
        rt.setPadding(dp(20), 0, 0, 0)
        row.addView(rt, lp(0, WRAP, 1f))
        fun paint() {
            box.background = if (agreed) rr(C.coral, 6) else rr(Color.TRANSPARENT, 6, C.coral, 2)
            tick.visibility = if (agreed) View.VISIBLE else View.INVISIBLE
        }
        paint()
        row.setOnClickListener {
            agreed = !agreed
            paint()
        }
        root.addView(row, lpm(MATCH, WRAP, 0, 0, 0, 18))

        // buttons
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        val skip = tv("SKIP", 19f, C.coral, 1)
        skip.gravity = Gravity.CENTER
        skip.background = rr(Color.TRANSPARENT, 30, C.coral, 2)
        skip.setOnClickListener {
            flow = 0
            showTab(0)
        }
        val on = tv("TURN ON", 19f, Color.WHITE, 1)
        on.gravity = Gravity.CENTER
        on.background = rr(C.coral, 30)
        on.setOnClickListener {
            if (!agreed) {
                toast("Please tick the box first")
            } else {
                flow = 1
                toast("Find Blocker in the list and turn it on")
                Perm.openAccessibility(this)
            }
        }
        btns.addView(skip, lp(0, dp(58), 1f))
        val ol = lp(0, dp(58), 1.4f)
        ol.leftMargin = dp(40)
        btns.addView(on, ol)
        root.addView(btns, lp(MATCH, WRAP))

        setScreen(root, C.bg)
    }

    // ---------- tabs ----------

    private fun navBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(C.nav)
        bar.setPadding(dp(8), dp(10), dp(8), dp(10))
        val icons = intArrayOf(G.BLOCK, G.LIST, G.GEAR)
        val names = arrayOf("Blocking", "Blocklist", "Settings")
        for (i in 0..2) {
            val sel = i == tab
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            val pill = FrameLayout(this)
            if (sel) pill.background = rr(C.coral, 16)
            pill.addView(
                Glyph(this, icons[i], if (sel) Color.WHITE else C.icon),
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
            )
            item.addView(pill, lp(dp(62), dp(32)))
            val t = tv(names[i], 15f, if (sel) C.coral else C.text, 1)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(4), 0, 0)
            item.addView(t)
            item.setOnClickListener { if (tab != i) showTab(i) }
            bar.addView(item, lp(0, WRAP, 1f))
        }
        return bar
    }

    private fun placeholder(name: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(30), dp(80), dp(30), dp(80))
        c.addView(tv(name, 24f, C.text, 2))
        c.addView(tv("Coming in Stage 2", 16f, C.sub), lpm(WRAP, WRAP, 0, 8, 0, 0))
        return c
    }

    internal fun showTab(i: Int) {
        tab = i
        screen = "main"
        val content: View = when (i) {
            0 -> buildBlocking()
            1 -> placeholder("Blocklist")
            else -> placeholder("Settings")
        }
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.isFillViewport = true
        sv.addView(content)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(sv, lp(MATCH, 0, 1f))
        root.addView(navBar(), lp(MATCH, WRAP))
        setScreen(root, C.nav)
    }
}
