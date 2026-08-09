package uz.ex.sip2go.network

import java.nio.ByteBuffer
import java.util.UUID

object Protocol {
    const val AUDIO_FRAME_TYPE: Byte = 0x01

    const val REGISTER = "register"
    const val REGISTERED = "registered"
    const val ERROR = "error"
    const val INCOMING_CALL = "incoming_call"
    const val ACCEPT = "accept"
    const val REJECT = "reject"
    const val HANGUP = "hangup"
    const val DIAL = "dial"
    const val DIALING = "dialing"
    const val CALL_RINGING = "call_ringing"
    const val CALL_ACCEPTED = "call_accepted"
    const val CALL_ENDED = "call_ended"
    const val CALL_RESYNC = "call_resync"
    const val CALL_HOLD_CHANGED = "call_hold_changed"
    const val HOLD = "hold"
    const val TRANSFER = "transfer"
    const val DTMF = "dtmf"
    const val PING = "ping"
    const val PONG = "pong"

    fun packAudio(callId: UUID, opusPayload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 16 + opusPayload.size)
        buffer.put(AUDIO_FRAME_TYPE)
        buffer.put(uuidToBytes(callId))
        buffer.put(opusPayload)
        return buffer.array()
    }

    fun unpackAudio(data: ByteArray): Pair<UUID, ByteArray> {
        require(data.size >= 17) { "audio frame too short" }
        require(data[0] == AUDIO_FRAME_TYPE) { "unknown frame type ${data[0]}" }
        val callId = bytesToUuid(data.copyOfRange(1, 17))
        val opus = data.copyOfRange(17, data.size)
        return callId to opus
    }

    fun uuidToBytes(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    fun bytesToUuid(bytes: ByteArray): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }
}
