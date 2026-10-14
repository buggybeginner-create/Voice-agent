package com.buggybeginner.autotranslate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions

class MainActivity : Activity() {

    private lateinit var statusView: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        fun title(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, dp(18), 0, dp(6))
        }

        root.addView(TextView(this).apply {
            text = "Auto Translate"
            textSize = 26f
            setTextColor(Color.parseColor("#111827"))
        })

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, dp(6))
        }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "1. Accessibility ON karo"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            text = "Settings mein 'Auto Translate' dhoondo, ON karo aur Allow dabao. Android ye permission sirf aap hi de sakte ho."
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
        })

        root.addView(title("2. Screen ke messages"))
        root.addView(Switch(this).apply {
            text = "Auto-translate ON"
            isChecked = prefs.getBoolean("auto", true)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("auto", c).apply() }
        })
        root.addView(Switch(this).apply {
            text = "Saari apps mein (OFF = sirf WhatsApp, Telegram, Gmail, SMS jaisi chat apps)"
            isChecked = prefs.getBoolean("allApps", false)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("allApps", c).apply() }
        })

        fun spinner(label: String, key: String, def: String) {
            root.addView(title(label))
            val sp = Spinner(this)
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Langs.list.map { it.first })
            sp.setSelection(Langs.indexOf(prefs.getString(key, def) ?: def))
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString(key, Langs.list[pos].second).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            root.addView(sp, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        spinner("Screen ka text is language mein dikhao", "screenTarget", "hi")

        root.addView(title("3. Floating widget (typing)"))
        root.addView(TextView(this).apply {
            text = "Widget mein jis language mein likhoge, wo doosri language mein badal kar text box mein bhar jayega."
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
        })
        spinner("Main likhunga (From)", "typeSource", "hi")
        spinner("Text box mein jayega (To)", "typeTarget", "en")

        root.addView(title("4. Language models download karo"))
        root.addView(Button(this).apply {
            text = "Models download karo (WiFi/internet chahiye)"
            setOnClickListener { downloadModels(prefs.getString("screenTarget", "hi")!!, prefs.getString("typeSource", "hi")!!, prefs.getString("typeTarget", "en")!!) }
        })
        root.addView(TextView(this).apply {
            text = "Pehli baar ek language ka model (~30 MB) download hota hai. Uske baad internet ke bina chalta hai.\n\nNote: Hindi ko Devanagari mein likho ya bolo (voice typing). Roman Hindi (Hinglish) ko ye translate nahi kar paata."
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(8), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = TranslateService.instance != null
        statusView.text = if (on) "Status: Service chal rahi hai. Floating bubble screen par dikhega." else "Status: Service OFF hai. Neeche Accessibility ON karo."
        statusView.setTextColor(Color.parseColor(if (on) "#15803D" else "#B91C1C"))
    }

    private fun downloadModels(screenTarget: String, typeSource: String, typeTarget: String) {
        val pairs = linkedSetOf(
            "en" to screenTarget,
            screenTarget to "en",
            typeSource to typeTarget
        ).filter { it.first != it.second }
        Toast.makeText(this, "Download shuru hua, thoda wait karo", Toast.LENGTH_SHORT).show()
        var left = pairs.size
        var failed = false
        if (left == 0) return
        for ((s, t) in pairs) {
            val src = TranslateLanguage.fromLanguageTag(s)
            val tgt = TranslateLanguage.fromLanguageTag(t)
            if (src == null || tgt == null) { left--; continue }
            val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
            tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnFailureListener { failed = true }
                .addOnCompleteListener {
                    tr.close()
                    left--
                    if (left <= 0) {
                        Toast.makeText(this, if (failed) "Kuch models download nahi hue. Internet check karke dobara dabao." else "Sab models ready hain", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
