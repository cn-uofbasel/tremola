package nz.scuttlebutt.tremola.doubleRatchet

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.*
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.ceil

/**
 * This class is responsible for encrypting and decrypting messages using the double ratchet
 * algorithm defined by the Signal Protocol.
 * For general usage, see the test class.
 * It uses LazySodiumAndroid for the cryptographic primitives.
 * It consists of three individual ratchets, the Diffie-Hellman ratchet, the sending and the
 * receiving ratchet.
 * This implementation does not utilize header encryption, although that would be a possible upgrade
 * to the privacy of the users.
 * Keep in mind: This implementation is general purpose. It is slightly different from the official
 * documentation in that it tries to use the cryptographic libraries also used in SSB to make it
 * easier for the two to work together. It also avoids passing ByteArrays, instead relying on Base64
 * encoded strings. This uses more space than the ByteArrays, but is harder to mess up. Furthermore,
 * it uses stringified JSON objects in a lot of places to facilitate passing multiple values. This
 * could also be changed to use different object each time, but seems to create a lot of boilerplate
 * code.
 * If you want to utilize this in combination with SSB, you can use the SSBDoubleRatchet class
 * instead, which contains instructions on how to properly initialize the object.
 * TODO
 *  <ul>
 *  <li> What happens if one of the participants sends a first message while a first message from
 *  another party is underway?
 *  <li> Where do we need to correct key length?
 *  <li> Should most JSON objects be replaced with tailor-made objects?
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
open class DoubleRatchet {
    private var dhSent: KeyPair
    private var dhReceived: Key?
    private var rootKey: Key
    private var chainKeySending: Key?
    private var chainKeyReceiving: Key?
    private var messageNumberSending: Int
    private var messageNumberReceiving: Int
    private var previousChainLength: Int
    private val messageKeysSkipped: Hashtable<String, Key>

    /**
     * This constructor is used when you are the person sending the first message.
     * If you are using this with SSB: The long term identity keys are Ec25519 keys. They have to be
     * converted to Curve25519 via the function convertKeyPairEd25519ToCurve25519 on the sender's
     * side, who will then send you the
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
        chainKeyReceiving = null
        messageNumberSending = 0
        messageNumberReceiving = 0
        previousChainLength = 0
        messageKeysSkipped = Hashtable<String, Key>()
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
        chainKeyReceiving = null
        messageNumberSending = 0
        messageNumberReceiving = 0
        previousChainLength = 0
        messageKeysSkipped = Hashtable<String, Key>()
    }

    /**
     * This constructor is used when you are deserializing the object from a string.
     * @param jsonString The string of the serialized DoubleRatchet object in JSON.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    constructor(jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        val dhSentPublicEncoded = jsonObject.getString(DH_SENT_PUBLIC)
        val dhSentSecretEncoded = jsonObject.getString(DH_SENT_SECRET)
        dhSent = KeyPair(
            Key.fromBase64String(dhSentPublicEncoded),
            Key.fromBase64String(dhSentSecretEncoded)
        )
        val dhReceivedEncoded = jsonObject.getString(DH_RECEIVED)
        dhReceived = Key.fromBase64String(dhReceivedEncoded)
        val rootKeyEncoded = jsonObject.getString(ROOT_KEY)
        rootKey = Key.fromBase64String(rootKeyEncoded)
        val chainKeySendingEncoded = jsonObject.getString(CHAIN_KEY_SENDING)
        chainKeySending = if (chainKeySendingEncoded != "") {
            Key.fromBase64String(chainKeySendingEncoded)
        } else {
            null
        }
        val chainKeyReceivingEncoded = jsonObject.getString(CHAIN_KEY_RECEIVING)
        chainKeyReceiving = if (chainKeyReceivingEncoded != "") {
            Key.fromBase64String(chainKeySendingEncoded)
        } else {
            null
        }
        messageNumberSending = jsonObject.getInt(MESSAGE_NUMBER_SENDING)
        messageNumberReceiving = jsonObject.getInt(MESSAGE_NUMBER_RECEIVING)
        previousChainLength = jsonObject.getInt(PREVIOUS_CHAIN_LENGTH)
        val messageKeysSkippedJSONObject = jsonObject.get(MESSAGE_KEYS_SKIPPED) as JSONObject
        messageKeysSkipped = Hashtable<String, Key>(messageKeysSkippedJSONObject.length())
        for (key in messageKeysSkippedJSONObject.keys()) {
            val entryValue = messageKeysSkippedJSONObject.getString(key)
            val skippedKeyDecoded = Key.fromBase64String(entryValue)
            messageKeysSkipped[key] = skippedKeyDecoded
        }
    }

    /**
     * Serializes the DoubleRatchet object to a stringified JSON object.
     * @return The serialized JSON string. All ByteArrays are Base64 encoded.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun serialize(): String {
        val jsonObject = JSONObject()
        val dhSentPublicEncoded =
            base64Encoder.encode(dhSent.publicKey.asBytes).toString(StandardCharsets.UTF_8)
        jsonObject.put(DH_SENT_PUBLIC, dhSentPublicEncoded)
        val dhSentSecretEncoded =
            base64Encoder.encode(dhSent.secretKey.asBytes).toString(StandardCharsets.UTF_8)
        jsonObject.put(DH_SENT_SECRET, dhSentSecretEncoded)
        val dhReceivedEncoded = if (dhReceived != null) {
            base64Encoder.encode(dhReceived!!.asBytes).toString(StandardCharsets.UTF_8)
        } else {
            ""
        }
        jsonObject.put(DH_RECEIVED, dhReceivedEncoded)
        val rootKeyEncoded =
            base64Encoder.encode(rootKey.asBytes).toString(StandardCharsets.UTF_8)
        jsonObject.put(ROOT_KEY, rootKeyEncoded)
        val chainKeySendingEncoded = if (chainKeySending != null) {
            base64Encoder.encode(chainKeySending!!.asBytes).toString(StandardCharsets.UTF_8)
        } else {
            ""
        }
        jsonObject.put(CHAIN_KEY_SENDING, chainKeySendingEncoded)
        val chainKeyReceivingEncoded = if (chainKeyReceiving != null) {
            base64Encoder.encode(chainKeyReceiving!!.asBytes).toString(StandardCharsets.UTF_8)
        } else {
            ""
        }
        jsonObject.put(CHAIN_KEY_RECEIVING, chainKeyReceivingEncoded)
        jsonObject.put(MESSAGE_NUMBER_SENDING, messageNumberSending)
        jsonObject.put(MESSAGE_NUMBER_RECEIVING, messageNumberReceiving)
        jsonObject.put(PREVIOUS_CHAIN_LENGTH, previousChainLength)
        val messageKeysSkippedEncoded = Hashtable<String, String>(messageKeysSkipped.size)
        for (entry in messageKeysSkipped.entries) {
            val encodedSkippedKey =
                base64Encoder.encode(entry.value.asBytes).toString(StandardCharsets.UTF_8)
            messageKeysSkippedEncoded[entry.key] = encodedSkippedKey
        }
        jsonObject.put(MESSAGE_KEYS_SKIPPED, messageKeysSkippedEncoded)
        return jsonObject.toString()
    }

    /**
     * Interface to encryptMessage. Takes a string [plaintext] and uses it plus a static string for
     * encryption.
     * @param plaintext The string to encrypt.
     * @return The stringified JSON object with the fields header and encodedEncryptedMessage. Only
     * the second is Base64 encoded.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun encryptString(plaintext: String): String {
        return encryptMessage(plaintext, DEFAULT_ASSOCIATED_DATA)
    }

    /**
     * Interface to decryptMessage. Takes the output string of encryptString and returns the
     * original plaintext.
     * Warning: Only works with messages produced with encryptString, since the
     * DEFAULT_ASSOCIATED_DATA value is used for the associatedData.
     * @param ciphertext The output of encryptString, a JSON object containing a header and the
     * encodedEncryptedMessage. Only the second is Base64 encoded.
     * @return The original plaintext.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun decryptString(ciphertext: String): String {
        val ciphertextJSONObject = JSONObject(ciphertext)
        val header = ciphertextJSONObject.getString(HEADER)
        val encodedEncryptedMessage = ciphertextJSONObject.getString(ENCODED_ENCRYPTED_MESSAGE)
        return decryptMessage(header, encodedEncryptedMessage, DEFAULT_ASSOCIATED_DATA)
    }

    /**
     * This function takes a plaintext of a message and encrypts it using the double ratchet
     * algorithm to derive the key. The output is Byte64 encoded.
     * @param plaintext The plaintext of the message to send. It will be encrypted and signed.
     * @param associatedData The Base64 encoded associated data to sign but not encrypt.
     * @return The stringified JSON object with the fields header and encodedEncryptedMessage. Only
     * the second is Base64 encoded.
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
        // Get the new keys, make header.
        val chainRatchetResult =
            keyDerivationFunctionChainKey(chainKeySending!!)
        chainKeySending = chainRatchetResult.secretKey
        val messageKey = chainRatchetResult.publicKey
        val header = header(dhSent, previousChainLength, messageNumberSending)
        messageNumberSending += 1
        // Make a JSON object with the resulting values and stringify it.
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
     * @param encodedEncryptedMessage Stringified JSON object that contains the nonce and the
     * ciphertext. Base64 encoded.
     * @param associatedData The signed but unencrypted data that came with the message. Base64
     * encoded.
     * @return The decrypted message that was the original plaintext.
     * @throws javax.crypto.AEADBadTagException If the verification of associatedData with the
     * ciphertext fails.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun decryptMessage(
        header: String,
        encodedEncryptedMessage: String,
        associatedData: String
    ): String {
        // Try decoding with a key we skipped.
        val plaintext = trySkippedMessageKeys(header, encodedEncryptedMessage, associatedData)
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
        return decrypt(messageKey, encodedEncryptedMessage, concatenate(associatedData, header))
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
        skippedMessageIdentifierObject.put(MESSAGE_NUMBER, headerObject.getInt(MESSAGE_NUMBER))
        val skippedMessageIdentifier = skippedMessageIdentifierObject.toString()
        return if (skippedMessageIdentifier in messageKeysSkipped.keys) {
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
    @RequiresApi(Build.VERSION_CODES.O)
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
                if (dhReceived == null) {
                    Log.e("DoubleRatchet:skipMessageKeys", "dhReceived is null.")
                    throw Exception("DoubleRatchet:skipMessageKeys, dhReceived is null.")
                }
                val encodedDHPublic =
                    base64Encoder.encode(dhReceived!!.asBytes).toString(StandardCharsets.UTF_8)
                skippedMessageIdentifierObject.put(DH_PUBLIC, encodedDHPublic)
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
        protected val lazySodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for
         * Diffie-Hellman key exchanges.
         */
        private val diffieHellmanLazy: DiffieHellman.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for AEAD
         * encryption.
         */
        private val aeadLazy: AEAD.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for\
         * authentication.
         */
        private val authLazy: Auth.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for helper
         * functions.
         */
        private val helpersLazy: Helpers.Lazy = lazySodium

        /**
         * The object to encode Strings or ByteArrays to Base64 ByteArrays.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val base64Encoder = Base64.getEncoder()

        /**
         * The object to decode Base64 ByteArrays to ByteArrays or Strings.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        private val base64Decoder = Base64.getDecoder()


        /**
         * Generates a Diffie-Hellman keypair. Uses elliptic curve primitive (Curve25519).
         * @return A new Diffie-Hellman keypair.
         */
        internal fun generateDH(): KeyPair {
            return lazySodium.cryptoKxKeypair()
        }

        /**
         * Takes a public and a private Diffie-Hellman key to produce the shared secret.
         * Uses the elliptic curve primitive (Curve25519).
         * NOTE: The order of arguments is swapped to what the cryptoScalarMult() defines as the
         *  correct order. The order reflects the one defined in the SSB protocol documentation.
         *  Establishing a shared secret only works this way, no idea why.
         * @param dhPair Our own Diffie-Hellman key pair.
         * @param dhPublicKey The other person's Diffie-Hellman public key.
         * @return The newly generated key, a shared secret.
         */
        internal fun diffieHellman(dhPair: KeyPair, dhPublicKey: Key): Key {
            return diffieHellmanLazy.cryptoScalarMult(dhPair.secretKey, dhPublicKey)
        }

        /**
         * The key derivation function used for the root chain. Uses primitive HKDF with SHA-512.
         * Source: RFC 5869.
         * @param rootKey The root key of the root chain.
         * @param dhOutput The output of the Diffie-Hellman key exchange.
         * @return A new keypair, a root key as secretKey and a chain key as publicKey.
         */
        private fun keyDerivationFunctionRootKey(rootKey: Key, dhOutput: Key): KeyPair {
            val hkdfExtracted = hkdfExtract(rootKey, dhOutput)
            val info = "TremolaDoubleRatchetKeyDerivationFunctionRootKey"
            val hkdfExpanded = hkdfExpand(hkdfExtracted, info, 32 + 32).asBytes
            val newRootKey = Key.fromBytes(hkdfExpanded.sliceArray(0..31))
            val newChainKey = Key.fromBytes(hkdfExpanded.sliceArray(32..63))
            return KeyPair(newChainKey, newRootKey)
        }

        /**
         * The key derivation function used for the sending and receiving chains. Uses primitive
         * HMAC with SHA-512.
         * @param chainKey The key of the chain to generate a new key.
         * @return A new keypair, a chain key as secretKey and a message key as publicKey.
         */
        private fun keyDerivationFunctionChainKey(chainKey: Key): KeyPair {
            // Input is chosen as constant as recommended within the Signal documentation.
            val newMessageHex = authLazy.cryptoAuthHMACSha(Auth.Type.SHA512, 1.toString(), chainKey)
            val newMessageKey = Key.fromHexString(newMessageHex)
            val newChainHex = authLazy.cryptoAuthHMACSha(Auth.Type.SHA512, 2.toString(), chainKey)
            val newChainKey = Key.fromHexString(newChainHex)
            return KeyPair(newMessageKey, newChainKey)
        }

        /**
         * Encrypts the [plaintext] with the given [messageKey]. Also authenticates the
         * [associatedData], but does not encrypt it. Uses the AEAD encryption
         * primitive with the XChaCha20-Poly1305 construction.
         * @param messageKey The key to encrypt and authenticate the data with.
         * @param plaintext The message to encrypt and authenticate, any string.
         * @param associatedData The data to authenticate, but not encrypt. Base64 encoded.
         * @return A stringified JSON object which contains the nonce and the ciphertext, both
         * Base64 encoded. Also contains the associatedData, base64 encoded.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        internal fun encrypt(
            messageKey: Key,
            plaintext: String,
            associatedData: String
        ): String {
            val nonce = lazySodium.nonce(SecretBox.NONCEBYTES)
            val hexCiphertext = aeadLazy.encrypt(
                plaintext,
                associatedData,
                nonce,
                messageKey,
                AEAD.Method.XCHACHA20_POLY1305_IETF
            )
            val byteCiphertext = helpersLazy.sodiumHex2Bin(hexCiphertext)
            val encodedCiphertext =
                base64Encoder.encode(byteCiphertext).toString(StandardCharsets.UTF_8)
            val messageObject = JSONObject()
            messageObject.put(CIPHERTEXT, encodedCiphertext)
            messageObject.put(NONCE, base64Encoder.encode(nonce).toString(StandardCharsets.UTF_8))
            messageObject.put(ASSOCIATED_DATA, associatedData)
            return messageObject.toString()
        }

        /**
         * Decrypts the [encryptedMessageJSON] with the given [messageKey]. Also checks the
         * authentication of the [associatedData], which is not encrypted. Uses the AEAD encryption
         * primitive with the XChaCha20-Poly1305 construction.
         * @param messageKey The key to decrypt and authenticate the data with.
         * @param encryptedMessageJSON The JSON object string that contains the nonce and the
         * message to decrypt and authenticate. Both are Base64 encoded.
         * @param associatedData The data to authenticate, but not decrypt. Base64 encoded.
         * @return The decrypted data of the ciphertext.
         * @throws javax.crypto.AEADBadTagException If the verification of associatedData with the
         * ciphertext fails.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        internal fun decrypt(
            messageKey: Key,
            encryptedMessageJSON: String,
            associatedData: String
        ): String {
            val messageObject = JSONObject(encryptedMessageJSON)
            val nonce = base64Decoder.decode(messageObject.getString(NONCE))
            val byteCiphertext = base64Decoder.decode(messageObject.getString(CIPHERTEXT))
            val hexEncodedCiphertext = helpersLazy.sodiumBin2Hex(byteCiphertext)
            return aeadLazy.decrypt(
                hexEncodedCiphertext,
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
            val encodedPublicKey =
                base64Encoder.encode(dhPair.publicKey.asBytes).toString(StandardCharsets.UTF_8)
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
            return associatedData +
                    base64Encoder.encodeToString(header.toByteArray(StandardCharsets.UTF_8))
        }

        /**
         * Takes an input and a salt and extracts a pseudorandom key from it.
         * @param salt Prevents the same input from creating the same key twice by being unique.
         * @param inputKeyingMaterial What should be used for the generation of the key. Since we
         * want to be able to detect small changes in it, we transform it to hex. When it used to be
         * transformed to UFT-8, some different input materials could result in the same output.
         * @return A pseudorandom key based on the input material, hexadecimal encoding.
         */
        internal fun hkdfExtract(salt: Key, inputKeyingMaterial: Key): String {
            // We are using inputKeyMaterial as input, not key, as stated in the RFC.
            return authLazy.cryptoAuthHMACSha(
                Auth.Type.SHA512,
                inputKeyingMaterial.asHexString,
                salt
            )
        }


        /**
         * Takes a pseudorandom key and extracts another key out of it of the correct length.
         * @param pseudorandomKey A hexadecimal encoded key which is not predictable.
         * @param info Application specific info.
         * @param outputLength The length in bytes that the output keying material should have.
         * TODO The allocation of the output array and filling it can be optimized.
         */
        internal fun hkdfExpand(pseudorandomKey: String, info: String, outputLength: Int): Key {
            val hashSizeBytes = 512 / 8
            val numberOfHashes = ceil(outputLength.toDouble() / hashSizeBytes).toInt()
            val totalHashOutput = MutableList(numberOfHashes + 1) { ByteArray(hashSizeBytes) }
            totalHashOutput[0] = "".toByteArray(StandardCharsets.UTF_8) // empty
            for (i in 1..numberOfHashes) {
                val hexHashOutput =
                    authLazy.cryptoAuthHMACSha(
                        Auth.Type.SHA512,
                        totalHashOutput[i - 1].toString(StandardCharsets.UTF_8)
                                + info + i.toChar(),
                        Key.fromHexString(pseudorandomKey)
                    )
                val byteHashOutput = helpersLazy.sodiumHex2Bin(hexHashOutput)
                totalHashOutput[i] = byteHashOutput
            }
            val outputArray = ByteArray(outputLength)
            for (i in 0 until outputLength) {
                val listIndex = 1 + i / hashSizeBytes
                val byteArrayIndex = i % hashSizeBytes
                outputArray[i] = totalHashOutput[listIndex][byteArrayIndex]
            }
            return Key.fromBytes(outputArray)
        }

        /**
         * The maximum number of skipped messages per chain. Prevents a malicious sender from
         * triggering an excessive recipient computation.
         */
        internal const val MAX_SKIP = 100

        /**
         * The default value that this app uses for the associated data.
         */
        private const val DEFAULT_ASSOCIATED_DATA = "TremolaDoubleRatchet"

        /** Used as identifier for dhPublic in JSONObjects. */
        private const val DH_PUBLIC = "dhPublic"

        /** Used as identifier for encodedEncryptedMessage in JSONObjects. */
        internal const val ENCODED_ENCRYPTED_MESSAGE = "encodedEncryptedMessage"

        /** Used as identifier for header in JSONObjects. */
        internal const val HEADER = "header"

        /** Used as identifier for previousChainLength in JSONObjects. */
        private const val PREVIOUS_CHAIN_LENGTH = "previousChainLength"

        /** Used as identifier for messageNumber in JSONObjects. */
        internal const val MESSAGE_NUMBER = "messageNumber"

        /** Used as identifier for ciphertext in JSONObjects. */
        internal const val CIPHERTEXT = "ciphertext"

        /** Used as identifier for nonce in JSONObjects. */
        private const val NONCE = "nonce"

        /** Used as identifier for associatedData in JSONObjects. */
        internal const val ASSOCIATED_DATA = "associatedData"

        /** Used as identifier for the public key of dhSent in JSONObjects. */
        private const val DH_SENT_PUBLIC = "dhSentPublic"

        /** Used as identifier for the secret key of dhSent in JSONObjects. */
        private const val DH_SENT_SECRET = "dhSentSecret"

        /** Used as identifier for dhReceived in JSONObjects. */
        private const val DH_RECEIVED = "dhReceived"

        /** Used as identifier for rootKey in JSONObjects. */
        private const val ROOT_KEY = "rootKey"

        /** Used as identifier for chainKeySending in JSONObjects. */
        private const val CHAIN_KEY_SENDING = "chainKeySending"

        /** Used as identifier for chainKeyReceiving in JSONObjects. */
        private const val CHAIN_KEY_RECEIVING = "chainKeyReceiving"

        /** Used as identifier for messageNumberSending in JSONObjects. */
        private const val MESSAGE_NUMBER_SENDING = "messageNumberSending"

        /** Used as identifier for messageNumberReceiving in JSONObjects. */
        private const val MESSAGE_NUMBER_RECEIVING = "messageNumberReceiving"

        /** Used as identifier for messageKeysSkipped in JSONObjects. */
        private const val MESSAGE_KEYS_SKIPPED = "messageKeysSkipped"
    }
}