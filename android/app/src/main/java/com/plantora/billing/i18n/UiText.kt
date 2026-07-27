package com.plantora.billing.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.plantora.billing.data.remote.friendlyError

/**
 * A user-facing message produced in a ViewModel but resolved to text in the UI
 * layer, so it always renders in the app's chosen language.
 *
 * ViewModels can't reliably resolve strings themselves: the per-app language is
 * applied to the *activity* context (see [LocaleManager]/`MainActivity`), not to
 * the application context a ViewModel would otherwise reach for. So a ViewModel
 * emits a [UiText] (a resource id + args, or an already-built string), and the
 * composable turns it into text via [resolve]/[asString] using `LocalContext`,
 * which carries the chosen locale.
 */
sealed interface UiText {

    /** Text that is already final (dynamic content with no localizable part). */
    data class Str(val value: String) : UiText

    /** A `strings.xml` entry, optionally with positional `%1$s`-style args. */
    class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * A network/API failure, resolved through [friendlyError] so the server's
     * message (or a localized [fallback]) reaches the user — never a stack trace.
     */
    class Err(val error: Throwable, @StringRes val fallback: Int) : UiText

    fun resolve(context: Context): String = when (this) {
        is Str -> value
        is Res -> if (args.isEmpty()) context.getString(id)
                  else context.getString(id, *args.toTypedArray())
        is Err -> friendlyError(context, error, fallback)
    }

    companion object {
        fun of(value: String): UiText = Str(value)
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun err(error: Throwable, @StringRes fallback: Int): UiText = Err(error, fallback)
    }
}

/** Resolve inside a composable using the locale-aware `LocalContext`. */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
