package uz.ex.sip2go.telecom

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CallConnectionRegistry {
    private val byCallId = ConcurrentHashMap<UUID, SipConnection>()
    private val outgoingByNumber = ConcurrentHashMap<String, SipConnection>()

    fun registerIncoming(callId: UUID, connection: SipConnection) {
        byCallId[callId] = connection
    }

    fun registerOutgoing(number: String, connection: SipConnection) {
        outgoingByNumber[normalize(number)] = connection
    }

    fun linkOutgoingCallId(callId: UUID, number: String) {
        val key = normalize(number)
        outgoingByNumber.remove(key)?.let { connection ->
            connection.bindCallId(callId)
            byCallId[callId] = connection
        }
    }

    fun get(callId: UUID): SipConnection? = byCallId[callId]

    fun remove(callId: UUID) {
        byCallId.remove(callId)
    }

    fun clear() {
        byCallId.clear()
        outgoingByNumber.clear()
    }

    private fun normalize(number: String): String = number.filter { it.isDigit() || it == '*' || it == '#' }
}
