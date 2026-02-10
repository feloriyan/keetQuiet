package io.github.feloriyan.keetquiet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.feloriyan.keetquiet.R
import io.github.feloriyan.keetquiet.data.Transcription
import io.github.feloriyan.keetquiet.data.TranscriptionRepository
import io.github.feloriyan.keetquiet.data.VoiceMessage
import io.github.feloriyan.keetquiet.data.VoiceMessageDiscovery
import io.github.feloriyan.keetquiet.model.TranscriptionManager
import io.github.feloriyan.keetquiet.model.TranscriptionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    private val repository: TranscriptionRepository,
    private val transcriptionManager: TranscriptionManager,
    private val discovery: VoiceMessageDiscovery,
    private val processVoiceMessageUseCase: io.github.feloriyan.keetquiet.domain.ProcessVoiceMessageUseCase
) : ViewModel() {

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowToast(val messageResId: Int) : UiEvent()
        data class ShowError(val message: String) : UiEvent()
    }

    private val _discoveredMessages = MutableStateFlow<List<VoiceMessage>>(emptyList())
    val discoveredMessages: StateFlow<List<VoiceMessage>> = _discoveredMessages

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val transcriptions: StateFlow<List<Transcription>> = repository.allTranscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transcriptionState: StateFlow<TranscriptionState> = transcriptionManager.state

    // Observe completion events
    val transcriptionComplete = transcriptionManager.transcriptionComplete

    // Track which file is currently being processed to show loading spinner
    private val _currentTranscribingUri = MutableStateFlow<String?>(null)
    val currentTranscribingUri: StateFlow<String?> = _currentTranscribingUri

    init {
        refreshDiscoveredMessages()
        
        viewModelScope.launch {
            transcriptionManager.transcriptionComplete.collect { _: io.github.feloriyan.keetquiet.data.Transcription ->
                _currentTranscribingUri.value = null
            }
        }

        viewModelScope.launch {
            transcriptionManager.transcriptionFailed.collect { message ->
                _currentTranscribingUri.value = null
                _uiEvent.emit(UiEvent.ShowError(message))
            }
        }

        viewModelScope.launch {
            transcriptionManager.state.collect { state ->
                if (state is TranscriptionState.Idle || state is TranscriptionState.Error) {
                    _currentTranscribingUri.value = null
                }
            }
        }
    }

    fun refreshDiscoveredMessages() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500) // Allow time for permissions/MediaStore to sync
            _discoveredMessages.value = discovery.discoverVoiceMessages()
            _isRefreshing.value = false
        }
    }

    fun transcribeFile(message: VoiceMessage) {
        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowToast(R.string.processing))
            _currentTranscribingUri.value = message.uri.toString() // Set loading state
            
            val result = processVoiceMessageUseCase(
                uri = message.uri,
                sourceApp = message.source,
                originalFilename = message.name
            )
            
            result.onFailure { e ->
                _uiEvent.emit(UiEvent.ShowError("Processing failed: ${e.message}"))
                _currentTranscribingUri.value = null // Reset on error
            }
        }
    }

    fun deleteTranscription(transcription: Transcription) {
        viewModelScope.launch {
            repository.delete(transcription)
        }
    }
}
