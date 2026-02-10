package io.github.feloriyan.keetquiet.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.feloriyan.keetquiet.R
import io.github.feloriyan.keetquiet.data.Transcription
import io.github.feloriyan.keetquiet.databinding.ItemTranscriptionBinding

class TranscriptionAdapter(
    private val onCopy: (Transcription) -> Unit,
    private val onShare: (Transcription) -> Unit,
    private val onDelete: (Transcription) -> Unit
) : ListAdapter<Transcription, TranscriptionAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemTranscriptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranscriptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            transcriptionText.text = item.text
            
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            dateText.text = root.context.getString(
                R.string.transcription_meta,
                relativeTime,
                item.sourceApp
            )

            sourceIcon.setImageResource(
                when {
                    item.sourceApp.contains("WhatsApp", ignoreCase = true) -> R.drawable.ic_whatsapp
                    item.sourceApp.contains("Signal", ignoreCase = true) -> R.drawable.ic_signal
                    item.sourceApp.contains("Telegram", ignoreCase = true) -> R.drawable.ic_telegram
                    else -> R.drawable.ic_files
                }
            )

            copyButton.setOnClickListener { onCopy(item) }
            shareButton.setOnClickListener { onShare(item) }
            deleteButton.setOnClickListener { onDelete(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Transcription>() {
        override fun areItemsTheSame(oldItem: Transcription, newItem: Transcription): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Transcription, newItem: Transcription): Boolean {
            return oldItem == newItem
        }
    }
}
