package com.example.voicetranscriber.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.voicetranscriber.R
import com.example.voicetranscriber.data.PreferenceManager
import com.example.voicetranscriber.databinding.DialogSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "SettingsBottomSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Language setup
        val languageMap = linkedMapOf(
            "System" to PreferenceManager.LANG_SYSTEM,
            "English" to "en",
            "Deutsch" to "de",
            "Български" to "bg",
            "Hrvatski" to "hr",
            "Čeština" to "cs",
            "Dansk" to "da",
            "Nederlands" to "nl",
            "Eesti" to "et",
            "Suomi" to "fi",
            "Français" to "fr",
            "Ελληνικά" to "el",
            "Magyar" to "hu",
            "Italiano" to "it",
            "Latviešu" to "lv",
            "Lietuvių" to "lt",
            "Malti" to "mt",
            "Polski" to "pl",
            "Português" to "pt",
            "Română" to "ro",
            "Русский" to "ru",
            "Slovenčina" to "sk",
            "Slovenščina" to "sl",
            "Español" to "es",
            "Svenska" to "sv",
            "Українська" to "uk"
        )

        val languages = languageMap.keys.toList()
        val languageCodes = languageMap.values.toList()

        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = adapter

        val currentLang = preferenceManager.getLanguage()
        val selectionIndex = languageCodes.indexOf(currentLang).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(selectionIndex)

        // Theme setup
        val themeMap = linkedMapOf(
            getString(R.string.theme_system) to PreferenceManager.THEME_SYSTEM,
            getString(R.string.theme_light) to PreferenceManager.THEME_LIGHT,
            getString(R.string.theme_dark) to PreferenceManager.THEME_DARK
        )

        val themes = themeMap.keys.toList()
        val themeCodes = themeMap.values.toList()

        val themeAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = themeAdapter

        val currentTheme = preferenceManager.getTheme()
        val themeSelectionIndex = themeCodes.indexOf(currentTheme).takeIf { it >= 0 } ?: 0
        binding.spinnerTheme.setSelection(themeSelectionIndex)

        // Threads setup (Read-only)
        val currentThreads = preferenceManager.getNumThreads()
        binding.tvThreadCount.text = getString(R.string.thread_count_auto, currentThreads)

        binding.btnDone.setOnClickListener {
            val selectedPosition = binding.spinnerLanguage.selectedItemPosition
            val selectedLang = languageCodes.getOrElse(selectedPosition) { PreferenceManager.LANG_SYSTEM }
            
            val selectedThemePosition = binding.spinnerTheme.selectedItemPosition
            val selectedTheme = themeCodes.getOrElse(selectedThemePosition) { PreferenceManager.THEME_SYSTEM }

            var needsRecreate = false

            if (selectedLang != currentLang) {
                preferenceManager.setLanguage(selectedLang)
                needsRecreate = true
            }

            if (selectedTheme != currentTheme) {
                preferenceManager.setTheme(selectedTheme)
                needsRecreate = true
            }

            if (needsRecreate) {
                activity?.recreate()
            }
            dismiss()
        }

        // Donate button - opens Buy Me a Coffee in browser
        binding.btnDonate.setOnClickListener {
            val url = "https://buymeacoffee.com/feloriyan"
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.donation_open_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateThreadCountText(count: Int) {
        // No longer used, but kept to avoid breaking if called elsewhere, though it's private.
        // Can be removed or ignored.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
