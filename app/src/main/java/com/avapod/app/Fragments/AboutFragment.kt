package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.avapod.app.R
import com.avapod.app.utils.DatabaseHelper
import com.avapod.app.utils.StringUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.avapod.app.network.PublicDatabase
import com.avapod.app.network.databaseString
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saman.zamani.persiandate.PersianDate

class AboutFragment : Fragment() {

    private lateinit var txtShamsi: TextView
    private lateinit var txtMiladi: TextView
    private lateinit var txtMessage: TextView
    private lateinit var appContext: android.content.Context

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
        view.findViewById<View>(R.id.btn_back_common).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }


        appContext = requireContext().applicationContext
        txtShamsi = view.findViewById(R.id.txt_shamsi)
        txtMiladi = view.findViewById(R.id.txt_miladi)
        txtMessage = view.findViewById(R.id.txt_daily_message)

        updateDates()
        fetchMessageFromFirebase()
        setupAppVersion(view)
    }

    private fun updateDates() {
        val now = PersianDate()
        val year = now.getShYear()
        val month = now.getShMonth()
        val day = now.getShDay()

        // تاریخ شمسی
        val shamsiDate = "${StringUtils.toPersianNumber(year.toString())}/" +
                "${StringUtils.toPersianNumber(String.format("%02d", month))}/" +
                "${StringUtils.toPersianNumber(String.format("%02d", day))}"
        txtShamsi.text = shamsiDate

        // تاریخ میلادی
        val pdate = PersianDate()
        pdate.setShYear(year)
        pdate.setShMonth(month)
        pdate.setShDay(day)
        txtMiladi.text = StringUtils.toPersianNumber("${pdate.getGrgYear()}/${String.format("%02d", pdate.getGrgMonth())}/${String.format("%02d", pdate.getGrgDay())}")
    }

    private fun fetchMessageFromFirebase() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { runCatching { DatabaseHelper(appContext).use { it.getMessage("daily_message") } }.getOrNull() }
            txtMessage.text = cached?.text?.takeIf { it.isNotBlank() } ?: getString(R.string.default_daily_message)
            try {
                val snapshot = withTimeout(15_000) {
                    PublicDatabase.read("app_messages")
                }
                val text = snapshot.databaseString("text")
                val date = snapshot.databaseString("date")
                if (text.isNotBlank()) {
                    withContext(Dispatchers.IO) { DatabaseHelper(appContext).use { it.saveMessage("daily_message", text, date) } }
                    txtMessage.text = text
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                // Keep the cached daily message.
            }
        }
    }

    private fun setupAppVersion(view: View) {
        val txtVersion = view.findViewById<TextView>(R.id.app_version_text)

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
            txtVersion.text = "نسخه $persianVersion"

        } catch (e: Exception) {
            e.printStackTrace()
            txtVersion.text = "نسخه ۱.۰"
        }
    }
}
