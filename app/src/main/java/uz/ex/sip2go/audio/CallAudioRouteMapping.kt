package uz.ex.sip2go.audio

import android.os.Build
import android.telecom.CallAudioState
import android.telecom.CallEndpoint

fun CallAudioRoute.toTelecomRoute(): Int = when (this) {
    CallAudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
    CallAudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
    CallAudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
    CallAudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
}

fun CallAudioRoute.toCallEndpointType(): Int = when (this) {
    CallAudioRoute.SPEAKER -> CallEndpoint.TYPE_SPEAKER
    CallAudioRoute.EARPIECE -> CallEndpoint.TYPE_EARPIECE
    CallAudioRoute.WIRED_HEADSET -> CallEndpoint.TYPE_WIRED_HEADSET
    CallAudioRoute.BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
}

fun CallAudioState.toCallAudioRoute(): CallAudioRoute = when (route) {
    CallAudioState.ROUTE_SPEAKER -> CallAudioRoute.SPEAKER
    CallAudioState.ROUTE_WIRED_HEADSET -> CallAudioRoute.WIRED_HEADSET
    CallAudioState.ROUTE_BLUETOOTH -> CallAudioRoute.BLUETOOTH
    else -> CallAudioRoute.EARPIECE
}

fun CallEndpoint.toCallAudioRoute(): CallAudioRoute = when (endpointType) {
    CallEndpoint.TYPE_SPEAKER -> CallAudioRoute.SPEAKER
    CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioRoute.WIRED_HEADSET
    CallEndpoint.TYPE_BLUETOOTH -> CallAudioRoute.BLUETOOTH
    else -> CallAudioRoute.EARPIECE
}

fun List<CallEndpoint>.findForRoute(route: CallAudioRoute): CallEndpoint? {
    val preferredType = route.toCallEndpointType()
    return firstOrNull { it.endpointType == preferredType }
        ?: firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
}

fun supportsCallEndpointApi(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
