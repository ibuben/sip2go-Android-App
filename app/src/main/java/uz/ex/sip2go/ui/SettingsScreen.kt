package uz.ex.sip2go.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.RingtoneManager
import android.net.Uri
import uz.ex.sip2go.R
import uz.ex.sip2go.data.AppLanguage
import uz.ex.sip2go.data.DeviceSettings
import uz.ex.sip2go.recording.CallRecordingStorage
import uz.ex.sip2go.service.ConnectionState
import uz.ex.sip2go.telecom.TelecomStatus
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: DeviceSettings,
    connectionState: ConnectionState,
    statusMessage: String,
    serviceRunning: Boolean,
    isIdle: Boolean,
    overlayPermissionGranted: Boolean,
    fullScreenIntentGranted: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onSettingsChange: (DeviceSettings) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleService: () -> Unit,
    onPickRingtone: () -> Unit,
    onResetRingtone: () -> Unit,
    onScanQr: () -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    telecomStatus: TelecomStatus = TelecomStatus.Unavailable,
    onOpenTelecomSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val ringtoneTitle = ringtoneDisplayName(context, settings.ringtoneUri)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (settings.organizationName.isNotBlank()) {
            Text(
                text = settings.organizationName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        StatusCard(connectionState, statusMessage, serviceRunning, isIdle, settings.backgroundPushMode)

        SettingsToggleCard(
            title = stringResource(R.string.settings_background_push_title),
            description = stringResource(R.string.settings_background_push_desc),
            checked = settings.backgroundPushMode,
            onCheckedChange = { onSettingsChange(settings.copy(backgroundPushMode = it)) },
        )

        SettingsToggleCard(
            title = stringResource(R.string.settings_missed_notifications_title),
            description = stringResource(R.string.settings_missed_notifications_desc),
            checked = settings.missedCallNotifications,
            onCheckedChange = { onSettingsChange(settings.copy(missedCallNotifications = it)) },
        )

        val (usedBytes, maxBytes) = CallRecordingStorage.formattedUsage(context)
        SettingsToggleCard(
            title = stringResource(R.string.settings_record_calls_title),
            description = stringResource(R.string.settings_record_calls_desc),
            checked = settings.recordCallsByDefault,
            onCheckedChange = { onSettingsChange(settings.copy(recordCallsByDefault = it)) },
        )
        Text(
            text = stringResource(
                R.string.settings_recordings_usage,
                formatRecordingSize(usedBytes),
                formatRecordingSize(maxBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Text(
            text = stringResource(R.string.settings_recordings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        LanguageSettingsCard(
            selected = settings.appLanguage,
            onSelect = onLanguageChange,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_ringtone_title),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_ringtone_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Text(
                    text = ringtoneTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onPickRingtone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_ringtone_change))
                    }
                    OutlinedButton(onClick = onResetRingtone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_ringtone_reset))
                    }
                }
            }
        }

        TelecomSettingsCard(
            status = telecomStatus,
            onOpenSettings = onOpenTelecomSettings,
        )

        if (!overlayPermissionGranted || !fullScreenIntentGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.permissions_incoming_hint),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!overlayPermissionGranted) {
                        Text(stringResource(R.string.permission_overlay_bullet))
                        OutlinedButton(onClick = onOpenOverlaySettings, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_overlay_settings))
                        }
                    }
                    if (!fullScreenIntentGranted) {
                        Text(stringResource(R.string.permission_fullscreen_bullet))
                        OutlinedButton(onClick = onOpenFullScreenSettings, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.open_fullscreen_settings))
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_qr_title),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_qr_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Button(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_qr_scan))
                }
            }
        }

        OutlinedTextField(
            value = settings.wsUrl,
            onValueChange = { onSettingsChange(settings.copy(wsUrl = it)) },
            label = { Text(stringResource(R.string.settings_ws_url_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = settings.deviceId,
            onValueChange = { onSettingsChange(settings.copy(deviceId = it)) },
            label = { Text(stringResource(R.string.settings_device_id_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = settings.deviceToken,
            onValueChange = { onSettingsChange(settings.copy(deviceToken = it)) },
            label = { Text(stringResource(R.string.settings_device_token_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onSaveSettings, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_save))
            }
            Button(onClick = onToggleService, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        connectionState == ConnectionState.REGISTERED -> stringResource(R.string.action_disconnect)
                        serviceRunning && isIdle && !settings.backgroundPushMode -> stringResource(R.string.action_connect)
                        serviceRunning -> stringResource(R.string.action_stop)
                        else -> stringResource(R.string.action_enable)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_battery_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

private fun ringtoneDisplayName(context: android.content.Context, uriString: String?): String {
    if (uriString == DeviceSettings.SILENT_RINGTONE) {
        return context.getString(R.string.settings_ringtone_silent)
    }
    val uri = if (uriString.isNullOrBlank()) {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    } else {
        Uri.parse(uriString)
    } ?: return context.getString(R.string.settings_ringtone_default)
    val ringtone = RingtoneManager.getRingtone(context, uri)
        ?: return context.getString(R.string.settings_ringtone_default)
    return ringtone.getTitle(context)
}

@Composable
private fun TelecomSettingsCard(
    status: TelecomStatus,
    onOpenSettings: () -> Unit,
) {
    val (statusLabel, statusColor) = when (status) {
        TelecomStatus.Ready -> stringResource(R.string.settings_telecom_ready) to Color(0xFF2E7D32)
        TelecomStatus.Disabled -> stringResource(R.string.settings_telecom_disabled) to Color(0xFFF57C00)
        TelecomStatus.Unavailable -> stringResource(R.string.settings_telecom_unavailable) to Color(0xFFC62828)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_telecom_title),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_telecom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Text(
                text = statusLabel,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (status == TelecomStatus.Disabled) {
                Text(
                    text = stringResource(R.string.settings_telecom_samsung_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_telecom_open))
                }
            } else if (status == TelecomStatus.Unavailable) {
                Text(
                    text = stringResource(R.string.settings_telecom_unavailable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun StatusCard(
    connectionState: ConnectionState,
    statusMessage: String,
    serviceRunning: Boolean,
    isIdle: Boolean,
    backgroundPushMode: Boolean,
) {
    val (label, color) = when {
        !serviceRunning -> stringResource(R.string.status_stopped) to Color.Gray
        isIdle && backgroundPushMode -> stringResource(R.string.status_idle_background) to Color(0xFF1565C0)
        isIdle -> stringResource(R.string.status_idle) to Color(0xFF1565C0)
        connectionState == ConnectionState.REGISTERED -> stringResource(R.string.status_online) to Color(0xFF2E7D32)
        connectionState == ConnectionState.ERROR -> stringResource(R.string.status_error) to Color(0xFFC62828)
        else -> stringResource(R.string.status_connecting) to Color(0xFFF57C00)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.status_label), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LanguageSettingsCard(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = selected == language,
                        onClick = { onSelect(language) },
                        label = { Text(languageLabel(language)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
    AppLanguage.RUSSIAN -> stringResource(R.string.settings_language_russian)
}

private fun formatRecordingSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.0f MB", mb)
}
