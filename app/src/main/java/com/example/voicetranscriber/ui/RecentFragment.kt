package com.example.voicetranscriber.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voicetranscriber.R
import com.example.voicetranscriber.data.VoiceMessage
import com.example.voicetranscriber.databinding.FragmentRecentBinding
import com.example.voicetranscriber.ui.TranscriptionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * RecentFragment displays the main screen of the application showing recently
 * discovered voice messages that can be transcribed.
 *
 * Key features:
 * 1. List of voice messages grouped by source app
 * 2. Source filtering (All, WhatsApp, Telegram, Signal)
 * 3. Audio playback functionality
 * 4. Transcription initiation
 * 5. Pull-to-refresh for discovering new messages
 *
 * The fragment follows MVVM architecture with ViewModel handling the business logic
 * and the fragment focusing on UI presentation and user interaction.
 */
@AndroidEntryPoint
class RecentFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TranscriptionViewModel by activityViewModels()

    // Media playback state
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var activeUri: String? = null
    private var selectedSource: String? = null // null means "All"

    // UI Components
    private lateinit var messageAdapter: RecentMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeUiComponents()
        setupObservers()
        setupEventListeners()
    }

    // --- Initialization ---

    private fun initializeUiComponents() {
        messageAdapter = createMessageAdapter()
        configureRecyclerView()
        configureSwipeRefresh()
        setupSourceFilterChips()
    }

    private fun createMessageAdapter(): RecentMessageAdapter {
        return RecentMessageAdapter(
            onTranscribe = { message -> handleTranscribeRequest(message) },
            onPlay = { message -> handlePlayRequest(message.uri) }
        )
    }

    private fun configureRecyclerView() {
        binding.rvRecentMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
            adapter = messageAdapter
        }
    }

    private fun configureSwipeRefresh() {
        binding.swipeRefresh.apply {
            setOnRefreshListener { refreshMessages() }
            setColorSchemeResources(R.color.keet_blue_accent)
        }
    }

    // --- Event Handling ---

    private fun setupEventListeners() {
        setupSourceFilterChips()
    }

    private fun setupSourceFilterChips() {
        binding.chipAll.setOnClickListener { selectSource(null) }
        binding.chipWhatsApp.setOnClickListener { selectSource("WhatsApp") }
        binding.chipTelegram.setOnClickListener { selectSource("Telegram") }
        binding.chipSignal.setOnClickListener { selectSource("Signal") }
    }

    private fun handleTranscribeRequest(message: VoiceMessage) {
        viewModel.transcribeFile(message)
    }

    private fun handlePlayRequest(uri: android.net.Uri) {
        playAudio(uri)
    }

    private fun refreshMessages() {
        viewModel.refreshDiscoveredMessages()
    }

    // --- Source Filtering ---

    private fun selectSource(source: String?) {
        selectedSource = source
        updateChipStyles()
        refreshMessageList()
    }

    private fun refreshMessageList() {
        val messages = viewModel.discoveredMessages.value
        updateList(messageAdapter, messages)
    }

    private fun updateChipStyles() {
        val chips = listOf(
            binding.chipAll to null,
            binding.chipWhatsApp to "WhatsApp",
            binding.chipTelegram to "Telegram",
            binding.chipSignal to "Signal"
        )

        chips.forEach { (view, source) ->
            val isSelected = selectedSource == source
            view.setBackgroundResource(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            view.setTextColor(getChipTextColor(isSelected))
            view.setTypeface(null, getChipTypeface(isSelected))
        }
    }

    private fun getChipTextColor(isSelected: Boolean): Int {
        return if (isSelected) android.graphics.Color.WHITE 
               else resources.getColor(R.color.text_primary, null)
    }

    private fun getChipTypeface(isSelected: Boolean): Int {
        return if (isSelected) android.graphics.Typeface.BOLD 
               else android.graphics.Typeface.NORMAL
    }

    // --- Data Processing ---

    private fun updateList(adapter: RecentMessageAdapter, messages: List<VoiceMessage>) {
        val filteredMessages = filterMessagesBySource(messages)
        val groupedItems = createGroupedItems(filteredMessages)
        
        adapter.submitList(groupedItems) {
            binding.rvRecentMessages.requestLayout()
            updateRecyclerViewVisibility(groupedItems)
        }
    }

    private fun filterMessagesBySource(messages: List<VoiceMessage>): List<VoiceMessage> {
        return if (selectedSource == null) messages 
               else messages.filter { it.source == selectedSource }
    }

    private fun createGroupedItems(messages: List<VoiceMessage>): List<RecentMessageAdapter.RecentItem> {
        val groupedItems = mutableListOf<RecentMessageAdapter.RecentItem>()
        val sources = messages.groupBy { it.source }
        
        sources.forEach { (source, msgs) ->
            groupedItems.add(RecentMessageAdapter.RecentItem.Header(source, msgs.size))
            msgs.forEach { groupedItems.add(RecentMessageAdapter.RecentItem.Message(it)) }
        }
        
        return groupedItems
    }

    private fun updateRecyclerViewVisibility(items: List<RecentMessageAdapter.RecentItem>) {
        binding.rvRecentMessages.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // --- Media Playback ---

    private fun playAudio(uri: android.net.Uri) {
        if (isPlayingSameUri(uri)) {
            stopCurrentPlayback()
            return
        }
        
        playNewAudio(uri)
    }

    private fun isPlayingSameUri(uri: android.net.Uri): Boolean {
        return mediaPlayer?.isPlaying == true && uri.toString() == currentPlayingUriString()
    }

    private fun stopCurrentPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        messageAdapter.setPlayingUri(null)
    }

    private fun playNewAudio(uri: android.net.Uri) {
        try {
            releaseCurrentPlayer()
            initializeNewPlayer(uri)
        } catch (e: Exception) {
            handlePlaybackError()
        }
    }

    private fun releaseCurrentPlayer() {
        mediaPlayer?.release()
    }

    private fun initializeNewPlayer(uri: android.net.Uri) {
        mediaPlayer = android.media.MediaPlayer().apply {
            setDataSource(requireContext(), uri)
            setOnPreparedListener { startPlayback(it, uri) }
            setOnCompletionListener { handlePlaybackCompletion(it) }
            setOnErrorListener { _, _, _ -> handlePlaybackError() }
            prepareAsync()
        }
    }

    private fun startPlayback(player: android.media.MediaPlayer, uri: android.net.Uri) {
        player.start()
        activeUri = uri.toString()
        messageAdapter.setPlayingUri(activeUri)
    }

    private fun handlePlaybackCompletion(player: android.media.MediaPlayer) {
        messageAdapter.setPlayingUri(null)
        player.release()
        mediaPlayer = null
        activeUri = null
    }

    private fun handlePlaybackError(): Boolean {
        messageAdapter.setPlayingUri(null)
        activeUri = null
        showPlaybackErrorToast()
        return false
    }

    private fun showPlaybackErrorToast() {
        android.widget.Toast.makeText(
            requireContext(), 
            "Playback failed", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun currentPlayingUriString(): String? {
        return activeUri
    }

    // --- Lifecycle ---

    override fun onResume() {
        super.onResume()
        refreshMessages()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cleanupResources()
    }

    private fun cleanupResources() {
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }

    // --- Observer Setup ---

    private fun setupObservers() {
        observeDiscoveredMessages()
        observeRefreshingState()
        observeTranscriptionState()
        observeTranscriptionResults()
        observeUiEvents()
    }

    private fun observeDiscoveredMessages() {
        viewModel.discoveredMessages.asLiveData().observe(viewLifecycleOwner) { messages ->
            updateList(messageAdapter, messages)
        }
    }

    private fun observeRefreshingState() {
        viewModel.isRefreshing.asLiveData().observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefresh.isRefreshing = isRefreshing
        }
    }

    private fun observeTranscriptionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentTranscribingUri.collect { uri ->
                    messageAdapter.setTranscribingUri(uri)
                }
            }
        }
    }

    private fun observeTranscriptionResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transcriptionComplete.collect { transcription ->
                    messageAdapter.setTranscribingUri(null)
                    showTranscriptionResult(transcription)
                }
            }
        }
    }

    private fun showTranscriptionResult(transcription: com.example.voicetranscriber.data.Transcription) {
        TranscriptionResultBottomSheet.newInstance(transcription.text)
            .show(parentFragmentManager, TranscriptionResultBottomSheet.TAG)
    }

    private fun observeUiEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is TranscriptionViewModel.UiEvent.ShowToast -> {
                            Toast.makeText(requireContext(), event.messageResId, Toast.LENGTH_SHORT).show()
                        }
                        is TranscriptionViewModel.UiEvent.ShowError -> {
                            Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
