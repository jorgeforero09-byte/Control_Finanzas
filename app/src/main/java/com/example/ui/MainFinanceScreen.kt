package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.BankAccountRecord
import com.example.data.FinanceCategory
import com.example.data.FinanceRepository
import com.example.data.MonthlySavingsSummary
import com.example.data.TransactionRecord
import com.example.export.ExcelExportHelper
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.SavingsGold
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFinanceScreen(
    currentUserId: String,
    userEmail: String?,
    onSignOutClick: () -> Unit
) {
    val viewModel: FinanceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FinanceViewModel(FinanceRepository(db), currentUserId)
            }
        }
    )

    val context = LocalContext.current
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val bankAccountsState by viewModel.bankAccountsState.collectAsStateWithLifecycle()
    val budgetsState by viewModel.budgetsState.collectAsStateWithLifecycle()
    val monthlySummaries by viewModel.monthlySummaries.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredMonthTransactions.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isSyncingBanks by viewModel.isSyncingBanks.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showLinkBankDialog by remember { mutableStateOf(false) }

    if (currentTab != 0) {
        BackHandler {
            currentTab = 0
        }
    }

    val allTransactions = (transactionsState as? UiState.Success)?.data.orEmpty()
    val bankAccounts = (bankAccountsState as? UiState.Success)?.data.orEmpty()
    val budgets = (budgetsState as? UiState.Success)?.data.orEmpty()
    val currentMonthSummary = monthlySummaries.find { it.yearMonth == selectedMonth }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            val res = ExcelExportHelper.writeExcelXmlToUri(
                context = context,
                uri = uri,
                selectedMonth = selectedMonth,
                transactions = allTransactions,
                monthlySummaries = monthlySummaries,
                bankAccounts = bankAccounts
            )
            if (res.isSuccess) {
                viewModel.showBanner("Archivo Excel (.xls) descargado correctamente")
            } else {
                viewModel.showBanner("Error al exportar Excel: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val res = ExcelExportHelper.writeCsvToUri(
                context = context,
                uri = uri,
                transactions = allTransactions
            )
            if (res.isSuccess) {
                viewModel.showBanner("Archivo CSV compatible con Excel guardado")
            } else {
                viewModel.showBanner("Error al exportar CSV: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            selectedMonth = selectedMonth,
            bankAccounts = bankAccounts,
            onDismiss = { showAddTransactionDialog = false },
            onConfirm = { title, amount, type, category, accountId, accountName, dateStr, notes ->
                viewModel.addTransaction(title, amount, type, category, accountId, accountName, dateStr, notes)
                showAddTransactionDialog = false
            }
        )
    }

    if (showLinkBankDialog) {
        LinkBankAccountDialog(
            onDismiss = { showLinkBankDialog = false },
            onConfirm = { bankName, accountType, lastFour, balance ->
                viewModel.linkNewBankAccount(bankName, accountType, lastFour, balance)
                showLinkBankDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FinanzaPro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.synchronizeAllBankAccountsNow() },
                        enabled = !isSyncingBanks,
                        modifier = Modifier.testTag("top_sync_banks_button")
                    ) {
                        if (isSyncingBanks) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(R.string.btn_sync_banks)
                            )
                        }
                    }
                    IconButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out_label)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTransactionDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_add_transaction)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_transaction")
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(
                            if (currentTab == 0) Icons.Default.PieChart else Icons.Outlined.PieChart,
                            contentDescription = stringResource(R.string.tab_dashboard)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_dashboard)) },
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        Icon(
                            if (currentTab == 1) Icons.Default.AutoGraph else Icons.Outlined.Savings,
                            contentDescription = stringResource(R.string.tab_charts)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_charts)) },
                    modifier = Modifier.testTag("nav_tab_charts")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = {
                        Icon(
                            if (currentTab == 2) Icons.Default.AccountBalance else Icons.Outlined.AccountBalance,
                            contentDescription = stringResource(R.string.tab_banks)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_banks)) },
                    modifier = Modifier.testTag("nav_tab_banks")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = {
                        Icon(
                            if (currentTab == 3) Icons.Default.Download else Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.tab_export)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_export)) },
                    modifier = Modifier.testTag("nav_tab_export")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                // Status Banner
                AnimatedVisibility(visible = statusBanner != null) {
                    statusBanner?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearBanner() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar aviso",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Month selector bar
                MonthSelectorRow(
                    months = viewModel.getAvailableMonths(),
                    selectedMonth = selectedMonth,
                    onSelectMonth = { viewModel.selectMonth(it) }
                )

                when (currentTab) {
                    0 -> DashboardTabContent(
                        summary = currentMonthSummary,
                        transactions = filteredTransactions,
                        allTransactionsEmpty = allTransactions.isEmpty(),
                        selectedCategoryFilter = selectedCategoryFilter,
                        onSelectCategoryFilter = { viewModel.selectCategoryFilter(it) },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onPopulateSampleData = { viewModel.populateSampleMultiMonthData() },
                        onSyncBanksClick = { viewModel.synchronizeAllBankAccountsNow() }
                    )

                    1 -> MonthlyChartsTabContent(
                        summaries = monthlySummaries,
                        selectedMonth = selectedMonth,
                        onSelectMonth = { viewModel.selectMonth(it) },
                        monthTransactions = allTransactions.filter { it.yearMonth == selectedMonth },
                        budgets = budgets.filter { it.yearMonth == selectedMonth },
                        selectedCategoryFilter = selectedCategoryFilter,
                        onSelectCategoryFilter = { viewModel.selectCategoryFilter(it) },
                        onPopulateSampleData = { viewModel.populateSampleMultiMonthData() }
                    )

                    2 -> BankSyncTabContent(
                        bankAccounts = bankAccounts,
                        isSyncing = isSyncingBanks,
                        onLinkBankClick = { showLinkBankDialog = true },
                        onSyncNowClick = { viewModel.synchronizeAllBankAccountsNow() },
                        onToggleAutoSync = { acc, enabled -> viewModel.toggleBankAutoSync(acc, enabled) },
                        onDeleteBank = { viewModel.deleteBankAccount(it) },
                        onPopulateSampleData = { viewModel.populateSampleMultiMonthData() }
                    )

                    3 -> ExcelExportTabContent(
                        selectedMonth = selectedMonth,
                        transactionsCount = allTransactions.size,
                        bankAccountsCount = bankAccounts.size,
                        currentSummary = currentMonthSummary,
                        onDownloadExcelClick = {
                            excelLauncher.launch("FinanzaPro_Reporte_$selectedMonth.xls")
                        },
                        onDownloadCsvClick = {
                            csvLauncher.launch("FinanzaPro_Movimientos_$selectedMonth.csv")
                        },
                        onShareSummaryClick = {
                            ExcelExportHelper.shareSummaryTextIntent(
                                context = context,
                                selectedMonth = selectedMonth,
                                monthlySummary = currentMonthSummary,
                                transactions = filteredTransactions
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelectorRow(
    months: List<Pair<String, String>>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        months.forEach { (ym, label) ->
            val selected = ym == selectedMonth
            FilterChip(
                selected = selected,
                onClick = { onSelectMonth(ym) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.testTag("month_chip_$ym")
            )
        }
    }
}

@Composable
private fun DashboardTabContent(
    summary: MonthlySavingsSummary?,
    transactions: List<TransactionRecord>,
    allTransactionsEmpty: Boolean,
    selectedCategoryFilter: String?,
    onSelectCategoryFilter: (String?) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onPopulateSampleData: () -> Unit,
    onSyncBanksClick: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val income = summary?.totalIncome ?: 0.0
    val expense = summary?.totalExpense ?: 0.0
    val netSavings = summary?.netSavings ?: 0.0
    val rate = summary?.savingsRatePercent ?: 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Wealth & Savings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_savings_card"),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF064E3B),
                                    Color(0xFF047857),
                                    Color(0xFF065F46)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ahorro Neto del Mes (${summary?.labelShort ?: ""})",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFA7F3D0)
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFFBBF24).copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "Tasa Ahorro: ${String.format(Locale.US, "%.1f", rate)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currency.format(netSavings),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Ingresos",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFD1FAE5)
                                        )
                                        Text(
                                            text = currency.format(income),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFFDA4AF)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Gastos",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFFD1FAE5)
                                        )
                                        Text(
                                            text = currency.format(expense),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Interactive Filter Bar
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtrar por Categoría",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedCategoryFilter != null) {
                        Text(
                            text = "Ver todas",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSelectCategoryFilter(null) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FinanceCategory.entries.forEach { cat ->
                        val isSel = selectedCategoryFilter == cat.key
                        FilterChip(
                            selected = isSel,
                            onClick = { onSelectCategoryFilter(cat.key) },
                            label = { Text(cat.labelEs) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(cat.colorHex))
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(cat.colorHex).copy(alpha = 0.22f)
                            )
                        )
                    }
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sin movimientos en esta vista",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Registra un gasto por categoría con el botón inferior, sincroniza tus cuentas bancarias o carga un historial multimensual de demostración.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (allTransactionsEmpty) {
                                Button(
                                    onClick = onPopulateSampleData,
                                    modifier = Modifier.testTag("load_sample_data_button")
                                ) {
                                    Text("Cargar Datos Ejemplo")
                                }
                            }
                            OutlinedButton(onClick = onSyncBanksClick) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sincronizar Bancos")
                            }
                        }
                    }
                }
            }
        } else {
            items(transactions, key = { it.id.ifEmpty { it.title + it.dateString } }) { tx ->
                TransactionRowItem(
                    transaction = tx,
                    onDelete = { onDeleteTransaction(tx.id) }
                )
            }
        }
    }
}

