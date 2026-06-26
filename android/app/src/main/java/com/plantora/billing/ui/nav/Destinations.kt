package com.plantora.billing.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.plantora.billing.domain.Role

/** Bottom-nav tabs (always labelled). Visibility is role-aware — see [tabsFor]. */
enum class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BILL("bill", "Bill", Icons.Rounded.PointOfSale, Icons.Rounded.PointOfSale),
    PRODUCTS("products", "Products", Icons.Rounded.Storefront, Icons.Outlined.Storefront),
    SALES("sales", "Sales", Icons.Rounded.Receipt, Icons.Outlined.Receipt),
    CUSTOMERS("customers", "Customers", Icons.Rounded.Group, Icons.Outlined.Group),
    MORE("more", "More", Icons.Rounded.MoreHoriz, Icons.Rounded.MoreHoriz),
}

/**
 * Role-aware tab list, mirroring the web (`BottomNav.tsx`): the shop owner does
 * not bill directly, so the Bill tab is hidden for them. The salesperson only
 * bills — product catalog management is owner-only, so Products is hidden for them.
 */
fun tabsFor(role: Role): List<Tab> =
    if (role == Role.SHOP_OWNER) Tab.entries.filter { it != Tab.BILL }
    else Tab.entries.filter { it != Tab.PRODUCTS }

/** Landing tab after login (web `AppIndexRedirect`): owner→Products, others→Bill. */
fun homeTabFor(role: Role): Tab =
    if (role == Role.SHOP_OWNER) Tab.PRODUCTS else Tab.BILL

object Routes {
    const val BILL_SUCCESS = "bill_success/{billId}"
    const val BILL_DETAIL = "bill_detail/{billId}"
    const val BILL_EDIT = "bill_edit/{billId}"
    const val DUES = "dues"
    const val CUSTOMER_DETAIL = "customer_detail/{customerId}"
    const val DETAILED_REPORT = "detailed_report"
    const val PRODUCT_EDIT = "product_edit"
    const val PRINTER_SETTINGS = "printer_settings"
    const val SHOP_SETTINGS = "shop_settings"
    const val STAFF_MANAGEMENT = "staff_management"
    const val WHATSAPP_SETTINGS = "whatsapp_settings"

    fun billSuccess(billId: String) = "bill_success/$billId"
    fun billDetail(billId: String) = "bill_detail/$billId"
    fun billEdit(billId: String) = "bill_edit/$billId"
    fun customerDetail(customerId: String) = "customer_detail/$customerId"
}
