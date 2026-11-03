package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.BankAccountRecord
import com.example.data.FinanceCategory
import com.example.data.MonthlySavingsSummary
import com.example.data.TransactionRecord
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale

object ExcelExportHelper {

    /**
     * Generates a SpreadsheetML (.xls XML) workbook that opens natively in Microsoft Excel,
     * Google Sheets, and LibreOffice Calc with multiple worksheets (Transacciones, Resumen Mensual,
     * Cuentas Bancarias) and styled headers, as well as a UTF-8 BOM CSV option.
     */
    fun writeExcelXmlToUri(
        context: Context,
        uri: Uri,
        selectedMonth: String,
        transactions: List<TransactionRecord>,
        monthlySummaries: List<MonthlySavingsSummary>,
        bankAccounts: List<BankAccountRecord>
    ): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(buildSpreadsheetXml(selectedMonth, transactions, monthlySummaries, bankAccounts))
                    writer.flush()
                }
            } ?: return Result.failure(IllegalStateException("No se pudo abrir el archivo de destino"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeCsvToUri(
        context: Context,
        uri: Uri,
        transactions: List<TransactionRecord>
    ): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Write UTF-8 BOM so Microsoft Excel detects accents and UTF-8 columns properly
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write("Fecha,Mes,Concepto,Tipo,Categoria,Monto,Cuenta,Sincronizado_Banco,Notas\r\n")
                    transactions.forEach { tx ->
                        val catLabel = FinanceCategory.fromKey(tx.category).labelEs
                        val typeLabel = if (tx.type == "INCOME") "Ingreso" else "Gasto"
                        val syncLabel = if (tx.isBankSynced) "Si" else "Manual"
                        writer.write(
                            "${escapeCsv(tx.dateString)}," +
                                "${escapeCsv(tx.yearMonth)}," +
                                "${escapeCsv(tx.title)}," +
                                "${escapeCsv(typeLabel)}," +
                                "${escapeCsv(catLabel)}," +
                                String.format(Locale.US, "%.2f", tx.amount) + "," +
                                "${escapeCsv(tx.accountName)}," +
                                "${escapeCsv(syncLabel)}," +
                                "${escapeCsv(tx.notes)}\r\n"
                        )
                    }
                    writer.flush()
                }
            } ?: return Result.failure(IllegalStateException("No se pudo abrir el archivo CSV"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareSummaryTextIntent(
        context: Context,
        selectedMonth: String,
        monthlySummary: MonthlySavingsSummary?,
        transactions: List<TransactionRecord>
    ) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        val income = currencyFormat.format(monthlySummary?.totalIncome ?: 0.0)
        val expense = currencyFormat.format(monthlySummary?.totalExpense ?: 0.0)
        val savings = currencyFormat.format(monthlySummary?.netSavings ?: 0.0)
        val rate = String.format(Locale.US, "%.1f%%", monthlySummary?.savingsRatePercent ?: 0.0)

        val sb = StringBuilder()
        sb.append("📊 Reporte FinanzaPro - Mes $selectedMonth\n")
        sb.append("• Ingresos: $income\n")
        sb.append("• Gastos: $expense\n")
        sb.append("• Ahorro Neto: $savings ($rate)\n")
        sb.append("• Total movimientos: ${transactions.size}\n")

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte FinanzaPro ($selectedMonth)")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartir resumen financiero"))
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildSpreadsheetXml(
        selectedMonth: String,
        transactions: List<TransactionRecord>,
        monthlySummaries: List<MonthlySavingsSummary>,
        bankAccounts: List<BankAccountRecord>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<?mso-application progid="Excel.Sheet"?>""").append("\n")
        sb.append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" """ +
            """xmlns:o="urn:schemas-microsoft-com:office:office" """ +
            """xmlns:x="urn:schemas-microsoft-com:office:excel" """ +
            """xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""").append("\n")

        sb.append("""
            <Styles>
                <Style ss:ID="Default" ss:Name="Normal">
                    <Alignment ss:Vertical="Center"/>
                    <Font ss:FontName="Calibri" ss:Size="11"/>
                </Style>
                <Style ss:ID="Header">
                    <Font ss:FontName="Calibri" ss:Size="11" ss:Bold="1" ss:Color="#FFFFFF"/>
                    <Interior ss:Color="#047857" ss:Pattern="Solid"/>
                </Style>
                <Style ss:ID="Currency">
                    <NumberFormat ss:Format="Standard"/>
                </Style>
            </Styles>
        """.trimIndent()).append("\n")

        // Worksheet 1: Movimientos
        sb.append("""<Worksheet ss:Name="Movimientos_$selectedMonth"><Table>""").append("\n")
        sb.append("""<Row ss:StyleID="Header">""")
        listOf("Fecha", "Mes", "Concepto", "Tipo", "Categoría", "Monto", "Cuenta Bancaria", "Origen", "Notas").forEach { col ->
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(col)}</Data></Cell>""")
        }
        sb.append("""</Row>""").append("\n")

        transactions.forEach { tx ->
            val catLabel = FinanceCategory.fromKey(tx.category).labelEs
            val typeLabel = if (tx.type == "INCOME") "Ingreso" else "Gasto"
            val syncLabel = if (tx.isBankSynced) "Sincronización Bancaria" else "Ingreso Manual"
            sb.append("<Row>")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(tx.dateString)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(tx.yearMonth)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(tx.title)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(typeLabel)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(catLabel)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.2f", tx.amount)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(tx.accountName)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(syncLabel)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(tx.notes)}</Data></Cell>""")
            sb.append("</Row>\n")
        }
        sb.append("</Table></Worksheet>\n")

        // Worksheet 2: Resumen Mensual de Ahorros
        sb.append("""<Worksheet ss:Name="Ahorros_Mensuales"><Table>""").append("\n")
        sb.append("""<Row ss:StyleID="Header">""")
        listOf("Mes", "Ingresos Totales", "Gastos Totales", "Ahorro Neto", "Tasa de Ahorro (%)").forEach { col ->
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(col)}</Data></Cell>""")
        }
        sb.append("""</Row>""").append("\n")

        monthlySummaries.forEach { summary ->
            sb.append("<Row>")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(summary.yearMonth)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.2f", summary.totalIncome)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.2f", summary.totalExpense)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.2f", summary.netSavings)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.1f", summary.savingsRatePercent)}</Data></Cell>""")
            sb.append("</Row>\n")
        }
        sb.append("</Table></Worksheet>\n")

        // Worksheet 3: Cuentas Bancarias
        sb.append("""<Worksheet ss:Name="Cuentas_Bancarias"><Table>""").append("\n")
        sb.append("""<Row ss:StyleID="Header">""")
        listOf("Banco / Entidad", "Tipo de Cuenta", "Número", "Saldo Actual", "Moneda", "Auto-Sync", "Última Sincronización").forEach { col ->
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(col)}</Data></Cell>""")
        }
        sb.append("""</Row>""").append("\n")

        bankAccounts.forEach { acc ->
            sb.append("<Row>")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(acc.bankName)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(acc.accountType)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(acc.maskedNumber)}</Data></Cell>""")
            sb.append("""<Cell ss:StyleID="Currency"><Data ss:Type="Number">${String.format(Locale.US, "%.2f", acc.balance)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(acc.currency)}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${if (acc.autoSyncEnabled) "Activo" else "Pausado"}</Data></Cell>""")
            sb.append("""<Cell><Data ss:Type="String">${escapeXml(acc.lastSyncSummary)}</Data></Cell>""")
            sb.append("</Row>\n")
        }
        sb.append("</Table></Worksheet>\n")
        sb.append("</Workbook>")
        return sb.toString()
    }
}
