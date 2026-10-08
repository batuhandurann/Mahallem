package com.example.ui.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.ConversationEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationsListScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun missingTimeIsNotFaked() {
        val conversation = ConversationEntity("test", "uid", "Ali", "Usta", "Merhaba", 0L)
        rule.setContent {
            MyApplicationTheme {
                ConversationsListScreen(listOf(conversation), {}, {})
            }
        }
        rule.onNodeWithTag("conversation_time_test").assertTextEquals("Tarih yok")
    }
}
