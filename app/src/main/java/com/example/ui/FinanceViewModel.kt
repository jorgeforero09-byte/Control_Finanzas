package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BankAccountRecord
import com.example.data.CategoryBudgetRecord
import com.example.data.FinanceRepository
import com.example.data.MonthlySavingsSummary
import com.example.data.TransactionRecord
import com.example.network.BankSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String,
    private val bankSyncService: BankSyncService = BankSyncService()
) : ViewModel() {

    private val availableMonths = listOf(
        "2026-06" to "Jun 26",
        "2026-07" to "Jul 26",
        "2026-08" to "Ago 26",
        "2026-09" to "Sep 26",
        "2026-10" to "Oct 26",
        "2026-11" to "Nov 26"
    )

    private val _selectedMonth = MutableStateFlow("2026-11")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isSyncingBanks = MutableStateFlow(false)
    val isSyncingBanks: StateFlow<Boolean> = _isSyncingBanks.asStateFlow()

    val transactionsState: StateFlow<UiState<List<TransactionRecord>>> = repository
        .observeTransactions(currentUserId)
        .map<List<TransactionRecord>, UiState<List<TransactionRecord>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing transactions", error)
            emit(UiState.Error(error.localizedMessage ?: "Error al cargar transacciones"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val bankAccountsState: StateFlow<UiState<List<BankAccountRecord>>> = repository
        .observeBankAccounts(currentUserId)
        .map<List<BankAccountRecord>, UiState<List<BankAccountRecord>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing bank accounts", error)
            emit(UiState.Error(error.localizedMessage ?: "Error al cargar cuentas bancarias"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val budgetsState: StateFlow<UiState<List<CategoryBudgetRecord>>> = repository
        .observeCategoryBudgets(currentUserId)
        .map<List<CategoryBudgetRecord>, UiState<List<CategoryBudgetRecord>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing budgets", error)
            emit(UiState.Error(error.localizedMessage ?: "Error al cargar presupuestos"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val monthlySummaries: StateFlow<List<MonthlySavingsSummary>> = transactionsState
        .map { state ->
            val allTx = (state as? UiState.Success)?.data.orEmpty()
            availableMonths.map { (ym, shortLabel) ->
                val monthTx = allTx.filter { it.yearMonth == ym }
                val income = monthTx.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expense = monthTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val netSavings = income - expense
                val rate = if (income > 0.0) ((netSavings / income) * 100.0).coerceIn(-100.0, 100.0) else 0.0
                MonthlySavingsSummary(
                    yearMonth = ym,
                    labelShort = shortLabel,
                    totalIncome = income,
                    totalExpense = expense,
                    netSavings = netSavings,
                    savingsRatePercent = rate
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val filteredMonthTransactions: StateFlow<List<TransactionRecord>> = combine(
        transactionsState,
        _selectedMonth,
        _selectedCategoryFilter
    ) { state, ym, catFilter ->
        val allTx = (state as? UiState.Success)?.data.orEmpty()
        allTx.filter { tx ->
            tx.yearMonth == ym && (catFilter == null || tx.category == catFilter)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    fun getAvailableMonths(): List<Pair<String, String>> = availableMonths

    fun selectMonth(yearMonth: String) {
        _selectedMonth.value = yearMonth
    }

    fun selectCategoryFilter(categoryKey: String?) {
        _selectedCategoryFilter.value = if (_selectedCategoryFilter.value == categoryKey) null else categoryKey
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        accountId: String,
        accountName: String,
        dateString: String,
        notes: String
    ) {
        if (title.isBlank() || amount <= 0.0) {
            _statusBanner.value = "Ingresa un concepto válido y un monto mayor a 0"
            return
        }
        val yearMonth = if (dateString.length >= 7) dateString.substring(0, 7) else _selectedMonth.value
        viewModelScope.launch {
            val result = repository.createTransaction(
                title = title,
                amount = amount,
                type = type,
                category = category,
                accountId = accountId,
                accountName = accountName,
                yearMonth = yearMonth,
                dateString = dateString,
                notes = notes,
                isBankSynced = false
            )
            if (result.isSuccess) {
                _statusBanner.value = "Movimiento registrado en $accountName"
            } else {
                _statusBanner.value = "No se pudo guardar: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
            _statusBanner.value = "Movimiento eliminado"
        }
    }

    fun linkNewBankAccount(
        bankName: String,
        accountType: String,
        lastFourDigits: String,
        initialBalance: Double,
        currency: String = "USD",
        autoSync: Boolean = true
    ) {
        val cleanDigits = lastFourDigits.filter { it.isDigit() }.takeLast(4).padStart(4, '0')
        val masked = "**** $cleanDigits"
        viewModelScope.launch {
            val res = repository.createBankAccount(
                bankName = bankName,
                accountType = accountType,
                maskedNumber = masked,
                balance = initialBalance,
                currency = currency,
                autoSyncEnabled = autoSync,
                externalInstitutionId = "openbank_${bankName.lowercase(Locale.ROOT).replace(" ", "_")}",
                lastSyncSummary = "Cuenta conectada • Lista para sincronizar"
            )
            if (res.isSuccess) {
                _statusBanner.value = "Cuenta $bankName ($masked) vinculada con éxito"
            } else {
                _statusBanner.value = "Error al vincular cuenta bancaria"
            }
        }
    }

    fun toggleBankAutoSync(account: BankAccountRecord, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBankAccountSyncState(
                account = account,
                newBalance = account.balance,
                autoSyncEnabled = enabled,
                syncSummary = if (enabled) "Auto-Sync activado" else "Auto-Sync en pausa"
            )
        }
    }

    fun deleteBankAccount(accountId: String) {
        viewModelScope.launch {
            repository.deleteBankAccount(accountId)
            _statusBanner.value = "Cuenta bancaria desvinculada"
        }
    }

    fun synchronizeAllBankAccountsNow() {
        val accounts = (bankAccountsState.value as? UiState.Success)?.data.orEmpty()
        val activeAccounts = accounts.filter { it.autoSyncEnabled }
        if (activeAccounts.isEmpty()) {
            _statusBanner.value = "Conecta o activa al menos una cuenta bancaria para sincronizar automáticamente"
            return
        }

        viewModelScope.launch {
            _isSyncingBanks.value = true
            var importedCount = 0
            val currentYm = _selectedMonth.value

            for (account in activeAccounts) {
                val (movements, syncSummary) = bankSyncService.fetchLiveSyncFeedForAccount(account, currentYm)
                var balanceDelta = 0.0
                for (mov in movements) {
                    val res = repository.createTransaction(
                        title = mov.title,
                        amount = mov.amount,
                        type = mov.type,
                        category = mov.category,
                        accountId = account.id.ifEmpty { "BANK" },
                        accountName = "${account.bankName} (${account.maskedNumber})",
                        yearMonth = mov.yearMonth,
                        dateString = mov.dateString,
                        notes = mov.notes,
                        isBankSynced = true
                    )
                    if (res.isSuccess) {
                        importedCount++
                        balanceDelta += if (mov.type == "INCOME") mov.amount else -mov.amount
                    }
                }
                repository.updateBankAccountSyncState(
                    account = account,
                    newBalance = account.balance + balanceDelta,
                    autoSyncEnabled = account.autoSyncEnabled,
                    syncSummary = syncSummary
                )
            }
            _isSyncingBanks.value = false
            _statusBanner.value = "Sincronización bancaria completada: $importedCount movimientos importados"
        }
    }

    fun populateSampleMultiMonthData() {
        viewModelScope.launch {
            _isSyncingBanks.value = true
            val currentAccounts = (bankAccountsState.value as? UiState.Success)?.data.orEmpty()
            var primaryAccountId = currentAccounts.firstOrNull()?.id ?: "BANK_MAIN"
            var primaryAccountName = currentAccounts.firstOrNull()?.bankName ?: "BBVA Nómina"

            if (currentAccounts.isEmpty()) {
                val createdAcc = repository.createBankAccount(
                    bankName = "BBVA Nómina Inteligente",
                    accountType = "CHECKING",
                    maskedNumber = "**** 4821",
                    balance = 4850.0,
                    currency = "USD",
                    autoSyncEnabled = true,
                    externalInstitutionId = "openbank_bbva",
                    lastSyncSummary = "Sincronización automática activa"
                )
                primaryAccountId = createdAcc.getOrNull() ?: "BANK_MAIN"
                primaryAccountName = "BBVA Nómina (**** 4821)"

                repository.createBankAccount(
                    bankName = "NuBank Cuenta Ahorro",
                    accountType = "SAVINGS",
                    maskedNumber = "**** 7740",
                    balance = 3200.0,
                    currency = "USD",
                    autoSyncEnabled = true,
                    externalInstitutionId = "openbank_nubank",
                    lastSyncSummary = "Rendimiento anual activo"
                )
            }

            val sampleMonths = listOf(
                Triple("2026-08", 3100.0, 2150.0),
                Triple("2026-09", 3250.0, 1980.0),
                Triple("2026-10", 3400.0, 2120.0),
                Triple("2026-11", 3600.0, 1890.0)
            )

            for ((ym, inc, exp) in sampleMonths) {
                repository.createTransaction(
                    title = "Salario Mensual Corporativo",
                    amount = inc,
                    type = "INCOME",
                    category = "SALARY",
                    accountId = primaryAccountId,
                    accountName = primaryAccountName,
                    yearMonth = ym,
                    dateString = "$ym-01",
                    notes = "Abono directo nómina",
                    isBankSynced = true
                )
                repository.createTransaction(
                    title = "Alquiler y Administración",
                    amount = (exp * 0.42),
                    type = "EXPENSE",
                    category = "HOUSING",
                    accountId = primaryAccountId,
                    accountName = primaryAccountName,
                    yearMonth = ym,
                    dateString = "$ym-05",
                    notes = "Pago mensual vivienda",
                    isBankSynced = true
                )
                repository.createTransaction(
                    title = "Supermercado y Alimentación",
                    amount = (exp * 0.25),
                    type = "EXPENSE",
                    category = "FOOD",
                    accountId = primaryAccountId,
                    accountName = primaryAccountName,
                    yearMonth = ym,
                    dateString = "$ym-10",
                    notes = "Compras quincenales",
                    isBankSynced = false
                )
                repository.createTransaction(
                    title = "Transporte y Combustible",
                    amount = (exp * 0.13),
                    type = "EXPENSE",
                    category = "TRANSPORT",
                    accountId = primaryAccountId,
                    accountName = primaryAccountName,
                    yearMonth = ym,
                    dateString = "$ym-14",
                    notes = "Movilidad mensual",
                    isBankSynced = true
                )
                repository.createTransaction(
                    title = "Fondo Ahorro Programado",
                    amount = (exp * 0.20),
                    type = "EXPENSE",
                    category = "SAVINGS",
                    accountId = primaryAccountId,
                    accountName = primaryAccountName,
                    yearMonth = ym,
                    dateString = "$ym-20",
                    notes = "Transferencia automática a inversión",
                    isBankSynced = true
                )
            }

            repository.setCategoryBudget("FOOD", 600.0, "2026-11")
            repository.setCategoryBudget("HOUSING", 900.0, "2026-11")
            repository.setCategoryBudget("TRANSPORT", 300.0, "2026-11")

            _isSyncingBanks.value = false
            _statusBanner.value = "Historial multimensual, cuentas y presupuestos cargados"
        }
    }

    fun updateCategoryBudget(category: String, monthlyLimit: Double) {
        if (monthlyLimit <= 0.0) return
        viewModelScope.launch {
            val res = repository.setCategoryBudget(
                category = category,
                monthlyLimit = monthlyLimit,
                yearMonth = _selectedMonth.value
            )
            if (res.isSuccess) {
                _statusBanner.value = "Meta mensual actualizada"
            }
        }
    }

    companion object {
        private const val TAG = "FinanceViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun todayDateString(selectedMonth: String): String {
            val cal = Calendar.getInstance()
            val day = SimpleDateFormat("dd", Locale.US).format(cal.time)
            return "$selectedMonth-$day"
        }
    }
}
