package io.github.feloriyan.keetquiet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.feloriyan.keetquiet.databinding.ActivityMainBinding
import io.github.feloriyan.keetquiet.ui.HistoryFragment
import io.github.feloriyan.keetquiet.ui.RecentFragment
import io.github.feloriyan.keetquiet.ui.SettingsBottomSheet
import io.github.feloriyan.keetquiet.ui.TranscriptionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var preferenceManager: io.github.feloriyan.keetquiet.data.PreferenceManager

    @javax.inject.Inject
    lateinit var transcriptionManager: io.github.feloriyan.keetquiet.model.TranscriptionManager

    override fun attachBaseContext(newBase: android.content.Context) {
        // We need to inject preferenceManager manually because attachBaseContext happens before Hilt injection
        // However, since we are in a Hilt @AndroidEntryPoint, we can use EntryPointAccessors if needed,
        // but for simplicity, we can just use the shared preference directly or let applyLanguage handle it.
        // Actually, the most reliable way in Hilt is to get the pref from application context.
        
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

    private lateinit var binding: ActivityMainBinding
    
    private val viewModel: TranscriptionViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Permissions granted
            viewModel.refreshDiscoveredMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply theme and language after injection, but before inflation
        preferenceManager.applyTheme(preferenceManager.getTheme())
        preferenceManager.applyLanguage(preferenceManager.getLanguage())
        
        transcriptionManager.preloadModel()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        observeDownloadState()
        checkPermissions()

        binding.btnHistory.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (currentFragment is HistoryFragment) {
                replaceFragment(RecentFragment())
            } else {
                replaceFragment(HistoryFragment())
            }
        }

        binding.btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, SettingsBottomSheet.TAG)
        }

        // Clicking logo/title returns to Recent
        binding.ivLogo.setOnClickListener { replaceFragment(RecentFragment()) }
        binding.titleContainer.setOnClickListener { replaceFragment(RecentFragment()) }
        
        // Set default fragment
        if (savedInstanceState == null) {
            replaceFragment(RecentFragment())
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateHeaderIcon()
        }
    }

    private var downloadDialog: com.google.android.material.dialog.MaterialAlertDialogBuilder? = null
    private var progressDialog: android.app.Dialog? = null
    private var progressBar: com.google.android.material.progressindicator.LinearProgressIndicator? = null
    private var progressText: android.widget.TextView? = null

    private fun observeDownloadState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transcriptionState.collect { state ->
                    when (state) {
                        is io.github.feloriyan.keetquiet.model.TranscriptionState.DownloadingModels -> {
                            showDownloadDialog(state.progress)
                        }
                        is io.github.feloriyan.keetquiet.model.TranscriptionState.Idle,
                        is io.github.feloriyan.keetquiet.model.TranscriptionState.WaitingForModel,
                        is io.github.feloriyan.keetquiet.model.TranscriptionState.Transcribing -> {
                            hideDownloadDialog()
                        }
                        is io.github.feloriyan.keetquiet.model.TranscriptionState.Error -> {
                            hideDownloadDialog()
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle(R.string.error)
                                .setMessage(state.message)
                                .setPositiveButton(R.string.done, null)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun showDownloadDialog(progress: Int) {
        if (progressDialog == null) {
            val view = layoutInflater.inflate(R.layout.dialog_download_progress, null)
            progressBar = view.findViewById(R.id.downloadProgressBar)
            progressText = view.findViewById(R.id.tvDownloadStatus)
            
            progressDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download_title)
                .setView(view)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
        
        progressBar?.progress = progress
        progressText?.text = getString(R.string.downloading_model, progress)
    }

    private fun hideDownloadDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }

    private fun updateHeaderIcon() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment is HistoryFragment) {
            binding.btnHistory.setImageResource(R.drawable.ic_recent)
        } else {
            binding.btnHistory.setImageResource(R.drawable.ic_history)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun applyWindowInsets() {
        val headerStart = binding.header.paddingStart
        val headerTop = binding.header.paddingTop
        val headerEnd = binding.header.paddingEnd
        val headerBottom = binding.header.paddingBottom

        val containerStart = binding.fragmentContainer.paddingStart
        val containerTop = binding.fragmentContainer.paddingTop
        val containerEnd = binding.fragmentContainer.paddingEnd
        val containerBottom = binding.fragmentContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.header.setPaddingRelative(
                headerStart,
                headerTop + systemBars.top,
                headerEnd,
                headerBottom
            )

            binding.fragmentContainer.setPaddingRelative(
                containerStart,
                containerTop,
                containerEnd,
                containerBottom + systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .apply {
                if (fragment !is RecentFragment) {
                    addToBackStack(null)
                }
            }
            .commit()
        
        // Immediate update
        if (fragment is HistoryFragment) {
            binding.btnHistory.setImageResource(R.drawable.ic_recent)
        } else {
            binding.btnHistory.setImageResource(R.drawable.ic_history)
        }
    }
}
