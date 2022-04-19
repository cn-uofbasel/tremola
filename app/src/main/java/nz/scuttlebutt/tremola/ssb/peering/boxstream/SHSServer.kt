package nz.scuttlebutt.tremola.ssb.peering.boxstream

import android.util.Log
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import java.io.InputStream
import java.io.OutputStream

import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretBox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.secretUnbox
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.toKey
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.verifySignDetached
import nz.scuttlebutt.tremola.ssb.core.SSBid

class SHSServer(
    myId: SSBid,
    networkIdentifier: ByteArray = Constants.SSB_NETWORKIDENTIFIER
) : SHS(myId, networkIdentifier) {
    private var detachedSignature: ByteArray? = null

    fun performHandshake(inputStream: InputStream, outputStream: OutputStream): BoxStream? {
        outputStream.write(mkHelloMessage())
        outputStream.flush()
        var buf = ByteArray(64)
        var size = inputStream.read(buf)
        if (size == -1 || !isValidHello(buf)) {
            Log.d("Handshake", "no hello, or bad?$size")
            return null
        }
        buf = ByteArray(112)
        size = inputStream.read(buf)
        if (size == -1 || !isValidAuthenticate(buf)) {
            Log.d("Handshake", "no authenticate, or bad?$size")
            return null
        }
        outputStream.write(mkAcceptMessage())
        outputStream.flush()
        return mkBoxStream()
    }

    fun isValidAuthenticate(message: ByteArray): Boolean {
        val zeroNonce = ByteArray(SecretBox.NONCEBYTES)
        val hashKey = (networkIdentifier + sharedSecretab!!.asBytes + sharedSecretaB!!.asBytes).sha256()
        val dataPlainText = secretUnbox(message, zeroNonce, hashKey)

        val detachedSignatureA = dataPlainText?.copyOfRange(0, 64)
        val clientLongTermPublicKey = dataPlainText?.copyOfRange(64, 96)
        val hashab = sharedSecretab!!.asBytes.sha256()
        val expectedMessage = networkIdentifier + myId.verifyKey + hashab

        if (verifySignDetached(detachedSignatureA!!, expectedMessage, clientLongTermPublicKey!!)) {
            this.remoteKey = clientLongTermPublicKey
            this.detachedSignature = detachedSignatureA

            val curve25519ClientKey = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
            lazySodium.convertPublicKeyEd25519ToCurve25519(curve25519ClientKey, remoteKey!!)
            this.sharedSecretAb =
                lazySodium.cryptoScalarMult(localEphemeralKeyPair.secretKey, curve25519ClientKey.toKey())
            return true
        }
        return false
    }

    fun mkAcceptMessage(): ByteArray {
        val hashab = sharedSecretab?.asBytes?.sha256()
        val message = networkIdentifier + detachedSignature!! + remoteKey!! + hashab!!
        val detachedSignatureB = myId.sign(message)

        val zeroNonce = ByteArray(SecretBox.NONCEBYTES)
        val key = (networkIdentifier + sharedSecretab!!.asBytes +
                sharedSecretaB!!.asBytes + sharedSecretAb!!.asBytes).sha256()
        val body = secretBox(detachedSignatureB, zeroNonce, key)

        completed = true
        return body
    }

    override fun computeSharedSecrets() {
        sharedSecretab = lazySodium.cryptoScalarMult(localEphemeralKeyPair.secretKey, remoteEphemeralKey!!.toKey())
        sharedSecretaB = Key.fromBytes(myId.deriveSharedSecretAb(remoteEphemeralKey!!))
    }
}
