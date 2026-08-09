@file:Suppress("SpellCheckingInspection")

package uz.ex.sip2go.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import uz.ex.sip2go.R
import uz.ex.sip2go.audio.CallAudioRoute
import uz.ex.sip2go.audio.ProximityScreenGuard

private val BgTop = Color(0xFF0A1219)
private val BgBottom = Color(0xFF0E1F2A)
private val TealAccent = Color(0xFF2DD4BF)
private val TealMuted = Color(0xFF5EEAD4)
private val TealGlow = Color(0x662DD4BF)
private val HeaderGrey = Color(0xFF8B9DAB)
private val BadgeBg = Color(0xFF1A3340)
private val AcceptGreen = Color(0xFF34C759)
private val AcceptGlow = Color(0x6634C759)
private val RejectRed = Color(0xFFFF3B30)
private val RecordRed = Color(0xFFFF453A)
private val RejectGlow = Color(0x66FF3B30)
private val PanelFill = Color(0xF0121820)
private val PanelBorderColor = Color(0xFF2E4054)

@Composable
private fun CallScreenBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgTop, BgBottom, Color(0xFF0A1520))),
            )
            .drawBehind { drawCallDecorations() },
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCallDecorations() {
    val cx = size.width / 2f
    val cy = size.height * 0.38f
    val radii = listOf(100f, 160f, 220f, 290f)
    radii.forEachIndexed { i, r ->
        drawCircle(
            color = TealAccent.copy(alpha = 0.07f - i * 0.012f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2f),
        )
    }
    val dotY = size.height * 0.74f
    val spacing = 14f
    var x = spacing
    while (x < size.width) {
        drawCircle(TealAccent.copy(alpha = 0.18f), 2.5f, Offset(x, dotY))
        x += spacing
    }
    var x2 = spacing * 1.5f
    val dotY2 = dotY + 18f
    while (x2 < size.width) {
        drawCircle(TealAccent.copy(alpha = 0.10f), 1.8f, Offset(x2, dotY2))
        x2 += spacing
    }
}

@Composable
private fun GlowingContactAvatar(label: String, contactId: Long?) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(TealGlow),
        )
        Box(
            modifier = Modifier
                .size(152.dp)
                .border(2.dp, TealAccent.copy(alpha = 0.85f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            ContactAvatar(label = label, contactId = contactId, size = 140.dp)
        }
    }
}

@Composable
private fun ExtensionBadge(number: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BadgeBg)
            .border(1.dp, TealAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Text(
            text = number,
            color = TealMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GlowingCallButton(
    onClick: () -> Unit,
    color: Color,
    glowColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "call_button_pulse")
    val glowScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_scale",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )
    val rippleScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_scale",
    )
    val rippleAlpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_alpha",
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha = rippleAlpha
                        clip = true
                    }
                    .background(glowColor),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (pulsing) {
                        Modifier.graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                            alpha = glowAlpha
                            clip = true
                        }
                    } else {
                        Modifier
                    },
                )
                .background(glowColor),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
