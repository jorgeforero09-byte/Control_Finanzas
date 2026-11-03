package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

enum class TransactionType(val labelEs: String) {
    EXPENSE("Gasto"),
    INCOME("Ingreso")
}

enum class FinanceCategory(
    val key: String,
    val labelEs: String,
    val defaultType: TransactionType,
    val colorHex: Long
) {
    FOOD("FOOD", "Alimentación", TransactionType.EXPENSE, 0xFFF59E0B),
    HOUSING("HOUSING", "Vivienda y Hogar", TransactionType.EXPENSE, 0xFF3B82F6),
    TRANSPORT("TRANSPORT", "Transporte", TransactionType.EXPENSE, 0xFF8B5CF6),
    UTILITIES("UTILITIES", "Servicios y Facturas", TransactionType.EXPENSE, 0xFF06B6D4),
    ENTERTAINMENT("ENTERTAINMENT", "Ocio y Suscripciones", TransactionType.EXPENSE, 0xFFEC4899),
    HEALTH("HEALTH", "Salud y Bienestar", TransactionType.EXPENSE, 0xFFEF4444),
    SHOPPING("SHOPPING", "Compras", TransactionType.EXPENSE, 0xFFF97316),
    EDUCATION("EDUCATION", "Educación", TransactionType.EXPENSE, 0xFF6366F1),
    SALARY("SALARY", "Salario y Nómina", TransactionType.INCOME, 0xFF10B981),
    FREELANCE("FREELANCE", "Negocios / Extra", TransactionType.INCOME, 0xFF14B8A6),
    INVESTMENT("INVESTMENT", "Inversiones", TransactionType.INCOME, 0xFFEAB308),
    SAVINGS("SAVINGS", "Ahorro Programado", TransactionType.EXPENSE, 0xFF059669);

    companion object {
        fun fromKey(key: String): FinanceCategory {
            return entries.find { it.key == key } ?: FOOD
        }
    }
}

@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val category: String = "FOOD",
    val accountId: String = "CASH",
    val accountName: String = "Efectivo",
    val yearMonth: String = "2026-11",
    val dateString: String = "2026-11-02",
    val notes: String = "",
    val isBankSynced: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BankAccountRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bankName: String = "",
    val accountType: String = "CHECKING",
    val maskedNumber: String = "**** 0000",
    val balance: Double = 0.0,
    val currency: String = "USD",
    val autoSyncEnabled: Boolean = true,
    val externalInstitutionId: String = "inst_default",
    val lastSyncSummary: String = "Sincronizado",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CategoryBudgetRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "FOOD",
    val monthlyLimit: Double = 500.0,
    val yearMonth: String = "2026-11",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class MonthlySavingsSummary(
    val yearMonth: String,
    val labelShort: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsRatePercent: Double
)
