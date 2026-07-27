package com.plantora.billing.ui.nav

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantora.billing.R
import com.plantora.billing.ui.notifications.NotificationsScreen
import com.plantora.billing.ui.notifications.NotificationsViewModel
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
import com.plantora.billing.ui.labour.LabourScreen
import com.plantora.billing.ui.borrowings.BorrowingsScreen
import com.plantora.billing.ui.printer.PrinterScreen
import com.plantora.billing.ui.products.ProductsScreen
import com.plantora.billing.ui.sales.BillDetailScreen
import com.plantora.billing.ui.sales.BillEditScreen
import com.plantora.billing.ui.sales.ApprovalsScreen
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    user: User,
    onLogout: () -> Unit,
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val tabs = tabsFor(user.role)
    val homeRoute = homeTabFor(user.role).route
    val isOwner = user.role == com.plantora.billing.domain.Role.MANAGER

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTabRoute = tabs.any { tab -> currentDestination?.route == tab.route }
    val currentTab = tabs.firstOrNull { it.route == currentDestination?.route }

    val notifUi by notificationsViewModel.ui.collectAsStateWithLifecycle()

    // Refresh the unread badge whenever the shop returns to the app (it is often
    // swiped out of Recents and relaunched), not only on first composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            // Slim bar on the top-level tabs only, carrying the notifications bell.
            if (onTabRoute) TopAppBar(
                title = { currentTab?.let { Text(stringResource(it.labelRes)) } },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                        },
                    ) {
                        BadgedBox(
                            badge = {
                                if (notifUi.unreadCount > 0) {
                                    Badge {
                                        Text(if (notifUi.unreadCount > 99) "99+" else notifUi.unreadCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = stringResource(R.string.cd_notifications),
                            )
                        }
                    }
                },
            )
        },
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
                                contentDescription = stringResource(tab.labelRes),
                            )
                        },
                        label = {
                            // Ellipsize (not overflow) so a long translated label
                            // — some Indic words are much longer than the English —
                            // can never spill over and break the tab bar.
                            Text(
                                stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
            // Pad by the shell's insets (bottom nav + system bars) AND mark them
            // consumed, so a nested screen's imePadding() lifts its bottom bar by
            // only (keyboard − navBar), not the full keyboard height. Without the
            // consume, the IME inset double-counts and leaves a large empty gap
            // between the Bill action bar and the keyboard.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
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
                    canApprove = isOwner,
                    onOpenApprovals = { navController.navigate(Routes.APPROVALS) },
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
            composable(Routes.APPROVALS) {
                ApprovalsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LABOUR) {
                LabourScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BORROWINGS) {
                BorrowingsScreen(onBack = { navController.popBackStack() })
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
                    canDelete = isOwner,
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
                    onOpenLabour = { navController.navigate(Routes.LABOUR) },
                    onOpenBorrowings = { navController.navigate(Routes.BORROWINGS) },
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
            composable(Routes.NOTIFICATIONS) {
                // Share the shell-scoped VM so opening the list clears the bell badge.
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = notificationsViewModel,
                )
            }
        }
    }
}
