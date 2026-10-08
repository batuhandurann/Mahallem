package com.batuhanduran.burada.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Simulates Firestore connectivity loss while switching Firebase Auth identities. */
@RunWith(AndroidJUnit4::class)
class OfflineAccountIsolationInstrumentedTest {
    @Test
    fun afterAccountSwitchOfflineSnapshotNeverContainsPreviousUsersPrivateRequests(): Unit = runBlocking {
        check(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            "Account isolation checks require isolated Firebase emulator."
        }
        val auth = FirebaseServices.auth
        val db = FirebaseServices.firestore
        val suffix = UUID.randomUUID().toString()
        val aliceEmail = "isolation-a-$suffix@example.com"
        val bobEmail = "isolation-b-$suffix@example.com"
        val password = "SecurePass123!"
        fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

        auth.signOut()
        val alice = await(auth.createUserWithEmailAndPassword(aliceEmail, password)).user!!
        val aliceRepo = MarketplaceRepository()
        val aliceRequestId = aliceRepo.createJobRequest(
            JobRequestEntity(
                title = "Private owner smoke",
                sector = "HOME_REPAIR",
                categoryId = "boyaci",
                district = "Buca",
                urgencyMode = "PLANNED",
                eventOrJobDate = "2026-10-10",
                eventTime = "12:00",
                address = "Owner-only test address",
                status = "PENDING",
                customerName = "Owner",
                customerPhone = "05551234567",
                provinceId = "tr_35",
                districtId = "tr_35_buca",
                neighborhoodId = "pilot_tr_35_buca_efeler",
                neighborhoodName = "Efeler"
            )
        )
        withTimeout(30_000) {
            aliceRepo.getMyRequests().first { requests -> requests.any { it.id == aliceRequestId } }
        }
        auth.signOut()
        await(db.disableNetwork())
        try {
            val bob = await(auth.createUserWithEmailAndPassword(bobEmail, password)).user!!
            val bobRepo = MarketplaceRepository()
            val offlineRequests = withTimeout(30_000) { bobRepo.getMyRequests().first() }
            assertTrue("Previous UID leaked through Firestore offline cache", offlineRequests.isEmpty())
            await(bob.delete())
        } finally {
            await(db.enableNetwork())
            auth.signOut()
            await(auth.signInWithEmailAndPassword(aliceEmail, password))
            await(auth.currentUser!!.delete())
            auth.signOut()
        }
    }
}
