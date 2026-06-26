package com.plantora.billing.ui.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plantora.billing.domain.DaySummary
import com.plantora.billing.domain.Expense
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.formatBillTime
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraCard
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.components.charts.DonutChart
import com.plantora.billing.ui.components.charts.DonutSlice
import com.plantora.billing.ui.components.charts.RatioBar
import com.plantora.billing.ui.theme.CashGreen
import com.plantora.billing.ui.theme.DueAmber
import com.plantora.billing.ui.theme.UpiBlue
import com.plantora.billing.ui.theme.Dimens

private val SalesColor = Color(0xFF6366F1)   // indigo
private val ExpenseColor = Color(0xFFF43F5E) // rose

@Composable
fun SummaryHero(
    summary: DaySummary,
    isOwner: Boolean,
    onAddExpense: () -> Unit,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit,
) {
    val sales = summary.totalSales.amount.toFloat()
    val expenses = summary.totalExpenses.amount.toFloat()
    val netNegative = summary.netSales.amount.signum() < 0
    val netColor = if (netNegative) MaterialTheme.colorScheme.error else CashGreen
    val netStr = (if (netNegative) "− " else "") + Money(summary.netSales.amount.abs()).format()

    PlantoraCard {
        // Prominent total, then a 2-up Expenses / Net row (fits large amounts).
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                .padding(Dimens.lg),
        ) {
            Text("TOTAL SALES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(summary.totalSales.format(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("${summary.billCount} bills", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Dimens.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            MetricCard("Expenses", "− " + summary.totalExpenses.format(), MaterialTheme.colorScheme.error, Modifier.weight(1f), "spending")
            MetricCard("Net profit", netStr, netColor, Modifier.weight(1f), "sales − expenses")
        }

        Spacer(Modifier.height(Dimens.lg))
        // Cash / UPI / Due
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            PayStat("Cash", summary.cashTotal, CashGreen, Modifier.weight(1f))
            PayStat("UPI", summary.upiTotal, UpiBlue, Modifier.weight(1f))
            PayStat("Due", summary.dueTotal, DueAmber, Modifier.weight(1f))
        }

        Spacer(Modifier.height(Dimens.lg))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(Dimens.lg))

        // Donut + legend
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = buildList {
                    if (sales > 0f) add(DonutSlice(sales, SalesColor))
                    if (expenses > 0f) add(DonutSlice(expenses, ExpenseColor))
                },
                diameter = 168.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NET", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        netStr,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = netColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Spacer(Modifier.size(Dimens.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                LegendRow(SalesColor, "Sales", summary.totalSales)
                LegendRow(ExpenseColor, "Expenses", summary.totalExpenses)
            }
        }

        if (sales > 0f) {
            Spacer(Modifier.height(Dimens.lg))
            RatioBar(label = "Expense ratio", ratio = if (sales > 0f) expenses / sales else 0f)
        }

        Spacer(Modifier.height(Dimens.lg))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(Dimens.md))

        // Expenses list
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Expenses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SecondaryButton(text = "+ Add", onClick = onAddExpense)
        }
        Spacer(Modifier.height(Dimens.sm))
        if (summary.expenses.isEmpty()) {
            Text("No expenses recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            summary.expenses.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.reason, style = MaterialTheme.typography.bodyLarge)
                        Text(formatBillTime(e.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("− " + e.amount.format(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    if (isOwner) {
                        IconButton(onClick = { onEditExpense(e) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit expense", modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { onDeleteExpense(e.id) }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete expense", modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, valueColor: Color, modifier: Modifier, sub: String) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(Dimens.md),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
        )
        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PayStat(label: String, money: Money, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(Dimens.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        MoneyText(money, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, money: Money) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.size(Dimens.sm))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        MoneyText(money, style = MaterialTheme.typography.bodyMedium, emphasize = false)
    }
}
