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
        FirebaseDatabase.getInstance().reference.updateChildren(mapOf(
            "podcasts/$id" to value,
            "db_config/version" to ServerValue.increment(1L)
        )).awaitResult()
        PodcastRepository.invalidate()
    }
    fun signOut() { FirebaseAuth.getInstance().signOut() }
}
