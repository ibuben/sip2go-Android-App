package uz.ex.sip2go.telecom

import android.telecom.DisconnectCause

object DisconnectCauseMapper {
    fun fromReason(reason: String): DisconnectCause {
        val code = when (reason) {
            "rejected", "declined" -> DisconnectCause.REJECTED
            "remote_hangup", "hangup" -> DisconnectCause.REMOTE
            "busy" -> DisconnectCause.BUSY
            "cancelled", "failed" -> DisconnectCause.CANCELED
            "reconnect_timeout" -> DisconnectCause.ERROR
            else -> DisconnectCause.LOCAL
        }
        return DisconnectCause(code)
    }
}
