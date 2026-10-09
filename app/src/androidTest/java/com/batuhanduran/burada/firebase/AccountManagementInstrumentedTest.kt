package com.batuhanduran.burada.firebase

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.MainActivity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.Source
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class AccountManagementInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun editProfilePersistsThroughReloginAndDeletionRequiresConfirmation() {
        check(BuildConfig.USE_FIREBASE_EMULATORS && BuildConfig.DEBUG)
        val auth = FirebaseServices.auth
        auth.signOut()
        val email = "managed-${UUID.randomUUID()}@example.com"
        val password = "SecurePass123!"
        val user = Tasks.await(auth.createUserWithEmailAndPassword(email,password),30,TimeUnit.SECONDS).user!!
        try {
            Tasks.await(user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName("Original Name").build()),30,TimeUnit.SECONDS)
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("open_account_management").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("open_account_management").performClick()
            compose.waitUntil(30_000) { runCatching { compose.onNodeWithTag("save_account_profile").assertIsEnabled() }.isSuccess }
            compose.onNodeWithTag("account_name").performTextReplacement("Updated Name")
            compose.onNodeWithTag("account_bio").performTextReplacement("Local services")
            compose.onNodeWithTag("save_account_profile").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("account_message").fetchSemanticsNodes().isNotEmpty() }
            val ref = FirebaseServices.firestore.collection("users").document(user.uid)
            val doc = Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS)
            assertEquals("Updated Name",doc.getString("displayName"))
            assertEquals("Local services",doc.getString("bio"))
            compose.onNodeWithTag("open_account_deletion").performScrollTo().performClick()
            compose.onNodeWithTag("confirm_account_deletion").assertIsNotEnabled()
            compose.onNodeWithTag("delete_account_confirmation").performTextInput("WRONG")
            compose.onNodeWithTag("confirm_account_deletion").assertIsNotEnabled()
            compose.onNodeWithText("Vazgeç").performClick()
            auth.signOut()
            Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("open_account_management").fetchSemanticsNodes().isNotEmpty() }
            val persisted = Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS)
            assertEquals("Updated Name",persisted.getString("displayName"))
        } finally {
            auth.currentUser?.takeIf { it.uid == user.uid }?.let { Tasks.await(it.delete(),30,TimeUnit.SECONDS) }
            auth.signOut()
        }
    }
}
