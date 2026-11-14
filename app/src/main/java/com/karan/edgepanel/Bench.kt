package com.karan.edgepanel

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object Bench {

    @Volatile private var sink = 0L
    private const val K = 300_000.0

    private fun cpuWork(): Long {
        var x = 1L
        var f = 1.0
        for (i in 1..15_000_000) {
            x = (x * 31 + i) xor (x ushr 7)
            f += Math.sqrt(i.toDouble()) / (1 + (i % 7))
        }
        return x + f.toLong()
    }

    private fun timeMs(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return maxOf((System.nanoTime() - t) / 1e6, 1.0)
    }

    fun cpuSingle(): Int {
        sink += cpuWork()
        var best = Double.MAX_VALUE
        repeat(3) { best = minOf(best, timeMs { sink += cpuWork() }) }
        return (K / best).toInt()
    }

    fun cpuMulti(): Int {
        val n = Runtime.getRuntime().availableProcessors()
        val pool = Executors.newFixedThreadPool(n)
        try {
            var best = Double.MAX_VALUE
            repeat(2) {
                val ms = timeMs {
                    val fs = (1..n).map { pool.submit(Callable { cpuWork() }) }
                    fs.forEach { sink += it.get() }
                }
                best = minOf(best, ms)
            }
            return (n * K / best).toInt()
        } finally {
            pool.shutdown()
        }
    }

    fun ram(): Pair<Int, Int> {
        return try {
            val size = 32 * 1024 * 1024
            val a = ByteArray(size) { (it and 0xFF).toByte() }
            val b = ByteArray(size)
            System.arraycopy(a, 0, b, 0, size)
            val passes = 20
            val ms = timeMs { repeat(passes) { System.arraycopy(a, 0, b, 0, size) } }
            sink += b[size - 1].toLong()
            val mbs = (size / 1048576.0 * passes) / (ms / 1000.0)
            Pair((mbs / 2).toInt(), mbs.toInt())
        } catch (e: Throwable) {
            Pair(0, 0)
        }
    }

    fun storage(dir: File): Triple<Int, Int, Int> {
        val f = File(dir, "bench.tmp")
        return try {
            val chunk = ByteArray(1024 * 1024) { 7 }
            val total = 32
            val w = timeMs {
                FileOutputStream(f).use { o ->
                    repeat(total) { o.write(chunk) }
                    o.fd.sync()
                }
            }
            val r = timeMs {
                FileInputStream(f).use { i ->
                    val buf = ByteArray(1024 * 1024)
                    while (i.read(buf) > 0) { }
                }
            }
            val wm = (total / (w / 1000.0)).toInt()
            val rm = (total / (r / 1000.0)).toInt()
            Triple(wm + rm / 4, wm, rm)
        } catch (e: Throwable) {
            Triple(0, 0, 0)
        } finally {
            f.delete()
        }
    }

    fun addCard(act: Activity, root: LinearLayout) {
        val dp = { v: Int -> (v * act.resources.displayMetrics.density).toInt() }
        val accent = Color.parseColor("#00E5FF")

        val title = TextView(act).apply {
            text = "🏆 Karan Score (Benchmark)"
            textSize = 18f
            setTextColor(accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        }
        root.addView(title)

        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D2E"))
                cornerRadius = dp(14).toFloat()
            }
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        root.addView(card, lp)

        val prefs = act.getSharedPreferences("bench", 0)
        val last = prefs.getInt("last", 0)

        val btn = Button(act).apply {
            text = "▶ Benchmark चलाओ"
            isAllCaps = false
        }
        val status = TextView(act).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        }
        val result = TextView(act).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = if (last > 0) "पिछला स्कोर: $last" else ""
        }
        val note = TextView(act).apply {
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
            text = "नोट: यह Karan Score है, असली AnTuTu स्कोर नहीं। GPU टेस्ट शामिल नहीं। " +
                    "टेस्ट के दौरान फोन थोड़ा गर्म हो सकता है और ऐप को खुला रखो।"
        }
        card.addView(btn); card.addView(status); card.addView(result); card.addView(note)

        btn.setOnClickListener {
            btn.isEnabled = false
            result.text = ""
            Thread {
                fun st(s: String) = act.runOnUiThread { status.text = s }
                st("⏳ CPU Single-core टेस्ट...")
                val s = cpuSingle()
                st("⏳ CPU Multi-core टेस्ट...")
                val m = cpuMulti()
                st("⏳ RAM स्पीड टेस्ट...")
                val (rs, rmb) = ram()
                st("⏳ Storage टेस्ट...")
                val (ss, w, r) = storage(act.cacheDir)
                val total = s + m + rs + ss
                val prev = prefs.getInt("last", 0)
                prefs.edit().putInt("last", total).apply()
                val cores = Runtime.getRuntime().availableProcessors()
                val text = "🏆 कुल स्कोर: $total\n\n" +
                        "⚙️ CPU Single: $s\n" +
                        "⚙️ CPU Multi ($cores कोर): $m\n" +
                        "💾 RAM: $rs ($rmb MB/s)\n" +
                        "📁 Storage: $ss (लिखना $w MB/s, पढ़ना $r MB/s)\n\n" +
                        (if (prev > 0) "पिछला स्कोर: $prev" else "यह पहला टेस्ट है")
                act.runOnUiThread {
                    status.text = "✅ टेस्ट पूरा"
                    result.text = text
                    btn.isEnabled = true
                }
            }.start()
        }
    }
}