@Composable
private fun TransactionRowItem(
    transaction: TransactionRecord,
    onDelete: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val cat = FinanceCategory.fromKey(transaction.category)
    val isIncome = transaction.type == "INCOME"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(cat.colorHex).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(cat.colorHex))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (transaction.isBankSynced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Banco",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${cat.labelEs} • ${transaction.accountName} • ${transaction.dateString}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncome) "+" else "-") + currency.format(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) IncomeEmerald else ExpenseCoral
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar movimiento",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyChartsTabContent(
    summaries: List<MonthlySavingsSummary>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit,
    monthTransactions: List<TransactionRecord>,
    budgets: List<com.example.data.CategoryBudgetRecord>,
    selectedCategoryFilter: String?,
    onSelectCategoryFilter: (String?) -> Unit,
    onPopulateSampleData: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MonthlySavingsBarChartCard(
                summaries = summaries,
                selectedMonth = selectedMonth,
                onSelectMonth = onSelectMonth
            )
        }

        item {
            CategoryDonutAndBudgetCard(
                transactions = monthTransactions,
                budgets = budgets,
                selectedCategoryKey = selectedCategoryFilter,
                onSelectCategory = onSelectCategoryFilter
            )
        }

        if (monthTransactions.isEmpty()) {
            item {
                OutlinedButton(
                    onClick = onPopulateSampleData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generar Historial Multimensual para Visualizar Gráficos")
                }
            }
        }
    }
}

