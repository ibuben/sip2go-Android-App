package uz.ex.sip2go.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import uz.ex.sip2go.R
import uz.ex.sip2go.history.CallDirection
import uz.ex.sip2go.history.CallHistoryEntry
import uz.ex.sip2go.history.CallStatus
import uz.ex.sip2go.recording.CallRecordingPlayer
import uz.ex.sip2go.recording.CallRecordingShare
import uz.ex.sip2go.recording.CallRecordingStorage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CallGreen = Color(0xFF34C759)
private val MissedRed = Color(0xFFFF6B6B)
private val OutgoingBlue = Color(0xFF64B5F6)
private val IncomingTeal = Color(0xFF4DB6AC)
private val RecordingOrange = Color(0xFFFF9500)

@Composable
fun CallHistoryScreen(
    entries: List<CallHistoryEntry>,
    onCallEntry: (CallHistoryEntry) -> Unit,
    onDeleteEntries: (Set<String>) -> Unit = {},
) {
    val context = LocalContext.current
    var playingPath by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    var menuEntry by remember { mutableStateOf<CallHistoryEntry?>(null) }

    DisposableEffect(Unit) {
        onDispose { CallRecordingPlayer.stop() }
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text(stringResource(R.string.history_delete_selected_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_selected_message,
                        selectedIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntries(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        confirmDeleteSelected = false
                        CallRecordingPlayer.stop()
                        playingPath = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val entryForMenu = menuEntry
    if (entryForMenu != null) {
        val recordingPath = resolveRecordingPath(context, entryForMenu)
        HistoryEntryMenu(
            recordingPath = recordingPath,
            onDismiss = { menuEntry = null },
            onCopyNumber = {
                copyNumber(context, entryForMenu.number)
                menuEntry = null
            },
            onShareRecording = {
                CallRecordingShare.share(
                    context,
                    recordingPath!!,
                    shareTitle(context, entryForMenu),
                )
                menuEntry = null
            },
            onCall = {
                menuEntry = null
                onCallEntry(entryForMenu)
            },
            onCallMobile = {
                dialWithMobile(context, entryForMenu.number)
                menuEntry = null
            },
            onDelete = {
                onDeleteEntries(setOf(entryForMenu.id))
                menuEntry = null
                if (playingPath == recordingPath) {
                    CallRecordingPlayer.stop()
                    playingPath = null
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HistoryToolbar(
            selectionMode = selectionMode,
            selectedCount = selectedIds.size,
            hasEntries = entries.isNotEmpty(),
            onEnterSelection = { selectionMode = true },
            onExitSelection = {
                selectionMode = false
                selectedIds = emptySet()
            },
            onSelectAll = {
                selectedIds = if (selectedIds.size == entries.size) {
                    emptySet()
                } else {
                    entries.map { it.id }.toSet()
                }
            },
            onDeleteSelected = { confirmDeleteSelected = true },
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                val recordingPath = resolveRecordingPath(context, entry)
                val selected = entry.id in selectedIds
                HistoryRow(
                    entry = entry,
                    recordingDurationSec = recordingDurationSec(context, entry, recordingPath),
                    playing = playingPath == recordingPath,
                    selectionMode = selectionMode,
                    selected = selected,
                    onToggleSelect = {
                        selectedIds = if (selected) {
                            selectedIds - entry.id
                        } else {
                            selectedIds + entry.id
                        }
                    },
                    onCall = { onCallEntry(entry) },
                    onOpenMenu = { menuEntry = entry },
                    onPlayRecording = recordingPath?.let { path ->
                        {
                            if (playingPath == path) {
                                CallRecordingPlayer.stop()
                                playingPath = null
                            } else {
                                CallRecordingPlayer.play(context, path) {
                                    playingPath = null
                                }
                                playingPath = path
                            }
                        }
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = if (selectionMode) 104.dp else 72.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun HistoryEntryMenu(
    recordingPath: String?,
    onDismiss: () -> Unit,
    onCopyNumber: () -> Unit,
    onShareRecording: () -> Unit,
    onCall: () -> Unit,
    onCallMobile: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                HistoryMenuAction(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.history_copy_number),
                    onClick = onCopyNumber,
                )
                if (recordingPath != null) {
                    HistoryMenuDivider()
                    HistoryMenuAction(
                        icon = Icons.Default.Share,
                        label = stringResource(R.string.history_share_recording),
                        onClick = onShareRecording,
                    )
                }
                HistoryMenuDivider()
                HistoryMenuAction(
                    icon = Icons.Default.Call,
                    label = stringResource(R.string.action_call),
                    tint = CallGreen,
                    onClick = onCall,
                )
                HistoryMenuDivider()
                HistoryMenuAction(
                    icon = Icons.Default.PhoneAndroid,
                    label = stringResource(R.string.history_call_mobile),
                    onClick = onCallMobile,
                )
                HistoryMenuDivider()
                HistoryMenuAction(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryMenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HistoryMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    )
}

@Composable
private fun HistoryToolbar(
    selectionMode: Boolean,
    selectedCount: Int,
    hasEntries: Boolean,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (selectionMode) {
            TextButton(onClick = onExitSelection) {
                Text(stringResource(R.string.action_cancel))
            }
            Text(
                text = stringResource(R.string.history_selected_count, selectedCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.history_select_all))
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_delete_selected),
                    tint = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.tab_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (hasEntries) {
                TextButton(onClick = onEnterSelection) {
                    Text(stringResource(R.string.history_select))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: CallHistoryEntry,
    recordingDurationSec: Int = 0,
    playing: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onCall: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onPlayRecording: (() -> Unit)? = null,
) {
    val title = entry.contactName ?: entry.number
    val subtitle = buildString {
        append(statusLabel(entry.status))
        append(" · ")
        append(formatWhen(entry.startedAt))
        if (entry.status == CallStatus.COMPLETED && entry.durationSec > 0) {
            append(" · ")
            append(formatDuration(entry.durationSec))
        }
        if (onPlayRecording != null) {
            append(" · ")
            append(stringResource(R.string.history_has_recording))
            if (recordingDurationSec > 0) {
                append(" ")
                append(formatDuration(recordingDurationSec))
            }
        }
        if (entry.contactName != null) {
            append(" · ")
            append(entry.number)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelect()
                    } else {
                        onCall()
                    }
                },
                onLongClick = {
                    if (!selectionMode) {
                        onOpenMenu()
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
        Icon(
            imageVector = directionIcon(entry),
            contentDescription = null,
            tint = directionColor(entry),
            modifier = Modifier
                .padding(start = if (selectionMode) 0.dp else 8.dp)
                .size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selectionMode && onPlayRecording != null) {
            IconButton(onClick = onPlayRecording) {
                Icon(
                    if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) R.string.action_stop else R.string.action_play_recording,
                    ),
                    tint = RecordingOrange,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (!selectionMode) {
            IconButton(onClick = onCall) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = stringResource(R.string.action_call),
                    tint = CallGreen,
                )
            }
        }
    }
}

private fun resolveRecordingPath(context: Context, entry: CallHistoryEntry): String? {
    if (entry.status != CallStatus.COMPLETED) return null
    entry.recordingPath
        ?.takeIf { it.isNotBlank() && File(it).exists() }
        ?.let { return it }
    return CallRecordingStorage.findNewestForCall(context, entry.id)?.absolutePath
}

private fun recordingDurationSec(
    context: Context,
    entry: CallHistoryEntry,
    path: String?,
): Int {
    if (entry.recordingDurationSec > 0) return entry.recordingDurationSec
    val file = path?.let { File(it) } ?: return 0
    return CallRecordingStorage.estimateDurationSec(file)
}

private fun copyNumber(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("phone", number))
    Toast.makeText(context, R.string.history_number_copied, Toast.LENGTH_SHORT).show()
}

private fun dialWithMobile(context: Context, number: String) {
    val dialable = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    if (dialable.isEmpty()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", dialable, null))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, R.string.history_mobile_dial_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun shareTitle(context: Context, entry: CallHistoryEntry): String {
    val label = entry.contactName ?: entry.number
    return context.getString(R.string.history_share_recording_subject, label)
}

@Composable
private fun statusLabel(status: CallStatus): String {
    return when (status) {
        CallStatus.COMPLETED -> stringResource(R.string.history_status_completed)
        CallStatus.MISSED -> stringResource(R.string.history_status_missed)
        CallStatus.REJECTED -> stringResource(R.string.history_status_rejected)
        CallStatus.BUSY -> stringResource(R.string.history_status_busy)
        CallStatus.FAILED -> stringResource(R.string.history_status_failed)
        CallStatus.CANCELLED -> stringResource(R.string.history_status_cancelled)
    }
}

private fun directionIcon(entry: CallHistoryEntry): ImageVector {
    return when {
        entry.status == CallStatus.MISSED -> Icons.AutoMirrored.Filled.PhoneMissed
        entry.direction == CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.AutoMirrored.Filled.CallReceived
    }
}

private fun directionColor(entry: CallHistoryEntry): Color {
    return when {
        entry.status == CallStatus.MISSED -> MissedRed
        entry.direction == CallDirection.OUTGOING -> OutgoingBlue
        else -> IncomingTeal
    }
}

private fun formatWhen(epochMs: Long, yesterdayLabel: String): String {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val date = dt.toLocalDate()
    val locale = Locale.getDefault()
    return when {
        date == today -> DateTimeFormatter.ofPattern("HH:mm", locale).format(dt)
        date == today.minusDays(1) -> String.format(
            Locale.getDefault(),
            yesterdayLabel,
            DateTimeFormatter.ofPattern("HH:mm", locale).format(dt),
        )
        date.year == today.year -> DateTimeFormatter.ofPattern("d MMM HH:mm", locale).format(dt)
        else -> DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", locale).format(dt)
    }
}

@Composable
private fun formatWhen(epochMs: Long): String {
    val yesterdayTemplate = stringResource(R.string.history_yesterday)
    return formatWhen(epochMs, yesterdayTemplate)
}

@Composable
private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) {
        "${m}:${s.toString().padStart(2, '0')}"
    } else {
        stringResource(R.string.history_seconds, s)
    }
}
