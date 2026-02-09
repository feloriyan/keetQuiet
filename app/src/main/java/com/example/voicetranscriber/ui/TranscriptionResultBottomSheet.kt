package com.example.voicetranscriber.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.voicetranscriber.R
import com.example.voicetranscriber.databinding.DialogTranscriptionResultBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TranscriptionResultBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogTranscriptionResultBinding? = null
    private val binding get() = _binding!!

    private var transcriptionText: String = ""

    companion object {
        const val TAG = "TranscriptionResultBottomSheet"
        private const val ARG_TEXT = "text"

        fun newInstance(text: String): TranscriptionResultBottomSheet {
            val fragment = TranscriptionResultBottomSheet()
            val args = Bundle()
            args.putString(ARG_TEXT, text)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transcriptionText = arguments?.getString(ARG_TEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTranscriptionResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvContent.text = transcriptionText

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Transcription", transcriptionText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, transcriptionText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }



    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                
                // Calculate screen height
                val displayMetrics = resources.displayMetrics
                val height = displayMetrics.heightPixels
                
                // Set height to 2/3 of screen
                val desiredHeight = (height * 0.66).toInt()
                
                sheet.layoutParams.height = desiredHeight
                behavior.peekHeight = desiredHeight
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
