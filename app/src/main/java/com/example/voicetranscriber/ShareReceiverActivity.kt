package com.example.voicetranscriber

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.voicetranscriber.databinding.ActivityShareReceiverBinding
import com.example.voicetranscriber.model.TranscriptionManager
import com.example.voicetranscriber.model.TranscriptionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = newBase.getSharedPreferences("keetquiet_prefs", android.content.Context.MODE_PRIVATE)
            .getString("pref_language", "system") ?: "system"
            
        val locale = if (lang == "system") {
            java.util.Locale.getDefault()
        } else {
            java.util.Locale(lang)
        }
        
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private lateinit var binding: ActivityShareReceiverBinding

    @Inject
    lateinit var transcriptionManager: TranscriptionManager

    @Inject
    lateinit var processVoiceMessageUseCase: com.example.voicetranscriber.domain.ProcessVoiceMessageUseCase

    @Inject
    lateinit var preferenceManager: com.example.voicetranscriber.data.PreferenceManager

    private var currentTranscriptionText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme and language after injection
        preferenceManager.applyTheme(preferenceManager.getTheme())
        preferenceManager.applyLanguage(preferenceManager.getLanguage())
        
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup Button Listeners
        binding.btnClose.setOnClickListener { finish() }
        binding.btnCloseError.setOnClickListener { finish() }
        
        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Transcription", currentTranscriptionText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, currentTranscriptionText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }

        if (intent?.action == Intent.ACTION_SEND) {
            handleSendIntent(intent)
        } else {
            finish()
        }

        observeState()
        observeTranscriptionResult()
        observeTranscriptionFailure()
    }

    private fun handleSendIntent(intent: Intent) {
        val audioUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (audioUri != null) {
            val callingPackage = callingActivity?.packageName ?: "Unknown"
            val appName = when {
                callingPackage.contains("whatsapp") -> "WhatsApp"
                callingPackage.contains("signal") -> "Signal"
                else -> getString(R.string.unknown_app)
            }

            lifecycleScope.launch {
                binding.loadingLayout.visibility = View.VISIBLE
                binding.resultLayout.visibility = View.GONE
                binding.errorLayout.visibility = View.GONE
                
                binding.statusText.text = getString(R.string.converting_audio)
                
                val result = processVoiceMessageUseCase(
                    uri = audioUri,
                    sourceApp = appName,
                    originalFilename = audioUri.lastPathSegment ?: "audio"
                )
                
                result.onFailure { e ->
                    showError("Processing failed: ${e.message}")
                }
            }
        } else {
            finish()
        }
    }

    private fun showError(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.resultLayout.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
    }

    private fun observeState() {
        lifecycleScope.launch {
            transcriptionManager.state.collect { state ->
                when (state) {
                    is TranscriptionState.DownloadingModels -> {
                        binding.progressBar.isIndeterminate = false
                        binding.progressBar.progress = state.progress
                        binding.statusText.text = getString(R.string.downloading_model, state.progress)
                    }
                    is TranscriptionState.WaitingForModel -> {
                        binding.progressBar.isIndeterminate = true
                        binding.statusText.text = getString(R.string.waiting_for_model)
                    }
                    is TranscriptionState.Transcribing -> {
                        binding.progressBar.isIndeterminate = true
                        binding.statusText.text = getString(R.string.transcribing)
                    }
                    is TranscriptionState.Idle -> {
                        // Handled by observeTranscriptionResult
                    }
                    is TranscriptionState.Error -> {
                        showError(state.message)
                    }
                }
            }
        }
    }

    private fun observeTranscriptionResult() {
        lifecycleScope.launch {
            transcriptionManager.transcriptionComplete.collect { transcription ->
                currentTranscriptionText = transcription.text
                binding.tvResultContent.text = transcription.text
                
                // Transition to result view
                binding.loadingLayout.visibility = View.GONE
                binding.errorLayout.visibility = View.GONE
                binding.resultLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun observeTranscriptionFailure() {
        lifecycleScope.launch {
            transcriptionManager.transcriptionFailed.collect { message ->
                showError(message)
            }
        }
    }
}
