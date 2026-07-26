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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saman.zamani.persiandate.PersianDate

class AboutFragment : Fragment() {

    private lateinit var txtShamsi: TextView
    private lateinit var txtMiladi: TextView
    private lateinit var txtMessage: TextView
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbHelper = DatabaseHelper(requireContext())
        txtShamsi = view.findViewById(R.id.txt_shamsi)
        txtMiladi = view.findViewById(R.id.txt_miladi)
        txtMessage = view.findViewById(R.id.txt_daily_message)

        updateDates()
        loadMessageFromLocal()
        fetchMessageFromFirebase()
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
        txtMiladi.text = "${pdate.getGrgYear()}/${String.format("%02d", pdate.getGrgMonth())}/${String.format("%02d", pdate.getGrgDay())}"
    }

    private fun loadMessageFromLocal() {
        lifecycleScope.launch(Dispatchers.IO) {
            val message = dbHelper.getMessage("daily_message")
            withContext(Dispatchers.Main) {
                if (message != null && message.text.isNotEmpty()) {
                    txtMessage.text = message.text
                } else {
                    txtMessage.text = getString(R.string.default_daily_message)
                }
            }
        }
    }

    private fun fetchMessageFromFirebase() {
        val db = FirebaseDatabase.getInstance().getReference("app_messages")

        db.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.child("text").getValue(String::class.java) ?: ""
                val date = snapshot.child("date").getValue(String::class.java) ?: ""

                if (text.isNotEmpty()) {

                    lifecycleScope.launch(Dispatchers.IO) {
                        dbHelper.saveMessage("daily_message", text, date)
                        withContext(Dispatchers.Main) {
                            txtMessage.text = text
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }
}