package com.example.voicetranscriber.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicetranscriber.R
import com.example.voicetranscriber.data.Transcription
import com.example.voicetranscriber.data.TranscriptionRepository
import com.example.voicetranscriber.data.VoiceMessage
import com.example.voicetranscriber.data.VoiceMessageDiscovery
import com.example.voicetranscriber.model.TranscriptionManager
import com.example.voicetranscriber.model.TranscriptionState
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
    private val processVoiceMessageUseCase: com.example.voicetranscriber.domain.ProcessVoiceMessageUseCase
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
            transcriptionManager.transcriptionComplete.collect { _: com.example.voicetranscriber.data.Transcription ->
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
