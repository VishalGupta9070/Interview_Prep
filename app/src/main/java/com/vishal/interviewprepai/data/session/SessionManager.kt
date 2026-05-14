package com.vishal.interviewprepai.data.session

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun userPhone(): String? = prefs.getString(KEY_USER_PHONE, null)

    fun login(phone: String) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_PHONE, phone)
            .apply()
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_PHONE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "session_prefs"
        const val KEY_IS_LOGGED_IN = "isLoggedIn"
        const val KEY_USER_PHONE = "userPhone"
    }
}
