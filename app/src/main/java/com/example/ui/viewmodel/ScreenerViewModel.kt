package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ScreenerConnectionStatus
import com.example.data.model.ScreenerRecord
import com.example.data.model.ScreenerStreamEvent
import com.example.data.repository.ScreenerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScreenerUiState(
    val companyInput: String = DEFAULT_COMPANY,
    val activeCompanyCode: String = DEFAULT_COMPANY,
    val inputError: String? = null,
    val pollingIntervalSeconds: Int = 15,
    val connectionStatus: ScreenerConnectionStatus = ScreenerConnectionStatus.DISCONNECTED,
    val isFetchingSnapshot: Boolean = false,
    val currentSnapshot: ScreenerRecord? = null,
    val priceHistory: List<ScreenerRecord> = emptyList(), // chronological for chart
    val streamedRecords: List<ScreenerRecord> = emptyList(), // reverse-chronological for table
    val errorMessage: String? = null,
) {
    companion object {
        const val DEFAULT_COMPANY = "TATAGOLD"
        val PRESETS = listOf("TATAGOLD", "RELIANCE", "TCS", "INFY", "HDFCBANK", "SUZLON")
        val INTERVALS = listOf(5, 15, 30, 60)
    }

    val isStreaming: Boolean
        get() = connectionStatus == ScreenerConnectionStatus.CONNECTING ||
            connectionStatus == ScreenerConnectionStatus.CONNECTED
}

