package com.example.myapplication2.data

import android.content.Context
import com.example.myapplication2.ui.auth.UserRole

data class UserSession(
    val isLoggedIn: Boolean = false,
    val role: UserRole = UserRole.PASSENGER,
    val userEmail: String = "",
    val busId: String = "",
    val driverBusCategory: String = "KTCL",
    val routeId: String = "route-42"
)

object UserSessionRepository {

    private const val PREF_NAME = "user_session_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_BUS_ID = "bus_id"
    private const val KEY_DRIVER_BUS_CATEGORY = "driver_bus_category"
    private const val KEY_ROUTE_ID = "route_id"

    fun getUserSession(context: Context): UserSession {
        return runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
            val roleStr = prefs.getString(KEY_USER_ROLE, UserRole.PASSENGER.name) ?: UserRole.PASSENGER.name
            val role = runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.PASSENGER)
            val userEmail = prefs.getString(KEY_USER_EMAIL, "") ?: ""
            val busId = prefs.getString(KEY_BUS_ID, "") ?: ""
            val category = prefs.getString(KEY_DRIVER_BUS_CATEGORY, "KTCL") ?: "KTCL"
            val routeId = prefs.getString(KEY_ROUTE_ID, "route-42") ?: "route-42"

            UserSession(
                isLoggedIn = isLoggedIn,
                role = role,
                userEmail = userEmail,
                busId = busId,
                driverBusCategory = category,
                routeId = routeId
            )
        }.getOrDefault(UserSession())
    }

    fun saveUserSession(context: Context, session: UserSession) {
        runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, session.isLoggedIn)
                .putString(KEY_USER_ROLE, session.role.name)
                .putString(KEY_USER_EMAIL, session.userEmail)
                .putString(KEY_BUS_ID, session.busId)
                .putString(KEY_DRIVER_BUS_CATEGORY, session.driverBusCategory)
                .putString(KEY_ROUTE_ID, session.routeId)
                .apply()
        }
    }

    fun clearUserSession(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}
