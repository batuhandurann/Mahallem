package com.batuhanduran.burada.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.remote.AtomicWriteBudget
import com.batuhanduran.burada.data.remote.WriteOperation
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import com.batuhanduran.burada.moderation.ModerationRepository
import com.batuhanduran.burada.moderation.ReportDraft
import com.batuhanduran.burada.moderation.ReportReason
import com.batuhanduran.burada.moderation.ReportTargetType
import com.batuhanduran.burada.validation.RequestSchedules
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Uses actual Android Firebase SDKs and deployed emulator rules. Never contacts production. */
@RunWith(AndroidJUnit4::class)
class AuthMarketplaceInstrumentedTest {
    @Test fun registerLoginListingOfferChatAndAccountIsolation(): Unit = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS) { "Run with -PfirebaseEmulators=true; production tests are forbidden." }
        val auth = FirebaseServices.auth
        val db = FirebaseServices.firestore
        val suffix = UUID.randomUUID().toString()
        val customerEmail = "customer-$suffix@example.com"
        val providerEmail = "provider-$suffix@example.com"
        val password = "SecurePass123!"
        fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)
        fun assertPermissionDenied(task: com.google.android.gms.tasks.Task<*>) {
            try { await(task); fail("Expected server-side permission denied") }
            catch (error: java.util.concurrent.ExecutionException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    (error.cause as? FirebaseFirestoreException)?.code)
            }
        }
        fun reportWrite(reporter: String, type: String, targetId: String, targetUid: String,
                        targetPath: String, conversationId: String = "") = db.runTransaction { tx ->
            val reportRef = db.collection("reports").document()
            val budget = AtomicWriteBudget(db, reporter).plan(tx, WriteOperation.REPORT, reportRef)
            budget.applyTo(tx)
            tx.set(reportRef, mapOf("reporterUid" to reporter, "targetType" to type, "targetId" to targetId,
                "targetUid" to targetUid, "targetRef" to db.document(targetPath), "conversationId" to conversationId,
                "reason" to "harassment", "details" to "", "status" to "pending", "createdAt" to FieldValue.serverTimestamp()))
            Unit
        }
        auth.signOut()
        val customer = await(auth.createUserWithEmailAndPassword(customerEmail, password)).user!!
        val customerUid = customer.uid
        val customerRepo = MarketplaceRepository()
        val emergencySchedule = RequestSchedules.now()
        val requestId = customerRepo.createJobRequest(JobRequestEntity(title = "Android test boya",
            sector = "HOME_REPAIR", categoryId = "boyaci", district = "Buca", urgencyMode = "EMERGENCY",
            eventOrJobDate = emergencySchedule.date, eventTime = emergencySchedule.time, address = "Özel adres", status = "PENDING",
            customerName = "Customer", customerPhone = "05551234567",
            provinceId="tr_35",districtId="tr_35_buca",neighborhoodId="pilot_tr_35_buca_efeler",neighborhoodName="Efeler"))
        assertEquals(customerUid, await(db.collection("requests").document(requestId).get(Source.SERVER)).getString("ownerUid"))
        val savedRequest = await(db.collection("requests").document(requestId).get(Source.SERVER))
        assertEquals("EMERGENCY", savedRequest.getString("data.urgencyMode"))
        assertEquals(emergencySchedule.date, savedRequest.getString("data.eventOrJobDate"))
        assertEquals(emergencySchedule.time, savedRequest.getString("data.eventTime"))
        assertEquals("", await(db.collection("requests").document(requestId).get(Source.SERVER)).getString("data.address"))
        auth.signOut()
        assertNull(auth.currentUser)
        val providerUser = await(auth.createUserWithEmailAndPassword(providerEmail, password)).user!!
        val providerUid = providerUser.uid
        val providerRepo = MarketplaceRepository()
        try {
            // A repository tied to the previous UID must refuse writes after account switching.
            try { customerRepo.createJobRequest(JobRequestEntity(title="Forbidden", sector="HOME_REPAIR",
                categoryId="boyaci",district="Buca",urgencyMode="PLANNED",eventOrJobDate="",eventTime="",
                address="",status="PENDING",customerName="Customer",customerPhone="")); fail("Old UID allowed") }
            catch (_: IllegalStateException) { }
            assertPermissionDenied(db.collection("requestContacts").document(requestId).get(Source.SERVER))
            providerRepo.publishProviderListing(ServiceProviderEntity(id="",name="Provider",title="Boyacı",
                sector="HOME_REPAIR",categoryId="boyaci",rating=0.0,reviewCount=0,experienceYears=2,
                district="Buca",city="İzmir",hourlyOrBasePrice="1000 ₺",isEmergencyAvailable=false,
                verifiedSafeBadge=false,mykCertified=false,childSafeCertified=false,phone="05559999999",bio="Boya hizmeti",
                provinceId="tr_35",districtId="tr_35_buca",neighborhoodId="pilot_tr_35_buca_efeler",neighborhoodName="Efeler"))
            val provider = withTimeout(30_000) { providerRepo.getAllProviders().first { list -> list.any { it.ownerUid==providerUid } } }.first { it.ownerUid==providerUid }
            val quoteId=providerRepo.sendQuote(QuoteEntity(requestId=requestId,providerId=provider.id,
                providerName="Provider",providerTitle="Boyacı",providerRating=0.0,price="1000 ₺",
                durationOrArrival="1 gün",notes="Android SDK teklifi"))
            val convId=providerRepo.startOrGetConversation(customerUid,"Customer","","Boya")
            // Reopening an existing chat must not spend a new-conversation allowance.
            assertEquals(convId, providerRepo.startOrGetConversation(customerUid,"Customer","","Boya"))
            providerRepo.sendChatMessage(convId,"Ignored","Merhaba Android",true)
            val providerBudgets = db.collection("users").document(providerUid).collection("writeBudgets")
            listOf("listing", "quote", "conversation", "message").forEach { operation ->
                assertEquals(1L, await(providerBudgets.document(operation).get(Source.SERVER)).getLong("count"))
            }
            val sent=withTimeout(30_000) { providerRepo.getMessagesForConversation(convId).first { it.isNotEmpty() } }.single()
            assertEquals(providerUid,sent.senderId); assertTrue(sent.isFromMe)
            // A valid allowance does not let a sender falsely blame the other participant.
            assertPermissionDenied(reportWrite(providerUid,"message",sent.id,customerUid,
                "conversations/$convId/messages/${sent.id}",convId))
            assertFalse(await(providerBudgets.document("report").get(Source.SERVER)).exists())
            auth.signOut()
            // A third account cannot discover either party's offers or conversations,
            // even with known document IDs and direct SDK calls bypassing the UI.
            await(auth.createUserWithEmailAndPassword("outsider-$suffix@example.com",password))
            val outsiderUid = auth.currentUser!!.uid
            val outsiderRepo = MarketplaceRepository()
            // The previous participant already loaded this conversation into SDK memory.
            // A new account must never receive that cached private snapshot through the app.
            assertTrue(withTimeout(30_000) {
                outsiderRepo.getMessagesForConversation(convId).first()
            }.isEmpty())
            assertTrue(await(db.collection("quotes").whereEqualTo("customerUid",outsiderUid).get(Source.SERVER)).isEmpty)
            assertTrue(await(db.collection("quotes").whereEqualTo("providerUid",outsiderUid).get(Source.SERVER)).isEmpty)
            assertTrue(await(db.collection("conversations").whereArrayContains("participantUids",outsiderUid).get(Source.SERVER)).isEmpty)
            assertPermissionDenied(db.collection("quotes").document(quoteId).get(Source.SERVER))
            assertPermissionDenied(db.collection("conversations").document(convId).get(Source.SERVER))
            assertPermissionDenied(db.collection("conversations").document(convId).collection("messages").get(Source.SERVER))
            assertPermissionDenied(db.collection("requestContacts").document(requestId).get(Source.SERVER))
            assertPermissionDenied(reportWrite(outsiderUid,"conversation",convId,providerUid,"conversations/$convId"))
            assertPermissionDenied(reportWrite(outsiderUid,"message",sent.id,providerUid,
                "conversations/$convId/messages/${sent.id}",convId))
            assertFalse(await(db.document("users/$outsiderUid/writeBudgets/report").get(Source.SERVER)).exists())
            await(auth.currentUser!!.delete())
            auth.signOut()
            try { await(auth.signInWithEmailAndPassword(customerEmail,"wrong-password")); fail("Wrong password accepted") }
            catch (error: java.util.concurrent.ExecutionException) {
                assertTrue(error.cause is FirebaseAuthInvalidCredentialsException)
            }
            await(auth.signInWithEmailAndPassword(customerEmail,password))
            val reloaded = MarketplaceRepository()
            val quotes=withTimeout(30_000) { reloaded.getAllQuotes().first { it.any { q->q.id==quoteId } } }
            assertEquals(customerUid,quotes.first { it.id==quoteId }.customerUid)
            val received=withTimeout(30_000) { reloaded.getMessagesForConversation(convId).first { it.isNotEmpty() } }.single()
            assertFalse(received.isFromMe)
            val moderation = ModerationRepository()
            val reportId = moderation.submitReport(ReportDraft(ReportTargetType.CONVERSATION,convId,providerUid,ReportReason.HARASSMENT))
            val savedReport = await(db.document("reports/$reportId").get(Source.SERVER))
            assertEquals("conversations/$convId", savedReport.getDocumentReference("targetRef")!!.path)
            moderation.submitReport(ReportDraft(ReportTargetType.MESSAGE,received.id,providerUid,
                ReportReason.HARASSMENT,conversationId=convId))
            assertPermissionDenied(reportWrite(customerUid,"conversation",convId,outsiderUid,"conversations/$convId"))
            assertEquals(2L,await(db.document("users/$customerUid/writeBudgets/report").get(Source.SERVER)).getLong("count"))
            reloaded.acceptQuote(requestId,quoteId)
            assertEquals("ACCEPTED",await(db.collection("quotes").document(quoteId).get(Source.SERVER)).getString("status"))
            // Real Android SDK -> Functions -> private lifecycle -> Rules read-back.
            auth.signOut()
            await(auth.signInWithEmailAndPassword(providerEmail,password))
            val jobProvider = MarketplaceRepository()
            assertTrue(withTimeout(30_000) { jobProvider.getAssignedRequests().first { it.isNotEmpty() } }.any { it.id == requestId })
            val startActionId = java.util.UUID.randomUUID().toString()
            jobProvider.manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.START, 0, "", "", startActionId)
            jobProvider.manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.START, 0, "", "", startActionId)
            assertEquals(1L, await(db.collection("jobs").document(requestId).get(Source.SERVER)).getLong("version"))
            jobProvider.manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.SUBMIT_COMPLETION, 1,
                "Boya işi tamamlandı ve kontrol edildi.", "", java.util.UUID.randomUUID().toString())
            auth.signOut()
            await(auth.signInWithEmailAndPassword(customerEmail,password))
            val jobCustomer = MarketplaceRepository()
            jobCustomer.manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.REQUEST_REVISION, 2,
                "Bir duvarın son katı eksik, lütfen tamamlayın.", "", java.util.UUID.randomUUID().toString())
            auth.signOut()
            await(auth.signInWithEmailAndPassword(providerEmail,password))
            MarketplaceRepository().manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.SUBMIT_COMPLETION, 3,
                "Eksik son kat tamamlandı, yeniden kontrol edebilirsiniz.", "", java.util.UUID.randomUUID().toString())
            auth.signOut()
            await(auth.signInWithEmailAndPassword(customerEmail,password))
            val confirmedCustomer = MarketplaceRepository()
            confirmedCustomer.manageJob(requestId, com.batuhanduran.burada.data.model.JobAction.CONFIRM_COMPLETION, 4,
                "", "", java.util.UUID.randomUUID().toString())
            val finished = await(db.collection("requests").document(requestId).get(Source.SERVER))
            assertEquals("COMPLETED", finished.getString("data.status"))
            assertEquals("closed", finished.getString("visibility"))
            assertEquals(5, withTimeout(30_000) { confirmedCustomer.getJobEvents(requestId).first { it.size == 5 } }.size)
            confirmedCustomer.submitJobReview(requestId, 2, "Gerçek tamamlanan işin müşteri yorumu")
            confirmedCustomer.submitJobReview(requestId, 2, "Gerçek tamamlanan işin müşteri yorumu")
            val ratedProvider = await(db.collection("providers").document(provider.id).get(Source.SERVER))
            assertEquals(1L, ratedProvider.getLong("data.reviewCount"))
            assertEquals(2.0, ratedProvider.getDouble("data.rating")!!, 0.001)
            assertTrue(withTimeout(30_000) { confirmedCustomer.getReviewedRequestIds().first { requestId in it } }.contains(requestId))
            val actualReviews = withTimeout(30_000) { confirmedCustomer.getProviderReviews(provider.id, 20).first { it.isNotEmpty() } }
            assertEquals(1, actualReviews.size)
            assertEquals("Gerçek tamamlanan işin müşteri yorumu", actualReviews.single().comment)
            await(auth.currentUser!!.delete())
            await(auth.signInWithEmailAndPassword(providerEmail,password))
            assertEquals("COMPLETED", await(db.collection("requests").document(requestId).get(Source.SERVER)).getString("data.status"))
            assertEquals("Özel adres",await(db.collection("requestContacts").document(requestId).get(Source.SERVER)).getString("address"))
            await(auth.currentUser!!.delete())
        } finally { auth.signOut() }
    }
}
