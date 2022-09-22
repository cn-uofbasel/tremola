package nz.scuttlebutt.tremola.ssb

import android.os.Build
import androidx.annotation.RequiresApi
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * This class is responsible for encrypting and decrypting messages using the double ratchet
 * algorithm defined by the Signal Protocol. It uses LazySodiumAndroid for the cryptographic
 * primitives.
 * It consists of three individual ratchets, the Diffie-Hellman ratchet, the sending and the
 * receiving ratchet.
 * This implementation does not utilize header encryption, although that would be a possible upgrade
 * to the privacy of the users.
 * Keep in mind: This implementation is general purpose. It is slightly different from the official
 * documentation in that it tries to use the cryptographic libraries also used in SSB to make it
 * easier for the two to work together. It also avoids using ByteArrays, instead relying on Base64
 * encoded strings. This uses more space than the ByteArrays, but is harder to mess up. Furthermore,
 * it uses stringified JSON objects in a lot of places to facilitate passing multiple values.
 * If you want to utilize this in combination with SSB, you can call the constructors with special
 * values which are used by SSB. Alternatively, you can use the SSBDoubleRatchet class which already
 * handles most of the cases.
 * TODO How do we keep the state during a restart?
 * @property dhSent Contains the Diffie-Hellman ratchet key pair (both public and private key). Of
 * this pair, only the public key is sent, the private key is kept secret.
 * @property dhReceived Contains the received public key for the Diffie-Hellman ratchet.
 * @property rootKey The root key obtained from the Diffie-Hellman ratchet.
 * @property chainKeySending The current key for the sending chain.
 * @property chainKeyReceiving The current key for the receiving chain.
 * @property messageNumberSending The number of messages in the current sending chain.
 * @property messageNumberReceiving The number of messages in the current receiving chain.
 * @property previousChainLength The number of messages in the previous sending chain.
 * @property messageKeysSkipped A Hashtable of skipped message keys. They are indexed by the
 * ratchet's public key and the message number in the respective chain, in a stringified JSON
 * object.
 */
class DoubleRatchet {
    private var dhSent: KeyPair // TODO Initialize with SSB ID?
    private var dhReceived: Key?
    private var rootKey: Key
    private var chainKeySending: Key?
    private var chainKeyReceiving: Key? = null
    private var messageNumberSending = 0
    private var messageNumberReceiving = 0
    private var previousChainLength = 0
    private val messageKeysSkipped = Hashtable<String, Key>()

    /**
     * This constructor is used when you are the person sending the first message.
     * @param sharedSecret The shared secret. This is typically derived by
     * TODO Implement.
     */
    constructor(sharedSecret: Key, dhReceivedParameter: Key) {
        dhSent = generateDH()
        dhReceived = dhReceivedParameter
        val rootKeyResult =
            keyDerivationFunctionRootKey(sharedSecret, diffieHellman(dhSent, dhReceived!!))
        rootKey = rootKeyResult.secretKey
        chainKeySending = rootKeyResult.publicKey
    }


    /**
     * This constructor is used when you are the person receiving the first message.
     * TODO Implement.
     */
    constructor(sharedSecret: Key, dhSentParameter: KeyPair) {
        dhSent = dhSentParameter
        dhReceived = null
        rootKey = sharedSecret
        chainKeySending = null
    }


    /**
     * This function takes a plaintext of a message and encrypts it using the double ratchet
     * algorithm to derive the key. The output is Byte64 encoded.
     */
    fun encryptMessage(plaintext: String): String {
        // TODO Implement
        return "hello $plaintext"
    }

    /**
     * This function takes a ciphertext of a message and decrypts it using the double ratchet
     * algorithm to derive the key. The output is Byte64 encoded.
     */
    fun decryptMessage(ciphertext: String): String {
        // TODO Implement
        return "hi $ciphertext"

    }

    /**
     * This class is used as the key of the [messageKeysSkipped] field.
     * This is necessary since Java does not accept tuples for its dictionary type class.
     * @param publicKey The public key of the Diffie-Hellman ratchet (of the other person).
     * @param messageNumber The number of the message in the receiving chain of that public key.
     * TODO Remove once it is replaced with JSON stringified object.
     */
    private class SkippedMessageIdentifier(val publicKey: Key, val messageNumber: Int) {

        /**
         * This function is necessary since objects of this class are used as keys for Hashtables.
         * If this was not implemented, the hashcode would be based on the object's reference.
         * Thus, two different objects with the same contents would be different keys.
         * We want them to generate identical hashcodes though, which is why this class only uses
         * the content of the object, not the reference.
         * Since the Key object does not implement the hashCode function, we encode its byte array
         * in hexadecimal and set it to lowercase to avoid the same problem from above.
         */
        override fun hashCode(): Int {
            return Objects.hash(this.publicKey.asHexString.lowercase(), this.messageNumber)
        }

        /**
         * This function is necessary since objects of this class are used as keys for Hashtables.
         * If this was not implemented, the equality would be based on the object's reference.
         * We do not seek reference equality but content equality.
         * @param other If this object is not null, of type SkippedMessageIdentifier and has
         * identical values in its fields as the this object, returns true. Otherwise, returns
         * false.
         */
        override fun equals(other: Any?): Boolean {
            return if (other != null && other is SkippedMessageIdentifier) {
                (this.publicKey == other.publicKey) && (this.messageNumber == other.messageNumber)
            } else {
                false
            }
        }
    }

