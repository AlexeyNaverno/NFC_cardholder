package io.github.alexnaverno.nfccardholder

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.alexnaverno.nfccardholder.core.Apdu
import io.github.alexnaverno.nfccardholder.core.Hex
import io.github.alexnaverno.nfccardholder.databinding.ActivityMainBinding
import io.github.alexnaverno.nfccardholder.databinding.DialogCardDetailsBinding
import io.github.alexnaverno.nfccardholder.hce.EmulationAids
import io.github.alexnaverno.nfccardholder.hce.EmulationControl
import io.github.alexnaverno.nfccardholder.hce.Type4TagEmulator
import io.github.alexnaverno.nfccardholder.model.AidEntry
import io.github.alexnaverno.nfccardholder.model.CardRecord
import io.github.alexnaverno.nfccardholder.model.CardStore
import io.github.alexnaverno.nfccardholder.model.CardVault
import io.github.alexnaverno.nfccardholder.nfc.CopyStep
import io.github.alexnaverno.nfccardholder.nfc.TagCopier
import io.github.alexnaverno.nfccardholder.ui.SlotAdapter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * One screen, ten slots. Every slot has three actions:
 *
 *  * copy  - read whatever an NFC tag hands out and store it in the slot,
 *  * apply - make the phone transmit that stored card (host card emulation),
 *  * delete - empty the slot.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var slots: SlotAdapter
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var nfcAdapter: NfcAdapter? = null
    private var pendingSlot: Int = -1
    private var reading: Boolean = false
    private var copyInFlight: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcAdapter = EmulationControl.adapter(this)

        slots = SlotAdapter(
            onCopy = { slot -> onCopyClicked(slot) },
            onApply = { slot -> onApplyClicked(slot) },
            onDelete = { slot -> onDeleteClicked(slot) },
            onDetails = { slot -> showDetails(slot) }
        )
        binding.slotList.layoutManager = LinearLayoutManager(this)
        binding.slotList.adapter = slots

        binding.btnCancel.setOnClickListener { stopCopyMode() }
        binding.btnNfcSettings.setOnClickListener { openNfcSettings() }
        binding.btnHelp.setOnClickListener { showHelp() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (reading) enableReaderMode()
    }

    override fun onPause() {
        disableReaderMode()
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (reading) {
            stopCopyMode()
            return
        }
        super.onBackPressed()
    }

    // ---------------------------------------------------------------- list state

    private fun refresh() {
        val store = CardVault.get(this)
        slots.submit(store.slots.toList(), store.activeSlot, pendingSlot, reading)
        binding.readPanel.visibility = if (reading) View.VISIBLE else View.GONE

        val nfcOn = nfcAdapter?.isEnabled == true
        binding.headerStatus.text = when {
            nfcAdapter == null -> getString(R.string.status_no_nfc)
            !nfcOn -> getString(R.string.status_nfc_off)
            store.activeSlot >= 0 -> getString(
                R.string.status_active,
                store.activeSlot + 1,
                displayName(store.activeSlot, store.slot(store.activeSlot))
            )
            else -> getString(R.string.status_idle, store.countFilled(), CardStore.SLOT_COUNT)
        }
        val color = when {
            nfcAdapter == null || !nfcOn -> R.color.status_error
            store.activeSlot >= 0 -> R.color.status_active
            else -> R.color.status_muted
        }
        binding.headerStatus.setTextColor(ContextCompat.getColor(this, color))
        binding.btnNfcSettings.visibility = if (nfcOn) View.GONE else View.VISIBLE
    }

    private fun displayName(slot: Int, record: CardRecord?): String {
        val name = record?.name
        return if (name.isNullOrBlank()) getString(R.string.slot_empty_name, slot + 1) else name
    }

    // ---------------------------------------------------------------- copy

    private fun onCopyClicked(slot: Int) {
        if (reading) {
            if (pendingSlot == slot) {
                stopCopyMode()
            } else {
                pendingSlot = slot
                copyInFlight = false
                binding.readHint.text = getString(R.string.read_hint, slot + 1)
                refresh()
            }
            return
        }
        val adapter = nfcAdapter
        if (adapter == null) {
            toast(getString(R.string.err_no_nfc))
            return
        }
        if (!adapter.isEnabled) {
            toast(getString(R.string.err_nfc_off))
            openNfcSettings()
            return
        }
        pendingSlot = slot
        copyInFlight = false
        reading = true
        binding.readHint.text = getString(R.string.read_hint, slot + 1)
        enableReaderMode()
        refresh()
    }

    private fun stopCopyMode() {
        reading = false
        copyInFlight = false
        pendingSlot = -1
        disableReaderMode()
        refresh()
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        try {
            adapter.enableReaderMode(this, readerCallback, flags, null)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enable reader mode", e)
            reading = false
            refresh()
            toast(getString(R.string.err_reader_mode, e.javaClass.simpleName))
        }
    }

    private fun disableReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot disable reader mode", e)
        }
    }

    private val readerCallback = NfcAdapter.ReaderCallback { tag -> onTagDiscovered(tag) }

    /** Runs on a binder thread, never touch the UI here without posting it. */
    private fun onTagDiscovered(tag: Tag) {
        val slot = pendingSlot
        if (!reading || copyInFlight || slot < 0) return
        copyInFlight = true

        val label = getString(R.string.default_card_name, slot + 1)
        val store = CardVault.get(this)
        runOnUiThread { binding.readHint.text = getString(R.string.read_working) }
        io.execute {
            try {
                val record = TagCopier.copy(tag, label, store) { step, value ->
                    runOnUiThread { binding.readHint.text = progressText(step, value) }
                }
                runOnUiThread { onCopied(slot, record) }
            } catch (e: Throwable) {
                Log.w(TAG, "Copy failed", e)
                runOnUiThread { onCopyFailed(slot, e) }
            }
        }
    }

    private fun progressText(step: CopyStep, value: Int): String = when (step) {
        CopyStep.START -> getString(R.string.progress_start)
        CopyStep.NDEF -> getString(R.string.progress_ndef)
        CopyStep.ISO_DEP -> getString(R.string.progress_iso_dep)
        CopyStep.PROBE -> getString(R.string.progress_probe, value)
        CopyStep.ULTRALIGHT -> getString(R.string.progress_ultralight, value)
        CopyStep.CLASSIC -> getString(R.string.progress_classic, value)
        CopyStep.DONE -> getString(R.string.progress_done)
    }

    private fun onCopied(slot: Int, record: CardRecord) {
        val store = CardVault.get(this)
        store.put(slot, record)
        if (store.activeSlot == slot) {
            // The slot is being transmitted right now: reload the engine with the new data.
            EmulationControl.activate(this, slot)
        }
        store.save()
        stopCopyMode()
        toast(getString(R.string.copied, displayName(slot, record)))
        if (!record.canEmulate()) showCannotEmulate(record)
    }

    private fun onCopyFailed(slot: Int, error: Throwable) {
        copyInFlight = false
        binding.readHint.text = getString(R.string.read_hint, slot + 1)
        toast(getString(R.string.err_copy_failed, error.javaClass.simpleName))
    }

    // ---------------------------------------------------------------- apply

    private fun onApplyClicked(slot: Int) {
        val store = CardVault.get(this)
        val record = store.slot(slot)
        if (record == null) {
            toast(getString(R.string.err_slot_empty))
            return
        }
        if (store.activeSlot == slot) {
            EmulationControl.stop(this)
            refresh()
            toast(getString(R.string.emulation_stopped, displayName(slot, record)))
            return
        }
        if (!record.canEmulate()) {
            showCannotEmulate(record)
            return
        }
        val result = EmulationControl.activate(this, slot)
        refresh()
        when (result) {
            EmulationControl.Result.ACTIVATED ->
                toast(getString(R.string.emulation_started, displayName(slot, record)))

            EmulationControl.Result.ACTIVATED_BASE_AIDS_ONLY -> {
                toast(getString(R.string.emulation_started, displayName(slot, record)))
                showBaseAidsOnly(record)
            }

            EmulationControl.Result.NO_NFC -> toast(getString(R.string.err_no_nfc))
            EmulationControl.Result.NFC_DISABLED -> {
                toast(getString(R.string.err_nfc_off))
                openNfcSettings()
            }

            EmulationControl.Result.NO_HCE -> toast(getString(R.string.err_no_hce))
            EmulationControl.Result.NOT_EMULATABLE -> showCannotEmulate(record)
            EmulationControl.Result.STOPPED -> Unit
        }
    }

    // ---------------------------------------------------------------- delete

    private fun onDeleteClicked(slot: Int) {
        val store = CardVault.get(this)
        val record = store.slot(slot) ?: return
        val name = displayName(slot, record)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_delete_short) { _, _ ->
                if (store.activeSlot == slot) EmulationControl.stop(this)
                store.remove(slot)
                store.save()
                refresh()
                toast(getString(R.string.deleted, name))
            }
            .show()
    }

    // ---------------------------------------------------------------- details

    private fun showDetails(slot: Int) {
        val store = CardVault.get(this)
        val record = store.slot(slot)
        val view = DialogCardDetailsBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_details_title, slot + 1))
            .setView(view.root)
            .setPositiveButton(R.string.close, null)
            .create()

        fun render() {
            view.detailsInfo.text =
                if (record == null) getString(R.string.details_empty) else describe(record)
            view.detailsAids.text = renderAids(record)
        }

        render()

        if (record == null) {
            view.btnRename.isEnabled = false
            view.btnAddAid.isEnabled = false
        } else {
            view.btnRename.setOnClickListener {
                promptRename(slot, record) {
                    render()
                    refresh()
                }
            }
            view.btnAddAid.setOnClickListener {
                promptAid(record) {
                    render()
                    refresh()
                }
            }
        }
        dialog.show()
    }

    private fun renderAids(record: CardRecord?): String {
        if (record == null || record.aids.isEmpty()) {
            return getString(R.string.details_no_aids)
        }
        val builder = StringBuilder()
        record.aids.forEach { entry ->
            builder.append(entry.aid).append(" -> ")
            builder.append(if (entry.response.isBlank()) "9000" else Hex.shorten(entry.response, 20, 4))
            builder.append('\n')
        }
        if (record.ndefHex.isNotEmpty()) {
            builder.append(Type4TagEmulator.describe(record.ndefHex))
        }
        return builder.toString().trimEnd()
    }

    private fun describe(record: CardRecord): String {
        val lines = ArrayList<String>()
        lines.add(getString(R.string.details_name, record.name))
        lines.add(getString(R.string.details_uid, if (record.uid.isEmpty()) "-" else record.uid))
        lines.add(getString(R.string.details_techs, record.techs.joinToString(", ")))
        if (record.atqa.isNotEmpty() || record.sak.isNotEmpty()) {
            lines.add(getString(R.string.details_nfc_a, record.atqa, record.sak))
        }
        if (record.historicalBytes.isNotEmpty()) {
            lines.add(getString(R.string.details_ats, record.historicalBytes))
        }
        if (record.hiLayerResponse.isNotEmpty()) {
            lines.add(getString(R.string.details_atqb, record.hiLayerResponse))
        }
        if (record.mifareType.isNotEmpty()) {
            lines.add(
                getString(
                    R.string.details_mifare,
                    record.mifareType,
                    record.mifareSize,
                    record.sectorsRead,
                    record.sectorsTotal
                )
            )
        }
        if (record.ultralightPages > 0) {
            lines.add(
                getString(
                    R.string.details_ultralight,
                    record.ultralightPages,
                    record.ultralightHex.length / 2
                )
            )
        }
        if (record.ndefText.isNotEmpty()) {
            lines.add(getString(R.string.details_ndef, record.ndefText))
        }
        lines.add(
            getString(
                R.string.details_created,
                if (record.createdAt > 0) {
                    DateFormat.getDateTimeInstance().format(Date(record.createdAt))
                } else {
                    "-"
                }
            )
        )
        lines.add(getString(R.string.details_taps, record.taps))
        lines.add(getString(R.string.details_emulation, emulationModeText(record)))
        record.notes.forEach { lines.add(it) }
        return lines.joinToString("\n")
    }

    private fun emulationModeText(record: CardRecord): String = when (record.emulationMode()) {
        CardRecord.MODE_AID -> getString(R.string.details_mode_aid, record.aids.size)
        CardRecord.MODE_TYPE4 -> getString(R.string.details_mode_type4)
        else -> getString(R.string.details_mode_none)
    }

    private fun promptRename(slot: Int, record: CardRecord, onSaved: () -> Unit) {
        val input = EditText(this).apply {
            setText(record.name)
            setSingleLine()
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.slot_empty_name, slot + 1)
        }
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_rename_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                record.name = input.text.toString().trim()
                CardVault.get(this).save()
                onSaved()
            }
            .show()
    }

    private fun promptAid(record: CardRecord, onSaved: () -> Unit) {
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
        val labelPadding = resources.getDimensionPixelSize(R.dimen.dialog_label_top)

        val aidInput = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = getString(R.string.aid_hint)
        }
        val responseInput = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = getString(R.string.response_hint)
        }

        fun label(textRes: Int) = TextView(this).apply {
            setText(textRes)
            setPadding(0, labelPadding, 0, 0)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(label(R.string.aid_label))
            addView(
                aidInput,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(label(R.string.response_label))
            addView(
                responseInput,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_add_aid_title)
            .setMessage(R.string.dialog_add_aid_message)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.show()
        // Keep the dialog open while the input is invalid.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val aid = aidInput.text.toString().trim().uppercase(Locale.US)
            val response = responseInput.text.toString().trim().uppercase(Locale.US)
            if (!Apdu.isValidAid(aid)) {
                aidInput.error = getString(R.string.err_invalid_aid)
                return@setOnClickListener
            }
            if (response.isNotEmpty() && !Hex.isValid(response)) {
                responseInput.error = getString(R.string.err_invalid_hex)
                return@setOnClickListener
            }
            record.putAid(aid, response, AidEntry.SOURCE_MANUAL)
            val store = CardVault.get(this)
            if (store.activeSlot >= 0 && store.slot(store.activeSlot) === record) {
                // Re-register so that the new AID is routed to this app right away.
                EmulationControl.activate(this, store.activeSlot)
            }
            store.save()
            dialog.dismiss()
            onSaved()
        }
    }

    // ---------------------------------------------------------------- information dialogs

    private fun showCannotEmulate(record: CardRecord) {
        val reason = when {
            record.techs.any { it == "MifareClassic" } -> getString(R.string.limit_classic)
            !record.isoDep -> getString(R.string.limit_not_iso_dep, record.techs.joinToString(", "))
            else -> getString(R.string.limit_no_aid)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_limit_title)
            .setMessage(getString(R.string.dialog_limit_message, reason))
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.dialog_limit_details) { _, _ -> openReadme() }
            .show()
    }

    private fun showBaseAidsOnly(record: CardRecord) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_base_aids_title)
            .setMessage(
                getString(
                    R.string.dialog_base_aids_message,
                    record.name,
                    EmulationAids.BASE.joinToString("\n")
                )
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_help_title)
            .setMessage(R.string.help_text)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.dialog_limit_details) { _, _ -> openReadme() }
            .show()
    }

    private fun openReadme() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(README_URL)))
        } catch (e: Exception) {
            toast(getString(R.string.err_no_browser))
        }
    }

    private fun openNfcSettings() {
        val actions = listOf(
            Settings.ACTION_NFC_SETTINGS,
            Settings.ACTION_NFC_PAYMENT_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS
        )
        for (action in actions) {
            try {
                startActivity(Intent(action))
                return
            } catch (e: Exception) {
                Log.d(TAG, "Cannot open $action")
            }
        }
        toast(getString(R.string.err_no_settings))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val README_URL =
            "https://github.com/AlexeyNaverno/NFC_cardholder#%D1%87%D1%82%D0%BE-" +
                "%D1%82%D0%B5%D0%BB%D0%B5%D1%84%D0%BE%D0%BD-%D0%BC%D0%BE%D0%B6%D0%B5%D1%82-" +
                "%D0%BF%D0%B5%D1%80%D0%B5%D0%B4%D0%B0%D1%82%D1%8C"
    }
}
