package nz.scuttlebutt.tremola.ssb.peering.boxstream

import android.util.Log
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretBox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretUnbox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.toKey
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.verifySignDetached
import nz.scuttlebutt.tremola.ssb.core.SSBid
import nz.scuttlebutt.tremola.utils.Constants
import java.io.InputStream
import java.io.OutputStream

class SHSClient(
    myId: SSBid,
    serverKey: ByteArray,
    networkIdentifier: ByteArray = Constants.SSB_NETWORKIDENTIFIER
) : SHS(myId, networkIdentifier) {

    init {
        remoteKey = serverKey
    }

    private var detachedSignatureA: ByteArray? = null

    fun performHandshake(inputStream: InputStream, outputStream: OutputStream): BoxStream? {
        outputStream.write(mkHelloMessage())
        outputStream.flush()
        var buf = ByteArray(64)
        var size = inputStream.read(buf)
        if (size != -1 && isValidHello(buf)) {
            outputStream.write(mkAuthenticateMessage())
            outputStream.flush()
        } else {
            Log.d("Handshake", "no hello, or bad?$size")
            return null
        }
        buf = ByteArray(80)
        size = inputStream.read(buf)
        if (size == -1 || !isValidAccept(buf)) {
            Log.d("Handshake", "no accept, or bad?$size")
            return null
        }
        return mkBoxStream()
    }

    fun isValidAccept(message: ByteArray): Boolean {
        val zeroNonce = ByteArray(SecretBox.NONCEBYTES)
        val responseKey = (networkIdentifier
                + sharedSecretab!!.asBytes
                + sharedSecretaB!!.asBytes
                + sharedSecretAb!!.asBytes).sha256()

        val hash = sharedSecretab!!.asBytes.sha256()

        val expectedMessage = networkIdentifier + detachedSignatureA!! + myId.verifyKey + hash

        secretUnbox(message, zeroNonce, responseKey)?.let {
            completed = verifySignDetached(it, expectedMessage, remoteKey!!)
            return completed
        }

        return false
    }

    fun mkAuthenticateMessage(): ByteArray {
        // Log.d("auth",sharedSecretab!!.asBytes.toHex())
        val hash = sharedSecretab!!.asBytes.sha256()
        val message = networkIdentifier + remoteKey!! + hash
        detachedSignatureA = myId.sign(message)

        val finalMessage = detachedSignatureA!! + myId.verifyKey
        val zeroNonce = ByteArray(SecretBox.NONCEBYTES)
        val boxKey = (networkIdentifier + sharedSecretab!!.asBytes + sharedSecretaB!!.asBytes).sha256()

        return secretBox(finalMessage, zeroNonce, boxKey)
    }

    override fun computeSharedSecrets() {
        val curve25519ServerKey = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        lazySodium.convertPublicKeyEd25519ToCurve25519(curve25519ServerKey, remoteKey!!)

        sharedSecretab = lazySodium.cryptoScalarMult(localEphemeralKeyPair.secretKey, remoteEphemeralKey?.toKey())
        sharedSecretaB = lazySodium.cryptoScalarMult(localEphemeralKeyPair.secretKey, curve25519ServerKey.toKey())
        sharedSecretAb = Key.fromBytes(myId.deriveSharedSecretAb(remoteEphemeralKey!!))
    }

}