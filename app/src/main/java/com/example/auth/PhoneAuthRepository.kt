package com.example.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.example.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class PhoneAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun startVerification(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        require(phoneNumber.matches(Regex("^(\\+90|0090|0)?5\\d{9}$"))) {
            "Geçerli bir Türkiye cep telefonu girin."
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onAutoVerified(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onFailure(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }

        val normalized = normalizeTurkishPhone(phoneNumber)
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalized)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun verifyCode(verificationId: String, code: String): Result<FirebaseUser> = runCatching {
        require(code.matches(Regex("^\\d{6}$"))) { "SMS kodu 6 haneli olmalı." }
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(credential).await().user
            ?: error("Kullanıcı oturumu oluşturulamadı.")
    }.recoverCatching { error ->
        if (error is FirebaseAuthInvalidCredentialsException) {
            throw IllegalArgumentException("SMS kodu geçersiz.", error)
        }
        throw error
    }

    fun signOut() {
        AppDatabase.clearLocalData()
        auth.signOut()
    }

    private fun normalizeTurkishPhone(phone: String): String {
        val clean = phone.filter { it.isDigit() || it == '+' }
        return when {
            clean.startsWith("+90") -> clean
            clean.startsWith("0090") -> "+${clean.drop(2)}"
            clean.startsWith("0") -> "+9$clean"
            else -> "+90$clean"
        }
    }
}