package com.avapod.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class AvapodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Appearance.mode(this))
    }
}

object Appearance {
    private const val KEY = "appearance_mode"
    val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun mode(context: Context): Int {
        val saved = context.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
            .getInt(KEY, AppCompatDelegate.MODE_NIGHT_YES)
        return saved.takeIf { it in modes } ?: AppCompatDelegate.MODE_NIGHT_YES
    }

    fun select(context: Context, mode: Int) {
        require(mode in modes)
        context.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
