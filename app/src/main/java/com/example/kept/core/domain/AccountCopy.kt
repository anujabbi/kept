package com.example.kept.core.domain

/**
 * Sign in with Google copy (#193; no mockup, recorded in DECISIONS.md). The account is optional,
 * so every line says what stays the same: habits never leave the phone.
 */
object AccountCopy {
    /** Google's own wording for the button, also the step title and the Settings row. */
    const val SIGN_IN = "Sign in with Google"
    const val SIGNING_IN = "Signing in…"

    // Onboarding step 1, right after the welcome screen
    const val STEP_TITLE = SIGN_IN
    const val STEP_LINE = "It's optional. Skip it and KEPT works exactly the same."
    const val FACT_ACCOUNT_BOLD = "Your KEPT account"
    const val FACT_ACCOUNT = " uses your Google name and email. Nothing else."
    const val FACT_PHONE_BOLD = "Habits stay on this phone."
    const val FACT_PHONE = " Check-ins, photos, goals and your lock never leave it."
    const val SIGNED_IN_AS = "Signed in as"

    // Settings
    const val GROUP = "Account"
    const val ROW_HINT = "Optional. Your habits stay on this phone."

    // Errors: one short line, and the app stays usable and signed out. "Check your connection"
    // only when the connection is what failed.
    const val CANCELLED = "Sign-in cancelled."
    const val FAILED_OFFLINE = "Couldn't sign in. Check your connection and try again."
    const val FAILED = "Couldn't sign in. Try again in a moment."

    fun signedInAs(who: String): String = "$SIGNED_IN_AS $who"
}

/** Where a sign-in attempt is, as a screen shows it. */
sealed interface SignInUi {
    data object Idle : SignInUi
    data object Busy : SignInUi
    data class Error(val message: String) : SignInUi

    companion object {
        /** The line an attempt that did not sign in leaves on screen. */
        fun failed(cancelled: Boolean, offline: Boolean = false): SignInUi = Error(
            when {
                cancelled -> AccountCopy.CANCELLED
                offline -> AccountCopy.FAILED_OFFLINE
                else -> AccountCopy.FAILED
            },
        )
    }
}
