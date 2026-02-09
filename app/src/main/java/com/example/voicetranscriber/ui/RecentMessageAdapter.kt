package com.example.voicetranscriber.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.voicetranscriber.R
import com.example.voicetranscriber.data.VoiceMessage
import com.example.voicetranscriber.databinding.ItemSectionHeaderBinding
import com.example.voicetranscriber.databinding.ItemVoiceMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class RecentMessageAdapter(
    private val onTranscribe: (VoiceMessage) -> Unit,
    private val onPlay: (VoiceMessage) -> Unit
) : ListAdapter<RecentMessageAdapter.RecentItem, RecyclerView.ViewHolder>(DiffCallback) {

    private var transcribingUri: String? = null
    private var playingUri: String? = null

    fun setTranscribingUri(uri: String?) {
        val oldUri = transcribingUri
        transcribingUri = uri
        notifyUriStateChanged(oldUri, uri)
    }

    fun setPlayingUri(uri: String?) {
        val oldUri = playingUri
        playingUri = uri
        notifyUriStateChanged(oldUri, uri)
    }

    sealed class RecentItem {
        data class Header(val source: String, val count: Int) : RecentItem()
        data class Message(val message: VoiceMessage) : RecentItem()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecentItem.Header -> TYPE_HEADER
            is RecentItem.Message -> TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemSectionHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            MessageViewHolder(ItemVoiceMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is RecentItem.Header) {
            holder.bind(item)
        } else if (holder is MessageViewHolder && item is RecentItem.Message) {
            holder.bind(item.message)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: RecentItem.Header) {
            binding.tvTitle.text = if (header.source.equals("Other", ignoreCase = true)) {
                binding.root.context.getString(R.string.other)
            } else {
                header.source
            }
            binding.tvCount.text = binding.root.context.getString(R.string.items_count, header.count)
            val iconRes = when (header.source.lowercase()) {
                "whatsapp" -> R.drawable.ic_whatsapp
                "telegram" -> R.drawable.ic_telegram
                "signal" -> R.drawable.ic_signal
                else -> R.drawable.ic_recent
            }
            binding.ivIcon.setImageResource(iconRes)
        }
    }

    inner class MessageViewHolder(private val binding: ItemVoiceMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: VoiceMessage) {
            val minutes = (message.duration / 1000) / 60
            val seconds = (message.duration / 1000) % 60
            binding.tvDuration.text = binding.root.context.getString(R.string.duration, minutes, seconds)
            
            val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            binding.tvTimestamp.text = sdf.format(Date(message.timestamp))
            
            
            val isTranscribing = transcribingUri == message.uri.toString()
            if (isTranscribing) {
                binding.btnAction.visibility = android.view.View.INVISIBLE
                binding.progressBar.visibility = android.view.View.VISIBLE
            } else {
                binding.btnAction.visibility = android.view.View.VISIBLE
                binding.progressBar.visibility = android.view.View.GONE
            }

            val isPlaying = playingUri == message.uri.toString()
            binding.ivPlayIcon.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

            binding.btnAction.setOnClickListener { onTranscribe(message) }
            binding.ivPlayIcon.setOnClickListener { onPlay(message) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_MESSAGE = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<RecentItem>() {
            override fun areItemsTheSame(oldItem: RecentItem, newItem: RecentItem): Boolean {
                return if (oldItem is RecentItem.Header && newItem is RecentItem.Header) {
                    oldItem.source == newItem.source
                } else if (oldItem is RecentItem.Message && newItem is RecentItem.Message) {
                    oldItem.message.uri == newItem.message.uri
                } else false
            }

            override fun areContentsTheSame(oldItem: RecentItem, newItem: RecentItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private fun notifyUriStateChanged(oldUri: String?, newUri: String?) {
        if (oldUri == newUri) return

        oldUri?.let { previous ->
            findMessagePosition(previous)?.let { notifyItemChanged(it) }
        }
        newUri?.let { current ->
            findMessagePosition(current)?.let { notifyItemChanged(it) }
        }
    }

    private fun findMessagePosition(targetUri: String): Int? {
        val index = currentList.indexOfFirst { item ->
            item is RecentItem.Message && item.message.uri.toString() == targetUri
        }
        return if (index >= 0) index else null
    }
}
