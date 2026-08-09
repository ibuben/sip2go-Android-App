package uz.ex.sip2go.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.ex.sip2go.R
import uz.ex.sip2go.contacts.ContactLookup
import uz.ex.sip2go.contacts.PhoneContact
import uz.ex.sip2go.data.AppLanguage
import uz.ex.sip2go.data.DeviceSettings
import uz.ex.sip2go.history.CallHistoryEntry
import uz.ex.sip2go.service.ConnectionState
import uz.ex.sip2go.telecom.TelecomStatus

private enum class PhoneTab { Dialer, History, Contacts, Settings }

@Composable
fun PhoneAppScreen(
    settings: DeviceSettings,
    connectionState: ConnectionState,
    statusMessage: String,
    serviceRunning: Boolean,
    isIdle: Boolean,
    dialNumber: String,
    callHistory: List<CallHistoryEntry>,
    lastDialedNumber: String?,
    overlayPermissionGranted: Boolean,
    fullScreenIntentGranted: Boolean,
    onDialNumberChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onSettingsChange: (DeviceSettings) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleService: () -> Unit,
    onPickRingtone: () -> Unit = {},
    onResetRingtone: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    telecomStatus: TelecomStatus = TelecomStatus.Unavailable,
    onOpenTelecomSettings: () -> Unit = {},
    openHistoryTab: Boolean = false,
    onHistorySeen: () -> Unit = {},
    onDeleteHistoryEntries: (Set<String>) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(PhoneTab.Dialer.ordinal) }
    var contactSearch by rememberSaveable { mutableStateOf("") }
    var contactHint by remember { mutableStateOf<String?>(null) }
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) {
            ContactLookup.loadContacts(context)
        }
    }

    LaunchedEffect(openHistoryTab) {
        if (openHistoryTab) {
            selectedTab = PhoneTab.History.ordinal
        }
    }

    LaunchedEffect(selectedTab) {
        if (PhoneTab.entries[selectedTab] == PhoneTab.History) {
            onHistorySeen()
        }
    }

    LaunchedEffect(dialNumber) {
        if (dialNumber.isBlank()) {
            contactHint = null
            return@LaunchedEffect
        }
        contactHint = withContext(Dispatchers.IO) {
            ContactLookup.lookupName(context, dialNumber)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == PhoneTab.Dialer.ordinal,
                    onClick = { selectedTab = PhoneTab.Dialer.ordinal },
                    icon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_dialer)) },
                )
                NavigationBarItem(
                    selected = selectedTab == PhoneTab.History.ordinal,
                    onClick = { selectedTab = PhoneTab.History.ordinal },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history)) },
                )
                NavigationBarItem(
                    selected = selectedTab == PhoneTab.Contacts.ordinal,
                    onClick = { selectedTab = PhoneTab.Contacts.ordinal },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_contacts)) },
                )
                NavigationBarItem(
                    selected = selectedTab == PhoneTab.Settings.ordinal,
                    onClick = { selectedTab = PhoneTab.Settings.ordinal },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (PhoneTab.entries[selectedTab]) {
                PhoneTab.Dialer -> DialerScreen(
                    dialNumber = dialNumber,
                    contactHint = contactHint,
                    lastDialedNumber = lastDialedNumber,
                    connectionState = connectionState,
                    statusMessage = statusMessage,
                    organizationName = settings.organizationName,
                    onDigit = { digit -> onDialNumberChange(dialNumber + digit) },
                    onBackspace = {
                        if (dialNumber.isNotEmpty()) {
                            onDialNumberChange(dialNumber.dropLast(1))
                        }
                    },
                    onClearNumber = { onDialNumberChange("") },
                    onRedial = {
                        lastDialedNumber?.let { onCall(it) }
                    },
                    onCall = { onCall(dialNumber) },
                )
                PhoneTab.History -> CallHistoryScreen(
                    entries = callHistory,
                    onCallEntry = { entry ->
                        onCall(entry.number)
                    },
                    onDeleteEntries = onDeleteHistoryEntries,
                )
                PhoneTab.Contacts -> ContactsScreen(
                    contacts = contacts,
                    searchQuery = contactSearch,
                    onSearchChange = { contactSearch = it },
                    onSelectContact = { contact ->
                        val number = ContactLookup.digitsOnly(contact.number)
                            .ifEmpty { contact.number.filter { it.isDigit() || it == '*' || it == '#' } }
                        onDialNumberChange(number)
                        selectedTab = PhoneTab.Dialer.ordinal
                    },
                    onCallContact = { contact ->
                        val number = ContactLookup.digitsOnly(contact.number)
                            .ifEmpty { contact.number.filter { it.isDigit() || it == '*' || it == '#' } }
                        onCall(number)
                    },
                )
                PhoneTab.Settings -> SettingsScreen(
                    settings = settings,
                    connectionState = connectionState,
                    statusMessage = statusMessage,
                    serviceRunning = serviceRunning,
                    isIdle = isIdle,
                    overlayPermissionGranted = overlayPermissionGranted,
                    fullScreenIntentGranted = fullScreenIntentGranted,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenFullScreenSettings = onOpenFullScreenSettings,
                    onSettingsChange = onSettingsChange,
                    onSaveSettings = onSaveSettings,
                    onToggleService = onToggleService,
                    onPickRingtone = onPickRingtone,
                    onResetRingtone = onResetRingtone,
                    onScanQr = onScanQr,
                    onLanguageChange = onLanguageChange,
                    telecomStatus = telecomStatus,
                    onOpenTelecomSettings = onOpenTelecomSettings,
                )
            }
        }
    }
}
