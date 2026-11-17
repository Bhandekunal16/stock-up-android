package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SignalsExecutionStatus
import com.example.data.model.SignalsHistoryEntry
import com.example.data.repository.SignalsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignalsUiState(
    val status: SignalsExecutionStatus = SignalsExecutionStatus.NOT_RUN,
    val exitCode: Int? = null,
    val lastExecutedAt: String? = null,
    val durationMs: Long? = null,
    val output: String = "",
    val error: String = "",
    val selectedEntryId: String? = null,
    val history: List<SignalsHistoryEntry> = emptyList(),
)

class SignalsViewModel(
    private val repository: SignalsRepository = SignalsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignalsUiState())
    val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

    fun executeSignals() {
        if (_uiState.value.status == SignalsExecutionStatus.RUNNING) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = SignalsExecutionStatus.RUNNING,
                    error = "",
                )
            }

            val entry = repository.executeSignals()
            _uiState.update { current ->
                current.copy(
                    status = entry.status,
                    exitCode = entry.exitCode,
                    lastExecutedAt = entry.executedAt,
                    durationMs = entry.durationMs,
                    output = entry.output,
                    error = entry.error,
                    selectedEntryId = entry.id,
                    history = listOf(entry) + current.history,
                )
            }
        }
    }

    fun selectHistoryEntry(entry: SignalsHistoryEntry) {
        if (_uiState.value.status == SignalsExecutionStatus.RUNNING) return
        _uiState.update {
            it.copy(
                status = entry.status,
                exitCode = entry.exitCode,
                lastExecutedAt = entry.executedAt,
                durationMs = entry.durationMs,
                output = entry.output,
                error = entry.error,
                selectedEntryId = entry.id,
            )
        }
    }
}
