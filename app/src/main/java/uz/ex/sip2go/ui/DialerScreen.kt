package uz.ex.sip2go.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.ex.sip2go.R
import uz.ex.sip2go.service.ConnectionState

private val CallGreen = Color(0xFF34C759)
private val CallGreenGlow = Color(0x4034C759)
private val KeyFillTop = Color(0xFF243041)
private val KeyFillBottom = Color(0xFF1A2330)
private val KeyBorder = Color(0xFF3A4F63)
private val KeyBorderPressed = Color(0x662DD4BF)
private val TealAccent = Color(0xFF2DD4BF)
private val StatusOnline = Color(0xFF66BB6A)
private val StatusIdle = Color(0xFF64B5F6)
private val StatusBusy = Color(0xFFFFB74D)
private val StatusOff = Color(0xFF78909C)

private val DialKeys = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("*", "0", "#"),
)

@Composable
fun DialerScreen(
    dialNumber: String,
    contactHint: String?,
    lastDialedNumber: String?,
    connectionState: ConnectionState,
    statusMessage: String,
    organizationName: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearNumber: () -> Unit,
    onRedial: () -> Unit,
    onCall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = organizationName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))

        ConnectionStatusRow(connectionState, statusMessage)

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (dialNumber.isEmpty()) " " else formatDialDisplay(dialNumber),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.Light,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )

        Text(
            text = contactHint ?: " ",
            style = MaterialTheme.typography.bodyLarge,
            color = TealAccent.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )

        Spacer(Modifier.weight(1f))

        DialKeyGrid(onDigit = onDigit)

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (dialNumber.isEmpty() && !lastDialedNumber.isNullOrBlank()) {
                    DialActionButton(onClick = onRedial) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.action_redial),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            DialCallButton(
                enabled = dialNumber.isNotBlank(),
                onClick = onCall,
            )
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (dialNumber.isNotEmpty()) {
                    DialActionButton(
                        onClick = onBackspace,
                        onLongClick = onClearNumber,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.action_backspace),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DialKeyGrid(onDigit: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DialKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    DialKey(digit = digit, onClick = { onDigit(digit) })
                }
            }
        }
    }
}

@Composable
private fun DialKey(
    digit: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed) 0.94f else 1f

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (pressed) {
                        listOf(Color(0xFF2A3A4E), Color(0xFF1E2A3A))
                    } else {
                        listOf(KeyFillTop, KeyFillBottom)
                    },
                ),
            )
            .border(
                width = 1.dp,
                color = if (pressed) KeyBorderPressed else KeyBorder,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            fontSize = when (digit) {
                "*", "#" -> 30.sp
                else -> 34.sp
            },
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DialCallButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed && enabled) 0.94f else 1f
    val green = if (enabled) CallGreen else CallGreen.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) CallGreenGlow else Color.Transparent)
            .padding(4.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        green.copy(alpha = if (enabled) 1f else 0.5f),
                        green.copy(alpha = if (enabled) 0.82f else 0.4f),
                    ),
                ),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Call,
            contentDescription = stringResource(R.string.action_call),
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(34.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialActionButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed) 0.94f else 1f

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF1E2A38))
            .border(1.dp, KeyBorder, CircleShape)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ConnectionStatusRow(connectionState: ConnectionState, statusMessage: String) {
    val (label, color) = when (connectionState) {
        ConnectionState.REGISTERED -> stringResource(R.string.status_online) to StatusOnline
        ConnectionState.IDLE -> stringResource(R.string.status_idle) to StatusIdle
        ConnectionState.STOPPED -> stringResource(R.string.status_stopped) to StatusOff
        ConnectionState.ERROR -> stringResource(R.string.status_error) to Color(0xFFC62828)
        ConnectionState.CONNECTING, ConnectionState.CONNECTED ->
            stringResource(R.string.status_connecting) to StatusBusy
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        if (statusMessage.isNotBlank() && connectionState != ConnectionState.REGISTERED) {
            Text(
                text = " · ${statusMessage.take(40)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatDialDisplay(raw: String): String =
    raw.filter { it.isDigit() || it == '*' || it == '#' }
