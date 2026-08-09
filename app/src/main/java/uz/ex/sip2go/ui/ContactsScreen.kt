package uz.ex.sip2go.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.ex.sip2go.R
import uz.ex.sip2go.contacts.PhoneContact

private val CallGreen = Color(0xFF34C759)

@Composable
fun ContactsScreen(
    contacts: List<PhoneContact>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectContact: (PhoneContact) -> Unit,
    onCallContact: (PhoneContact) -> Unit,
) {
    val filtered = if (searchQuery.isBlank()) {
        contacts
    } else {
        val q = searchQuery.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        contacts.filter { contact ->
            contact.name.lowercase().contains(q) ||
                contact.number.contains(q) ||
                (qDigits.isNotEmpty() && contact.normalizedDigits.contains(qDigits))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.contacts_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { "${it.id}:${it.number}" }) { contact ->
                ContactRow(
                    contact = contact,
                    onClick = { onSelectContact(contact) },
                    onCall = { onCallContact(contact) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: PhoneContact,
    onClick: () -> Unit,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            ContactAvatar(label = contact.name, contactId = contact.id, size = 48.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = contact.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCall) {
            Icon(
                Icons.Default.Call,
                contentDescription = stringResource(R.string.action_call),
                tint = CallGreen,
            )
        }
    }
}
