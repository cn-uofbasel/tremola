package nz.scuttlebutt.tremola.doubleRatchet

import android.os.Build
import androidx.annotation.RequiresApi
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.DiffieHellman
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import nz.scuttlebutt.tremola.ssb.core.SSBid

/**
 * A specialized [DoubleRatchet] for use with the SSB protocol.
 * For general usage, see the test class.
 * For detailed information on a general [DoubleRatchet], see the respective source file.
 * Instead of relying on a central server like Signal does, this uses the decentralized peer-to-peer
 * protocol Scuttlebutt. Because of this, it is not guaranteed that the correspondence partner is
 * online for a Diffie-Hellman key exchange or anyone has his prekeys (such as with Signal).
 * Thus, we utilize other methods to make it possible to send messages even with the other person
 * offline. The keys used in the DoubleRatchet have to be Curve25519, but the SSB Identities are in
 * Ed25519, which is why we have to transform those.
 */
class SSBDoubleRatchet : DoubleRatchet {

    /**
     * This constructor is used when you are the person sending the first message.
     * The long term identity keys are Ed25519 keys. They have to be converted to Curve25519 via the
     * function convertKeyPairEd25519ToCurve25519 on the your side. You will then send the converted
     * public key to the recipient.
     * @param sharedSecret The scalar product of your private key and your correspondent's public
     * key. Both have been converted from Ed25519 to Curve25519 before being passed to this
     * constructor.
     * @param publicKeyReceived Your correspondent's public SSB ID. It has been converted from
     * Ed25519 to Curve25519 ahead of time.
     */
    constructor(sharedSecret: Key, publicKeyReceived: Key) : super(sharedSecret, publicKeyReceived)

    /**
     * This constructor is used when you are the person receiving the first message.
     * The long term identity keys are Ed25519 keys. They have to be converted to Curve25519 via the
     * function convertKeyPairEd25519ToCurve25519 on the sender's side, who will then send you the
     * public key of the Keypair they obtained.
     * @param sharedSecret The scalar product of your private key and your correspondent's public
     * key. Both have been converted from Ed25519 to Curve25519 before being passed to this
     * constructor.
     * @param keyPairSent Your SSB ID KeyPair. It has been converted from Ed25519 to Curve25519
     * ahead of time.
     */
    constructor(sharedSecret: Key, keyPairSent: KeyPair) : super(sharedSecret, keyPairSent)


    /**
     * This constructor is used when you are deserializing the object from a string.
     * @param jsonString The string of the serialized DoubleRatchet object in JSON.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    constructor(jsonString: String) : super(jsonString)


    companion object {
        /**
         * This object is a cast of the lazySodium object to use its lazy functions for signatures
         * (most importantly for transforming between key types (Ed25519 to Curve25519).
         */
        private val signLazy: Sign.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its native functions for signatures
         * (most importantly for transforming between key types (Ed25519 to Curve25519).
         */
        private val signNative: Sign.Native = lazySodium

        /**
         * Takes an [SSBid] and returns a [KeyPair] transformed from Ed25519 to Curve25519.
         * @param ssbID An SSBid object, containing a pair of keys in Ed25519.
         * @return A [KeyPair] in Curve25519, transformed from the input.
         */
        fun ssbIDToCurve(ssbID: SSBid): KeyPair {
            val ed25519PublicKey = Key.fromBytes(ssbID.signingKey)
            val ed25519SecretKey = Key.fromBytes(ssbID.verifyKey)
            println("edPublicKeyLength = ${ed25519PublicKey.asBytes.size}")
            println("edSecretKeyLength = ${ed25519SecretKey.asBytes.size}")

            // Once again, the labels of the arguments of KeyPair are wrong.
            // The order of secret and public key is inverted for the conversion function.
            val ed25519KeyPair = KeyPair(ed25519SecretKey, ed25519PublicKey)
            return signLazy.convertKeyPairEd25519ToCurve25519(ed25519KeyPair)
        }

        /**
         * Takes a public Ed25519 key, such as the SSB public ID, and transforms it to a Curve25519
         * key.
         * @param publicEDKey The key of your correspondent, in Ed25519.
         * @return The Curve25519 equivalent of your correspondent's key.
         * @throws SodiumException If the key could not be converted.
         */
        fun publicEDKeyToCurve(publicEDKey: Key): Key {
            val numOfBytes: Int = DiffieHellman.SCALARMULT_CURVE25519_BYTES
            val edByteArray = publicEDKey.asBytes
            val curveByteArray = ByteArray(numOfBytes)
            val result = signNative.convertPublicKeyEd25519ToCurve25519(curveByteArray, edByteArray)
            if (result) {
                return Key.fromBytes(curveByteArray)
            } else {
                throw SodiumException("Could not convert public key.")
            }
        }

        /**
         * Takes an [SSBid] containing two Ed25519 keys and the public Ed25519 key of someone else and
         * calculates a shared secret.
         * @param ownSSBid Your own [SSBid].
         * @param otherPublicEDKey The key of your correspondent, in Ed25519.
         * @return Shared secret, a scalar multiplication of your private key and someone else's
         * public key (each after being transformed to Curve25519).
         * @throws SodiumException If the key could not be converted.
         */
        fun calculateSharedSecret(ownSSBid: SSBid, otherPublicEDKey: Key): Key {
            val ownCurveKeyPair = ssbIDToCurve(ownSSBid)
            val otherCurvePublicKey = publicEDKeyToCurve(otherPublicEDKey)
            return diffieHellman(ownCurveKeyPair, otherCurvePublicKey)
        }
    }
}