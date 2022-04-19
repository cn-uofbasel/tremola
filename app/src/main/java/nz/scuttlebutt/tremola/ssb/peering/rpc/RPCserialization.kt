package nz.scuttlebutt.tremola.ssb.peering.rpc

import android.util.Log
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toByteArray
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toInt32
import kotlin.experimental.and
import kotlin.experimental.or

class RPCserialization {
    companion object {
        // 1 byte flags, 4 bytes body length, 4 bytes request number
        const val HEADER_SIZE = 9

        private const val STREAM: Byte = 0b00001000
        private const val ENDERROR: Byte = 0b00000100
        const val BINARY: Byte = 0b00000000
        private const val UTF8: Byte = 0b00000001
        private const val JSON: Byte = 0b00000010

        enum class RPCBodyType {
            UTF8, JSON, BINARY
        }

        fun getBodyLength(header: ByteArray): Int {
            if (header.size < HEADER_SIZE) {
                Log.e("EXCEPTION: ", "Wrong header size: ${header.size}")
                throw RuntimeException("Header wrong size.")
            }
            return header.copyOfRange(1, 5).toInt32()
        }

        /**
         * Parse a received message
         */
        fun fromByteArray(buf: ByteArray): RPCMessage {
            val header = buf.copyOfRange(0, HEADER_SIZE)
            val flags = header[0]
            val stream = (flags and STREAM) != 0x00.toByte()
            val enderror = (flags and ENDERROR) != 0x00.toByte()
            val bodyType = when {
                flags and JSON != 0x00.toByte() -> RPCBodyType.JSON
                flags and UTF8 != 0x00.toByte() -> RPCBodyType.UTF8
                else -> RPCBodyType.BINARY
            }
            val bodyLength = header.copyOfRange(1, 5).toInt32()
            val requestNumber = header.copyOfRange(5, 9).toInt32()
            val body = buf.copyOfRange(HEADER_SIZE, bodyLength + HEADER_SIZE)
            /*
            Log.d("deserialize Boxstream message", "hdr: ${header.toHex()}, buf len: ${buf.size}, body len: ${body.size}")
            var s = body.toHex() // .decodeToString()
            var i = 0
            while (s.length > 0) {
                val t = if (s.length > 64) s.substring(0, 64) else s
                Log.d("BOX " + i, t)
                if (s.length <= 64)
                    break
                s = s.slice(64 .. s.lastIndex)
                i++
            }
            */
            return RPCMessage(stream, enderror, bodyType, bodyLength, requestNumber, body)
        }

        fun mkGoodbye(requestNumber: Int): ByteArray {
            return toByteArray(
                RPCMessage(
                    false,
                    true,
                    RPCBodyType.BINARY,
                    9,
                    requestNumber,
                    ByteArray(9)
                )
            )
        }

        /**
         * Create a message before sending it.
         */
        fun toByteArray(msg: RPCMessage): ByteArray {
            var headerFlags = 0x00.toByte()
            if (msg.stream) headerFlags = headerFlags or STREAM
            if (msg.endError) headerFlags = headerFlags or ENDERROR
            headerFlags = when (msg.bodyType) {
                RPCBodyType.JSON -> headerFlags or JSON
                RPCBodyType.UTF8 -> headerFlags or UTF8
                RPCBodyType.BINARY -> headerFlags
            }
            val lenField = msg.rawEvent.size.toByteArray()
            val rnrField = msg.requestNumber.toByteArray()
            return byteArrayOf(headerFlags) + lenField + rnrField + msg.rawEvent
        }
    }
}