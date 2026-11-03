package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    fun observeTransactions(userId: String = requireUserId()): Flow<List<TransactionRecord>> {
        val path = "users/$userId/transactions"
        return db.collection("users").document(userId).collection("transactions")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    TransactionRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.dateString }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeBankAccounts(userId: String = requireUserId()): Flow<List<BankAccountRecord>> {
        val path = "users/$userId/bank_accounts"
        return db.collection("users").document(userId).collection("bank_accounts")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BankAccountRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeCategoryBudgets(userId: String = requireUserId()): Flow<List<CategoryBudgetRecord>> {
        val path = "users/$userId/budgets"
        return db.collection("users").document(userId).collection("budgets")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CategoryBudgetRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        accountId: String,
        accountName: String,
        yearMonth: String,
        dateString: String,
        notes: String,
        isBankSynced: Boolean
    ): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("transactions")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "amount" to amount,
            "type" to type,
            "category" to category,
            "accountId" to accountId,
            "accountName" to accountName.trim().take(100),
            "yearMonth" to yearMonth,
            "dateString" to dateString,
            "notes" to notes.trim().take(500),
            "isBankSynced" to isBankSynced,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getTransactionById(transactionId: String, ownerUid: String = requireUserId()): Result<TransactionRecord?> {
        val docRef = db.collection("users").document(ownerUid).collection("transactions").document(transactionId)
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toObject(TransactionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("transactions").document(transactionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createBankAccount(
        bankName: String,
        accountType: String,
        maskedNumber: String,
        balance: Double,
        currency: String = "USD",
        autoSyncEnabled: Boolean = true,
        externalInstitutionId: String = "inst_openbanking",
        lastSyncSummary: String = "Vinculada hoy"
    ): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("bank_accounts")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "bankName" to bankName.trim().take(100),
            "accountType" to accountType,
            "maskedNumber" to maskedNumber.trim().take(30),
            "balance" to balance,
            "currency" to currency,
            "autoSyncEnabled" to autoSyncEnabled,
            "externalInstitutionId" to externalInstitutionId,
            "lastSyncSummary" to lastSyncSummary.take(250),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateBankAccountSyncState(
        account: BankAccountRecord,
        newBalance: Double,
        autoSyncEnabled: Boolean,
        syncSummary: String
    ): Result<Unit> {
        val uid = requireUserId()
        require(account.id.isNotBlank()) { "Account ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("bank_accounts").document(account.id)
        val updates = mapOf(
            "balance" to newBalance,
            "autoSyncEnabled" to autoSyncEnabled,
            "lastSyncSummary" to syncSummary.take(250),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBankAccount(accountId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("bank_accounts").document(accountId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setCategoryBudget(
        category: String,
        monthlyLimit: Double,
        yearMonth: String
    ): Result<String> {
        val uid = requireUserId()
        val budgetDocId = "${category}_${yearMonth}_${System.currentTimeMillis()}".replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        val docRef = db.collection("users").document(uid).collection("budgets").document(budgetDocId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "category" to category,
                    "monthlyLimit" to monthlyLimit,
                    "yearMonth" to yearMonth,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(budgetDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
