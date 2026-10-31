package com.akay.blocker

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

private fun MainActivity.infoBtn(title: String, text: String): View {
    val f = FrameLayout(this)
    f.addView(Glyph(this, G.INFO, C.sub), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
    f.setOnClickListener { info(title, text) }
    return f
}

private fun MainActivity.stateOf(key: String): Boolean {
    if (key == "uninstall") {
        val on = Store.on(key)
        if (on && !Perm.adminActive(this)) {
            Store.set(key, false)
            return false
        }
        return on
    }
    return Store.on(key)
}

private fun MainActivity.toggleRow(icon: Int, title: String, key: String, info: String): View {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(12), dp(10), dp(4), dp(10))
    r.addView(Glyph(this, icon, C.icon), lp(dp(30), dp(30)))
    val t = tv(title, 17f, C.text)
    t.setPadding(dp(14), 0, dp(6), 0)
    r.addView(t, lp(0, WRAP, 1f))
    val sw = Sw(this)
    sw.on = stateOf(key)
    r.addView(sw, lp(dp(46), dp(28)))
    r.addView(infoBtn(title, info), lp(dp(44), dp(40)))
    val click = View.OnClickListener { toggleKey(key, sw) }
    r.setOnClickListener(click)
    sw.setOnClickListener(click)
    return r
}

private fun MainActivity.editRow(icon: Int, title: String, sub: String, info: String, onEdit: () -> Unit): View {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(12), dp(10), dp(4), dp(10))
    r.addView(Glyph(this, icon, C.icon), lp(dp(30), dp(30)))
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(14), 0, dp(6), 0)
    col.addView(tv(title, 17f, C.text))
    val s = tv(sub, 14f, C.sub)
    s.maxLines = 2
    col.addView(s)
    r.addView(col, lp(0, WRAP, 1f))
    val pen = FrameLayout(this)
    pen.addView(Glyph(this, G.PENCIL, C.icon), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
    pen.setOnClickListener { onEdit() }
    r.addView(pen, lp(dp(44), dp(40)))
    r.addView(infoBtn(title, info), lp(dp(44), dp(40)))
    r.setOnClickListener { onEdit() }
    return r
}

private fun MainActivity.section(headIcon: Int, title: String, rows: List<View>): View {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL

    val ribbon = LinearLayout(this)
    ribbon.orientation = LinearLayout.HORIZONTAL
    ribbon.gravity = Gravity.CENTER_VERTICAL
    ribbon.background = RibbonDrawable(dp(16).toFloat(), dp(12).toFloat())
    ribbon.setPadding(dp(14), dp(7), dp(30), dp(7))
    ribbon.addView(Glyph(this, headIcon, Color.WHITE), lp(dp(22), dp(22)))
    val t = tv(title, 18f, Color.WHITE, 1)
    t.setPadding(dp(8), 0, 0, 0)
    ribbon.addView(t)
    box.addView(ribbon, lp(WRAP, WRAP))

    val card = LinearLayout(this)
    card.orientation = LinearLayout.VERTICAL
    card.background = rr(C.card, 18, C.edge, 2)
    for (i in rows.indices) {
        if (i > 0) {
            val dv = View(this)
            dv.setBackgroundColor(C.divider)
            card.addView(dv, lpm(MATCH, 1, 56, 0, 14, 0))
        }
        card.addView(rows[i], lp(MATCH, WRAP))
    }
    box.addView(card, lpm(MATCH, WRAP, 0, -1, 0, 0))
    return box
}

private fun MainActivity.permRow(name: String, go: () -> Unit): View {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.addView(tv(name, 15f, C.text), lp(0, WRAP, 1f))
    val b = tv("ALLOW", 13f, Color.WHITE, 2)
    b.setPadding(dp(16), dp(8), dp(16), dp(8))
    b.background = rr(C.coral, 16)
    b.setOnClickListener { go() }
    r.addView(b)
    return r
}

