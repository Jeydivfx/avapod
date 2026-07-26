package com.avapod.app.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.avapod.app.R

object DialogHelper {

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        onPositiveClick: () -> Unit
    ) {
        // ایجاد بیلدر بدون تم پیش‌فرض دکمه‌ها
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_confirm, null)

        val txtTitle = view.findViewById<TextView>(R.id.dialog_title)
        val txtMessage = view.findViewById<TextView>(R.id.dialog_message)
        val btnPositive = view.findViewById<TextView>(R.id.btn_positive)
        val btnNegative = view.findViewById<TextView>(R.id.btn_negative)

        // ست کردن متون دینامیک
        txtTitle.text = title
        txtMessage.text = message

        builder.setView(view)
        val dialog = builder.create()

        // حذف پس‌زمینه پیش‌فرض دایره‌ای سیستم برای فیکس شدن گوشه‌های گرد لایوت ما
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnPositive.setOnClickListener {
            onPositiveClick()
            dialog.dismiss()
        }

        btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}