package com.plantora.billing.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.OwnerRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.OwnerOverview
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class OwnerPeriod(val label: String) { TODAY("Today"), WEEK("Week"), MONTH("Month"), CUSTOM("Custom") }

data class OwnerDashboardState(
    val loading: Boolean = true,
    val error: String? = null,
    val overview: OwnerOverview? = null,
    val period: OwnerPeriod = OwnerPeriod.TODAY,
    val customFrom: LocalDate = todayInShopZone().minusDays(6),
    val customTo: LocalDate = todayInShopZone(),
)

@HiltViewModel
class OwnerDashboardViewModel @Inject constructor(
    private val repo: OwnerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(OwnerDashboardState())
    val ui: StateFlow<OwnerDashboardState> = _ui.asStateFlow()

    init { load() }

    fun setPeriod(p: OwnerPeriod) {
        if (p == _ui.value.period) return
        _ui.update { it.copy(period = p) }
        load()
    }

    fun setCustomFrom(d: LocalDate) {
        _ui.update { it.copy(customFrom = d, customTo = if (d.isAfter(it.customTo)) d else it.customTo) }
        if (_ui.value.period == OwnerPeriod.CUSTOM) load()
    }

    fun setCustomTo(d: LocalDate) {
        _ui.update { it.copy(customTo = d, customFrom = if (d.isBefore(it.customFrom)) d else it.customFrom) }
        if (_ui.value.period == OwnerPeriod.CUSTOM) load()
    }

    fun load() {
        val today = todayInShopZone()
        val s = _ui.value
        val (from, to) = when (s.period) {
            OwnerPeriod.TODAY -> today to today
            OwnerPeriod.WEEK -> today.minusDays(6) to today
            OwnerPeriod.MONTH -> today.withDayOfMonth(1) to today
            OwnerPeriod.CUSTOM -> s.customFrom to s.customTo
        }
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.overview(from.toApiDate(), to.toApiDate()) }
                .onSuccess { o -> _ui.update { it.copy(loading = false, overview = o) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e)) } }
        }
    }
}
