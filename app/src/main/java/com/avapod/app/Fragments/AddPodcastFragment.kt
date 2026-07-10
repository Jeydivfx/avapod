package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.avapod.app.R
import com.avapod.app.models.Category
import com.avapod.app.models.Podcast
import com.google.firebase.database.FirebaseDatabase

class AddPodcastFragment : Fragment() {

    private lateinit var edtTitle: EditText
    private lateinit var edtArtist: EditText
    private lateinit var edtRssUrl: EditText
    private lateinit var edtThumbnailUrl: EditText
    private lateinit var edtDescription: EditText
    private lateinit var switchTrending: SwitchCompat
    private lateinit var switchFeatured: SwitchCompat
    private lateinit var btnSave: Button
    private lateinit var spinnerCategories: Spinner

    private var editingPodcastId: String? = null
    private val categoryList = mutableListOf<Category>()
    private val categoryNames = mutableListOf<String>()
    private var selectedCategoryId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_podcast, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupToolbar(view)

        val podcast = arguments?.getSerializable("podcast_data") as? Podcast

        loadCategories(podcast)

        btnSave.setOnClickListener {
            validateAndSave()
        }
    }

    private fun initViews(view: View) {
        edtTitle = view.findViewById(R.id.edt_title)
        edtArtist = view.findViewById(R.id.edt_artist)
        edtRssUrl = view.findViewById(R.id.edt_rss_url)
        edtThumbnailUrl = view.findViewById(R.id.edt_thumbnail_url)
        edtDescription = view.findViewById(R.id.edt_description)
        switchTrending = view.findViewById(R.id.switch_is_trending)
        switchFeatured = view.findViewById(R.id.switch_is_featured)
        btnSave = view.findViewById(R.id.btn_save_podcast)
        spinnerCategories = view.findViewById(R.id.spinner_categories)
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
        btnBack.rotationY = 180f
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.findViewById<View>(R.id.btn_menu_common).visibility = View.GONE
    }

    private fun loadCategories(podcastToEdit: Podcast?) {
        val repository = com.avapod.app.network.PodcastRepository(requireContext())
        repository.getCategories { list ->
            activity?.runOnUiThread {
                if (list.isNotEmpty()) {
                    categoryList.clear()
                    categoryNames.clear()
                    categoryList.addAll(list)
                    categoryNames.addAll(list.map { it.name })

                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategories.adapter = adapter

                    if (podcastToEdit != null) {
                        editingPodcastId = podcastToEdit.id
                        edtTitle.setText(podcastToEdit.title)
                        edtArtist.setText(podcastToEdit.artist)
                        edtRssUrl.setText(podcastToEdit.rss_url)
                        edtThumbnailUrl.setText(podcastToEdit.thumbnail_url)
                        edtDescription.setText(podcastToEdit.description)
                        switchTrending.isChecked = podcastToEdit.is_trending
                        switchFeatured.isChecked = podcastToEdit.is_featured

                        val pos = categoryList.indexOfFirst { it.id == podcastToEdit.category_id }
                        if (pos != -1) spinnerCategories.setSelection(pos)
                    }

                    spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedCategoryId = categoryList[position].id
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
            }
        }
    }

    private fun validateAndSave() {
        val title = edtTitle.text.toString().trim()
        val artist = edtArtist.text.toString().trim()
        val rssUrl = edtRssUrl.text.toString().trim()
        val thumbnailUrl = edtThumbnailUrl.text.toString().trim()
        val description = edtDescription.text.toString().trim()
        val isTrending = switchTrending.isChecked
        val isFeatured = switchFeatured.isChecked

        if (title.isEmpty() || artist.isEmpty() || rssUrl.isEmpty() || thumbnailUrl.isEmpty()) {
            Toast.makeText(requireContext(), "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
            return
        }

        saveToFirebase(title, artist, rssUrl, thumbnailUrl, selectedCategoryId, description, isTrending, isFeatured)
    }

    private fun saveToFirebase(
        title: String, artist: String, rss: String,
        thumb: String, catId: String, desc: String, trending: Boolean,
        featured: Boolean
    ) {
        btnSave.isEnabled = false
        val db = FirebaseDatabase.getInstance().getReference("podcasts")
        val podId = editingPodcastId ?: "pod_${System.currentTimeMillis()}"

        val podcastData = mapOf(
            "title" to title, "artist" to artist, "rss_url" to rss,
            "thumbnail_url" to thumb, "category_id" to catId,
            "description" to desc, "is_trending" to trending,
            "is_featured" to featured
        )

        db.child(podId).setValue(podcastData).addOnSuccessListener {
            val configRef = FirebaseDatabase.getInstance().getReference("db_config/version")
            configRef.get().addOnSuccessListener { snapshot ->
                val currentVersion = snapshot.getValue(Int::class.java) ?: 1
                configRef.setValue(currentVersion + 1).addOnCompleteListener {
                    Toast.makeText(requireContext(), "با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }.addOnFailureListener {
            btnSave.isEnabled = true
            Toast.makeText(requireContext(), "خطا در اتصال", Toast.LENGTH_SHORT).show()
        }
    }
}