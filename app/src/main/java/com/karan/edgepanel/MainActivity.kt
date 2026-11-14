package com.karan.edgepanel

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var cpuText: TextView
    private lateinit var cpuBar: ProgressBar
    private lateinit var tempText: TextView
    private lateinit var battText: TextView
    private lateinit var pingText: TextView
    private lateinit var coolStatus: TextView
    private var lastAlert = 0L
    @Volatile private var pingValue = -2
    private var running = false

    private val BG = Color.parseColor("#0D0F1A")
    private val CARD = Color.parseColor("#1A1D2E")
    private val ACCENT = Color.parseColor("#00E5FF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); addView(root) }
        setContentView(scroll)

        root.addView(label("🎮 KARAN EDGE PANEL", 22f, ACCENT, true))
        root.addView(label("Gaming Dashboard", 13f, Color.GRAY, false))

        val ramCard = card(root)
        ramText = label("RAM", 15f, Color.WHITE, false)
        ramBar = bar()
        ramCard.addView(ramText); ramCard.addView(ramBar)

        val cpuCard = card(root)
        cpuText = label("CPU", 15f, Color.WHITE, false)
        cpuBar = bar()
        cpuCard.addView(cpuText); cpuCard.addView(cpuBar)

        val miscCard = card(root)
        tempText = label("🌡 तापमान", 15f, Color.WHITE, false)
        battText = label("🔋 बैटरी", 15f, Color.WHITE, false)
        pingText = label("📶 Ping", 15f, Color.WHITE, false)
        miscCard.addView(tempText); miscCard.addView(battText); miscCard.addView(pingText)

        root.addView(label("❄️ Phone Cool Down", 18f, ACCENT, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        val coolCard = card(root)
        coolStatus = label("तापमान जाँच रहा है...", 15f, Color.WHITE, false)
        coolCard.addView(coolStatus)
        val dim = Switch(this).apply {
            text = "इस ऐप की स्क्रीन डिम करो"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, on ->
                val lp = window.attributes
                lp.screenBrightness = if (on) 0.2f else -1f
                window.attributes = lp
            }
        }
        coolCard.addView(dim)
        coolCard.addView(actionButton("🔋 Battery Saver खोलो", Settings.ACTION_BATTERY_SAVER_SETTINGS))
        coolCard.addView(actionButton("☀️ Brightness / Refresh Rate कम करो", Settings.ACTION_DISPLAY_SETTINGS))
        coolCard.addView(actionButton("✈️ Airplane Mode (नेटवर्क बंद)", Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        coolCard.addView(label(
            "टिप्स:\n• चार्जिंग के दौरान गेम न खेलो\n• फोन का कवर हटा दो\n" +
            "• धूप/गर्म जगह से हटाओ\n• बैकग्राउंड ऐप बंद करो (Recent Apps से)\n" +
            "• गेम में Graphics/FPS कम करो", 13f, Color.LTGRAY, false))

        root.addView(label("🚀 Game Launcher", 18f, ACCENT, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        loadGames(root)
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun bar() = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(14).toFloat() }
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        parent.addView(c, lp)
        return c
    }

    private fun actionButton(t: String, action: String) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener {
            try { startActivity(Intent(action)) }
            catch (e: Exception) { Toast.makeText(context, "यह सेटिंग इस फोन पर नहीं खुली", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun coolAdvice(t: Float, charging: Boolean): String {
        val base = when {
            t >= 45 -> "🔴 फोन बहुत गर्म है! गेम बंद करो और फोन को आराम दो।"
            t >= 40 -> "🟠 फोन गर्म है। नीचे के उपाय करो।"
            t >= 38 -> "🟡 हल्का गर्म है।"
            else -> "✅ फोन ठंडा है।"
        }
        val extra = if (charging && t >= 40) "\n🔌 चार्जर निकाल दो, चार्जिंग से गर्मी बढ़ती है।" else ""
        return base + extra
    }

    private fun overheatAlert() {
        val now = System.currentTimeMillis()
        if (now - lastAlert < 60_000) return
        lastAlert = now
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(500)
        Toast.makeText(this, "⚠️ फोन बहुत गर्म है!", Toast.LENGTH_LONG).show()
    }

    private fun loadGames(root: LinearLayout) {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val games = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .filter {
                @Suppress("DEPRECATION")
                (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                        (android.os.Build.VERSION.SDK_INT >= 26 && it.category == ApplicationInfo.CATEGORY_GAME)
            }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        if (games.isEmpty()) {
            root.addView(label("कोई गेम नहीं मिला।", 14f, Color.GRAY, false))
            return
        }
        for (g in games) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(12).toFloat() }
                setOnClickListener {
                    pm.getLaunchIntentForPackage(g.packageName)?.let { startActivity(it) }
                }
            }
            val icon = ImageView(this).apply { setImageDrawable(pm.getApplicationIcon(g)) }
            row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
            val name = label(pm.getApplicationLabel(g).toString(), 16f, Color.WHITE, false)
            name.setPadding(dp(12), 0, 0, 0)
            row.addView(name)
            val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            root.addView(row, lp)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            handler.postDelayed(this, 1000)
        }
    }

    private fun update() {
        val r = Stats.ram(this)
        ramText.text = "💾 RAM: ${r.usedMb} / ${r.totalMb} MB (${r.percent}%)"
        ramBar.progress = r.percent

        val cpu = Stats.cpuFreqPercent()
        if (cpu != null) {
            cpuText.text = "⚙️ CPU: ${cpu.second} कोर, औसत स्पीड ${cpu.first}%"
            cpuBar.progress = cpu.first
        } else {
            cpuText.text = "⚙️ CPU: जानकारी उपलब्ध नहीं (Android सीमा)"
        }

        val b = Stats.battery(this)
        val warn = when {
            b.tempC >= 45 -> " ⚠️ बहुत गर्म!"
            b.tempC >= 40 -> " ⚠️ गर्म"
            else -> ""
        }
        tempText.text = "🌡 तापमान: ${"%.1f".format(b.tempC)}°C$warn"
        coolStatus.text = coolAdvice(b.tempC, b.charging)
        if (b.tempC >= 45) overheatAlert()
        battText.text = "🔋 बैटरी: ${b.percent}%" + if (b.charging) " (चार्ज हो रही)" else ""

        pingText.text = when {
            pingValue == -2 -> "📶 Ping: जाँच रहा है..."
            pingValue < 0 -> "📶 Ping: नेटवर्क नहीं"
            else -> "📶 Ping: $pingValue ms"
        }
        Thread { pingValue = Stats.pingMs() }.start()
    }

    override fun onResume() {
        super.onResume()
        running = true
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(tick)
    }
}
