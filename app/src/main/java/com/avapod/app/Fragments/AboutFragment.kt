package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.avapod.app.R
import com.avapod.app.utils.StringUtils
import saman.zamani.persiandate.PersianDate

class AboutFragment : Fragment() {

    private lateinit var txtShamsi: TextView
    private lateinit var txtMiladi: TextView

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


        txtShamsi = view.findViewById(R.id.txt_shamsi)
        txtMiladi = view.findViewById(R.id.txt_miladi)

        updateDates()
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
