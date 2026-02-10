package io.github.feloriyan.keetquiet.model

import android.content.Context
import android.util.Log
import io.github.feloriyan.keetquiet.data.Transcription
import io.github.feloriyan.keetquiet.data.TranscriptionRepository
import io.github.feloriyan.keetquiet.di.ApplicationScope
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.WaveReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TranscriptionManager handles the complete transcription workflow including:
 * 
 * 1. Model downloading and management
 * 2. Speech recognition using Sherpa-ONNX
 * 3. Queue management for multiple transcription requests
 * 4. State management and progress tracking
 *
 * The manager uses a state machine pattern to track the transcription process:
 * - Idle: Ready to accept new transcription requests
 * - DownloadingModels: Downloading required speech recognition models
 * - WaitingForModel: Waiting for model initialization before processing
 * - Transcribing: Actively processing audio files
 * - Error: Error state with descriptive message
 *
 * All transcription operations are performed on background threads using coroutines
 * to ensure smooth UI performance.
 */
@Singleton
class TranscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader,
    private val repository: TranscriptionRepository,
    private val preferenceManager: io.github.feloriyan.keetquiet.data.PreferenceManager,
    private val fileLogger: io.github.feloriyan.keetquiet.util.FileLogger,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private var recognizer: OfflineRecognizer? = null
    private val isTranscribing = AtomicBoolean(false)
    private val pendingFiles = ConcurrentLinkedQueue<TranscriptionTask>()
    private var isModelLoaded = false
    private var isInitializing = false

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    // Use SharedFlow for one-time events like completion
    private val _transcriptionComplete = kotlinx.coroutines.flow.MutableSharedFlow<Transcription>()
    val transcriptionComplete = _transcriptionComplete.asSharedFlow()
    private val _transcriptionFailed = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val transcriptionFailed = _transcriptionFailed.asSharedFlow()

    companion object {
        private const val TAG = "TranscriptionManager"
    }

    /**
     * Represents a transcription task with file and metadata.
     *
     * @property file The audio file to transcribe
     * @property sourceApp The app that created the voice message
     * @property originalFilename The original filename for reference
     */
    data class TranscriptionTask(
        val file: File,
        val sourceApp: String,
        val originalFilename: String
    )

    /**
     * Preloads the transcription model if not already loaded.
     * This is useful for reducing latency when the user first requests transcription.
     */
    fun preloadModel() {
        if (shouldInitializeModel()) {
            logDebug("Preloading model...")
            initialize()
        }
    }

    /**
     * Initializes the transcription system by downloading models if needed.
     * This should be called before attempting any transcription.
     */
    fun initialize() {
        if (!canInitialize()) return
        
        setInitializingState(true)
        
        applicationScope.launch {
            handleModelDownload()
        }
    }

    // --- Model Management ---

    private fun shouldInitializeModel(): Boolean {
        return !isInitializing && !isModelLoaded
    }

    private fun canInitialize(): Boolean {
        return !isInitializing && !isModelLoaded
    }

    private fun setInitializingState(isInitializing: Boolean) {
        this.isInitializing = isInitializing
    }

    private suspend fun handleModelDownload() {
        modelDownloader.ensureModelsAvailable().collect { downloadState ->
            when (downloadState) {
                is DownloadState.Checking -> handleCheckingState()
                is DownloadState.Downloading -> handleDownloadingState(downloadState.progress)
                is DownloadState.Complete -> handleDownloadComplete()
                is DownloadState.Error -> handleDownloadError(downloadState.message)
            }
        }
    }

    private fun handleCheckingState() {
        // No action needed during checking phase
    }

    private fun handleDownloadingState(progress: Int) {
        updateState(TranscriptionState.DownloadingModels(progress))
    }

    private fun handleDownloadComplete() {
        loadModel()
    }

    private fun handleDownloadError(message: String) {
        updateState(TranscriptionState.Error(message))
        setInitializingState(false)
    }

    // --- State Management ---

    private fun updateState(newState: TranscriptionState) {
        _state.value = newState
    }

    private fun updateStateIfCurrent(expectedState: TranscriptionState, newState: TranscriptionState) {
        if (_state.value == expectedState) {
            _state.value = newState
        }
    }

    // --- Logging Helpers ---

    private fun logDebug(message: String) {
        fileLogger.log(TAG, "[DEBUG] $message")
    }

    private fun logInfo(message: String) {
        fileLogger.log(TAG, message)
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (exception != null) {
            fileLogger.logError(TAG, message, exception)
        } else {
            fileLogger.logError(TAG, message)
        }
    }

    private fun loadModel() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                loadModelFiles()
            } catch (e: Exception) {
                handleModelLoadError(e)
            }
        }
    }

    private suspend fun loadModelFiles() {
        val modelDir = getModelDirectory()
        logInfo("Loading models from: $modelDir")
        
        val modelFiles = validateModelFiles(modelDir) ?: return
        
        val config = createRecognizerConfig(modelFiles)
        initializeRecognizer(config)
        
        handleModelLoadSuccess()
    }

    private fun getModelDirectory(): java.io.File {
        return java.io.File(modelDownloader.getModelDirectory())
    }

    private suspend fun validateModelFiles(modelDir: java.io.File): ModelFiles? {
        val encoder = java.io.File(modelDir, "encoder.int8.onnx")
        val decoder = java.io.File(modelDir, "decoder.int8.onnx")
        val joiner = java.io.File(modelDir, "joiner.int8.onnx")
        val tokens = java.io.File(modelDir, "tokens.txt")
        
        return if (encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()) {
            ModelFiles(encoder, decoder, joiner, tokens)
        } else {
            val missing = listOf(encoder, decoder, joiner, tokens).filter { !it.exists() }.joinToString { it.name }
            logError("Missing model files: $missing")
            updateState(TranscriptionState.Error("Missing model files: $missing"))
            setInitializingState(false)
            null
        }
    }

    private fun createRecognizerConfig(modelFiles: ModelFiles): OfflineRecognizerConfig {
        return OfflineRecognizerConfig(
            featConfig = com.k2fsa.sherpa.onnx.FeatureConfig(
                sampleRate = 16000,
                featureDim = 128
            ),
            modelConfig = com.k2fsa.sherpa.onnx.OfflineModelConfig(
                transducer = com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig(
                    encoder = modelFiles.encoder.absolutePath,
                    decoder = modelFiles.decoder.absolutePath,
                    joiner = modelFiles.joiner.absolutePath,
                ),
                tokens = modelFiles.tokens.absolutePath,
                numThreads = preferenceManager.getNumThreads(),
                debug = false,
                modelType = "nemo_transducer"
            )
        )
    }

    private fun initializeRecognizer(config: OfflineRecognizerConfig) {
        logInfo("Initializing OfflineRecognizer with ${preferenceManager.getNumThreads()} threads...")
        recognizer = OfflineRecognizer(null, config)
        logInfo("OfflineRecognizer initialized successfully")
    }

    private fun handleModelLoadSuccess() {
        isModelLoaded = true
        isInitializing = false
        
        if (pendingFiles.isNotEmpty()) {
            processQueue()
        } else if (shouldUpdateStateToIdle()) {
            updateState(TranscriptionState.Idle)
        }
    }

    private fun shouldUpdateStateToIdle(): Boolean {
        return _state.value is TranscriptionState.WaitingForModel || _state.value is TranscriptionState.DownloadingModels
    }

    private fun handleModelLoadError(e: Exception) {
        logError("Failed to initialize recognizer", e)
        updateState(TranscriptionState.Error("Model initialization failed: ${e.message}"))
        setInitializingState(false)
    }

    private data class ModelFiles(
        val encoder: java.io.File,
        val decoder: java.io.File,
        val joiner: java.io.File,
        val tokens: java.io.File
    )

    fun enqueueTranscription(file: File, sourceApp: String, originalFilename: String) {
        fileLogger.log(TAG, "Enqueueing transcription for: ${file.name} (from $sourceApp)")
        pendingFiles.add(TranscriptionTask(file, sourceApp, originalFilename))
        if (isModelLoaded) {
            processQueue()
        } else {
            Log.d(TAG, "Model not loaded, waiting for model...")
            _state.value = TranscriptionState.WaitingForModel
            initialize()
        }
    }

    private fun processQueue() {
        applicationScope.launch {
            if (!isTranscribing.compareAndSet(false, true)) {
                Log.d(TAG, "Already transcribing, task added to queue")
                return@launch
            }

            try {
                while (pendingFiles.isNotEmpty() && isModelLoaded) {
                    val task = pendingFiles.peek() ?: break
                    _state.value = TranscriptionState.Transcribing
                    Log.d(TAG, "Processing task: ${task.originalFilename}")
                    
                    withContext(Dispatchers.IO) {
                        try {
                            if (!task.file.exists()) {
                                Log.e(TAG, "Task file missing: ${task.file.absolutePath}")
                                _transcriptionFailed.emit("Audio file could not be accessed.")
                                return@withContext
                            }

                            // ... (WaveReader and stream creation unchanged)

                            val waveReader = WaveReader.readWave(task.file.absolutePath)

                            val stream = recognizer?.createStream()
                            if (stream == null) {
                                Log.e(TAG, "Failed to create stream")
                                _transcriptionFailed.emit("Transcription engine is not ready.")
                                return@withContext
                            }
                            
                            stream.acceptWaveform(waveReader.samples, waveReader.sampleRate)
                            recognizer?.decode(stream)
                            
                            val resultText = (recognizer?.getResult(stream)?.text ?: "").trim()
                            Log.d(TAG, "Transcription result: $resultText")
                            
                            if (resultText.isNotBlank()) {
                                val transcription = Transcription(
                                    text = resultText,
                                    sourceApp = task.sourceApp,
                                    originalFilename = task.originalFilename,
                                    timestamp = System.currentTimeMillis()
                                )
                                repository.insert(transcription)
                                _transcriptionComplete.emit(transcription) // Emit Success
                            } else {
                                Log.w(TAG, "Transcription result was empty")
                                _transcriptionFailed.emit("No speech could be recognized in this audio.")
                            }
                            
                            task.file.delete()
                            stream.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Transcription failed for ${task.file.name}", e)
                            val errorMessage = e.message ?: "Unknown error"
                            _transcriptionFailed.emit("Transcription failed: $errorMessage")
                        } finally {
                            pendingFiles.poll()
                        }
                        Unit
                    }
                }
            } finally {
                isTranscribing.set(false)
                if (pendingFiles.isEmpty()) {
                    _state.value = TranscriptionState.Idle
                    Log.d(TAG, "Queue processing complete, back to Idle")
                }
            }
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
        isModelLoaded = false
        isInitializing = false
    }
}

sealed class TranscriptionState {
    object Idle : TranscriptionState()
    data class DownloadingModels(val progress: Int) : TranscriptionState()
    object WaitingForModel : TranscriptionState()
    object Transcribing : TranscriptionState()
    data class Error(val message: String) : TranscriptionState()
}
