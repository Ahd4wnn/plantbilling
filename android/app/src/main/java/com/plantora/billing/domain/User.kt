package com.plantora.billing.domain

enum class Role {
    OWNER, MANAGER, SALESPERSON, ADMIN, UNKNOWN;

    companion object {
        fun from(raw: String?): Role = when (raw) {
            "owner" -> OWNER
            // "shop_owner" is the legacy name for the per-shop operator, now "manager".
            "manager", "shop_owner" -> MANAGER
            "salesperson" -> SALESPERSON
            "admin" -> ADMIN
            else -> UNKNOWN
        }
    }
}

/** The authenticated user. The shop roles run the shop app; ADMIN gets the
 *  in-app admin portal (dashboard, notifications, platform books). UNKNOWN is the
 *  only role that still lands on the "use the web app" fallback. */
data class User(
    val id: String,
    val email: String,
    val role: Role,
    val shopId: String?,
    val shopName: String?,
    val businessName: String?,
    val businessUpi: String?,
) {
    val canUseApp: Boolean
        get() = role == Role.OWNER || role == Role.MANAGER || role == Role.SALESPERSON || role == Role.ADMIN
    val isOwner: Boolean get() = role == Role.OWNER
    val isAdmin: Boolean get() = role == Role.ADMIN
    val displayShop: String get() = businessName ?: shopName ?: "Your shop"
}
