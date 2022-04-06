package nz.scuttlebutt.tremola.ssb.peering.boxstream

import com.goterl.lazysodium.interfaces.SecretBox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.increment
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretBox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretUnbox
import java.io.IOException
import java.io.InputStream

class BoxStream(
    private val clientToServerKey: ByteArray,
    private val serverToClientKey: ByteArray,
    private val clientToServerNonce: ByteArray,
    private val serverToClientNonce: ByteArray
) {
    companion object {
        const val HEADER_SIZE = 34
        const val MAX_MESSAGE_SIZE = 4096
    }

    var receiveCount = 0

    fun sendToClient(message: ByteArray): ByteArray {
        return encrypt(message, serverToClientKey, serverToClientNonce)
    }

    fun readFromClient(source: InputStream): ByteArray? {
        return decrypt(source, clientToServerKey, clientToServerNonce)
    }

    fun encryptForServer(message: ByteArray): ByteArray {
        return encrypt(message, clientToServerKey, clientToServerNonce)
    }

    fun readFromServer(source: InputStream): ByteArray? {
        return decrypt(source, serverToClientKey, serverToClientNonce)
    }

    fun createGoodbye(key: ByteArray, nonce: ByteArray): ByteArray {
        return encryptSegment(ByteArray(18), key, nonce)
    }

    private fun decrypt(source: InputStream, key: ByteArray, nonce: ByteArray): ByteArray? {
        val headerNonce = nonce.copyOf()
        val hdrBuffer = ByteArray(HEADER_SIZE)
        var hdrLen = 0
        while (hdrLen < HEADER_SIZE) {
            val sz = source.read(hdrBuffer, hdrLen, HEADER_SIZE - hdrLen)
            if (sz < 0) throw IOException("in decrypt")
            if (sz > 0) hdrLen += sz
            // Log.d("decrypt", "hdr size increased by " + sz.toString())
        }
        val header = secretUnbox(hdrBuffer, headerNonce, key)
        if (header == null) {
            // Log.d("decrypt", "hdr unbox")
            throw IOException("decrypt secretUnbox")
        }
        var bodyLen = (header[0].toInt() and 0xFF) * 256 + (header[1].toInt() and 0xFF)
        val bodyTag = header.copyOfRange(2, header.size)
        // Log.d("copyofrange", bodyTag.size.toString() + " " + bodyLen.toString())
        val bodyNonce = nonce.copyOf().increment()

        bodyLen += bodyTag.size
        val rdBuf = ByteArray(bodyLen)
        bodyTag.copyInto(rdBuf)
        var cnt = bodyTag.size
        while (cnt < bodyLen) {
            val sz = source.read(rdBuf, cnt, bodyLen - cnt)
            if (sz < 0) throw IOException("in decrypt");
            if (sz > 0) cnt += sz
            // Log.d("decrypt", "encrBody size increased by " + sz.toString())
        }
        // Log.d("rdBuf size", rdBuf.size.toString() + " " + bodyLen.toString())
        val decryptedBody = secretUnbox(rdBuf, bodyNonce, key)
        if (decryptedBody == null)
            throw IOException("decrypt secretUnbox 2");
        nonce.increment()
        nonce.increment()

        receiveCount += 1
        // Log.d("rx", "packet #" + receiveCount.toString() + " with " + decryptedBody!!.size.toString() + "B")
        return decryptedBody
    }

    private fun encrypt(message: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        var buf = ByteArray(0)
        var remaining = message.size
        var offset = 0

        while (remaining > 0) {
            val sz = if (remaining > MAX_MESSAGE_SIZE) MAX_MESSAGE_SIZE
            else remaining
            val segment = message.copyOfRange(offset, offset + sz)
            buf += encryptSegment(segment, key, nonce)
            offset += sz
            remaining -= sz
        }
        return buf
    }

    private fun encryptSegment(seg: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val headerNonce = nonce.copyOf()
        val bodyNonce = nonce.increment().copyOf()
        nonce.increment()

        val encryptedBody = secretBox(seg, bodyNonce, key)

        val sz = byteArrayOf((seg.size / 256).toByte(), (seg.size % 256).toByte())
        val hdrVal = sz + encryptedBody.copyOfRange(0, SecretBox.MACBYTES)
        // Log.d("hdrVal before encr", hdrVal.toHex() + ", " + messageSegment.size.toString())
        val encryptedHeader = secretBox(hdrVal, headerNonce, key)

        return encryptedHeader + encryptedBody.copyOfRange(SecretBox.MACBYTES, encryptedBody.size)
    }
}
