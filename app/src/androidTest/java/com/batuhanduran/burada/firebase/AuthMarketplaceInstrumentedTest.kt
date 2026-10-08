package com.batuhanduran.burada.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import com.batuhanduran.burada.validation.RequestSchedules
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
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
            providerRepo.sendChatMessage(convId,"Ignored","Merhaba Android",true)
            val sent=withTimeout(30_000) { providerRepo.getMessagesForConversation(convId).first { it.isNotEmpty() } }.single()
            assertEquals(providerUid,sent.senderId); assertTrue(sent.isFromMe)
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
            reloaded.acceptQuote(requestId,quoteId)
            assertEquals("ACCEPTED",await(db.collection("quotes").document(quoteId).get(Source.SERVER)).getString("status"))
            await(auth.currentUser!!.delete())
            await(auth.signInWithEmailAndPassword(providerEmail,password))
            assertEquals("Özel adres",await(db.collection("requestContacts").document(requestId).get(Source.SERVER)).getString("address"))
            await(auth.currentUser!!.delete())
        } finally { auth.signOut() }
    }
}
