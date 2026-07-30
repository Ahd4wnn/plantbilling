package com.plantora.billing.ui.admin

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plantora.billing.domain.User
import com.plantora.billing.ui.components.PlantoraCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The three peer areas of the admin portal. */
private enum class AdminTab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("admin_dashboard", "Dashboard", Icons.Rounded.Dashboard),
    NOTIFY("admin_notify", "Notify", Icons.Rounded.Campaign),
    BOOKS("admin_books", "Books", Icons.AutoMirrored.Rounded.ReceiptLong),
}

/** The signed-in shell for the platform ADMIN — a 3-tab bottom-nav experience. */
@Composable
fun AdminShell(user: User, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    val onTab = AdminTab.entries.any { tab -> current?.route == tab.route }

    Scaffold(
        bottomBar = {
            if (onTab) NavigationBar {
                AdminTab.entries.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = AdminTab.DASHBOARD.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(AdminTab.DASHBOARD.route) {
                AdminDashboardScreen(
                    adminEmail = user.email,
                    onOpenShop = { id -> nav.navigate("admin_shop/$id") },
                    onLogout = onLogout,
                )
            }
            composable(AdminTab.NOTIFY.route) {
                AdminNotifyScreen(onLogout = onLogout)
            }
            composable(AdminTab.BOOKS.route) {
                AdminBooksScreen(onLogout = onLogout)
            }
            composable(
                "admin_shop/{shopId}",
                arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
            ) {
                AdminShopDetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

// ── Shared admin UI helpers (package-visible) ─────────────────────────────────

/** A labelled stat tile. Values here are counts, not money. */
@Composable
internal fun AdminKpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    PlantoraCard(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

private val DAY_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Format a plain calendar-date string ("2026-07-30") for display; passthrough on failure. */
internal fun formatDayDate(raw: String): String =
    runCatching { LocalDate.parse(raw).format(DAY_FMT) }.getOrDefault(raw)
