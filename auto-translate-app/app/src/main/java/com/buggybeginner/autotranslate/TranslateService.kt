package com.buggybeginner.autotranslate

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.math.abs

class TranslateService : AccessibilityService() {

    companion object {
        var instance: TranslateService? = null
    }

    private data class Item(val text: String, val rect: Rect)

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var overlayRoot: FrameLayout? = null
    private var bubble: View? = null
    private var panel: View? = null

    private var items: List<Item> = emptyList()
    private val cache = HashMap<String, String>()
    private val pending = HashSet<String>()
    private val translators = HashMap<String, Translator>()
    private val langId by lazy { LanguageIdentification.getClient() }

    private var targetNode: AccessibilityNodeInfo? = null

    private val chatApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram",
        "com.instagram.android", "com.facebook.orca", "com.facebook.katana", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.snapchat.android"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        addOverlayRoot()
        addBubble()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        removeViewSafe(panel)
        removeViewSafe(bubble)
        removeViewSafe(overlayRoot)
        translators.values.forEach { it.close() }
        translators.clear()
        super.onDestroy()
    }

    private fun removeViewSafe(v: View?) {
        try { if (v != null) wm.removeView(v) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- auto translate

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() == packageName) return
        if (!prefs.getBoolean("auto", true)) {
            clearOverlay()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            clearOverlay()
        }
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 450)
    }

    private val scanRunnable = Runnable { scan() }
    private val renderRunnable = Runnable { render() }

    private fun allowed(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui") return false
        return if (prefs.getBoolean("allApps", false)) true else pkg in chatApps
    }

    private fun scan() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""
        if (root == null || !allowed(pkg)) {
            clearOverlay()
            return
        }
        val list = ArrayList<Item>()
        collect(root, list, 0)
        items = list
        render()

        val target = prefs.getString("screenTarget", "hi") ?: "hi"
        for (it in list) requestTranslation(it.text, target)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Item>, depth: Int) {
        if (n == null || depth > 40 || out.size >= 80) return
        if (n.isVisibleToUser) {
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length >= 3 && !n.isEditable && n.childCount == 0 && t.any { it.isLetter() }) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() > dp(20) && r.height() > dp(10)) out.add(Item(t, r))
            }
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun requestTranslation(text: String, target: String) {
        val key = "$target|$text"
        if (cache.containsKey(key) || !pending.add(key)) return
        langId.identifyLanguage(text)
            .addOnSuccessListener { code ->
                val src = TranslateLanguage.fromLanguageTag(code)
                if (code == "und" || code == target || src == null) {
                    finishTranslation(key, "")
                } else {
                    translateWith(src, target, text) { res -> finishTranslation(key, res ?: "") }
                }
            }
            .addOnFailureListener { finishTranslation(key, "") }
    }

    private fun finishTranslation(key: String, result: String) {
        if (cache.size > 500) cache.clear()
        cache[key] = result
        pending.remove(key)
        handler.removeCallbacks(renderRunnable)
        handler.postDelayed(renderRunnable, 150)
    }

    private fun clearOverlay() {
        items = emptyList()
        overlayRoot?.removeAllViews()
    }

    private fun render() {
        val rootView = overlayRoot ?: return
        rootView.removeAllViews()
        val target = prefs.getString("screenTarget", "hi") ?: "hi"
        for (item in items) {
            val tr = cache["$target|${item.text}"] ?: continue
            if (tr.isEmpty() || tr == item.text) continue
            val tv = TextView(this).apply {
                text = tr
                textSize = 14f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F21F2937"))
                    cornerRadius = dp(6).toFloat()
                }
                setPadding(dp(6), dp(3), dp(6), dp(3))
                minHeight = item.rect.height()
            }
            val lp = FrameLayout.LayoutParams(item.rect.width(), FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = item.rect.left
            lp.topMargin = item.rect.top
            rootView.addView(tv, lp)
        }
    }

    private fun addOverlayRoot() {
        val v = FrameLayout(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        wm.addView(v, p)
        overlayRoot = v
    }

    // ---------------------------------------------------------------- translation helpers

    private fun translateWith(src: String, tgt: String, text: String, cb: (String?) -> Unit) {
        val s = TranslateLanguage.fromLanguageTag(src)
        val t = TranslateLanguage.fromLanguageTag(tgt)
        if (s == null || t == null) {
            cb(null)
            return
        }
        val tr = translators.getOrPut("$s>$t") {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build())
        }
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                tr.translate(text)
                    .addOnSuccessListener { cb(it) }
                    .addOnFailureListener { cb(null) }
            }
            .addOnFailureListener { cb(null) }
    }

    /** Splits long text into small pieces so any length can be translated. Keeps line breaks. */
    private fun splitText(text: String): List<String> {
        val out = ArrayList<String>()
        for (seg in text.split(Regex("(?<=\n)"))) {
            if (seg.length <= 450) {
                out.add(seg)
                continue
            }
            val sentences = seg.split(Regex("(?<=[.!?।])\\s+"))
            val sb = StringBuilder()
            for (s in sentences) {
                if (sb.isNotEmpty() && sb.length + s.length > 450) {
                    out.add(sb.toString().trimEnd() + " ")
                    sb.clear()
                }
                sb.append(s).append(' ')
            }
            if (sb.isNotEmpty()) out.add(sb.toString().trimEnd() + (if (seg.endsWith("\n")) "\n" else ""))
        }
        return out
    }

    private fun translateLong(src: String, tgt: String, text: String, cb: (String?) -> Unit) {
        val parts = splitText(text)
        val out = StringBuilder()
        fun step(i: Int) {
            if (i >= parts.size) {
                cb(out.toString())
                return
            }
            val p = parts[i]
            val core = p.trimEnd()
            val tail = p.substring(core.length)
            if (core.isEmpty()) {
                out.append(p)
                step(i + 1)
                return
            }
            translateWith(src, tgt, core) { r ->
                if (r == null) {
                    cb(null)
                } else {
                    out.append(r).append(tail)
                    step(i + 1)
                }
            }
        }
        step(0)
    }

    // ---------------------------------------------------------------- floating bubble

    private fun addBubble() {
        val tv = TextView(this).apply {
            text = "A文"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E62563EB"))
            }
        }
        val size = dp(48)
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = resources.displayMetrics.widthPixels - size - dp(4)
        p.y = resources.displayMetrics.heightPixels / 3

        tv.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = p.x; startY = p.y
                        downX = e.rawX; downY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            p.x = startX + dx
                            p.y = startY + dy
                            try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) togglePanel()
                }
                return true
            }
        })
        wm.addView(tv, p)
        bubble = tv
    }

    // ---------------------------------------------------------------- typing panel

    private fun togglePanel() {
        if (panel != null) closePanel() else openPanel()
    }

    private fun closePanel() {
        removeViewSafe(panel)
        panel = null
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    private fun openPanel() {
        // Remember which text box (WhatsApp, Gmail ...) the text should go into.
        val root = rootInActiveWindow
        targetNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)

        val typeSource = prefs.getString("typeSource", "hi") ?: "hi"
        val typeTarget = prefs.getString("typeTarget", "en") ?: "en"

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#D1D5DB"))
            }
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "$typeSource → $typeTarget"
            textSize = 14f
            setTextColor(Color.parseColor("#111827"))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            text = "X"
            setOnClickListener { closePanel() }
        })
        box.addView(header)

        val edit = EditText(this).apply {
            hint = "Yahan likho (jitna lamba chahiye)"
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
        }
        box.addView(edit)

        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#B45309"))
        }

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, body) in Templates.list) {
            chips.addView(Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener { edit.setText(body) }
            })
        }
        val chipScroll = HorizontalScrollView(this).apply {
            visibility = View.GONE
            addView(chips)
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Translate + Fill"
            isAllCaps = false
            setOnClickListener {
                val txt = edit.text.toString()
                if (txt.isBlank()) {
                    status.text = "Pehle kuch likho"
                    return@setOnClickListener
                }
                status.text = "Translate ho raha hai..."
                if (typeSource == typeTarget) {
                    fillTarget(txt, edit, status)
                } else {
                    translateLong(typeSource, typeTarget, txt) { res ->
                        if (res == null) {
                            status.text = "Translate nahi hua. Pehli baar internet chahiye (model download). App kholke 'Models download karo' dabao."
                        } else {
                            fillTarget(res, edit, status)
                        }
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "Templates"
            isAllCaps = false
            setOnClickListener {
                chipScroll.visibility = if (chipScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(buttons)
        box.addView(chipScroll)
        box.addView(status)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP
        p.y = dp(40)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        p.horizontalMargin = 0.02f

        wm.addView(box, p)
        panel = box
        edit.requestFocus()
    }

    /** Puts [text] into the remembered text box. The send button is left for the user. */
    private fun fillTarget(text: String, edit: EditText, status: TextView) {
        val node = targetNode
        if (node == null || !node.refresh()) {
            copyToClipboard(text)
            status.text = "Text box nahi mila, isliye text copy ho gaya hai. Text box mein paste kar do."
            return
        }
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            edit.setText("")
            closePanel()
        } else {
            copyToClipboard(text)
            status.text = "Is app mein direct fill nahi hua, text copy ho gaya hai. Text box mein paste kar do."
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translated", text))
    }
}
