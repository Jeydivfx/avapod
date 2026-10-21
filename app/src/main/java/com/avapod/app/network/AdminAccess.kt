package com.avapod.app.network

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.withTimeout

object AdminAccess {
    private const val ADMIN_EMAIL = "javadtest777@gmail.com"
    class MissingAdminClaimException : IllegalStateException("Administrator access required")
    suspend fun signIn(username: String, password: String) = withTimeout(20_000) {
        val email = username.trim().let { if (it.equals("admin", ignoreCase = true)) ADMIN_EMAIL else it }
        val auth = FirebaseAuth.getInstance()
        try {
            auth.signInWithEmailAndPassword(email, password).awaitResult()
            requireAdmin()
        } catch (e: Exception) { auth.signOut(); throw e }
    }
    suspend fun requireAdmin() {
        val user = checkNotNull(FirebaseAuth.getInstance().currentUser) { "Sign in required" }
        val token = withTimeout(15_000) { user.getIdToken(true).awaitResult() }
        if (token.claims["admin"] != true) throw MissingAdminClaimException()
    }
    suspend fun savePodcast(id: String, value: Map<String, Any>?) = withTimeout(20_000) {
        requireAdmin()
        require(id.isNotBlank() && id.none { it in ".#$[]/" })
        val root = FirebaseDatabase.getInstance().reference
        val changes = mutableMapOf<String, Any?>(
            "podcasts/$id" to value,
            "db_config/version" to ServerValue.increment(1L)
        )
        // Keep ordering metadata outside podcast records, compatible with existing strict rules.
        val orderPath = "db_config/podcast_order/$id"
        if (value == null) {
            changes[orderPath] = null
        } else {
            val previous = root.child("podcasts/$id").get().awaitResult()
            if (!previous.exists()) changes["$orderPath/created_at"] = ServerValue.TIMESTAMP
            val trending = value["is_trending"] == true
            if (trending && previous.child("is_trending").getValue(Boolean::class.java) != true) {
                changes["$orderPath/trending_at"] = ServerValue.TIMESTAMP
            } else if (!trending) {
                changes["$orderPath/trending_at"] = null
            }
        }
        root.updateChildren(changes).awaitResult()
        PodcastRepository.invalidate()
    }
    fun signOut() { FirebaseAuth.getInstance().signOut() }
}