@Composable
private fun BankSyncTabContent(
    bankAccounts: List<BankAccountRecord>,
    isSyncing: Boolean,
    onLinkBankClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onToggleAutoSync: (BankAccountRecord, Boolean) -> Unit,
    onDeleteBank: (String) -> Unit,
    onPopulateSampleData: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sincronización Bancaria Automática",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Importa y categoriza tus ingresos, pagos con tarjeta y apartados de ahorro automáticamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLinkBankClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("link_bank_account_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Vincular Banco")
                        }

                        OutlinedButton(
                            onClick = onSyncNowClick,
                            enabled = !isSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_all_banks_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSyncing) "Sincronizando..." else "Sincronizar Ya")
                        }
                    }
                }
            }
        }

        if (bankAccounts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No tienes cuentas bancarias vinculadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Conecta tu banco (BBVA, Bancolombia, NuBank, Santander, etc.) para sincronizar saldos y gastos automáticamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(onClick = onPopulateSampleData) {
                            Text("Conectar Cuentas de Ejemplo")
                        }
                    }
                }
            }
        } else {
            items(bankAccounts, key = { it.id.ifEmpty { it.bankName } }) { acc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_account_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = acc.bankName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${acc.accountType} • ${acc.maskedNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = currency.format(acc.balance),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeEmerald
                                )
                                Text(
                                    text = acc.currency,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (acc.autoSyncEnabled) IncomeEmerald else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = acc.lastSyncSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Sync",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = acc.autoSyncEnabled,
                                    onCheckedChange = { onToggleAutoSync(acc, it) }
                                )
                                IconButton(onClick = { onDeleteBank(acc.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Desvincular cuenta",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcelExportTabContent(
    selectedMonth: String,
    transactionsCount: Int,
    bankAccountsCount: Int,
    currentSummary: MonthlySavingsSummary?,
    onDownloadExcelClick: () -> Unit,
    onDownloadCsvClick: () -> Unit,
    onShareSummaryClick: () -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("excel_export_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(IncomeEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = IncomeEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Exportar a Microsoft Excel",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Libro multi-hoja (.xls) y CSV con codificación UTF-8",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Contenido del Libro Excel a Descargar:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Hoja 1 (Movimientos_$selectedMonth): $transactionsCount registros detallados por categoría y banco",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Hoja 2 (Ahorros_Mensuales): Comparativa de ingresos, gastos, ahorro neto (${currency.format(currentSummary?.netSavings ?: 0.0)}) y tasa %",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Hoja 3 (Cuentas_Bancarias): $bankAccountsCount cuentas conectadas y estado de sincronización",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDownloadExcelClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("download_excel_xls_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeEmerald)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.btn_export_excel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF022C22)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onDownloadCsvClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("download_excel_csv_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.btn_export_csv),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onShareSummaryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("share_summary_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.btn_share_summary),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
