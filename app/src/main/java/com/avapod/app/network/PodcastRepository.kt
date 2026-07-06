package com.avapod.app.network

import android.content.Context
import com.avapod.app.models.Category
import com.avapod.app.models.Podcast
import com.avapod.app.utils.DatabaseHelper
import com.google.firebase.database.FirebaseDatabase
import com.avapod.app.utils.AdManager

class PodcastRepository(context: Context) {
    private val dbHelper = DatabaseHelper(context)
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val prefs = context.getSharedPreferences("AvapodPrefs", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    fun getPodcasts(onResult: (List<Podcast>) -> Unit) {
        val localData = dbHelper.getAllPodcasts()
        if (localData.isNotEmpty()) {
            onResult(localData)
        }

        firebaseDb.getReference("db_config/version").get().addOnSuccessListener { snapshot ->
            val fireVersion = snapshot.getValue(Int::class.java) ?: 1
            val localVersion = prefs.getInt("db_version", 0)

            if (fireVersion > localVersion) {
                downloadAndSync(fireVersion, onResult)
            }
        }
    }

    fun getCategories(onResult: (List<Category>) -> Unit) {
        val localCats = dbHelper.getAllCategories()
        if (localCats.isNotEmpty()) {
            onResult(localCats)
        }

        firebaseDb.getReference("db_config/version").get().addOnSuccessListener { snapshot ->
            val fireVersion = snapshot.getValue(Int::class.java) ?: 1
            val localVersion = prefs.getInt("db_version", 0)

            if (fireVersion > localVersion) {
                firebaseDb.getReference("categories").get().addOnSuccessListener { catSnapshot ->
                    val newList = mutableListOf<Category>()
                    for (data in catSnapshot.children) {
                        val cat = data.getValue(Category::class.java)
                        cat?.let {
                            it.id = data.key ?: ""
                            newList.add(it)
                        }
                    }
                    dbHelper.insertCategories(newList)
                    onResult(newList)
                }
            }
        }
    }

    private fun downloadAndSync(newVersion: Int, onResult: (List<Podcast>) -> Unit) {

        firebaseDb.getReference("ad_config").get().addOnSuccessListener { adSnapshot ->
            val bannerEnabled = adSnapshot.child("ad_banner_enabled").getValue(Boolean::class.java) ?: true
            val interstitialEnabled = adSnapshot.child("ad_interstitial_enabled").getValue(Boolean::class.java) ?: false
            val rewardedEnabled = adSnapshot.child("ad_rewarded_enabled").getValue(Boolean::class.java) ?: false


            dbHelper.insertOrUpdateAdConfig(bannerEnabled, interstitialEnabled, rewardedEnabled)


            AdManager.preloadInterstitialAd(appContext)
            AdManager.preloadRewardedAd(appContext)


            firebaseDb.getReference("categories").get().addOnSuccessListener { catSnapshot ->
                val categories = mutableListOf<Category>()
                for (data in catSnapshot.children) {
                    val name = data.child("name").getValue(String::class.java) ?: ""
                    val icon = data.child("icon").getValue(String::class.java) ?: ""
                    val id = data.key ?: ""
                    categories.add(Category(id, name, icon))
                }
                dbHelper.insertCategories(categories)

                firebaseDb.getReference("podcasts").get().addOnSuccessListener { podSnapshot ->
                    val newList = mutableListOf<Podcast>()
                    for (data in podSnapshot.children) {
                        val podcast = data.getValue(Podcast::class.java)
                        podcast?.let {
                            it.id = data.key ?: ""
                            newList.add(it)
                        }
                    }
                    dbHelper.insertPodcasts(newList)
                    prefs.edit().putInt("db_version", newVersion).apply()
                    onResult(newList)
                }
            }
        }
    }
}