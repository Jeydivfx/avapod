package com.avapod.app.Fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.avapod.app.R
import com.avapod.app.network.AnnouncementInbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class InboxFragment : Fragment(R.layout.fragment_inbox) {
    private var request: Job? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
        view.findViewById<View>(R.id.btn_back_common).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.inbox_retry).setOnClickListener { refresh() }
        render(AnnouncementInbox.cached(requireContext()))
        refresh()
    }
    private fun addMessage(container: android.widget.LinearLayout, heading: String, body: String, date: String) {
        val card = layoutInflater.inflate(R.layout.item_inbox_message, container, false)
        card.findViewById<TextView>(R.id.message_card_heading).text = heading
        card.findViewById<TextView>(R.id.message_card_body).text = body
        card.findViewById<TextView>(R.id.message_card_date).apply {
            text = date
            visibility = if (date.isBlank()) View.GONE else View.VISIBLE
        }
        container.addView(card)
    }
    private fun renderPushHistory() {
        val context = requireContext()
        val databaseMessages = AnnouncementInbox.history(context)
        val entries = com.avapod.app.network.PushInbox.entries(context)
            .filterNot { push -> databaseMessages.any { it.first == push.body } }
            .take((3 - databaseMessages.size).coerceAtLeast(0))
        val container = requireView().findViewById<android.widget.LinearLayout>(R.id.inbox_push_history)
        container.removeAllViews()
        entries.forEach { entry ->
            addMessage(container, entry.title.ifBlank { getString(R.string.inbox_card_label) }, entry.body, "")
        }
        com.avapod.app.network.PushInbox.markRead(context, entries.map { it.id }.toSet())
    }
    private fun render(message: Pair<String, String>) {
        val context = requireContext()
        val messages = AnnouncementInbox.history(context)
        val container = requireView().findViewById<android.widget.LinearLayout>(R.id.inbox_database_history)
        container.removeAllViews()
        messages.forEachIndexed { index, item ->
            addMessage(container, getString(if (index == 0) R.string.inbox_latest_message else R.string.inbox_card_label), item.first, item.second)
        }
        renderPushHistory()
        AnnouncementInbox.markRead(context, message)
        requireView().findViewById<View>(R.id.inbox_empty).visibility =
            if (messages.isEmpty() && com.avapod.app.network.PushInbox.entries(context).isEmpty()) View.VISIBLE else View.GONE
    }
    private fun refresh() {
        if (request?.isActive == true) return
        val context = requireContext().applicationContext
        request = viewLifecycleOwner.lifecycleScope.launch {
            view?.findViewById<View>(R.id.inbox_retry)?.visibility = View.GONE
            view?.findViewById<TextView>(R.id.inbox_status)?.setText(R.string.inbox_loading)
            try {
                render(AnnouncementInbox.refresh(context, force = true))
                view?.findViewById<TextView>(R.id.inbox_status)?.text = ""
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                view?.findViewById<TextView>(R.id.inbox_status)?.setText(R.string.inbox_error)
                view?.findViewById<View>(R.id.inbox_retry)?.visibility = View.VISIBLE
            }
        }
    }
}