private fun MainActivity.permCard(): View {
    val card = LinearLayout(this)
    card.orientation = LinearLayout.VERTICAL
    card.background = rr(C.card, 18, C.coral, 2)
    card.setPadding(dp(16), dp(14), dp(16), dp(14))
    card.addView(tv("Permissions required", 18f, C.coral, 2))
    card.addView(tv("Blocking works only after these are allowed.", 13f, C.sub), lpm(WRAP, WRAP, 0, 2, 0, 8))
    if (!Perm.accEnabled(this)) {
        card.addView(permRow("Accessibility service") { showDisclosure() }, lpm(MATCH, WRAP, 0, 6, 0, 0))
    }
    if (!Perm.overlayOk(this)) {
        card.addView(permRow("Display over other apps") { Perm.openOverlay(this) }, lpm(MATCH, WRAP, 0, 10, 0, 0))
    }
    if (!Perm.batteryOk(this)) {
        card.addView(permRow("Battery: no restrictions") { Perm.openBattery(this) }, lpm(MATCH, WRAP, 0, 10, 0, 0))
    }
    return card
}

private fun MainActivity.editDialog(title: String, cur: String, type: Int, onSave: (String) -> Unit) {
    val e = EditText(this)
    e.inputType = type
    e.setText(cur)
    e.setSelection(e.text.length)
    val box = FrameLayout(this)
    box.setPadding(dp(20), dp(8), dp(20), 0)
    box.addView(e)
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton("Save") { _, _ -> onSave(e.text.toString().trim()) }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.buildBlocking(): View {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.setPadding(dp(14), dp(12), dp(14), dp(28))

    if (!Perm.accEnabled(this) || !Perm.overlayOk(this) || !Perm.batteryOk(this)) {
        c.addView(permCard(), lpm(MATCH, WRAP, 0, 0, 0, 14))
    }

    c.addView(
        section(
            G.GLOBE, "Content Blocking",
            listOf(
                toggleRow(G.ADULT, "Block adult content", "adult", "Blocks adult websites, keywords and searches in browsers and other apps."),
                toggleRow(G.SEARCH, "Block image & video search", "imgvid", "Blocks image and video search pages (Google, Bing and others) in browsers."),
                toggleRow(G.YT, "Block YouTube Shorts", "ytshorts", "Closes YouTube Shorts when they open. Normal videos still work."),
                toggleRow(G.IG, "Block Instagram Reels", "igreels", "Closes Instagram Reels when they open. Chats and the feed still work."),
                toggleRow(G.SNAP, "Block Snapchat Snaps & Stories", "snap", "Closes Snapchat Spotlight, Stories and Discover screens."),
                toggleRow(G.WA, "Block WhatsApp channels", "wachan", "Closes WhatsApp Channels screens. Chats and status still work.")
            )
        ),
        lpm(MATCH, WRAP, 0, 0, 0, 14)
    )

    c.addView(
        section(
            G.SHIELD, "Advanced Blocking",
            listOf(
                toggleRow(G.TRASH, "Uninstall protection", "uninstall", "Stops this app from being uninstalled and its settings from being changed. Turn it off here first if you want to uninstall."),
                toggleRow(G.GLOBE, "Block unsupported browsers", "browsers", "Blocks browsers this app cannot filter. Only Chrome, Brave, Firefox, Ulaa, Edge, Opera and similar browsers are allowed."),
                toggleRow(G.DLBOX, "Block newly installed apps", "newapps", "Every app you install from now on is blocked automatically.")
            )
        ),
        lpm(MATCH, WRAP, 0, 0, 0, 14)
    )

    c.addView(financeSection(), lpm(MATCH, WRAP, 0, 0, 0, 14))

    val redirect = Store.redirect()
    c.addView(
        section(
            G.MONITOR, "Customize Blocked Screen",
            listOf(
                editRow(G.CHAT, "Blocked screen message", Store.message(), "The text shown on the blocked screen.") {
                    editDialog("Blocked screen message", Store.message(), InputType.TYPE_CLASS_TEXT) { v ->
                        Store.setMessage(v)
                        showTab(tab)
                    }
                },
                editRow(G.TIMER, "Blocked screen countdown", Store.countdown().toString() + " seconds", "How long the blocked screen stays on the screen (1 to 60 seconds).") {
                    editDialog("Countdown (seconds)", Store.countdown().toString(), InputType.TYPE_CLASS_NUMBER) { v ->
                        val n = v.toIntOrNull()
                        if (n != null) Store.setCountdown(n)
                        showTab(tab)
                    }
                },
                editRow(G.LINK, "Redirect after closing (any URL)", if (redirect.isBlank()) "Not set" else redirect, "A web address that opens after the blocked screen closes. Leave empty for none.") {
                    editDialog("Redirect URL", Store.redirect(), InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT) { v ->
                        Store.setRedirect(v)
                        showTab(tab)
                    }
                }
            )
        ),
        lpm(MATCH, WRAP, 0, 0, 0, 0)
    )
    return c
}

private val FIN_NAMES = mapOf(
    "com.snapwork.IDBI" to "IDBI Bank GO Mobile+",
    "in.org.npci.upiapp" to "BHIM UPI",
    "com.sbi.lotusintouch" to "SBI YONO",
    "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.phonepe.app" to "PhonePe",
    "net.one97.paytm" to "Paytm",
    "com.snapwork.hdfc" to "HDFC Bank",
    "com.csam.icici.bank.imobile" to "ICICI iMobile Pay"
)

private fun MainActivity.finAppRow(pk: String): View {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(12), dp(8), dp(4), dp(8))

    var name = FIN_NAMES[pk] ?: pk
    val iv = android.widget.ImageView(this)
    var haveIcon = false
    try {
        iv.setImageDrawable(packageManager.getApplicationIcon(pk))
        name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pk, 0)).toString()
        haveIcon = true
    } catch (e: Throwable) {
    }
    if (haveIcon) {
        r.addView(iv, lp(dp(30), dp(30)))
    } else {
        r.addView(Glyph(this, G.SHIELD, C.icon), lp(dp(30), dp(30)))
    }

    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(14), 0, dp(6), 0)
    col.addView(tv(name, 16f, C.text))
    val sub = tv(pk, 11f, C.sub)
    sub.maxLines = 1
    col.addView(sub)
    r.addView(col, lp(0, WRAP, 1f))

    val del = FrameLayout(this)
    del.addView(Glyph(this, G.TRASH, C.icon), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    del.setOnClickListener {
        gate {
            Store.removeFin(pk)
            BlockService.refresh()
            showTab(tab)
        }
    }
    r.addView(del, lp(dp(44), dp(40)))
    return r
}

