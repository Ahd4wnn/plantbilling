package com.plantora.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantora.billing.data.AuthState
import com.plantora.billing.data.NetworkMonitor
import com.plantora.billing.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    val authState: StateFlow<AuthState> = session.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    val isConnected: StateFlow<Boolean> = networkMonitor.isConnected

    fun retryConnection() = viewModelScope.launch { networkMonitor.recheck() }

    fun logout() = session.logout()
}