fun CallReconnectBanner(reconnecting: Boolean) {
    if (!reconnecting) return
    Text(
        text = stringResource(R.string.call_reconnecting),
        color = TealMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
fun IncomingCallScreen(
    caller: String,
    called: String,
    contactName: String?,
    contactId: Long? = null,
    reconnecting: Boolean = false,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val primary = contactName ?: caller

    CallScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.incoming_call_title),
                    color = HeaderGrey,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
                CallReconnectBanner(reconnecting)
                Spacer(Modifier.height(36.dp))
                GlowingContactAvatar(label = primary, contactId = contactId)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = primary,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                )
                Spacer(Modifier.height(14.dp))
                ExtensionBadge(caller)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.call_to_endpoint, called),
                    color = TealMuted.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                )
            }
            InCallPanel(Modifier.padding(bottom = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlowingCallButton(
                        onClick = onReject,
                        color = RejectRed,
                        glowColor = RejectGlow,
                        pulsing = true,
                        icon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_reject),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        },
                    )
                    GlowingCallButton(
                        onClick = onAccept,
                        color = AcceptGreen,
                        glowColor = AcceptGlow,
                        pulsing = true,
                        icon = {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(R.string.action_accept),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OngoingCallScreen(
    connected: Boolean,
    caller: String,
    called: String,
    contactName: String?,
    contactId: Long? = null,
    outgoing: Boolean,
    ringing: Boolean,
    incomingRinging: Boolean = false,
    reconnecting: Boolean = false,
    callConnectedAt: Long? = null,
    audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    micMuted: Boolean = false,
    callRecording: Boolean = false,
    callHeld: Boolean = false,
    onToggleMute: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onToggleHold: () -> Unit = {},
    onTransfer: (String) -> Unit = {},
    onCycleAudio: () -> Unit = {},
    onDtmf: (String) -> Unit = {},
    onHangup: () -> Unit = {},
    onCancel: () -> Unit = {},
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    val primary = when {
        incomingRinging -> contactName ?: caller
        outgoing -> contactName ?: called
        else -> contactName ?: caller
    }
    val badgeNumber = when {
        incomingRinging -> caller
        outgoing -> called
        else -> caller
    }
    val subtitle = when {
        incomingRinging -> stringResource(R.string.call_to_endpoint, called)
        outgoing -> stringResource(R.string.call_from_endpoint, caller)
        else -> stringResource(R.string.call_to_endpoint, called)
    }
    val statusText = when {
        connected && outgoing -> stringResource(R.string.active_outgoing)
        connected -> stringResource(R.string.active_in_call)
        incomingRinging -> stringResource(R.string.incoming_call_title)
        ringing -> stringResource(R.string.outgoing_ringing)
        else -> stringResource(R.string.outgoing_calling)
    }
    var showKeypad by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(connected) {
        if (!connected) {
            showKeypad = false
            showTransferDialog = false
        }
    }

    if (showTransferDialog) {
        TransferCallDialog(
            onDismiss = { showTransferDialog = false },
            onTransfer = { target ->
                showTransferDialog = false
                onTransfer(target)
            },
        )
    }

    DisposableEffect(connected, audioRoute) {
        val guard = ProximityScreenGuard(context)
        guard.setEnabled(connected && audioRoute == CallAudioRoute.EARPIECE)
        onDispose { guard.release() }
    }

    CallScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (connected && showKeypad) {
                    CallStatusHeader(
                        statusText,
                        reconnecting,
                        callConnectedAt,
                        callRecording = callRecording,
                        callHeld = callHeld,
                        reserveIndicatorSpace = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = primary,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                } else {
                    CallStatusHeader(
                        statusText = statusText,
                        reconnecting = reconnecting,
                        callConnectedAt = callConnectedAt,
                        reserveDurationSpace = connected,
                        reserveIndicatorSpace = connected,
                        callRecording = connected && callRecording,
                        callHeld = connected && callHeld,
                    )
                    Spacer(Modifier.height(36.dp))
                    GlowingContactAvatar(label = primary, contactId = contactId)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = primary,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    ExtensionBadge(badgeNumber)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = subtitle,
                        color = TealMuted.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (incomingRinging) {
                InCallPanel(Modifier.padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlowingCallButton(
                            onClick = onReject,
                            color = RejectRed,
                            glowColor = RejectGlow,
                            pulsing = true,
                            icon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_reject),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            },
                        )
                        GlowingCallButton(
                            onClick = onAccept,
                            color = AcceptGreen,
                            glowColor = AcceptGlow,
                            pulsing = true,
                            icon = {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = stringResource(R.string.action_accept),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            },
                        )
                    }
                }
            } else {
                InCallPanel(Modifier.padding(bottom = 16.dp)) {
                    if (connected && showKeypad) {
                        InCallDtmfPad(onDigit = onDtmf)
                        InCallPanelDivider()
                    }
                    InCallControlsPanel(
                        showKeypad = showKeypad,
                        onToggleKeypad = { showKeypad = !showKeypad },
                        showKeypadHandle = connected,
                        showTopRow = connected,
                        callHeld = callHeld,
                        onToggleHold = onToggleHold,
                        onTransfer = { showTransferDialog = true },
                        callRecording = callRecording,
                        onToggleRecording = onToggleRecording,
                        audioRoute = audioRoute,
                        micMuted = micMuted,
                        onToggleMute = onToggleMute,
                        onCycleAudio = onCycleAudio,
                        onHangup = if (connected) onHangup else onCancel,
                        outgoingOnly = !connected,
                        onCancel = onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallStatusIndicators(
    callHeld: Boolean,
    callRecording: Boolean,
    reserveSpace: Boolean,
) {
    CallIndicatorSlot(
        visible = callHeld,
        reserved = reserveSpace,
        text = stringResource(R.string.call_on_hold),
        color = TealAccent,
    )
    CallIndicatorSlot(
        visible = callRecording,
        reserved = reserveSpace,
        text = stringResource(R.string.call_recording_active),
        color = RecordRed,
    )
}

@Composable
private fun CallIndicatorSlot(
    visible: Boolean,
    reserved: Boolean,
    text: String,
    color: Color,
) {
    if (!visible && !reserved) return
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Text(
                text = text,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CallStatusHeader(
    statusText: String,
    reconnecting: Boolean,
    callConnectedAt: Long?,
    reserveDurationSpace: Boolean = false,
    reserveIndicatorSpace: Boolean = false,
    callRecording: Boolean = false,
    callHeld: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = statusText,
            color = HeaderGrey,
            fontSize = 16.sp,
        )
        CallStatusIndicators(
            callHeld = callHeld,
            callRecording = callRecording,
            reserveSpace = reserveIndicatorSpace,
        )
        CallReconnectBanner(reconnecting)
        when {
            callConnectedAt != null -> {
                Spacer(Modifier.height(8.dp))
                CallDurationText(startedAtMillis = callConnectedAt)
            }
            reserveDurationSpace -> Spacer(Modifier.height(28.dp))
        }
    }
}


private val KeyFillTop = Color(0xFF243041)
private val KeyFillBottom = Color(0xFF1A2330)
private val KeyBorder = Color(0xFF3A4F63)
private val KeyBorderPressed = Color(0x662DD4BF)

private val InCallDtmfKeys = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("*", "0", "#"),
)

@Composable
fun ActiveCallScreen(
    caller: String,
    called: String,
    contactName: String?,
    contactId: Long? = null,
    outgoing: Boolean,
    reconnecting: Boolean = false,
    callConnectedAt: Long? = null,
    audioRoute: CallAudioRoute = CallAudioRoute.EARPIECE,
    micMuted: Boolean = false,
    callRecording: Boolean = false,
    callHeld: Boolean = false,
    onToggleMute: () -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onToggleHold: () -> Unit = {},
    onTransfer: (String) -> Unit = {},
    onCycleAudio: () -> Unit = {},
    onDtmf: (String) -> Unit = {},
    onHangup: () -> Unit,
) {
    val primary = when {
        outgoing -> contactName ?: called
        else -> contactName ?: caller
    }
    val badgeNumber = if (outgoing) called else caller
    val subtitle = when {
        outgoing -> stringResource(R.string.call_from_endpoint, caller)
        else -> stringResource(R.string.call_to_endpoint, called)
    }
    var showKeypad by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showTransferDialog) {
        TransferCallDialog(
            onDismiss = { showTransferDialog = false },
            onTransfer = { target ->
                showTransferDialog = false
                onTransfer(target)
            },
        )
    }

    DisposableEffect(audioRoute) {
        val guard = ProximityScreenGuard(context)
        guard.setEnabled(audioRoute == CallAudioRoute.EARPIECE)
        onDispose { guard.release() }
    }

    CallScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (showKeypad) {
                    ActiveCallStatusHeader(
                        outgoing,
                        reconnecting,
                        callConnectedAt,
                        callRecording = callRecording,
                        callHeld = callHeld,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = primary,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                } else {
                    ActiveCallStatusHeader(
                        outgoing,
                        reconnecting,
                        callConnectedAt,
                        callRecording = callRecording,
                        callHeld = callHeld,
                    )
                    Spacer(Modifier.height(36.dp))
                    GlowingContactAvatar(label = primary, contactId = contactId)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = primary,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    ExtensionBadge(badgeNumber)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = subtitle,
                        color = TealMuted.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            InCallPanel(Modifier.padding(bottom = 16.dp)) {
                if (showKeypad) {
                    InCallDtmfPad(onDigit = onDtmf)
                    InCallPanelDivider()
                }
                InCallControlsPanel(
                    showKeypad = showKeypad,
                    onToggleKeypad = { showKeypad = !showKeypad },
                    showTopRow = true,
                    callHeld = callHeld,
                    onToggleHold = onToggleHold,
                    onTransfer = { showTransferDialog = true },
                    callRecording = callRecording,
                    onToggleRecording = onToggleRecording,
                    audioRoute = audioRoute,
                    micMuted = micMuted,
                    onToggleMute = onToggleMute,
                    onCycleAudio = onCycleAudio,
                    onHangup = onHangup,
                )
            }
        }
    }
}

@Composable
private fun ActiveCallStatusHeader(
    outgoing: Boolean,
    reconnecting: Boolean,
    callConnectedAt: Long?,
    callRecording: Boolean = false,
    callHeld: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (outgoing) {
                stringResource(R.string.active_outgoing)
            } else {
                stringResource(R.string.active_in_call)
            },
            color = HeaderGrey,
            fontSize = 16.sp,
        )
        CallStatusIndicators(
            callHeld = callHeld,
            callRecording = callRecording,
            reserveSpace = true,
        )
        CallReconnectBanner(reconnecting)
        if (callConnectedAt != null) {
            Spacer(Modifier.height(8.dp))
            CallDurationText(startedAtMillis = callConnectedAt)
        }
    }
}

@Composable
private fun InCallDtmfPad(
    onDigit: (String) -> Unit,
) {
    val keySize = 56.dp
    val rowSpacing = 8.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InCallDtmfKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    InCallDtmfKey(
                        digit = digit,
                        size = keySize,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallDtmfKey(
    digit: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed) 0.94f else 1f
    val fontSize = when {
        size <= 56.dp -> if (digit == "*" || digit == "#") 22.sp else 24.sp
        else -> if (digit == "*" || digit == "#") 26.sp else 28.sp
    }

    Box(
        modifier = Modifier
            .size(size)
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
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            color = Color.White,
        )
    }
}

@Composable
private fun CallDurationText(startedAtMillis: Long) {
    var elapsedSec by remember(startedAtMillis) { mutableIntStateOf(0) }
    LaunchedEffect(startedAtMillis) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
            delay(1.seconds)
        }
    }
    Text(
        text = formatCallDuration(elapsedSec),
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun InCallPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PanelFill)
            .border(1.dp, PanelBorderColor, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun InCallPanelDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = PanelBorderColor.copy(alpha = 0.85f), thickness = 1.dp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun InCallKeypadExpandHandle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 2.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(TealMuted.copy(alpha = 0.3f)),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.action_keypad),
            tint = TealMuted.copy(alpha = 0.65f),
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun InCallControlsPanel(
    showKeypad: Boolean,
    onToggleKeypad: () -> Unit,
    showKeypadHandle: Boolean = true,
    showTopRow: Boolean = false,
    callHeld: Boolean = false,
    onToggleHold: () -> Unit = {},
    onTransfer: () -> Unit = {},
    callRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    audioRoute: CallAudioRoute,
    micMuted: Boolean,
    onToggleMute: () -> Unit,
    onCycleAudio: () -> Unit,
    onHangup: () -> Unit,
    outgoingOnly: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    if (showKeypadHandle) {
        InCallKeypadExpandHandle(expanded = showKeypad, onClick = onToggleKeypad)
    }

    if (showTopRow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InCallControlSlot {
                InCallControlButton(
                    icon = Icons.Default.Pause,
                    contentDescription = stringResource(
                        if (callHeld) R.string.action_unhold else R.string.action_hold,
                    ),
                    active = callHeld,
                    onClick = onToggleHold,
                    size = 56.dp,
                )
            }
            InCallControlSlot {
                InCallControlButton(
                    icon = Icons.Default.FiberManualRecord,
                    contentDescription = stringResource(
                        if (callRecording) R.string.action_stop_record else R.string.action_record,
                    ),
                    active = callRecording,
                    activeColor = RecordRed,
                    onClick = onToggleRecording,
                    size = 56.dp,
                )
            }
            InCallControlSlot {
                InCallControlButton(
                    icon = Icons.AutoMirrored.Filled.PhoneForwarded,
                    contentDescription = stringResource(R.string.action_transfer),
                    active = false,
                    onClick = onTransfer,
                    size = 56.dp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InCallControlSlot {
            InCallControlButton(
                icon = audioRouteIcon(audioRoute),
                contentDescription = audioRouteLabel(audioRoute),
                active = audioRoute != CallAudioRoute.EARPIECE,
                onClick = onCycleAudio,
                size = 56.dp,
            )
        }
        InCallControlSlot {
            if (!outgoingOnly) {
                InCallControlButton(
                    icon = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(
                        if (micMuted) R.string.action_unmute else R.string.action_mute,
                    ),
                    active = micMuted,
                    onClick = onToggleMute,
                    size = 56.dp,
                )
            }
        }
        InCallControlSlot {
            InCallHangupButton(
                onClick = if (outgoingOnly) onCancel ?: onHangup else onHangup,
                contentDescription = stringResource(
                    if (outgoingOnly) R.string.action_cancel else R.string.action_hangup,
                ),
                size = 56.dp,
            )
        }
    }
}

@Composable
private fun TransferCallDialog(
    onDismiss: () -> Unit,
    onTransfer: (String) -> Unit,
) {
    var target by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.transfer_dialog_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { ch -> ch.isDigit() || ch == '*' || ch == '#' } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.transfer_target_hint)) },
                )
                TextButton(
                    onClick = { onTransfer(target.trim()) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = target.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_transfer))
                }
            }
        }
    }
}

