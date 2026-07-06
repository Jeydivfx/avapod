package com.avapod.app.Fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.avapod.app.PlayerActivity
import com.avapod.app.R
import com.avapod.app.adapters.EpisodeAdapter
import com.avapod.app.models.RssItem
import com.avapod.app.models.Podcast
import com.avapod.app.network.RetrofitClient
import com.avapod.app.network.RssParser
import com.avapod.app.utils.PreferenceHelper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastDetailFragment : Fragment() {

    private lateinit var rvEpisodes: RecyclerView
    private lateinit var loader: ProgressBar
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtDescription: TextView
    private lateinit var prefHelper: PreferenceHelper
    private lateinit var currentPodcast: Podcast
    private lateinit var btnSubscribe: AppCompatButton

    private lateinit var btnReport: View
    private var episodeAdapter: EpisodeAdapter? = null
    private val episodeList = mutableListOf<RssItem>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_podcast_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rssUrl = arguments?.getString("rss_url") ?: ""
        val coverUrl = arguments?.getString("cover_url") ?: ""
        val podcastTitle = arguments?.getString("title") ?: ""
        val podcastDescription = arguments?.getString("description") ?: getString(R.string.default_podcast_description)
        val podcastArtist = arguments?.getString("artist") ?: getString(R.string.default_podcast_artist)

        val imgCover = view.findViewById<ImageView>(R.id.img_full_cover)
        txtTitle = view.findViewById(R.id.txt_podcast_title)
        txtArtist = view.findViewById(R.id.txt_podcast_artist)
        txtDescription = view.findViewById(R.id.txt_podcast_description)

        btnSubscribe = view.findViewById(R.id.btn_subscribe)
        rvEpisodes = view.findViewById(R.id.rv_episodes)
        loader = view.findViewById(R.id.loader)
        btnReport = view.findViewById(R.id.btn_report_podcast)


        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBackCommon = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        val btnMenuCommon = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

        btnBackCommon.scaleX = -1f

        btnBackCommon.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        btnMenuCommon.visibility = View.GONE

        prefHelper = PreferenceHelper(requireContext())
        currentPodcast = Podcast(
            title = podcastTitle,
            rss_url = rssUrl,
            thumbnail_url = coverUrl,
            description = podcastDescription,
            artist = podcastArtist
        )

        txtTitle.text = podcastTitle
        txtArtist.text = podcastArtist
        txtDescription.text = Html.fromHtml(podcastDescription, Html.FROM_HTML_MODE_COMPACT)

        Glide.with(requireContext().applicationContext)
            .load(coverUrl)
            .placeholder(R.drawable.placeholder_podcast)
            .into(imgCover)

        updateSubscribeButton()

        btnSubscribe.setOnClickListener {
            if (prefHelper.isSubscribed(currentPodcast.rss_url)) {
                prefHelper.unsubscribe(currentPodcast.rss_url)
                Toast.makeText(requireContext(), getString(R.string.toast_unsubscribed), Toast.LENGTH_SHORT).show()
            } else {
                prefHelper.subscribe(currentPodcast)
                Toast.makeText(requireContext(), getString(R.string.toast_subscribed), Toast.LENGTH_SHORT).show()
            }
            updateSubscribeButton()
        }

        btnReport.setOnClickListener {
            showReportDialog(podcastTitle)
        }


        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.initialPrefetchItemCount = 6
        rvEpisodes.layoutManager = layoutManager
        rvEpisodes.setHasFixedSize(true)



        episodeAdapter = EpisodeAdapter(episodeList, coverUrl) { rssItem ->
            if (!rssItem.audioUrl.isNullOrEmpty()) {

                com.avapod.app.utils.AdManager.showAdWithCapping(requireActivity()) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("audio_url", rssItem.audioUrl)
                        putExtra("title", rssItem.title)
                        val imageToSend = if (!rssItem.imageUrl.isNullOrEmpty()) rssItem.imageUrl else coverUrl
                        putExtra("cover", imageToSend)
                        putExtra("podcast_name", podcastTitle)
                        putExtra("duration", rssItem.duration)
                    }
                    startActivity(intent)
                }

            } else {
                Toast.makeText(requireContext(), getString(R.string.error_audio_file_not_found), Toast.LENGTH_SHORT).show()
            }
        }
        rvEpisodes.adapter = episodeAdapter

        fetchPodcastEpisodes(rssUrl, coverUrl, podcastTitle)

    }

    private fun updateSubscribeButton() {
        if (prefHelper.isSubscribed(currentPodcast.rss_url)) {
            btnSubscribe.text = getString(R.string.btn_subscribed_text)
            btnSubscribe.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
            btnSubscribe.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.btn_subscribed_background))
        } else {
            btnSubscribe.text = getString(R.string.btn_subscribe_text)
            btnSubscribe.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0)
            btnSubscribe.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(R.color.btn_subscribe_background))
        }
        btnSubscribe.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun fetchPodcastEpisodes(url: String, coverUrl: String, podcastName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                if (isAdded) loader.visibility = View.VISIBLE
            }

            try {
                val response = RetrofitClient.instance.getRssFeed(url)
                if (response.isSuccessful) {
                    val xmlString = response.body()?.string()
                    if (xmlString != null) {
                        val parsedEpisodes = RssParser().parse(xmlString)
                        withContext(Dispatchers.Main) {
                            if (isAdded && view != null) {
                                episodeList.clear()
                                episodeList.addAll(parsedEpisodes)
                                episodeAdapter?.notifyItemRangeInserted(0, parsedEpisodes.size)
                                loader.visibility = View.GONE
                            }
                        }
                    }
                } else {

                    withContext(Dispatchers.Main) {
                        if (isAdded) showError(getString(R.string.error_network_connection))
                    }
                }
            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    if (isAdded) showError(getString(R.string.error_loading_failed))
                }
            }
        }
    }


    private fun showError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isAdded && view != null) {
                loader.visibility = View.GONE
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        try {
            view?.findViewById<ImageView>(R.id.img_full_cover)?.let {
                Glide.with(requireContext().applicationContext).clear(it)
            }


            rvEpisodes.stopScroll()
            rvEpisodes.layoutManager = null

            if (::rvEpisodes.isInitialized) {
                rvEpisodes.adapter = null
            }

            episodeList.clear()
            episodeAdapter = null

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showReportDialog(podcastTitle: String) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_report_bottom_sheet, null)
        bottomSheetDialog.setContentView(view)

        val podcastUrl = arguments?.getString("rss_url") ?: getString(R.string.unknown_podcast_id)
        val podcastId = if (podcastUrl.contains("/")) podcastUrl.substringAfterLast("/") else podcastUrl


        view.findViewById<LinearLayout>(R.id.item_copyright).setOnClickListener {
            sendReportEmail(podcastTitle, podcastId, getString(R.string.report_reason_copyright))
            bottomSheetDialog.dismiss()
        }


        view.findViewById<LinearLayout>(R.id.item_inappropriate).setOnClickListener {
            sendReportEmail(podcastTitle, podcastId, getString(R.string.report_reason_inappropriate))
            bottomSheetDialog.dismiss()
        }

        view.findViewById<LinearLayout>(R.id.item_low_quality).setOnClickListener {
            sendReportEmail(podcastTitle, podcastId, getString(R.string.report_reason_low_quality))
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setOnShowListener {
            val bottomSheetContainer = view.parent as? View
            bottomSheetContainer?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        bottomSheetDialog.show()
    }

    private fun sendReportEmail(podcastTitle: String, podcastUrl: String, reason: String) {
        val emailBody = getString(R.string.report_email_body_template, podcastTitle, podcastUrl, reason)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("avapod.project@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_email_subject))
            putExtra(Intent.EXTRA_TEXT, emailBody)
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.report_email_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_email_app_not_found), Toast.LENGTH_SHORT).show()
        }
    }
}