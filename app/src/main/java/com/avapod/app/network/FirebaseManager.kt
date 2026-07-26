package com.avapod.app.network

import com.avapod.app.models.Podcast
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class FirebaseManager {
    private val database = FirebaseDatabase.getInstance().getReference("podcasts")

    fun getAllPodcasts(onResult: (List<Podcast>) -> Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Podcast>()
                println("FirebaseDebug: Snapshot children count: ${snapshot.childrenCount}")

                for (data in snapshot.children) {
                    val podcast = data.getValue(Podcast::class.java)
                    println("FirebaseDebug: Podcast Title: ${podcast?.title}, IsTrending: ${podcast?.is_trending}")
                    podcast?.let { list.add(it) }
                }
                onResult(list)
            }
            override fun onCancelled(error: DatabaseError) {
                println("FirebaseDebug: Error: ${error.message}")
            }
        })
    }
}