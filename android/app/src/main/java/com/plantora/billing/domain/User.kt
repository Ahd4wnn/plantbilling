package com.plantora.billing.domain

enum class Role {
    SHOP_OWNER, SALESPERSON, ADMIN, UNKNOWN;

    companion object {
        fun from(raw: String?): Role = when (raw) {
            "shop_owner" -> SHOP_OWNER
            "salesperson" -> SALESPERSON
            "admin" -> ADMIN
            else -> UNKNOWN
        }
    }
}

/** The authenticated user. Admin is out of scope for this app, but parsed so we
 *  can show a clear "use the web app" message rather than failing silently. */
data class User(
    val id: String,
    val email: String,
    val role: Role,
    val shopId: String?,
    val shopName: String?,
    val businessName: String?,
    val businessUpi: String?,
) {
    val canUseApp: Boolean get() = role == Role.SHOP_OWNER || role == Role.SALESPERSON
    val displayShop: String get() = businessName ?: shopName ?: "Your shop"
}
