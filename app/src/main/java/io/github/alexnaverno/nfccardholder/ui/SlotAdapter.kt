package io.github.alexnaverno.nfccardholder.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.alexnaverno.nfccardholder.R
import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.databinding.ItemSlotBinding
import io.github.alexnaverno.nfccardholder.model.CardRecord
import io.github.alexnaverno.nfccardholder.model.CardStore
import java.util.Locale

/**
 * The single screen list: ten slots, each with copy / transmit / delete.
 *
 * All ten rows always exist, so empty slots are rendered as "slot N" placeholders and the whole
 * list is refreshed whenever anything changes.
 */
class SlotAdapter(
    private val onCopy: (Int) -> Unit,
    private val onApply: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onDetails: (Int) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private val items: MutableList<CardRecord?> = MutableList(CardStore.SLOT_COUNT) { null }
    private var activeSlot: Int = -1
    private var pendingSlot: Int = -1
    private var reading: Boolean = false

    fun submit(slots: List<CardRecord?>, active: Int, pending: Int, isReading: Boolean) {
        for (index in items.indices) items[index] = slots.getOrNull(index)
        activeSlot = active
        pendingSlot = pending
        reading = isReading
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemCount(): Int = CardStore.SLOT_COUNT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder =
        SlotViewHolder(ItemSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class SlotViewHolder(private val binding: ItemSlotBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            val context = binding.root.context
            val record = items[position]
            val isActive = activeSlot == position
            val isPending = reading && pendingSlot == position
            val number = position + 1

            binding.slotIndex.text = String.format(Locale.US, "%02d", number)
            binding.root.setOnClickListener { onDetails(position) }
            binding.btnCopy.setOnClickListener { onCopy(position) }
            binding.btnApply.setOnClickListener { onApply(position) }
            binding.btnDelete.setOnClickListener { onDelete(position) }

            binding.btnCopy.setIconResource(if (isPending) R.drawable.ic_stop else R.drawable.ic_copy)
            binding.btnCopy.contentDescription = context.getString(
                if (isPending) R.string.action_copy_cancel else R.string.action_copy,
                number
            )
            binding.btnApply.contentDescription = context.getString(
                if (isActive) R.string.action_apply_stop else R.string.action_apply,
                number
            )
            binding.btnDelete.contentDescription = context.getString(R.string.action_delete, number)
            binding.btnDelete.isEnabled = record != null

            if (record == null) {
                binding.slotName.text = context.getString(R.string.slot_empty_name, number)
                binding.slotStatus.text =
                    if (isPending) context.getString(R.string.slot_waiting)
                    else context.getString(R.string.slot_empty_status)
                binding.slotName.setTextColor(
                    ContextCompat.getColor(context, R.color.slot_name_empty)
                )
                binding.btnApply.isEnabled = false
            } else {
                val name = if (record.name.isBlank()) {
                    context.getString(R.string.slot_empty_name, number)
                } else {
                    record.name
                }
                binding.slotName.text = name
                binding.slotStatus.text = statusOf(context, record, isActive, isPending)
                binding.slotName.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isActive) R.color.slot_name_active else R.color.slot_name_filled
                    )
                )
                binding.btnApply.isEnabled = isActive || record.canEmulate()
            }

            binding.btnApply.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    when {
                        isActive -> R.color.slot_action_active
                        record != null && record.canEmulate() -> R.color.slot_action_enabled
                        else -> R.color.slot_action_disabled
                    }
                )
            )

            val background = when {
                isActive -> R.drawable.slot_active
                isPending -> R.drawable.slot_pending
                else -> 0
            }
            if (background == 0) {
                binding.root.background = null
            } else {
                binding.root.setBackgroundResource(background)
            }
        }

        private fun statusOf(
            context: Context,
            record: CardRecord,
            isActive: Boolean,
            isPending: Boolean
        ): String {
            if (isPending) return context.getString(R.string.slot_reading)
            val parts = ArrayList<String>(5)
            if (isActive) parts.add(context.getString(R.string.slot_active_badge))
            if (record.techs.isNotEmpty()) parts.add(record.techs.joinToString("/"))
            if (record.uid.isNotEmpty()) parts.add("UID " + Hex.shorten(record.uid, 6, 2))
            if (record.aids.isNotEmpty()) {
                parts.add(context.getString(R.string.slot_aid_count, record.aids.size))
            }
            if (record.sectorsTotal > 0) {
                parts.add(
                    context.getString(
                        R.string.slot_sectors,
                        record.sectorsRead,
                        record.sectorsTotal
                    )
                )
            }
            if (record.ndefText.isNotEmpty()) parts.add(record.ndefText)
            if (!record.canEmulate()) parts.add(context.getString(R.string.slot_not_emulatable))
            return if (parts.isEmpty()) context.getString(R.string.slot_empty_status)
            else parts.joinToString(SEPARATOR)
        }
    }

    companion object {
        private const val SEPARATOR = " \u00B7 "
    }
}
