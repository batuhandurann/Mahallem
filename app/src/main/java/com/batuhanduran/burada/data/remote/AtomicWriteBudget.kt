package com.batuhanduran.burada.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction

/** These are also enforced by Firestore Rules. A client cannot bypass the limits. */
enum class WriteOperation(val id: String, val hourlyLimit: Long, val label: String) {
    LISTING("listing", 10, "İlan"),
    QUOTE("quote", 60, "Teklif"),
    CONVERSATION("conversation", 30, "Yeni sohbet"),
    MESSAGE("message", 240, "Mesaj"),
    REPORT("report", 10, "Şikayet")
}

class WriteQuotaExceededException(operation: WriteOperation) : IllegalStateException(
    "${operation.label} için saatlik sınırınıza ulaştınız. Bir saatlik kullanım penceresi dolunca yeniden deneyin."
)

internal data class BudgetIncrement(val count: Long, val resetWindow: Boolean)

internal fun nextBudgetIncrement(
    operation: WriteOperation, previousCount: Long?, windowStartedAtMillis: Long?, nowMillis: Long
): BudgetIncrement {
    if (previousCount == null && windowStartedAtMillis == null) return BudgetIncrement(1, true)
    check(previousCount != null && previousCount in 1..operation.hourlyLimit && windowStartedAtMillis != null) {
        "Kullanım sınırı bilgisi doğrulanamadı. Lütfen yeniden deneyin."
    }
    // This clock only proposes a rollover; Rules compare against server request.time.
    // Clock skew can therefore reject a write, never grant an unverified allowance.
    if (nowMillis >= windowStartedAtMillis && nowMillis - windowStartedAtMillis >= 3_600_000L) {
        return BudgetIncrement(1, true)
    }
    if (previousCount >= operation.hourlyLimit) throw WriteQuotaExceededException(operation)
    return BudgetIncrement(previousCount + 1, false)
}

/** Read every transaction dependency before applying the returned plan or any other writes. */
class AtomicWriteBudget(private val db: FirebaseFirestore, private val uid: String) {
    fun plan(transaction: Transaction, operation: WriteOperation, target: DocumentReference): Plan {
        val ref = db.collection("users").document(uid).collection("writeBudgets").document(operation.id)
        val snapshot = transaction.get(ref)
        val previousWindow = if (snapshot.exists()) snapshot.getTimestamp("windowStartedAt") else null
        val previousCount = if (snapshot.exists()) snapshot.getLong("count") else null
        check(!snapshot.exists() || (previousWindow != null && previousCount != null)) {
            "Kullanım sınırı bilgisi doğrulanamadı. Lütfen yeniden deneyin."
        }
        val increment = nextBudgetIncrement(operation, previousCount, previousWindow?.toDate()?.time, System.currentTimeMillis())
        return Plan(ref, mapOf(
            "count" to increment.count,
            "windowStartedAt" to if (increment.resetWindow) FieldValue.serverTimestamp() else requireNotNull(previousWindow),
            "updatedAt" to FieldValue.serverTimestamp(),
            "target" to target
        ))
    }

    class Plan internal constructor(private val ref: DocumentReference, private val fields: Map<String, Any>) {
        fun applyTo(transaction: Transaction) { transaction.set(ref, fields) }
    }
}
