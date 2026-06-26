package com.plantora.billing.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plantora.billing.domain.User
import com.plantora.billing.ui.billing.BillScreen
import com.plantora.billing.ui.customers.CustomerDetailScreen
import com.plantora.billing.ui.customers.CustomersScreen
import com.plantora.billing.ui.printer.PrinterScreen
import com.plantora.billing.ui.products.ProductsScreen
import com.plantora.billing.ui.sales.BillDetailScreen
import com.plantora.billing.ui.sales.BillEditScreen
import com.plantora.billing.ui.sales.DetailedReportScreen
import com.plantora.billing.ui.sales.DuesScreen
import com.plantora.billing.ui.sales.SalesScreen
import com.plantora.billing.ui.settings.MoreScreen
import com.plantora.billing.ui.settings.ShopSettingsScreen
import com.plantora.billing.ui.settings.staff.StaffManagementScreen

/**
 * The signed-in app shell: role-aware bottom navigation + an inner NavHost.
 * Mirrors the web flow — the shop owner lands on Products with the Bill tab
 * hidden; salespeople land on Bill.
 */
@Composable
fun MainShell(
    user: User,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val tabs = tabsFor(user.role)
    val homeRoute = homeTabFor(user.role).route
    val isOwner = user.role == com.plantora.billing.domain.Role.SHOP_OWNER

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTabRoute = tabs.any { tab -> currentDestination?.route == tab.route }

    Scaffold(
        bottomBar = {
            // Only show the bottom nav on the top-level tabs, not on pushed
            // sub-screens (bill detail, settings, printer, customer detail).
            if (onTabRoute) NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = homeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.BILL.route) {
                BillScreen()
            }
            composable(Tab.PRODUCTS.route) {
                ProductsScreen(canManage = isOwner)
            }
            composable(Tab.SALES.route) {
                SalesScreen(
                    onOpenBill = { id -> navController.navigate(Routes.billDetail(id)) },
                    onOpenReport = { navController.navigate(Routes.DETAILED_REPORT) },
                    onOpenDues = { navController.navigate(Routes.DUES) },
                )
            }
            composable(Routes.DETAILED_REPORT) {
                DetailedReportScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DUES) {
                DuesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBill = { id -> navController.navigate(Routes.billDetail(id)) },
                )
            }
            composable(Tab.CUSTOMERS.route) {
                CustomersScreen(onOpenCustomer = { id -> navController.navigate(Routes.customerDetail(id)) })
            }
            composable(
                route = Routes.BILL_DETAIL,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) { entry ->
                val billId = entry.arguments?.getString("billId").orEmpty()
                BillDetailScreen(
                    onBack = { navController.popBackStack() },
                    canEdit = isOwner,
                    onEdit = { navController.navigate(Routes.billEdit(billId)) },
                )
            }
            composable(
                route = Routes.BILL_EDIT,
                arguments = listOf(navArgument("billId") { type = NavType.StringType }),
            ) {
                BillEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.CUSTOMER_DETAIL,
                arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
            ) {
                CustomerDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBill = { id -> navController.navigate(Routes.billDetail(id)) },
                )
            }
            composable(Tab.MORE.route) {
                MoreScreen(
                    user = user,
                    onOpenShop = { navController.navigate(Routes.SHOP_SETTINGS) },
                    onOpenPrinter = { navController.navigate(Routes.PRINTER_SETTINGS) },
                    onOpenStaff = { navController.navigate(Routes.STAFF_MANAGEMENT) },
                    onLogout = onLogout,
                )
            }
            composable(Routes.PRINTER_SETTINGS) {
                PrinterScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SHOP_SETTINGS) {
                ShopSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STAFF_MANAGEMENT) {
                StaffManagementScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
