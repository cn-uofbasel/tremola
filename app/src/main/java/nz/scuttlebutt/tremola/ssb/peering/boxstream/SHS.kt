package nz.scuttlebutt.tremola.ssb.peering.boxstream

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Auth
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import java.nio.charset.StandardCharsets

import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.ssb.core.SSBid

abstract class SHS( // state for SSB Secure Hand Shake
    val myId: SSBid,
    val networkIdentifier: ByteArray = Constants.SSB_NETWORKIDENTIFIER
) {
    protected val lazySodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

    protected var sharedSecretab: Key? = null
    protected var sharedSecretaB: Key? = null
    protected var sharedSecretAb: Key? = null

    var localEphemeralKeyPair: KeyPair = lazySodium.cryptoKxKeypair()
    var remoteEphemeralKey: ByteArray? = null
    var remoteKey: ByteArray? = null
    var completed = false

    /** Hash-based Message Authentication Code */
    private fun createHMAC(key: ByteArray, message: ByteArray): ByteArray {
        val hmac = ByteArray(Auth.BYTES)
        lazySodium.cryptoAuth(hmac, message, message.size.toLong(), key)
        return hmac
    }

    fun mkHelloMessage(): ByteArray {
        // Create Hash-based Message Authentication Code
        val hmac = createHMAC(networkIdentifier, localEphemeralKeyPair.publicKey.asBytes)
        return hmac + localEphemeralKeyPair.publicKey.asBytes
    }

    /**
     * Asserts that the expected and received Message Authentication Code are equals.
     * If true, compute the shared secrets.
     */
    fun isValidHello(message: ByteArray): Boolean {
        if (message.size != 64)
            return false

        val mac = message.copyOfRange(0, 32)
        val remoteEphemeralKey = message.copyOfRange(32, 64)
        val expectedMac = createHMAC(networkIdentifier, remoteEphemeralKey)

        if (mac contentEquals expectedMac) {
            this.remoteEphemeralKey = remoteEphemeralKey
            computeSharedSecrets()
            return true
        }
        return false
    }

    fun mkBoxStream(): BoxStream {
        val localToRemoteKey = (
                (networkIdentifier + sharedSecretab!!.asBytes + sharedSecretaB!!.asBytes + sharedSecretAb!!.asBytes).sha256().sha256()
                        + remoteKey!!
                ).sha256()

        val remoteToLocalKey = (
                (networkIdentifier + sharedSecretab!!.asBytes + sharedSecretaB!!.asBytes + sharedSecretAb!!.asBytes).sha256().sha256()
                        + myId.verifyKey
                ).sha256()

        val localToRemoteNonce = createHMAC(networkIdentifier, remoteEphemeralKey!!).copyOfRange(0, SecretBox.NONCEBYTES)
        val remoteToLocalNonce = createHMAC(networkIdentifier, localEphemeralKeyPair.publicKey.asBytes).copyOfRange(0, SecretBox.NONCEBYTES)

        return BoxStream(localToRemoteKey, remoteToLocalKey, localToRemoteNonce, remoteToLocalNonce)
    }

    protected abstract fun computeSharedSecrets()
}