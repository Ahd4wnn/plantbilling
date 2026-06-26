package com.plantora.billing.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.domain.DiscountType
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.billing.PaymentMode
import com.plantora.billing.ui.components.ErrorState
import com.plantora.billing.ui.components.LoadingState
import com.plantora.billing.ui.components.MoneyText
import com.plantora.billing.ui.components.PlantoraTextField
import com.plantora.billing.ui.components.PrimaryButton
import com.plantora.billing.ui.components.QuantityStepper
import com.plantora.billing.ui.components.SecondaryButton
import com.plantora.billing.ui.components.SectionHeader
import com.plantora.billing.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BillEditViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(ui.saved) { if (ui.saved) onSaved() }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Edit bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingState(Modifier.padding(padding))
            ui.error != null -> ErrorState(ui.error!!, onRetry = viewModel::load, icon = Icons.Rounded.Add, modifier = Modifier.padding(padding))
            else -> EditBody(ui, viewModel, Modifier.padding(padding))
        }
    }

    if (ui.showAddPicker) {
        ModalBottomSheet(onDismissRequest = viewModel::closeAddPicker, sheetState = pickerState) {
            ProductPicker(products = ui.products, onPick = viewModel::addProduct)
        }
    }
}

@Composable
private fun EditBody(ui: BillEditUiState, viewModel: BillEditViewModel, modifier: Modifier) {
    val totals = ui.totals
    val (cash, upi) = ui.payment
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg)
            .padding(bottom = Dimens.xl),
    ) {
        Spacer(Modifier.height(Dimens.md))
        SectionHeader("Items")
        ui.lines.forEach { line ->
            Column(Modifier.padding(vertical = Dimens.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    MoneyText(line.lineTotal, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Dimens.sm))
                    IconButton(onClick = { viewModel.removeLine(line.product.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${line.product.name}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    PlantoraTextField(
                        value = line.unitPrice.toWire(),
                        onValueChange = { viewModel.setUnitPrice(line.product.id, it) },
                        label = "Price",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    QuantityStepper(
                        quantity = line.quantity,
                        onDecrement = { viewModel.setQuantity(line.product.id, line.quantity - 1) },
                        onIncrement = { viewModel.setQuantity(line.product.id, line.quantity + 1) },
                        onQuantityChange = { q -> viewModel.setQuantity(line.product.id, q) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(Dimens.md))
        SecondaryButton(
            text = "Add plant",
            onClick = viewModel::openAddPicker,
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Dimens.lg))
        SectionHeader("Discount")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = ui.discountType == DiscountType.FLAT, onClick = { viewModel.setDiscountType(DiscountType.FLAT) }, label = { Text("₹ Flat") })
            FilterChip(selected = ui.discountType == DiscountType.PERCENT, onClick = { viewModel.setDiscountType(DiscountType.PERCENT) }, label = { Text("% Percent") })
            Spacer(Modifier.width(Dimens.sm))
            PlantoraTextField(
                value = ui.discountInput,
                onValueChange = viewModel::setDiscountInput,
                label = if (ui.discountType == DiscountType.FLAT) "Amount" else "Percent",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(130.dp),
            )
        }

        Spacer(Modifier.height(Dimens.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            MoneyText(totals.total, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(Dimens.lg))
        SectionHeader("Payment")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            FilterChip(selected = ui.paymentMode == PaymentMode.CASH, onClick = { viewModel.setPaymentMode(PaymentMode.CASH) }, label = { Text("Cash") })
            FilterChip(selected = ui.paymentMode == PaymentMode.UPI, onClick = { viewModel.setPaymentMode(PaymentMode.UPI) }, label = { Text("UPI") })
            FilterChip(selected = ui.paymentMode == PaymentMode.SPLIT, onClick = { viewModel.setPaymentMode(PaymentMode.SPLIT) }, label = { Text("Split") })
        }
        if (ui.paymentMode == PaymentMode.SPLIT) {
            Spacer(Modifier.height(Dimens.sm))
            PlantoraTextField(ui.cashInput, viewModel::setCashInput, label = "Cash part", keyboardType = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(Dimens.sm))
        PlantoraTextField(ui.dueInput, viewModel::setDueInput, label = "Due (owed later, optional)", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(Dimens.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.lg)) {
            PayPill("Cash", cash, Modifier.weight(1f))
            PayPill("UPI", upi, Modifier.weight(1f))
        }

        Spacer(Modifier.height(Dimens.md))
        PlantoraTextField(ui.remarks, viewModel::setRemarks, label = "Remarks (optional)", singleLine = false)

        ui.saveError?.let {
            Spacer(Modifier.height(Dimens.md))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Dimens.xl))
        PrimaryButton(
            text = "Save changes • ${totals.total.format()}",
            onClick = viewModel::save,
            enabled = !ui.isEmpty,
            loading = ui.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PayPill(label: String, money: Money, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProductPicker(products: List<Product>, onPick: (Product) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = products.filter { it.isActive && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg).padding(bottom = Dimens.xl)) {
        Text("Add a plant", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Dimens.md))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search products") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Dimens.sm))
        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
            items(filtered, key = { it.id }) { product ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(product) }
                        .padding(vertical = Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    MoneyText(product.retailPrice, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
