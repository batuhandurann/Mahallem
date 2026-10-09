package com.batuhanduran.burada.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.remote.AtomicWriteBudget
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.remote.WriteOperation
import com.batuhanduran.burada.data.remote.WriteQuotaExceededException
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import com.batuhanduran.burada.moderation.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Actual Android SDK transactions against emulator Rules; quota fixtures never contact production. */
@RunWith(AndroidJUnit4::class)
class WriteBudgetInstrumentedTest {
    @Test fun createCapsAtomicRollbackAndBudgetAccountIsolation(): Unit = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS)
        val auth = FirebaseServices.auth
        val db = FirebaseServices.firestore
        val suffix = UUID.randomUUID().toString()
        val email = "budget-$suffix@example.com"
        val otherEmail = "budget-other-$suffix@example.com"
        val password = "SecurePass123!"
        fun <T> await(task: Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)
        fun denied(task: Task<*>) {
            try { await(task); fail("Rules allowed an unauthorized transaction") }
            catch (error: ExecutionException) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (error.cause as? FirebaseFirestoreException)?.code)
            }
        }
        auth.signOut()
        val uid = await(auth.createUserWithEmailAndPassword(email, password)).user!!.uid
        val budgets = db.collection("users").document(uid).collection("writeBudgets")
        val moderation = ModerationRepository()
        val marketplace = MarketplaceRepository()
        try {
            // A rejected target must roll back its paired allowance as well.
            val invalid = db.collection("reports").document()
            denied(db.runTransaction { tx ->
                val budget = AtomicWriteBudget(db, uid).plan(tx, WriteOperation.REPORT, invalid)
                budget.applyTo(tx)
                tx.set(invalid, mapOf("reporterUid" to uid, "targetType" to "user", "targetId" to "target-user",
                    "targetUid" to "target-user", "targetRef" to db.document("users/target-user"),
                    "conversationId" to "", "reason" to "spam", "details" to "", "status" to "resolved",
                    "createdAt" to FieldValue.serverTimestamp()))
            })
            assertFalse(await(budgets.document("report").get(Source.SERVER)).exists())
            assertTrue(await(db.collection("reports").whereEqualTo("reporterUid", uid).get(Source.SERVER)).isEmpty)
            repeat(10) {
                moderation.submitReport(ReportDraft(ReportTargetType.USER, "target-user", "target-user", ReportReason.SPAM))
            }
            try {
                moderation.submitReport(ReportDraft(ReportTargetType.USER, "target-user", "target-user", ReportReason.SPAM))
                fail("Report hourly cap exceeded")
            } catch (_: WriteQuotaExceededException) { }
            assertEquals(10L, await(budgets.document("report").get(Source.SERVER)).getLong("count"))
            assertEquals(10, await(db.collection("reports").whereEqualTo("reporterUid", uid).get(Source.SERVER)).size())

            // Provider and customer listings share one allowance, not ten each.
            marketplace.publishProviderListing(ServiceProviderEntity(id="",name="Provider",title="Boyacı",
                sector="HOME_REPAIR",categoryId="boyaci",rating=0.0,reviewCount=0,experienceYears=2,
                district="Buca",city="İzmir",hourlyOrBasePrice="1000 ₺",isEmergencyAvailable=false,
                verifiedSafeBadge=false,mykCertified=false,childSafeCertified=false,phone="05559999999",bio="Boya hizmeti",
                provinceId="tr_35",districtId="tr_35_buca",neighborhoodId="pilot_tr_35_buca_efeler",neighborhoodName="Efeler"))
            val request = JobRequestEntity(title="Boya bütçe testi",sector="HOME_REPAIR",categoryId="boyaci",district="Buca",
                urgencyMode="PLANNED",eventOrJobDate="2027-01-10",eventTime="10:00",address="Özel adres",status="PENDING",
                customerName="Customer",customerPhone="05551234567",provinceId="tr_35",districtId="tr_35_buca",
                neighborhoodId="pilot_tr_35_buca_efeler",neighborhoodName="Efeler")
            repeat(9) { marketplace.createJobRequest(request) }
            try { marketplace.createJobRequest(request); fail("Shared listing cap exceeded") }
            catch (_: WriteQuotaExceededException) { }
            assertEquals(10L, await(budgets.document("listing").get(Source.SERVER)).getLong("count"))
            assertEquals(9, await(db.collection("requests").whereEqualTo("ownerUid", uid).get(Source.SERVER)).size())

            auth.signOut()
            val otherUid = await(auth.createUserWithEmailAndPassword(otherEmail, password)).user!!.uid
            denied(budgets.document("report").get(Source.SERVER))
            try {
                moderation.submitReport(ReportDraft(ReportTargetType.USER, "target-user", "target-user", ReportReason.SPAM))
                fail("Previous account repository reused")
            } catch (error: IllegalStateException) { assertFalse(error is WriteQuotaExceededException) }
            ModerationRepository().submitReport(ReportDraft(ReportTargetType.USER, uid, uid, ReportReason.SPAM))
            assertEquals(1L, await(db.collection("users").document(otherUid).collection("writeBudgets").document("report").get(Source.SERVER)).getLong("count"))
            await(auth.currentUser!!.delete())
            await(auth.signInWithEmailAndPassword(email, password))
            assertEquals(10L, await(budgets.document("report").get(Source.SERVER)).getLong("count"))
            await(auth.currentUser!!.delete())
        } finally { auth.signOut() }
    }
}
