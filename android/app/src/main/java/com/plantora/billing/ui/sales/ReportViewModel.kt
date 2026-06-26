package com.plantora.billing.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.ReportRepository
import com.plantora.billing.data.SalespersonRepository
import com.plantora.billing.data.SessionRepository
import com.plantora.billing.data.remote.friendlyError
import com.plantora.billing.domain.DetailedReport
import com.plantora.billing.domain.ReportPeriod
import com.plantora.billing.domain.Role
import com.plantora.billing.domain.Salesperson
import com.plantora.billing.domain.monthBounds
import com.plantora.billing.domain.toApiDate
import com.plantora.billing.domain.todayInShopZone
import com.plantora.billing.domain.weekBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.DAILY,
    val anchorDate: LocalDate = todayInShopZone(),
    val customFrom: LocalDate = todayInShopZone(),
    val customTo: LocalDate = todayInShopZone(),
    val isOwner: Boolean = false,
    val staff: List<Salesperson> = emptyList(),
    val staffId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val report: DetailedReport? = null,
    val downloading: Boolean = false,
    val message: String? = null,
) {
    /** Resolved [from,to] for the current period selection. */
    fun bounds(): Pair<LocalDate, LocalDate> = when (period) {
        ReportPeriod.DAILY -> anchorDate to anchorDate
        ReportPeriod.WEEKLY -> weekBounds(anchorDate)
        ReportPeriod.MONTHLY -> monthBounds(anchorDate)
        ReportPeriod.CUSTOM -> customFrom to customTo
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepo: ReportRepository,
    private val salespersonRepo: SalespersonRepository,
    session: SessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReportUiState())
    val ui: StateFlow<ReportUiState> = _ui.asStateFlow()

    init {
        val owner = (session.state.value as? AuthState.Authenticated)?.user?.role == Role.SHOP_OWNER
        _ui.update { it.copy(isOwner = owner) }
        if (owner) viewModelScope.launch {
            runCatching { salespersonRepo.list() }.onSuccess { list -> _ui.update { it.copy(staff = list) } }
        }
    }

    fun setPeriod(p: ReportPeriod) = _ui.update { it.copy(period = p) }
    fun setAnchorDate(d: LocalDate) = _ui.update { it.copy(anchorDate = d) }
    fun setCustomFrom(d: LocalDate) = _ui.update { it.copy(customFrom = d) }
    fun setCustomTo(d: LocalDate) = _ui.update { it.copy(customTo = d) }
    fun setStaff(id: String?) = _ui.update { it.copy(staffId = id) }

    fun generate() {
        val (from, to) = _ui.value.bounds()
        _ui.update { it.copy(loading = true, error = null, report = null) }
        viewModelScope.launch {
            runCatching { reportRepo.generate(from.toApiDate(), to.toApiDate(), _ui.value.staffId) }
                .onSuccess { r -> _ui.update { it.copy(loading = false, report = r) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendlyError(e, "Couldn't generate the report.")) } }
        }
    }

    fun downloadCsv() {
        val (from, to) = _ui.value.bounds()
        _ui.update { it.copy(downloading = true) }
        viewModelScope.launch {
            runCatching { reportRepo.downloadCsv(from.toApiDate(), to.toApiDate(), _ui.value.staffId) }
                .onSuccess { name -> _ui.update { it.copy(downloading = false, message = "Saved to Downloads: $name") } }
                .onFailure { e -> _ui.update { it.copy(downloading = false, message = friendlyError(e, "Couldn't download the CSV.")) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
