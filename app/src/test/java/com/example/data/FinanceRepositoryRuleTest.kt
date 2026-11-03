package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveTransaction_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FinanceRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTransaction(
                title = "Compra Supermercado",
                amount = 85.50,
                type = "EXPENSE",
                category = "FOOD",
                accountId = "acc_1",
                accountName = "Cuenta Principal",
                yearMonth = "2026-11",
                dateString = "2026-11-02",
                notes = "Mercado mensual",
                isBankSynced = false
            )
        }
        assertTrue("Expected transaction creation to succeed", createResult.isSuccess)
        val txId = createResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTransactions(aliceUid).first { list -> list.any { it.id == txId } }
        }
        assertTrue(emitted.any { it.id == txId && it.title == "Compra Supermercado" })
    }

    @Test
    fun getTransactionById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FinanceRepository(firestore, auth)
        val txId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTransaction(
                title = "Secreto Alice",
                amount = 200.0,
                type = "INCOME",
                category = "SALARY",
                accountId = "acc_1",
                accountName = "Banco",
                yearMonth = "2026-11",
                dateString = "2026-11-02",
                notes = "",
                isBankSynced = true
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FinanceRepository(firestore, auth)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getTransactionById(txId, ownerUid = aliceUid)
        }
        assertTrue("Expected cross-user read to fail", crossResult.isFailure)
        val ex = crossResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", ex)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = FinanceRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTransactions(aliceUid).first()
            }
            fail("Expected exception when unauthenticated user observes transactions")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected wrapped FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun createBankAccountAndBudget_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FinanceRepository(firestore, auth)

        val bankResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBankAccount(
                bankName = "Bancolombia",
                accountType = "SAVINGS",
                maskedNumber = "**** 9182",
                balance = 1500.0,
                currency = "USD",
                autoSyncEnabled = true
            )
        }
        assertTrue(bankResult.isSuccess)

        val budgetResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.setCategoryBudget(
                category = "FOOD",
                monthlyLimit = 450.0,
                yearMonth = "2026-11"
            )
        }
        assertTrue(budgetResult.isSuccess)

        val accounts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBankAccounts(aliceUid).first { it.isNotEmpty() }
        }
        assertTrue(accounts.any { it.bankName == "Bancolombia" })
    }

    private companion object {
        const val ALICE_EMAIL = "alice@finanzapro.test"
        const val BOB_EMAIL = "bob@finanzapro.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
