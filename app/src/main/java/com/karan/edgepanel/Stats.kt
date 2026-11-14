package com.karan.edgepanel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

data class RamInfo(val usedMb: Long, val totalMb: Long) {
    val percent get() = if (totalMb == 0L) 0 else ((usedMb * 100) / totalMb).toInt()
}

data class BatteryInfo(val percent: Int, val tempC: Float, val charging: Boolean)

object Stats {

    fun ram(ctx: Context): RamInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / 1048576
        val free = mi.availMem / 1048576
        return RamInfo(total - free, total)
    }

    fun cpuFreqPercent(): Pair<Int, Int>? {
        val cores = Runtime.getRuntime().availableProcessors()
        var cur = 0L
        var max = 0L
        for (i in 0 until cores) {
            try {
                val c = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong()
                val m = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                cur += c; max += m
            } catch (e: Exception) { }
        }
        if (max == 0L) return null
        return Pair(((cur * 100) / max).toInt(), cores)
    }

    fun battery(ctx: Context): BatteryInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(level * 100 / scale, temp, charging)
    }

    fun pingMs(): Int {
        val t = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 1500) }
            ((System.nanoTime() - t) / 1_000_000).toInt()
        } catch (e: Exception) { -1 }
    }
}
