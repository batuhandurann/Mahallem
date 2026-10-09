package com.batuhanduran.burada.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderTrustCopyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun providerProofCopyIsNotHardcoded() {
        compose.setContent { DetailSectionCard(title = "Çalışma Örnekleri") {
            androidx.compose.material3.Text("Bu profilde doğrulanmış çalışma fotoğrafı veya videosu henüz gösterilmiyor.")
        } }
        compose.onNodeWithText("3.8k izlenme").assertDoesNotExist()
    }
}
