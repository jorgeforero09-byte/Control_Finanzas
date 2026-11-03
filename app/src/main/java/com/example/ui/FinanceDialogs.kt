package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BankAccountRecord
import com.example.data.FinanceCategory
import com.example.data.TransactionType
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
    selectedMonth: String,
    bankAccounts: List<BankAccountRecord>,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        amount: Double,
        type: String,
        category: String,
        accountId: String,
        accountName: String,
        dateString: String,
        notes: String
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf(FinanceCategory.FOOD) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf(FinanceViewModel.todayDateString(selectedMonth)) }
    var notes by remember { mutableStateOf("") }

    val accountOptions = remember(bankAccounts) {
        val list = mutableListOf("CASH" to "Efectivo / Billetera")
        bankAccounts.forEach { acc ->
            list.add(acc.id.ifEmpty { acc.bankName } to "${acc.bankName} (${acc.maskedNumber})")
        }
        list
    }
    var selectedAccount by remember(accountOptions) { mutableStateOf(accountOptions.first()) }

    val quickAmounts = listOf(15.0, 50.0, 120.0, 300.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Registrar Movimiento por Categoría",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Expense vs Income segmented buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TransactionType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        val activeColor = if (type == TransactionType.EXPENSE) ExpenseCoral else IncomeEmerald
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedType = type
                                    val matchingCat = FinanceCategory.entries.firstOrNull { it.defaultType == type }
                                    if (matchingCat != null) selectedCategory = matchingCat
                                }
                                .testTag("type_selector_${type.name.lowercase()}"),
                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = type.labelEs,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Quick amount chips + numeric input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.forEach { preset ->
                        OutlinedButton(
                            onClick = { amountText = preset.toInt().toString() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+${preset.toInt()}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Concepto o Comercio (ej. Supermercado)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_title_input")
                )

                Text(
                    text = "Selecciona una Categoría:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FinanceCategory.entries.forEach { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
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
                            ),
                            modifier = Modifier.testTag("category_chip_${cat.key.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Cuenta o Medio de Pago:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountOptions.forEach { option ->
                        val isSel = selectedAccount.first == option.first
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedAccount = option },
                            label = { Text(option.second) },
                            leadingIcon = if (isSel) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                OutlinedTextField(
                    value = dateString,
                    onValueChange = { dateString = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas adicionales (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && parsedAmount > 0.0) {
                        onConfirm(
                            title,
                            parsedAmount,
                            selectedType.name,
                            selectedCategory.key,
                            selectedAccount.first,
                            selectedAccount.second,
                            dateString,
                            notes
                        )
                    }
                },
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text("Guardar Movimiento")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkBankAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        bankName: String,
        accountType: String,
        lastFourDigits: String,
        initialBalance: Double
    ) -> Unit
) {
    val presetBanks = listOf(
        "BBVA Digital",
        "Bancolombia",
        "NuBank",
        "Santander",
        "Citibank",
        "Banco de Bogotá"
    )
    var bankName by remember { mutableStateOf(presetBanks.first()) }
    var accountType by remember { mutableStateOf("CHECKING") }
    var lastFour by remember { mutableStateOf("4829") }
    var balanceText by remember { mutableStateOf("2500") }

    val accountTypes = listOf(
        "CHECKING" to "Cuenta Corriente / Nómina",
        "SAVINGS" to "Cuenta de Ahorros",
        "CREDIT_CARD" to "Tarjeta de Crédito",
        "WALLET" to "Billetera Digital"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Vincular Cuenta Bancaria",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Selecciona tu institución financiera o escríbela para activar la sincronización automática:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetBanks.forEach { preset ->
                        FilterChip(
                            selected = bankName == preset,
                            onClick = { bankName = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Nombre del Banco") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_name_input")
                )

                Text(
                    text = "Tipo de Producto:",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountTypes.forEach { (key, label) ->
                        FilterChip(
                            selected = accountType == key,
                            onClick = { accountType = key },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = lastFour,
                    onValueChange = { lastFour = it.take(4) },
                    label = { Text("Últimos 4 dígitos de la cuenta/tarjeta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_digits_input")
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Saldo Disponible Inicial ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_balance_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bal = balanceText.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (bankName.isNotBlank()) {
                        onConfirm(bankName, accountType, lastFour, bal)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_link_bank_button")
            ) {
                Text("Conectar y Sincronizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