    companion object {
        /**
         * This contains the object which does the entire crypto. It calls the native libSodium
         * implementation when used.
         */
        @JvmStatic
        private val lazySodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

        /**
         * Generates a Diffie-Hellman keypair. Uses elliptic curve primitive (Curve25519).
         * @return A new Diffie-Hellman keypair.
         */
        private fun generateDH(): KeyPair {
            return lazySodium.cryptoKxKeypair()
        }

        /**
         * Takes a public and a private Diffie-Hellman key to produce the shared secret.
         * Uses the elliptic curve primitive (Curve25519).
         * @param dhPair Our own Diffie-Hellman key pair.
         * @param dhPublicKey The other person's Diffie-Hellman public key.
         * @return The newly generated key, a shared secret.
         */
        private fun diffieHellman(dhPair: KeyPair, dhPublicKey: Key): Key {
            // TODO Implement. Return value is stand-in.
            return lazySodium.cryptoKxKeypair().publicKey
        }

        /**
         * The key derivation function used for the root chain. Uses primitive HKDF with SHA-512.
         * @param rootKey The root key of the root chain.
         * @param dhOutput The output of the Diffie-Hellman key exchange.
         * @return A new keypair, a root key as secretKey and a chain key as publicKey.
         */
        private fun keyDerivationFunctionRootKey(rootKey: Key, dhOutput: Key): KeyPair {
            // TODO Implement. Return value is stand-in.
            return lazySodium.cryptoKxKeypair()
        }

        /**
         * The key derivation function used for the sending and receiving chains. Uses primitive
         * HMAC with SHA-512.
         * @param chainKey The key of the chain to generate a new key.
         * @return A new keypair, a chain key as secretKey and a message key as publicKey.
         */
        private fun keyDerivationFunctionChainKey(chainKey: Key): KeyPair {
            // TODO Implement. Return value is stand-in.
            return lazySodium.cryptoKxKeypair()
        }

        /**
         * Encrypts the [plaintext] with the given [messageKey]. Also authenticates the
         * [associatedData], but does not encrypt it. Uses the AEAD encryption primitive.
         * // TODO More precision on AEAD implementation.
         * @param messageKey The key to encrypt and authenticate the data with.
         * @param plaintext The message to encrypt and authenticate, a stringified JSON object.
         * @param associatedData The data to authenticate, but not encrypt. Base64 encoded.
         * @return The encrypted and authenticated data as a Base64 encoded string.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun encrypt(
            messageKey: Key,
            plaintext: String,
            associatedData: String
        ): String {
            // TODO Implement. Return value is stand-in.
            return Base64.getEncoder()
                .encodeToString("hi $plaintext $associatedData $messageKey".toByteArray())
        }

        /**
         * Decrypts the [ciphertext] with the given [messageKey]. Also checks the authentication
         * of the [associatedData], which is not encrypted. Uses the AEAD encryption primitive.
         * // TODO More precision on AEAD implementation.
         * @param messageKey The key to decrypt and authenticate the data with.
         * @param ciphertext The message to decrypt and authenticate. Base64 encoded.
         * @param associatedData The data to authenticate, but not decrypt. Base64 encoded.
         * @return The decrypted data as a stringified JSON object.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun decrypt(
            messageKey: Key,
            ciphertext: String,
            associatedData: String
        ): String {
            // TODO Implement. Return value is stand-in.
            return Base64.getEncoder()
                .encodeToString("hello $ciphertext $associatedData $messageKey".toByteArray())
        }

        /**
         * Creates a message header which contains the Diffie-Hellman public key from the [dhPair],
         * the [previousChainLength] and the current [messageNumber].
         * @param dhPair The pair of Diffie-Hellman keys currently used.
         * @param previousChainLength The length of the previous sending chain.
         * @param messageNumber The number of the current message in the current sending chain.
         * @return The stringified JSON object of the public key (Base64 encoded) and the two
         * numbers.
         */
        private fun header(
            dhPair: KeyPair,
            previousChainLength: Int,
            messageNumber: Int
        ): String {
            // TODO Implement. Return value is stand-in.
            return "hey ${dhPair.publicKey} $previousChainLength $messageNumber"
        }

        /**
         * Takes the [header], encodes it in Base64 and prepends the [associatedData] which is
         * already in Base64.
         * @param associatedData The Base64 encoded data that is prepended.
         * @param header Contains the info about the public Diffie-Hellman key, the
         * previousChainLength and the messageNumber. Stringified JSON object, unencoded.
         * @returns [associatedData] + Base64([header])
         */
        private fun concatenate(associatedData: String, header: String): String {
            // TODO Implement. Return value is stand-in.
            return "greetings $associatedData $header"
        }

        /**
         * The maximum number of skipped messages per chain. Prevents a malicious sender from
         * triggering an excessive recipient computation.
         * */
        private val MAX_SKIP = 100
    }

}