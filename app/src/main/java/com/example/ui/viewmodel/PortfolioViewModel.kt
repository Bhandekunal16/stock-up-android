package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExtractResult
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.ServerReachability
import com.example.data.model.ServerStatusState
import com.example.data.repository.PortfolioRepository
import com.example.util.Formatters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed class PortfolioLoadState {
    data object Loading : PortfolioLoadState()
    data class Success(val snapshots: List<PortfolioSnapshot>) : PortfolioLoadState()
    data class Error(val message: String) : PortfolioLoadState()
}

sealed class SnapshotDetailState {
    data object Idle : SnapshotDetailState()
    data object Loading : SnapshotDetailState()
    data class Success(val snapshot: PortfolioSnapshot) : SnapshotDetailState()
    data class Error(val message: String) : SnapshotDetailState()
}

data class UploadUiState(
    val selectedUri: Uri? = null,
    val isUploading: Boolean = false,
    val extractResult: ExtractResult? = null,
    val errorMessage: String? = null,
)

data class PortfolioUiState(
    val loadState: PortfolioLoadState = PortfolioLoadState.Loading,
    val isRefreshing: Boolean = false,
    val uploadState: UploadUiState = UploadUiState(),
    val detailState: SnapshotDetailState = SnapshotDetailState.Idle,
    val serverStatus: ServerStatusState = ServerStatusState(),
    val isResetting: Boolean = false,
) {
    val snapshots: List<PortfolioSnapshot>
        get() = (loadState as? PortfolioLoadState.Success)?.snapshots.orEmpty()

    val latestSnapshot: PortfolioSnapshot?
        get() = snapshots.lastOrNull()
}

class PortfolioViewModel(
    private val repository: PortfolioRepository = PortfolioRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        loadAllSnapshots(isManualRefresh = false)
    }

    fun loadAllSnapshots(isManualRefresh: Boolean = true) {
        viewModelScope.launch {
            val hasExistingData = _uiState.value.loadState is PortfolioLoadState.Success
            _uiState.update {
                if (isManualRefresh && hasExistingData) {
                    it.copy(isRefreshing = true)
                } else {
                    it.copy(loadState = PortfolioLoadState.Loading, isRefreshing = false)
                }
            }

            repository.getAllSnapshots()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            loadState = PortfolioLoadState.Success(list),
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Unable to connect to portfolio backend"
                    if (isManualRefresh && hasExistingData) {
                        _uiState.update { it.copy(isRefreshing = false) }
                        _messages.emit(msg)
                    } else {
                        _uiState.update {
                            it.copy(
                                loadState = PortfolioLoadState.Error(msg),
                                isRefreshing = false,
                            )
                        }
                    }
                }
        }
    }

    fun loadSnapshotByDate(date: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(detailState = SnapshotDetailState.Loading) }
            repository.getSnapshotByDate(date)
                .onSuccess { snapshot ->
                    // Fallback to in-memory snapshot if backend returned empty for a date we already loaded
                    val resolved = if (snapshot.stocks.isEmpty()) {
                        _uiState.value.snapshots.firstOrNull { it.date == date } ?: snapshot
                    } else {
                        snapshot
                    }
                    _uiState.update { it.copy(detailState = SnapshotDetailState.Success(resolved)) }
                }
                .onFailure { err ->
                    val cached = _uiState.value.snapshots.firstOrNull { it.date == date }
                    if (cached != null) {
                        _uiState.update { it.copy(detailState = SnapshotDetailState.Success(cached)) }
                    } else {
                        _uiState.update {
                            it.copy(
                                detailState = SnapshotDetailState.Error(
                                    err.message ?: "Failed to load snapshot for $date"
                                )
                            )
                        }
                    }
                }
        }
    }

    fun selectUploadImage(uri: Uri?) {
        _uiState.update {
            it.copy(
                uploadState = it.uploadState.copy(
                    selectedUri = uri,
                    errorMessage = null,
                    extractResult = if (uri == null) null else it.uploadState.extractResult,
                )
            )
        }
    }

    fun clearUpload() {
        _uiState.update { it.copy(uploadState = UploadUiState()) }
    }

    fun uploadSelectedImage(context: Context) {
        val uri = _uiState.value.uploadState.selectedUri ?: return
        if (_uiState.value.uploadState.isUploading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    uploadState = it.uploadState.copy(
                        isUploading = true,
                        errorMessage = null,
                    )
                )
            }

            repository.extractPortfolioFromUri(context, uri)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            uploadState = it.uploadState.copy(
                                isUploading = false,
                                extractResult = result,
                                errorMessage = null,
                            )
                        )
                    }
                    _messages.emit("Extracted ${result.stocks.size} holdings for ${result.date}")
                    loadAllSnapshots(isManualRefresh = true)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Screenshot extraction failed"
                    _uiState.update {
                        it.copy(
                            uploadState = it.uploadState.copy(
                                isUploading = false,
                                errorMessage = msg,
                            )
                        )
                    }
                }
        }
    }

    fun checkServerStatus() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    serverStatus = it.serverStatus.copy(
                        reachability = ServerReachability.CHECKING,
                        detail = null,
                    )
                )
            }
            val now = Formatters.formatIstTimestamp(Instant.now().toString())
            repository.checkServerUp()
                .onSuccess { isUp ->
                    _uiState.update {
                        it.copy(
                            serverStatus = ServerStatusState(
                                reachability = if (isUp) ServerReachability.UP else ServerReachability.DOWN,
                                lastCheckedAt = now,
                                detail = if (isUp) "Portfolio API is reachable" else "Unexpected server response",
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            serverStatus = ServerStatusState(
                                reachability = ServerReachability.DOWN,
                                lastCheckedAt = now,
                                detail = err.message ?: "Connection refused or timed out",
                            )
                        )
                    }
                }
        }
    }

    fun resetAllSnapshots() {
        if (_uiState.value.isResetting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            repository.deleteAllSnapshots()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isResetting = false,
                            loadState = PortfolioLoadState.Success(emptyList()),
                            uploadState = UploadUiState(),
                        )
                    }
                    _messages.emit("All portfolio snapshots cleared")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isResetting = false) }
                    _messages.emit(err.message ?: "Failed to reset snapshots")
                }
        }
    }
}
