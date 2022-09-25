package nz.scuttlebutt.tremola.doubleRatchet

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.utils.Key
import org.json.JSONObject
import org.junit.Test
import java.nio.charset.StandardCharsets

class DoubleRatchetLocalTest {

    /**
     * This makes sure that a Diffie-Hellman key exchange gives the same result for both parties.
     */
    @Test
    fun dhIsSymmetric() {
        val keyPair1 = DoubleRatchet.generateDH()
        val keyPair2 = DoubleRatchet.generateDH()
        val firstProduct: Key = DoubleRatchet.diffieHellman(keyPair1, keyPair2.publicKey)
        val secondProduct: Key = DoubleRatchet.diffieHellman(keyPair2, keyPair1.publicKey)
        assert(firstProduct == secondProduct)
    }

    /**
     * This tests that the provided encryption and decryption algorithms work with one another.
     */
    @Test
    fun encryptDecryptWorks() {
        val message = "Hello there, General Kenobi!"
        val associatedData = "Beep-boop, I am testing."
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA)
        val decryptedMessage =
            DoubleRatchet.decrypt(key, encryptedJSONString, associatedDataOnEncrypted)
        assert(message == decryptedMessage)
    }

    /**
     * This tests that the provided encryption and decryption algorithms work with one another, even
     * if the associatedData is an empty string.
     */
    @Test
    fun encryptDecryptWorksEmptyAssociatedData() {
        val message = "Hello there, General Kenobi!"
        val associatedData = ""
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA)
        val decryptedMessage =
            DoubleRatchet.decrypt(key, encryptedJSONString, associatedDataOnEncrypted)
        assert(message == decryptedMessage)
    }

    /**
     * This tests that the provided encryption and decryption algorithms work with one another, even
     * if the message is an empty string.
     */
    @Test
    fun encryptDecryptWorksEmptyMessage() {
        val message = ""
        val associatedData = "Beep-boop, I am testing."
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA)
        val decryptedMessage =
            DoubleRatchet.decrypt(key, encryptedJSONString, associatedDataOnEncrypted)
        assert(message == decryptedMessage)
    }

    /**
     * This function ensures that the decryption is halted upon a change in the ciphertext.
     */
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun encryptDecryptRejectChangedCiphertext() {
        val message = "Hello there, General Kenobi!"
        val associatedData = "Beep-boop, I am testing."
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA).toCharArray()
        if (associatedDataOnEncrypted[0] != 'a') {
            associatedDataOnEncrypted[0] = 'a'
        } else {
            associatedDataOnEncrypted[0] = 'b'
        }
        DoubleRatchet.decrypt(key, encryptedJSONString, String(associatedDataOnEncrypted))    }

    /**
     * This function ensures that the decryption is halted upon a change in the associatedData.
     */
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun encryptDecryptRejectChangedAssociatedData() {
        val message = "Hello there, General Kenobi!"
        val associatedData = "Beep-boop, I am testing."
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA)
        val charArrayCiphertext =
            ciphertextJSONObject.getString(DoubleRatchet.CIPHERTEXT).toCharArray()
        if (charArrayCiphertext[0] != 'a') {
            charArrayCiphertext[0] = 'a'
        } else {
            charArrayCiphertext[0] = 'b'
        }
        ciphertextJSONObject.put(
            DoubleRatchet.CIPHERTEXT,
            String(charArrayCiphertext)
        )
        val changedJSONString = ciphertextJSONObject.toString()
        DoubleRatchet.decrypt(key, changedJSONString, associatedDataOnEncrypted)
    }


    /**
     * Tests that the way we utilize the Base64 encoders and decoders gives the original output.
     */
    @Test
    fun encodingDecodingWorks() {
        val text = "My name is Inigo Montoya. You killed my father. Prepare to die!"
        val encodedByte = base64Encoder.encode(text.toByteArray())
        val encodedString = encodedByte.toString(StandardCharsets.UTF_8)
        val decodedByte = base64Decoder.decode(encodedString)
        val decodedString = decodedByte.toString(StandardCharsets.UTF_8)
        assert(text == decodedString)
    }

    /**
     * Tests that the way we utilize the Base64 encoders and decoders works with empty strings.
     */
    @Test
    fun encodingDecodingEmptyStringWorks() {
        val text = ""
        val encodedByte = base64Encoder.encode(text.toByteArray())
        val encodedString = encodedByte.toString(StandardCharsets.UTF_8)
        val decodedByte = base64Decoder.decode(encodedString)
        val decodedString = decodedByte.toString(StandardCharsets.UTF_8)
        assert(text == decodedString)
    }

    companion object {
        /**
         * This contains the object which does the entire crypto. It calls the native libSodium
         * implementation when used.
         */
        @JvmStatic
        private val lazySodium = LazySodiumJava(SodiumJava(), StandardCharsets.UTF_8)

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for AEAD
         * encryption.
         */
        private val aeadLazy: AEAD.Lazy = lazySodium

        /**
         * The object to encode Strings or ByteArrays to Base64 ByteArrays.
         */
        private val base64Encoder = java.util.Base64.getEncoder()

        /**
         * The object to decode Base64 ByteArrays to ByteArrays or Strings.
         */
        private val base64Decoder = java.util.Base64.getDecoder()


    }
}