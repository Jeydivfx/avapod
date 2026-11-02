package com.avapod.app.Fragments

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.avapod.app.R
import com.avapod.app.models.Podcast
import com.avapod.app.models.RssItem
import com.avapod.app.network.*
import com.avapod.app.utils.DialogHelper
import com.avapod.app.utils.UrlValidator
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

fun Fragment.showTopEpisodePicker(podcast: Podcast) {
    val owner = viewLifecycleOwner
    val appContext = requireContext().applicationContext
    val loading = AlertDialog.Builder(requireContext()).setMessage(R.string.admin_episode_loading)
        .setNegativeButton(android.R.string.cancel, null).create()
    DialogHelper.bind(loading, owner)
    loading.show()
    val job = owner.lifecycleScope.launch {
        try {
            val (episodes, existing) = withTimeout(35_000) {
                AdminAccess.requireAdmin()
                val snapshot = FirebaseDatabase.getInstance().getReference(HomeEpisodes.PATH).get().awaitResult()
                val retained = snapshot.children.filter { it.child("podcast_id").value == podcast.id }
                    .mapNotNull { runCatching { Gson().fromJson(Gson().toJson(it.value), RssItem::class.java) }.getOrNull() }
                val feed = RssLoader.load(podcast.rss_url, appContext)
                (retained + feed.take(300)).filter { UrlValidator.isWebUrl(it.audioUrl) }.distinctBy { it.audioUrl } to
                    snapshot.children.mapNotNull { it.key }.toSet()
            }
            loading.dismiss()
            val checked = BooleanArray(episodes.size) { HomeEpisodes.key(episodes[it].audioUrl.orEmpty()) in existing }
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.admin_top_episodes) + " — " + podcast.title)
                .setMultiChoiceItems(episodes.map { it.title.orEmpty() }.toTypedArray(), checked) { _, index, value -> checked[index] = value }
                .setPositiveButton(R.string.home_save, null)
                .setNegativeButton(android.R.string.cancel, null).create()
            DialogHelper.bind(dialog, owner)
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.setCancelable(false)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                owner.lifecycleScope.launch {
                    try {
                        HomeEpisodes.save(podcast, episodes, checked)
                        Toast.makeText(context, R.string.admin_episode_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                        Toast.makeText(context, R.string.admin_episode_error, Toast.LENGTH_LONG).show()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                        dialog.setCancelable(true)
                    }
                }
            }
        } catch (e: Exception) {
            loading.dismiss()
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            Toast.makeText(context, R.string.admin_episode_error, Toast.LENGTH_LONG).show()
        }
    }
    loading.setOnCancelListener { job.cancel() }
    loading.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { job.cancel(); loading.dismiss() }
}
