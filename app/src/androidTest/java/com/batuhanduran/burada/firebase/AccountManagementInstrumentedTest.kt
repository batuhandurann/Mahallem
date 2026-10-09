package com.batuhanduran.burada.firebase

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.MainActivity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import com.batuhanduran.burada.validation.RequestSchedules
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class AccountManagementInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editAndRemoveRequestThroughAccountScreen(): Unit = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS && BuildConfig.DEBUG)
        val auth = FirebaseServices.auth
        auth.signOut()
        val user = Tasks.await(auth.createUserWithEmailAndPassword("listing-${UUID.randomUUID()}@example.com", "SecurePass123!"),30,TimeUnit.SECONDS).user!!
        try {
            val repository = MarketplaceRepository(uid = user.uid)
            val schedule = RequestSchedules.now()
            val id = repository.createJobRequest(JobRequestEntity(title = "Musluk tamiri", sector = "HOME_REPAIR",
                categoryId = "boyaci", district = "Buca", urgencyMode = "EMERGENCY", eventOrJobDate = schedule.date,
                eventTime = schedule.time, address = "Özel adres", status = "PENDING", customerName = "Test Müşteri",
                customerPhone = "05551234567", provinceId = "tr_35", districtId = "tr_35_buca",
                neighborhoodId = "pilot_tr_35_buca_efeler", neighborhoodName = "Efeler"))
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("open_account_management").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("open_account_management").performClick()
            val list = compose.onNodeWithTag("account_management_list")
            compose.waitUntil(30_000) {
                runCatching { list.performScrollToNode(hasTestTag("manage_request_$id")); compose.onNodeWithTag("manage_request_$id").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithTag("manage_request_$id").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("listing_edit_title").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("listing_edit_title").performTextReplacement("Mutfak musluğu tamiri")
            compose.onNodeWithTag("save_listing").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("save_listing").fetchSemanticsNodes().isEmpty() }
            val ref = FirebaseServices.firestore.document("requests/$id")
            assertEquals("Mutfak musluğu tamiri", Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS).getString("data.title"))
            list.performScrollToNode(hasTestTag("manage_request_$id"))
            compose.onNodeWithTag("manage_request_$id").performClick()
            compose.waitUntil(30_000) {
                runCatching { compose.onNodeWithTag("listing_management_list").performScrollToNode(hasTestTag("remove_listing")) }.isSuccess
            }
            compose.onNodeWithTag("remove_listing").performScrollTo().performClick()
            compose.onNodeWithTag("confirm_remove_listing").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("confirm_remove_listing").fetchSemanticsNodes().isEmpty() }
            assertEquals("archived", Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS).getString("visibility"))
            val archived = withTimeout(30_000) { repository.getMyRequests().first { rows -> rows.any { it.id == id && it.visibility == "archived" } } }
            assertEquals("Mutfak musluğu tamiri", archived.single { it.id == id }.title)
        } finally {
            auth.currentUser?.takeIf { it.uid == user.uid }?.let { Tasks.await(it.delete(),30,TimeUnit.SECONDS) }
            auth.signOut()
        }
    }
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
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Profiliniz buluta kaydedildi.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("open_account_management").performClick()
            val list = compose.onNodeWithTag("account_management_list")
            list.performScrollToNode(hasTestTag("account_name"))
            compose.waitUntil(30_000) { runCatching { compose.onNodeWithTag("account_name").assertIsEnabled() }.isSuccess }
            compose.onNodeWithTag("account_name").performTextReplacement("Updated Name")
            list.performScrollToNode(hasTestTag("account_bio"))
            compose.onNodeWithTag("account_bio").performTextReplacement("Local services")
            list.performScrollToNode(hasTestTag("save_account_profile"))
            compose.onNodeWithTag("save_account_profile").performScrollTo().performClick()
            compose.waitUntil(30_000) {
                runCatching { list.performScrollToNode(hasTestTag("account_message") or hasTestTag("account_error")) }.isSuccess
            }
            compose.onNodeWithTag("account_error").assertDoesNotExist()
            compose.onNodeWithTag("account_message").assertTextEquals("Profiliniz kaydedildi.")
            val ref = FirebaseServices.firestore.collection("users").document(user.uid)
            val doc = Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS)
            assertEquals("Updated Name",doc.getString("displayName"))
            assertEquals("Local services",doc.getString("bio"))
            list.performScrollToNode(hasTestTag("open_account_deletion"))
            compose.onNodeWithTag("open_account_deletion").performClick()
            compose.onNodeWithTag("confirm_account_deletion").assertIsNotEnabled()
            compose.onNodeWithTag("delete_account_confirmation").performTextInput("WRONG")
            compose.onNodeWithTag("confirm_account_deletion").assertIsNotEnabled()
            compose.onNodeWithText("Vazgeç").performClick()
            auth.signOut()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("auth_email").fetchSemanticsNodes().isNotEmpty() }
            Tasks.await(auth.signInWithEmailAndPassword(email,password),30,TimeUnit.SECONDS)
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("open_account_management").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Profiliniz buluta kaydedildi.").fetchSemanticsNodes().isNotEmpty() }
            val persisted = Tasks.await(ref.get(Source.SERVER),30,TimeUnit.SECONDS)
            assertEquals("Updated Name",persisted.getString("displayName"))
        } finally {
            auth.currentUser?.takeIf { it.uid == user.uid }?.let { Tasks.await(it.delete(),30,TimeUnit.SECONDS) }
            auth.signOut()
        }
    }
}
