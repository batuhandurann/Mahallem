package com.example.core

import com.example.BuildConfig

object AppEnvironment {
    enum class Mode { LOCAL, STAGING, PRODUCTION }

    val mode: Mode = when (BuildConfig.BUILD_TYPE) {
        "release" -> Mode.PRODUCTION
        "staging" -> Mode.STAGING
        else -> Mode.LOCAL
    }

    val isProduction: Boolean get() = mode == Mode.PRODUCTION
    val isTest: Boolean get() = !isProduction

    /**
     * Demo verification is intentionally restricted to non-production builds.
     * Production authentication must use the configured Firebase Auth provider.
     */
    const val DEMO_VERIFICATION_CODE = "123456"
}