class ScreenerViewModel(
    private val repository: ScreenerRepository = ScreenerRepository(),
) : ViewModel() {

    private val companyRegex = Regex("^[A-Z0-9_-]+$")

    private val _uiState = MutableStateFlow(ScreenerUiState())
    val uiState: StateFlow<ScreenerUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var snapshotJob: Job? = null
    private var streamJob: Job? = null

    init {
        fetchSnapshotForCode(ScreenerUiState.DEFAULT_COMPANY)
    }

    fun onCompanyInputChanged(raw: String) {
        val cleaned = raw.uppercase()
        _uiState.update { it.copy(companyInput = cleaned, inputError = null) }
    }

    fun setPollingInterval(seconds: Int) {
        if (seconds !in ScreenerUiState.INTERVALS) return
        val wasStreaming = _uiState.value.isStreaming
        _uiState.update { it.copy(pollingIntervalSeconds = seconds) }
        if (wasStreaming) {
            // Restart stream with updated interval
            stopStreaming()
            startStreaming()
        }
    }

    fun validateAndSwitchCompany(rawInput: String = _uiState.value.companyInput) {
        val normalized = rawInput.trim().uppercase()
        val validationError = validateCompanyCode(normalized)
        if (validationError != null) {
            _uiState.update { it.copy(inputError = validationError) }
            return
        }

        if (normalized == _uiState.value.activeCompanyCode && _uiState.value.currentSnapshot != null) {
            fetchSnapshot()
            return
        }

        // Company Switching Rule:
        // Immediately cancel active stream, clear prior snapshot & price history,
        // fetch new company's snapshot, and wait for user action before starting a new stream.
        cancelStreamInternal()
        snapshotJob?.cancel()
        _uiState.update {
            it.copy(
                companyInput = normalized,
                activeCompanyCode = normalized,
                inputError = null,
                connectionStatus = ScreenerConnectionStatus.DISCONNECTED,
                currentSnapshot = null,
                priceHistory = emptyList(),
                streamedRecords = emptyList(),
                errorMessage = null,
            )
        }
        fetchSnapshotForCode(normalized)
    }

    fun selectPreset(code: String) {
        _uiState.update { it.copy(companyInput = code, inputError = null) }
        validateAndSwitchCompany(code)
    }

    fun fetchSnapshot() {
        val normalized = _uiState.value.companyInput.trim().uppercase()
        val validationError = validateCompanyCode(normalized)
        if (validationError != null) {
            _uiState.update { it.copy(inputError = validationError) }
            return
        }
        if (normalized != _uiState.value.activeCompanyCode) {
            validateAndSwitchCompany(normalized)
            return
        }
        fetchSnapshotForCode(normalized)
    }

    private fun fetchSnapshotForCode(code: String) {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingSnapshot = true,
                    errorMessage = null,
                )
            }

            repository.fetchSnapshot(code)
                .onSuccess { record ->
                    _uiState.update { current ->
                        if (current.activeCompanyCode != code) return@update current
                        val updatedHistory = appendDedupedChartPoint(current.priceHistory, record)
                        current.copy(
                            isFetchingSnapshot = false,
                            currentSnapshot = record,
                            priceHistory = updatedHistory,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Failed to fetch snapshot for $code"
                    _uiState.update { current ->
                        if (current.activeCompanyCode != code) return@update current
                        current.copy(
                            isFetchingSnapshot = false,
                            errorMessage = msg,
                        )
                    }
                    _messages.emit(msg)
                }
        }
    }

    fun startStreaming() {
        val normalized = _uiState.value.companyInput.trim().uppercase()
        val validationError = validateCompanyCode(normalized)
        if (validationError != null) {
            _uiState.update { it.copy(inputError = validationError) }
            return
        }

        // If user typed a different company and hit Start Streaming directly, switch first cleanly
        if (normalized != _uiState.value.activeCompanyCode) {
            cancelStreamInternal()
            snapshotJob?.cancel()
            _uiState.update {
                it.copy(
                    companyInput = normalized,
                    activeCompanyCode = normalized,
                    inputError = null,
                    currentSnapshot = null,
                    priceHistory = emptyList(),
                    streamedRecords = emptyList(),
                    errorMessage = null,
                )
            }
        }

        // Prevent duplicate simultaneous streams
        if (streamJob?.isActive == true) return

        val targetCode = _uiState.value.activeCompanyCode
        val interval = _uiState.value.pollingIntervalSeconds

        streamJob = viewModelScope.launch {
            repository.streamCompany(targetCode, interval).collect { event ->
                if (_uiState.value.activeCompanyCode != targetCode) return@collect

                when (event) {
                    is ScreenerStreamEvent.Connecting -> {
                        _uiState.update {
                            it.copy(
                                connectionStatus = ScreenerConnectionStatus.CONNECTING,
                                errorMessage = null,
                            )
                        }
                    }
                    is ScreenerStreamEvent.Connected -> {
                        _uiState.update {
                            it.copy(
                                connectionStatus = ScreenerConnectionStatus.CONNECTED,
                                errorMessage = null,
                            )
                        }
                    }
                    is ScreenerStreamEvent.Data -> {
                        val rec = event.record
                        _uiState.update { current ->
                            val chartPoints = appendDedupedChartPoint(current.priceHistory, rec)
                            val updatedRecords = (listOf(rec) + current.streamedRecords).take(500)
                            current.copy(
                                connectionStatus = ScreenerConnectionStatus.CONNECTED,
                                currentSnapshot = rec,
                                priceHistory = chartPoints,
                                streamedRecords = updatedRecords,
                                errorMessage = null,
                            )
                        }
                    }
                    is ScreenerStreamEvent.Warning -> {
                        _messages.emit(event.message)
                    }
                    is ScreenerStreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                connectionStatus = ScreenerConnectionStatus.ERROR,
                                errorMessage = event.message,
                            )
                        }
                        _messages.emit(event.message)
                    }
                    is ScreenerStreamEvent.Completed -> {
                        _uiState.update {
                            it.copy(connectionStatus = ScreenerConnectionStatus.DISCONNECTED)
                        }
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        cancelStreamInternal()
        _uiState.update {
            it.copy(connectionStatus = ScreenerConnectionStatus.DISCONNECTED)
        }
    }

    private fun cancelStreamInternal() {
        streamJob?.cancel()
        streamJob = null
    }

    private fun appendDedupedChartPoint(
        existing: List<ScreenerRecord>,
        incoming: ScreenerRecord,
    ): List<ScreenerRecord> {
        if (incoming.price == null) return existing
        val last = existing.lastOrNull()
        if (last != null && last.time == incoming.time && last.price == incoming.price) {
            return existing
        }
        return (existing + incoming).takeLast(200)
    }

    private fun validateCompanyCode(code: String): String? {
        if (code.isBlank()) {
            return "Company ticker code is required"
        }
        if (code.contains("HTTP", ignoreCase = true) ||
            code.contains("SCREENER.IN", ignoreCase = true) ||
            code.contains("/") ||
            code.contains(".")
        ) {
            return "Enter an uppercase ticker code (e.g. RELIANCE), not a URL"
        }
        if (!companyRegex.matches(code)) {
            return "Use only uppercase letters, numbers, '_' or '-'"
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        snapshotJob?.cancel()
        cancelStreamInternal()
    }
}
