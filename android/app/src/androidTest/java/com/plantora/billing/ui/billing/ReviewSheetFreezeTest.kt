package com.plantora.billing.ui.billing

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import com.plantora.billing.domain.Money
import com.plantora.billing.domain.Product
import com.plantora.billing.ui.theme.PlantoraTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reproduction guard for the salesperson "Review" freeze: tapping Review while
 * making a bill hard-freezes the app (ANR) before the sheet even slides up, on all
 * devices, for both cash and UPI.
 *
 * Uses NEITHER the Compose test rule NOR Compose finders — both route through
 * `Espresso.onIdle`, which crashes on the API-37 preview emulator
 * (`InputManager.getInstance` removed). Instead the review is hosted in a bare
 * `ComponentActivity` via `ActivityScenario` + Compose `setContent`, mirroring
 * [BillScreen] (a product grid with a persistent `sheetState`, then the sheet shown
 * conditionally). A canary Runnable posted to the main thread detects a wedge: if
 * composition/measure/animation loops, the canary never runs and the latch times
 * out — the freeze, caught as a red test.
 */
class ReviewSheetFreezeTest {

    private fun product(id: String, name: String, price: Int) = Product(
        id = id,
        name = name,
        category = "Plants",
        retailPrice = Money(BigDecimal(price)),
        photoUrl = null,
        isActive = true,
    )

    private fun fakeCatalog(n: Int): List<Product> =
        (1..n).map { product("cat$it", "Plant $it", 50 + it) }

    private fun sampleState(mode: PaymentMode = PaymentMode.CASH) = BillingUiState(
        productsLoading = false,
        products = fakeCatalog(30),
        lines = listOf(
            CartLine("l1", product("p1", "Rose", 120), quantity = 2, unitPrice = Money(BigDecimal(120))),
            CartLine("l2", product("p2", "Tulsi", 60), quantity = 1, unitPrice = Money(BigDecimal(60))),
            CartLine("l3", product("p3", "Money Plant", 250), quantity = 3, unitPrice = Money(BigDecimal(250))),
        ),
        paymentMode = mode,
        businessUpi = "shop@upi",
        businessName = "Test Nursery",
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReviewSheet(state: BillingUiState) {
        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden },
            ),
        ) {
            CartSheetContent(
                state = state,
                onSetQuantity = { _, _ -> }, onSetUnitPrice = { _, _ -> }, onRemoveLine = { },
                onSetDiscountType = { }, onSetDiscountInput = { }, onSetPaymentMode = { },
                onSetCashInput = { }, onSetDueInput = { }, onSetCustomerName = { },
                onSetCustomerPhone = { }, onSetRemarks = { }, onClose = { }, onClearCart = { },
                onAddItem = { }, onHold = { }, onCheckout = { },
            )
        }
    }

    /** Mirrors BillScreen + MainActivity: edge-to-edge, the fontScale=1f density
     *  override, a product grid, and the sheet shown conditionally. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BillLikeHost(state: BillingUiState, show: MutableState<Boolean>) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = 1f),
        ) {
            PlantoraTheme {
                com.plantora.billing.ui.components.ProductCatalog(
                    products = state.products,
                    viewMode = com.plantora.billing.domain.ProductViewMode.GRID,
                    onClick = { },
                    addable = true,
                    modifier = Modifier.fillMaxSize(),
                )
                if (show.value) ReviewSheet(state)
            }
        }
    }

    private fun launchHost() = ActivityScenario.launch(ComponentActivity::class.java).apply {
        onActivity { it.enableEdgeToEdge() }
    }

    /**
     * Posts a canary to the main thread and fails if it doesn't run within the
     * window. The canary is DELAYED so it lands after several compose/layout/
     * animation frames — an immediately-posted canary can run before the first
     * Choreographer frame and thus miss a composition loop (false pass).
     */
    private fun assertMainThreadNotWedged(where: String) {
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).postDelayed({ latch.countDown() }, 1_500)
        val ran = latch.await(20, TimeUnit.SECONDS)
        assertTrue(
            "Main thread wedged $where — composition/measure/animation is looping. FREEZE reproduced.",
            ran,
        )
    }

    /** Static: sheet already open at first composition (control — expected to pass). */
    private fun staticOpen(state: BillingUiState) {
        val scenario = launchHost()
        scenario.onActivity { it.setContent { BillLikeHost(state, mutableStateOf(true)) } }
        assertMainThreadNotWedged("on static open")
        scenario.close()
    }

    /** Transition: render the grid, then flip showReview true (the real "tap Review"). */
    private fun transitionOpen(state: BillingUiState) {
        val scenario = launchHost()
        val show = mutableStateOf(false)
        scenario.onActivity { it.setContent { BillLikeHost(state, show) } }
        assertMainThreadNotWedged("rendering the product grid")
        scenario.onActivity { show.value = true }   // <-- tap Review
        assertMainThreadNotWedged("opening the Review sheet over the grid")
        scenario.close()
    }

    @Test(timeout = 60_000)
    fun staticOpen_cash() = staticOpen(sampleState(PaymentMode.CASH))

    @Test(timeout = 60_000)
    fun transitionOpen_cash() = transitionOpen(sampleState(PaymentMode.CASH))

    @Test(timeout = 60_000)
    fun transitionOpen_split() =
        transitionOpen(sampleState(PaymentMode.SPLIT).copy(cashInput = "100", dueInput = "50"))
}
