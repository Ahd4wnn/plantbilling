package com.plantora.billing.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.R
import com.plantora.billing.data.NotificationRepository
import com.plantora.billing.domain.Notification
import com.plantora.billing.i18n.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val loading: Boolean = true,
    val items: List<Notification> = emptyList(),
    val error: UiText? = null,
    val unreadCount: Int = 0,
)

/**
 * Shell-scoped: one instance backs both the top-bar bell badge and the
 * notifications list, so opening the list and marking read clears the badge.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: NotificationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(NotificationsUiState())
    val ui: StateFlow<NotificationsUiState> = _ui.asStateFlow()

    init { load() }

    /** Full (re)load with spinner — used on first open and manual retry. */
    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        fetch()
    }

    /** Silent refresh (e.g. on app resume) — keeps the current list visible. */
    fun refresh() {
        if (_ui.value.loading) return
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching { repo.feed() }
                .onSuccess { feed ->
                    _ui.update {
                        it.copy(loading = false, items = feed.items, unreadCount = feed.unreadCount, error = null)
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = UiText.err(e, R.string.err_generic)) }
                }
        }
    }

    /**
     * Mark every currently-unread notification read (called when the list opens).
     * Optimistically clears the badge; failures are non-fatal (the next fetch
     * reconciles), so the shop never gets stuck on a stale count.
     */
    fun markAllRead() {
        val unread = _ui.value.items.filter { !it.read }
        if (unread.isEmpty()) return
        _ui.update { s ->
            s.copy(items = s.items.map { it.copy(read = true) }, unreadCount = 0)
        }
        viewModelScope.launch {
            unread.forEach { n -> runCatching { repo.markRead(n.id) } }
        }
    }
}
