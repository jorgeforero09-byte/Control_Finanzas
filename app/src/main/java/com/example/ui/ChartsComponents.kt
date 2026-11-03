package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CategoryBudgetRecord
import com.example.data.FinanceCategory
import com.example.data.MonthlySavingsSummary
import com.example.data.TransactionRecord
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.SavingsGold
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MonthlySavingsBarChartCard(
    summaries: List<MonthlySavingsSummary>,
    selectedMonth: String,
    onSelectMonth: (String) -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val maxVal = (summaries.maxOfOrNull { maxOf(it.totalIncome, it.totalExpense, 100.0) } ?: 1000.0).coerceAtLeast(100.0)
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "barChartAnim"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_savings_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Evolución Mensual de Ingresos, Gastos y Ahorro",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toca cualquier mes para inspeccionar el desglose detallado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = IncomeEmerald, label = "Ingresos")
                LegendDot(color = ExpenseCoral, label = "Gastos")
                LegendDot(color = SavingsGold, label = "Ahorro Neto")
            }

            Spacer(modifier = Modifier.height(18.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                if (summaries.isEmpty()) return@Canvas
                val groupWidth = size.width / summaries.size
                val barWidth = (groupWidth * 0.26f).coerceAtMost(28f)
                val chartHeight = size.height * 0.84f

                // Grid lines
                for (i in 0..3) {
                    val y = chartHeight * (i / 3f)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                val savingsPoints = mutableListOf<Offset>()

                summaries.forEachIndexed { index, summary ->
                    val centerX = groupWidth * index + groupWidth / 2f
                    val incHeight = ((summary.totalIncome / maxVal).toFloat() * chartHeight * animProgress).coerceIn(4f, chartHeight)
                    val expHeight = ((summary.totalExpense / maxVal).toFloat() * chartHeight * animProgress).coerceIn(4f, chartHeight)

                    // Highlight selected month pill
                    if (summary.yearMonth == selectedMonth) {
                        drawRoundRect(
                            color = IncomeEmerald.copy(alpha = 0.12f),
                            topLeft = Offset(centerX - groupWidth * 0.42f, 0f),
                            size = Size(groupWidth * 0.84f, chartHeight + 8f),
                            cornerRadius = CornerRadius(18f, 18f)
                        )
                    }

                    // Income Bar
                    drawRoundRect(
                        color = IncomeEmerald,
                        topLeft = Offset(centerX - barWidth - 3f, chartHeight - incHeight),
                        size = Size(barWidth, incHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    // Expense Bar
                    drawRoundRect(
                        color = ExpenseCoral,
                        topLeft = Offset(centerX + 3f, chartHeight - expHeight),
                        size = Size(barWidth, expHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )

                    val netNormalized = (summary.netSavings.coerceAtLeast(0.0) / maxVal).toFloat()
                    val pointY = chartHeight - (netNormalized * chartHeight * animProgress)
                    savingsPoints.add(Offset(centerX, pointY.coerceIn(8f, chartHeight - 4f)))
                }

                // Savings trend line
                if (savingsPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(savingsPoints.first().x, savingsPoints.first().y)
                        for (i in 1 until savingsPoints.size) {
                            lineTo(savingsPoints[i].x, savingsPoints[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = SavingsGold,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    savingsPoints.forEach { pt ->
                        drawCircle(
                            color = SavingsGold,
                            radius = 7f,
                            center = pt
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                summaries.forEach { summary ->
                    val isSelected = summary.yearMonth == selectedMonth
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectMonth(summary.yearMonth) }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = summary.labelShort,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.0f", summary.savingsRatePercent)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (summary.netSavings >= 0) IncomeEmerald else ExpenseCoral
                        )
                    }
                }
            }

            val currentSummary = summaries.find { it.yearMonth == selectedMonth }
            if (currentSummary != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ahorro Neto (${currentSummary.labelShort})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currency.format(currentSummary.netSavings),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (currentSummary.netSavings >= 0) IncomeEmerald else ExpenseCoral,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Tasa de Ahorro",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", currentSummary.savingsRatePercent)}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = SavingsGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDonutAndBudgetCard(
    transactions: List<TransactionRecord>,
    budgets: List<CategoryBudgetRecord>,
    selectedCategoryKey: String?,
    onSelectCategory: (String?) -> Unit
) {
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val expenseTx = transactions.filter { it.type == "EXPENSE" }
    val totalExpense = expenseTx.sumOf { it.amount }.coerceAtLeast(0.01)

    val byCategory = expenseTx
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .entries
        .sortedByDescending { it.value }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_donut_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Distribución por Categorías y Presupuesto",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toca una categoría para filtrar tus movimientos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (byCategory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no hay gastos registrados en este mes.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(130.dp)) {
                            var startAngle = -90f
                            byCategory.forEach { (catKey, catSum) ->
                                val sweep = ((catSum / totalExpense) * 360.0).toFloat()
                                val catColor = Color(FinanceCategory.fromKey(catKey).colorHex)
                                val strokeWidth = if (selectedCategoryKey == catKey) 32f else 24f
                                drawArc(
                                    color = catColor,
                                    startAngle = startAngle,
                                    sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Gastos",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currency.format(expenseTx.sumOf { it.amount }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        byCategory.take(5).forEach { (catKey, catAmount) ->
                            val categoryEnum = FinanceCategory.fromKey(catKey)
                            val pct = (catAmount / totalExpense) * 100.0
                            val budgetRecord = budgets.find { it.category == catKey }
                            val isSelected = selectedCategoryKey == catKey

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .clickable { onSelectCategory(catKey) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(categoryEnum.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = categoryEnum.labelEs,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${currency.format(catAmount)} (${String.format(Locale.US, "%.0f", pct)}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (budgetRecord != null && budgetRecord.monthlyLimit > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val ratio = (catAmount / budgetRecord.monthlyLimit).toFloat().coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (catAmount > budgetRecord.monthlyLimit) ExpenseCoral else Color(categoryEnum.colorHex),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
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
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
