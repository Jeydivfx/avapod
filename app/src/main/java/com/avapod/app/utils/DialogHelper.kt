package com.avapod.app.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.avapod.app.R

object DialogHelper {
    fun bind(dialog: android.app.Dialog, owner: androidx.lifecycle.LifecycleOwner) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { dialog.dismiss() }
        }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
    }

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        owner: androidx.lifecycle.LifecycleOwner? = context as? androidx.lifecycle.LifecycleOwner,
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
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { dialog.dismiss() }
        }
        owner?.lifecycle?.addObserver(observer)
        dialog.setOnDismissListener { owner?.lifecycle?.removeObserver(observer) }

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