private fun MainActivity.pickFinApp() {
    val pm = packageManager
    val i = android.content.Intent(android.content.Intent.ACTION_MAIN)
    i.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    val have = Store.finApps()
    val list = ArrayList<Pair<String, String>>()
    val seen = HashSet<String>()
    for (ri in pm.queryIntentActivities(i, 0)) {
        val pk = ri.activityInfo.packageName
        if (pk == packageName || have.contains(pk) || !seen.add(pk)) continue
        list.add(Pair(pk, ri.loadLabel(pm).toString()))
    }
    list.sortBy { it.second.lowercase() }
    if (list.isEmpty()) {
        toast("No apps found")
        return
    }
    val names = Array(list.size) { list[it].second }
    AlertDialog.Builder(this)
        .setTitle("Add finance app")
        .setItems(names) { _, w ->
            Store.addFin(list[w].first)
            BlockService.refresh()
            toast(list[w].second + " added")
            showTab(tab)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.finAddRow(): View {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(12), dp(12), dp(12), dp(12))
    val plus = tv("+", 22f, Color.WHITE, 2)
    plus.gravity = Gravity.CENTER
    plus.background = rr(C.coral, 15)
    r.addView(plus, lp(dp(30), dp(30)))
    val t = tv("Add finance app", 16f, C.coral, 1)
    t.setPadding(dp(14), 0, 0, 0)
    r.addView(t)
    r.setOnClickListener { pickFinApp() }
    return r
}

private fun MainActivity.finInfoRow(): View {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(dp(12), dp(10), dp(12), dp(6))
    col.addView(
        tv(
            "These apps are never scanned, whatever your other settings are. Their screens, text, buttons and layout are never read by this service.",
            13f, C.sub
        )
    )
    return col
}

internal fun MainActivity.financeSection(): View {
    val rows = ArrayList<View>()
    rows.add(finInfoRow())
    for (pk in Store.finApps().sortedBy { (FIN_NAMES[it] ?: it).lowercase() }) rows.add(finAppRow(pk))
    rows.add(finAddRow())
    return section(G.SHIELD, "Finance Apps (Never Scanned)", rows)
}
