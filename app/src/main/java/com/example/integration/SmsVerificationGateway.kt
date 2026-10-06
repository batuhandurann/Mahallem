package com.example.integration

interface SmsVerificationGateway {
    suspend fun sendVerificationCode(phoneNumber: String): String
    suspend fun verifyCode(phoneNumber: String, code: String): Boolean
}

class TestSmsVerificationGateway : SmsVerificationGateway {
    override suspend fun sendVerificationCode(phoneNumber: String): String =
        "TEST_ONLY"

    override suspend fun verifyCode(phoneNumber: String, code: String): Boolean =
        code == "123456"
}

/**
 * Production adapter contract.
 *
 * Implement this with the selected SMS provider's server-side API.
 * Provider credentials must never be placed in the Android application.
 */
class ProductionSmsVerificationGateway : SmsVerificationGateway {
    override suspend fun sendVerificationCode(phoneNumber: String): String =
        error("Production SMS provider is not configured")

    override suspend fun verifyCode(phoneNumber: String, code: String): Boolean =
        error("Production SMS provider is not configured")
}