@Composable
private fun InCallControlSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun InCallHangupButton(
    onClick: () -> Unit,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = RejectRed,
            contentColor = Color.White,
        ),
    ) {
        Icon(
            Icons.Default.CallEnd,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun InCallControlButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    activeColor: Color = TealAccent,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) activeColor.copy(alpha = 0.25f) else Color(0xFF1E2A38),
            contentColor = if (active) {
                if (activeColor == RecordRed) RecordRed else TealMuted
            } else {
                Color.White.copy(alpha = 0.9f)
            },
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun audioRouteLabel(route: CallAudioRoute): String {
    return when (route) {
        CallAudioRoute.SPEAKER -> stringResource(R.string.audio_route_speaker)
        CallAudioRoute.WIRED_HEADSET -> stringResource(R.string.audio_route_headset)
        CallAudioRoute.BLUETOOTH -> stringResource(R.string.audio_route_bluetooth)
        CallAudioRoute.EARPIECE -> stringResource(R.string.audio_route_earpiece)
    }
}

private fun audioRouteIcon(route: CallAudioRoute): ImageVector {
    return when (route) {
        CallAudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        CallAudioRoute.BLUETOOTH -> Icons.Default.Bluetooth
        CallAudioRoute.WIRED_HEADSET -> Icons.Default.Headset
        CallAudioRoute.EARPIECE -> Icons.Default.Call
    }
}

private fun formatCallDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
    }
}
