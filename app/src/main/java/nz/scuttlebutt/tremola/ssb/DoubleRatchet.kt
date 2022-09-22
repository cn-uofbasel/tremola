package nz.scuttlebutt.tremola.ssb

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.DiffieHellman
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.json.JSONObject
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
 * TODO
 *  <ul>
 *  <li> How do we keep the state during a restart?
 *  <li> What happens if one of the participants sends a first message while a first message from
 *  another party is underway?
 *  <li> Where is it necessary to cast from Ed25519 to Curve25519?
 *  <li> Where do we need to correct key length?
 *  </ul>
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
     * @param sharedSecret The shared secret. This is typically derived via a Diffie-Hellman key
     * exchange or in a SSB setting by doing a scalar multiplication of the sender's secret key
     * with the recipient's public key.
     * @param dhReceivedParameter The recipient's Diffie-Hellman public key.
     */
    constructor(sharedSecret: Key, dhReceivedParameter: Key) {
        dhSent = generateDH()
        dhReceived = dhReceivedParameter
        val rootRatchetResult =
            keyDerivationFunctionRootKey(sharedSecret, diffieHellman(dhSent, dhReceived!!))
        rootKey = rootRatchetResult.secretKey
        chainKeySending = rootRatchetResult.publicKey
    }


    /**
     * This constructor is used when you are the person receiving the first message.
     * @param sharedSecret The shared secret. This is typically derived via a Diffie-Hellman key
     * exchange or in a SSB setting by doing a scalar multiplication of the sender's secret key
     * with the recipient's public key (see SSB Handshake).
     * @param dhSentParameter The keypair that makes up your Diffie-Hellman ratchet. The sender
     * knows the public key already.
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
     * @param plaintext The plaintext of the message to send. It will be encrypted and signed.
     * @param associatedData The Base64 encoded associated data to sign but not encrypt.
     * @return The stringified JSON object with the fields header and encodedEncryptedMessage. Only the
     * second is Base64 encoded.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun encryptMessage(plaintext: String, associatedData: String): String {
        // This should never happen (except with a very specific race condition where we are both
        // receiving and sending at the same time, maybe), but just in case we throw an exception
        // for debugging.
        if (chainKeySending == null) {
            Log.e("DoubleRatchet:encryptMessage", "chainKeySending is missing.")
            throw Exception("DoubleRatchet:encryptMessage, chainKeySending is missing.")
        }
        val chainRatchetResult =
            keyDerivationFunctionChainKey(chainKeySending!!)
        chainKeySending = chainRatchetResult.secretKey
        val messageKey = chainRatchetResult.publicKey
        val header = header(dhSent, previousChainLength, messageNumberSending)
        messageNumberSending += 1
        val jsonObject = JSONObject()
        jsonObject.put(HEADER, header)
        val encodedEncryptedMessage =
            encrypt(messageKey, plaintext, concatenate(associatedData, header))
        jsonObject.put(ENCODED_ENCRYPTED_MESSAGE, encodedEncryptedMessage)
        return jsonObject.toString()
    }

    /**
     * This function takes a ciphertext of a message and decrypts it using the double ratchet
     * algorithm to derive the key. The output is Byte64 encoded.
     * @param header The stringified JSON object of the public Diffie-Hellman key (Base64 encoded)
     * and the two numbers representing the length of the previous receiving chain and the
     * message's number in the current receiving chain.
     * @param encodedCiphertext The encrypted text that was received. Base64 encoded.
     * @param associatedData The signed but unencrypted data that came with the message. Base64
     * encoded.
     * @return A stringified JSON object signifying the decrypted message.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun decryptMessage(header: String, encodedCiphertext: String, associatedData: String): String {
        // Try decoding with a key we skipped.
        val plaintext = trySkippedMessageKeys(header, encodedCiphertext, associatedData)
        if (plaintext != null) {
            return plaintext
        }
        val headerObject = JSONObject(header)
        val encodedDiffieHellman = headerObject.getString(DH_PUBLIC)
        val messageDiffieHellman = Key.fromBase64String(encodedDiffieHellman)
        if (messageDiffieHellman != dhReceived) { // New receiving chain was started.
            skipMessageKeys(headerObject.getInt(PREVIOUS_CHAIN_LENGTH))
            dhRatchet(header)
        }
        skipMessageKeys(headerObject.getInt(MESSAGE_NUMBER))
        // This should never happen (except with a very specific race condition where we are both
        // receiving and sending at the same time, maybe), but just in case we throw an exception
        // for debugging.
        if (chainKeyReceiving == null) {
            Log.e("DoubleRatchet:decryptMessage", "chainKeyReceiving is missing.")
            throw Exception("DoubleRatchet:decryptMessage, chainKeyReceiving is missing.")
        }
        // Do a ratchet step and decrypt.
        val chainRatchetResult = keyDerivationFunctionChainKey(chainKeyReceiving!!)
        chainKeyReceiving = chainRatchetResult.secretKey
        val messageKey = chainRatchetResult.publicKey
        messageNumberReceiving += 1
        return decrypt(messageKey, encodedCiphertext, concatenate(associatedData, header))
    }

    /**
     * This function checks if the given message can be decrypted with a key we skipped previously.
     * @param header The stringified JSON object of the public Diffie-Hellman key (Base64 encoded)
     * and the two numbers representing the length of the previous receiving chain and the
     * message's number in the current receiving chain.
     * @param encodedCiphertext The encrypted text that was received. Base64 encoded.
     * @param associatedData The signed but unencrypted data that came with the message. Base64
     * encoded.
     * @return A stringified JSON object signifying the decrypted message.     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun trySkippedMessageKeys(
        header: String,
        encodedCiphertext: String,
        associatedData: String
    ): String? {
        val headerObject = JSONObject(header)
        val skippedMessageIdentifierObject = JSONObject()
        skippedMessageIdentifierObject.put(DH_PUBLIC, headerObject.getString(DH_PUBLIC))
        skippedMessageIdentifierObject.put(MESSAGE_NUMBER, headerObject.getString(MESSAGE_NUMBER))
        val skippedMessageIdentifier = skippedMessageIdentifierObject.toString()
        return if (skippedMessageIdentifier in messageKeysSkipped) {
            val messageKey = messageKeysSkipped.remove(skippedMessageIdentifier)
            decrypt(messageKey!!, encodedCiphertext, concatenate(associatedData, header))
        } else {
            null
        }
    }

    /**
     * Stores all message keys in [messageKeysSkipped] in the current receiving ratchet up to but
     * not including [until].
     * Will throw an exception if too many keys are skipped at a time, hinting at a DoS attack.
     * @param until After the message keys are skipped, this will be the value that
     * [messageNumberReceiving] will have. All keys in between are stored in [messageKeysSkipped].
     */
    private fun skipMessageKeys(until: Int) {
        if (messageNumberReceiving + MAX_SKIP < until) { // Number of messages to skip is too big.
            Log.e("DoubleRatchet:skipMessageKeys", "Too many skipped messages.")
            throw Exception("DoubleRatchet:skipMessageKeys, too many skipped messages.")
        }
        // True unless this is the first message received.
        if (chainKeyReceiving != null) {
            while (messageNumberReceiving < until) {
                val chainRatchetResult = keyDerivationFunctionChainKey(chainKeyReceiving!!)
                chainKeyReceiving = chainRatchetResult.secretKey
                val messageKey = chainRatchetResult.publicKey
                val skippedMessageIdentifierObject = JSONObject()
                skippedMessageIdentifierObject.put(DH_PUBLIC, dhReceived)
                skippedMessageIdentifierObject.put(MESSAGE_NUMBER, messageNumberReceiving)
                val skippedMessageIdentifier = skippedMessageIdentifierObject.toString()
                messageKeysSkipped[skippedMessageIdentifier] = messageKey
                messageNumberReceiving += 1
            }
        }
    }

    /**
     * This performs two ratchet steps for the Diffie-Hellman (Root) ratchet. It creates two new
     * ratchets, one for the receiving and one for the sending chain. For each new ratchet it
     * performs one step.
     * @param header The stringified JSON object of the public Diffie-Hellman key (Base64 encoded)
     * and the two numbers representing the length of the previous receiving chain and the
     * message's number in the current receiving chain.
     */
    private fun dhRatchet(header: String) {
        previousChainLength = messageNumberSending
        messageNumberSending = 0
        messageNumberReceiving = 0
        val headerObject = JSONObject(header)
        val encodedDiffieHellman = headerObject.getString(DH_PUBLIC)
        dhReceived = Key.fromBase64String(encodedDiffieHellman)
        if (dhReceived == null) { // Something went wrong when decoding the key.
            Log.e("DoubleRatchet:dhRatchet", "dhReceived is null.")
            throw Exception("DoubleRatchet:dhRatchet, dhReceived is null.")
        }
        if (dhReceived!!.asHexString == "") { // Something went wrong when decoding the key.
            Log.e("DoubleRatchet:dhRatchet", "dhReceived is empty.")
            throw Exception("DoubleRatchet:dhRatchet, dhReceived is empty.")
        }
        var rootRatchetResult =
            keyDerivationFunctionRootKey(rootKey, diffieHellman(dhSent, dhReceived!!))
        rootKey = rootRatchetResult.secretKey
        chainKeyReceiving = rootRatchetResult.publicKey
        dhSent = generateDH()
        rootRatchetResult =
            keyDerivationFunctionRootKey(rootKey, diffieHellman(dhSent, dhReceived!!))
        rootKey = rootRatchetResult.secretKey
        chainKeySending = rootRatchetResult.publicKey
    }

    companion object {
        /**
         * This contains the object which does the entire crypto. It calls the native libSodium
         * implementation when used.
         */
        @JvmStatic
        private val lazySodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for
         * Diffie-Hellman key exchanges.
         */
        private val diffieHellmanLazy: DiffieHellman.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for general
         * encryption (AEAD).
         */
        private val secretBoxLazy: SecretBox.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for AEAD
         * encryption.
         */
        private val aeadLazy: AEAD.Lazy = lazySodium

        /**
         * The object to encode Strings or ByteArrays to Base64 Strings.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val base64Encoder = Base64.getEncoder()

        /**
         * The object to decode Base64 Strings to ByteArrays or Strings.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val base64Decoder = Base64.getDecoder()


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
            return diffieHellmanLazy.cryptoScalarMult(dhPublicKey, dhPair.secretKey)
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
         * [associatedData], but does not encrypt it. Uses the AEAD encryption
         * primitive with the XChaCha20-Poly1305 construction.
         * @param messageKey The key to encrypt and authenticate the data with.
         * @param plaintext The message to encrypt and authenticate, a stringified JSON object.
         * @param associatedData The data to authenticate, but not encrypt. Base64 encoded.
         * @return A stringified JSON object which contains the nonce and the ciphertext, both
         * Base64 encoded.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun encrypt(
            messageKey: Key,
            plaintext: String,
            associatedData: String
        ): String {
            val nonce = lazySodium.nonce(SecretBox.NONCEBYTES)
            // TODO Alternative version if the AEAD should not work as intended.
            // secretBoxLazy.cryptoSecretBoxEasy(plaintext, nonce, messageKey)
            val ciphertext = aeadLazy.encrypt(
                plaintext,
                associatedData,
                nonce,
                messageKey,
                AEAD.Method.XCHACHA20_POLY1305_IETF
            )
            val messageObject = JSONObject()
            messageObject.put(CIPHERTEXT, base64Encoder.encode(ciphertext.toByteArray()))
            messageObject.put(NONCE, base64Encoder.encode(nonce))
            return messageObject.toString()
        }

        /**
         * Decrypts the [encryptedMessage] with the given [messageKey]. Also checks the
         * authentication of the [associatedData], which is not encrypted. Uses the AEAD encryption
         * primitive with the XChaCha20-Poly1305 construction.
         * @param messageKey The key to decrypt and authenticate the data with.
         * @param encryptedMessage The JSON object that contains the nonce and the message to
         * decrypt and authenticate. Both are Base64 encoded.
         * @param associatedData The data to authenticate, but not decrypt. Base64 encoded.
         * @return The decrypted data as a stringified JSON object.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun decrypt(
            messageKey: Key,
            encryptedMessage: String,
            associatedData: String
        ): String {
            val messageObject = JSONObject(encryptedMessage)
            val nonce = base64Decoder.decode(messageObject.getString(NONCE))
            val ciphertext = base64Decoder.decode(messageObject.getString(CIPHERTEXT)).toString()
            // TODO Alternative version if the AEAD should not work as intended.
            // secretBoxLazy.cryptoSecretBoxEasy(ciphertext, nonce, messageKey)
            return aeadLazy.decrypt(
                ciphertext,
                associatedData,
                nonce,
                messageKey,
                AEAD.Method.XCHACHA20_POLY1305_IETF
            )
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
        @RequiresApi(Build.VERSION_CODES.O)
        private fun header(
            dhPair: KeyPair,
            previousChainLength: Int,
            messageNumber: Int
        ): String {
            val headerObject = JSONObject()
            val encodedPublicKey = base64Encoder.encode(dhPair.publicKey.asBytes)
            headerObject.put(DH_PUBLIC, encodedPublicKey)
            headerObject.put(PREVIOUS_CHAIN_LENGTH, previousChainLength)
            headerObject.put(MESSAGE_NUMBER, messageNumber)
            return headerObject.toString()
        }

        /**
         * Takes the [header], encodes it in Base64 and prepends the [associatedData] which is
         * already in Base64.
         * @param associatedData The Base64 encoded data that is prepended.
         * @param header Contains the info about the public Diffie-Hellman key, the
         * previousChainLength and the messageNumber. Stringified JSON object, unencoded.
         * @returns [associatedData] + Base64([header])
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private fun concatenate(associatedData: String, header: String): String {
            return associatedData + base64Encoder.encodeToString(header.toByteArray())
        }

        /**
         * The maximum number of skipped messages per chain. Prevents a malicious sender from
         * triggering an excessive recipient computation.
         * */
        private const val MAX_SKIP = 1000

        /** Used as identifier for dhPublic in JSONObjects. */
        private const val DH_PUBLIC = "dhPublic"

        /** Used as identifier for encodedEncryptedMessage in JSONObjects. */
        private const val ENCODED_ENCRYPTED_MESSAGE = "encodedEncryptedMessage"

        /** Used as identifier for header in JSONObjects. */
        private const val HEADER = "header"

        /** Used as identifier for previousChainLength in JSONObjects. */
        private const val PREVIOUS_CHAIN_LENGTH = "previousChainLength"

        /** Used as identifier for messageNumber in JSONObjects. */
        private const val MESSAGE_NUMBER = "messageNumber"

        /** Used as identifier for ciphertext in JSONObjects. */
        private const val CIPHERTEXT = "ciphertext"

        /** Used as identifier for nonce in JSONObjects. */
        private const val NONCE = "nonce"


    }

}