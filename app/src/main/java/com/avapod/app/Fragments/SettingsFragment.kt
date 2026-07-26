package com.avapod.app.Fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.avapod.app.R
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.PreferenceHelper
import com.avapod.app.utils.SleepTimerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    private var adminClickCount = 0
    private lateinit var prefHelper: PreferenceHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        prefHelper = PreferenceHelper(requireContext())

        val txtVersion = view.findViewById<TextView>(R.id.txt_app_version)
        val layoutClearCache = view.findViewById<LinearLayout>(R.id.layout_clear_cache)
        val layoutClearDownloads = view.findViewById<LinearLayout>(R.id.layout_clear_downloads)
        val layoutSleepTimer = view.findViewById<LinearLayout>(R.id.layout_sleep_timer)
        val txtTimerStatus = view.findViewById<TextView>(R.id.txt_timer_status)
        updateTimerStatusText(txtTimerStatus)

        try {
            val packageManager = requireContext().packageManager
            val packageName = requireContext().packageName

            val versionName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }

            val persianVersion = com.avapod.app.utils.StringUtils.toPersianNumber(versionName ?: "1.0")
            txtVersion.text = " نسخه $persianVersion"

        } catch (e: Exception) {
            e.printStackTrace()
            txtVersion.text = "نسخه ۱.۰"
        }

        view.findViewById<View>(R.id.btn_back_common).apply {
            scaleX = -1f
            setOnClickListener { parentFragmentManager.popBackStack() }
        }

        view.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE

        layoutClearCache.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                clearApplicationCache(requireContext())
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                }
            }
        }

        layoutClearDownloads.setOnClickListener {
            DialogHelper.showConfirmDialog(
                context = requireContext(),
                title = getString(R.string.dialog_clear_downloads_title),
                message = getString(R.string.dialog_clear_downloads_message),
                onPositiveClick = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        prefHelper.clearAllDownloads()

                        val downloadDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                        if (downloadDir != null && downloadDir.isDirectory) {
                            val files = downloadDir.listFiles()
                            if (files != null) {
                                for (file in files) {
                                    if (file.isFile) {
                                        file.delete()
                                    }
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.toast_downloads_cleared), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }


        layoutSleepTimer.setOnClickListener {
            val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            val dialogView = layoutInflater.inflate(R.layout.dialog_sleep_timer, null)

            builder.setView(dialogView)
            val dialog = builder.create()

            // پیدا کردن دکمه‌های رنگی لایوت جدید
            val btn15 = dialogView.findViewById<TextView>(R.id.btn_timer_15)
            val btn30 = dialogView.findViewById<TextView>(R.id.btn_timer_30)
            val btn60 = dialogView.findViewById<TextView>(R.id.btn_timer_60)
            val btnOff = dialogView.findViewById<TextView>(R.id.btn_timer_off)

            btn15.setOnClickListener {
                startServiceTimer(15, txtTimerStatus)
                dialog.dismiss()
            }

            btn30.setOnClickListener {
                startServiceTimer(30, txtTimerStatus)
                dialog.dismiss()
            }

            btn60.setOnClickListener {
                startServiceTimer(60, txtTimerStatus)
                dialog.dismiss()
            }

            btnOff.setOnClickListener {
                SleepTimerManager.stopTimer()
                updateTimerStatusText(txtTimerStatus)
                Toast.makeText(requireContext(), getString(R.string.toast_sleep_timer_canceled), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

            dialog.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
            dialog.show()
        }

        txtVersion.setOnClickListener {
            adminClickCount++
            if (adminClickCount >= 5) {
                adminClickCount = 0
                showAdminLoginDialog()
            }
        }

        return view
    }

    private fun clearApplicationCache(context: Context) {
        try {
            val dir = context.cacheDir
            if (dir != null && dir.isDirectory) {
                deleteFilesInDirectory(dir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteFilesInDirectory(dir: File?) {
        if (dir != null && dir.isDirectory) {
            val children = dir.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        deleteFilesInDirectory(child)
                    } else {
                        child.delete()
                    }
                }
            }
            dir.delete()
        } else if (dir != null && dir.isFile) {
            dir.delete()
        }
    }

    private fun showAdminLoginDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val view = layoutInflater.inflate(R.layout.dialog_admin_login, null)

        val edtUser = view.findViewById<EditText>(R.id.edt_admin_user)
        val edtPass = view.findViewById<EditText>(R.id.edt_admin_pass)
        val btnLogin = view.findViewById<Button>(R.id.btn_dialog_login)

        builder.setView(view)
        val dialog = builder.create()

        btnLogin.setOnClickListener {
            val user = edtUser.text.toString()
            val pass = edtPass.text.toString()

            if (user == "admin" && pass == "padmin") {
                dialog.dismiss()
                openAdminDashboard()
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_invalid_admin_credentials), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun openAdminDashboard() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, AdminDashboardFragment()) // تغییر مقصد
            .addToBackStack(null)
            .commit()
    }

    private fun startServiceTimer(minutes: Int, statusTextView: TextView) {
        SleepTimerManager.startTimer(
            context = requireContext(),
            minutes = minutes,
            onTick = { left ->
                activity?.runOnUiThread {
                    statusTextView.text = getString(R.string.timer_status_on, left)
                }
            },
            onFinish = {
                activity?.runOnUiThread {
                    statusTextView.text = getString(R.string.timer_status_off)
                }
            }
        )
        updateTimerStatusText(statusTextView)

        val timeString = getString(if (minutes == 15) R.string.sleep_timer_15 else if (minutes == 30) R.string.sleep_timer_30 else R.string.sleep_timer_60)
        Toast.makeText(requireContext(), getString(R.string.toast_sleep_timer_set, timeString), Toast.LENGTH_SHORT).show()
    }

    private fun updateTimerStatusText(statusTextView: TextView) {
        if (SleepTimerManager.isTimerRunning) {
            statusTextView.text = getString(R.string.timer_status_on, SleepTimerManager.minutesLeft)
        } else {
            statusTextView.text = getString(R.string.timer_status_off)
        }
    }
}