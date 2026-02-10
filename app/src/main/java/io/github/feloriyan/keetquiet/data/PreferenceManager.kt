package io.github.feloriyan.keetquiet.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("keetquiet_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_LANGUAGE = "pref_language"
        const val KEY_THEME = "pref_theme"
        const val KEY_NUM_THREADS = "pref_num_threads"
        const val LANG_SYSTEM = "system"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val DEFAULT_NUM_THREADS = 2
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        applyTheme(theme)
    }

    fun applyTheme(theme: String) {
        val mode = when (theme) {
            THEME_LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
        applyLanguage(langCode)
    }

    fun applyLanguage(langCode: String) {
        val locale = if (langCode == LANG_SYSTEM) {
            java.util.Locale.getDefault()
        } else {
            java.util.Locale(langCode)
        }
        
        java.util.Locale.setDefault(locale)
        // Note: Activities now handle locale via attachBaseContext using createConfigurationContext
    }

    fun getNumThreads(): Int {
        if (!prefs.contains(KEY_NUM_THREADS)) {
            return calculateBestThreadCount()
        }
        return prefs.getInt(KEY_NUM_THREADS, DEFAULT_NUM_THREADS)
    }

    private fun calculateBestThreadCount(): Int {
        val availableCores = Runtime.getRuntime().availableProcessors()
        
        // Safety check: if standard 2-4 cores, just use all or half. logic mainly helps with big.LITTLE 6+ cores
        if (availableCores <= 4) {
            return availableCores.coerceAtLeast(2)
        }

        try {
            val maxFreqs = IntArray(availableCores)
            var coresRead = 0

            for (i in 0 until availableCores) {
                // Read max frequency for each core from sysfs
                val file = java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (file.exists() && file.canRead()) {
                    val freqStr = file.readText().trim()
                    if (freqStr.isNotEmpty()) {
                        maxFreqs[i] = freqStr.toInt()
                        coresRead++
                    }
                }
            }

            // If we couldn't read frequencies (e.g. strict SELinux), fallback
            if (coresRead != availableCores) {
                return (availableCores / 2).coerceIn(2, 8)
            }

            // Group distinct frequencies to find clusters
            val distinctFreqs = maxFreqs.distinct().sorted()

            // If only one cluster (all cores same speed), use roughly half to be safe/efficient
            if (distinctFreqs.size == 1) {
                return (availableCores / 2).coerceIn(2, 8)
            }

            // Strategy: Exclude the lowest frequency cluster (Efficiency cores)
            // Use all remaining cores (Performance + Prime)
            val lowestFreq = distinctFreqs[0]
            val performanceCores = maxFreqs.count { it > lowestFreq }

            // Ensure we return a sane value (at least 2, max 8)
            return performanceCores.coerceIn(2, 8)

        } catch (e: Exception) {
            // Fallback on error
            return (availableCores / 2).coerceIn(2, 8)
        }
    }

    fun setNumThreads(count: Int) {
        prefs.edit().putInt(KEY_NUM_THREADS, count).apply()
    }
}
