package com.example.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        require(email.isNotBlank()) { "E-posta gerekli" }
        require(password.isNotBlank()) { "Şifre gerekli" }
        return auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Kullanıcı oturumu oluşturulamadı")
    }

    suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        require(email.isNotBlank()) { "E-posta gerekli" }
        require(password.length >= 8) { "Şifre en az 8 karakter olmalı" }
        return auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Kullanıcı oluşturulamadı")
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        auth.signOut()
    }
}