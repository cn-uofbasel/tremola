package nz.scuttlebutt.tremola.doubleRatchet

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Auth
import com.goterl.lazysodium.utils.Key
import org.json.JSONObject
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

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
        DoubleRatchet.decrypt(key, encryptedJSONString, String(associatedDataOnEncrypted))
    }

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
     * This function ensures that the decryption throws an exception with the wrong key.
     */
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun encryptDecryptRejectWrongKey() {
        val message = "Hello there, General Kenobi!"
        val associatedData = "Beep-boop, I am testing."
        val associatedDataEncoded =
            base64Encoder.encode(associatedData.toByteArray()).toString(StandardCharsets.UTF_8)
        val key = aeadLazy.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val encryptedJSONString = DoubleRatchet.encrypt(key, message, associatedDataEncoded)
        val ciphertextJSONObject = JSONObject(encryptedJSONString)
        val associatedDataOnEncrypted =
            ciphertextJSONObject.getString(DoubleRatchet.ASSOCIATED_DATA)
        val keyBytes = key.asBytes
        keyBytes[0] = keyBytes[0].inc()
        val wrongKey = Key.fromBytes(keyBytes)
        DoubleRatchet.decrypt(wrongKey, encryptedJSONString, associatedDataOnEncrypted)
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

    /**
     * Test that the HMAC function used produces the correct output length.
     */
    @Test
    fun hmacHasCorrectLength() {
        val key = authLazy.cryptoAuthHMACShaKeygen(Auth.Type.SHA512)
        val hmacOutput = authLazy.cryptoAuthHMACSha(Auth.Type.SHA512, "It's a trap!", key)
        val correctLength = 512 / 8 * 2 // Total bits to byte to hex
        assert(hmacOutput.length == correctLength)
    }

    /**
     * This test makes sure that the output of the hkdfExtractMethod has the correct length.
     */
    @Test
    fun hkdfExtractReturnsCorrectLength() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        // 512 bit output to byte to hex encoding
        assert(hkdfExtractOutput.length == 512 / 8 * 2)
    }

    /**
     * This test makes sure that the output of the hkdfExtractMethod stays the same for the same
     * inputs.
     */
    @Test
    fun hkdfExtractReturnsSameOutput() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput1 =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val hkdfExtractOutput2 =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        // 512 bit output to byte to hex encoding
        assert(hkdfExtractOutput1 == hkdfExtractOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExtractMethod changes for even one changed
     * byte in the salt input.
     */
    @Test
    fun hkdfExtractReturnsDifferentOutputOnChangedSalt() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val publicBytes1 = generatedKeyPair.publicKey.asBytes
        val publicBytes2 = publicBytes1.clone()
        publicBytes2[0] = publicBytes2[0].inc()
        assert(!publicBytes1.contentEquals(publicBytes2))
        val hkdfExtractOutput1 =
            DoubleRatchet.hkdfExtract(Key.fromBytes(publicBytes1), generatedKeyPair.secretKey)
        val hkdfExtractOutput2 =
            DoubleRatchet.hkdfExtract(Key.fromBytes(publicBytes2), generatedKeyPair.secretKey)
        assert(hkdfExtractOutput1 != hkdfExtractOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExtractMethod changes for even one changed
     * byte in the inputKeyingMaterial.
     */
    @Test
    fun hkdfExtractReturnsDifferentOutputOnChangedInputKeyingMaterial() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val secretBytes1 = generatedKeyPair.secretKey.asBytes
        val secretBytes2 = secretBytes1.copyOf()
        secretBytes2[0] = secretBytes2[0].inc()
        assert(!secretBytes1.contentEquals(secretBytes2))
        val hkdfExtractOutput1 =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, Key.fromBytes(secretBytes1))
        val hkdfExtractOutput2 =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, Key.fromBytes(secretBytes2))
        assert(hkdfExtractOutput1 != hkdfExtractOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExpand stays the same for same parameters.
     */
    @Test
    fun hkdfExpandReturnsSameOutput() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo = "hkdfExpandReturnsCorrectLength"
        val outputLength = 64
        val hkdfExpandOutput1 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength)
        val hkdfExpandOutput2 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength)
        assert(hkdfExpandOutput1 == hkdfExpandOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExpand changes if the hkdfExtractedOutput is
     * different.
     */
    @Test
    fun hkdfExpandReturnsDifferentOutputOnChangedHKDFExtractOutput() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput1 =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val hkdfExtractedOutputCharArray = hkdfExtractOutput1.toCharArray()
        hkdfExtractedOutputCharArray[0] = if (hkdfExtractedOutputCharArray[0] != 'A') {
            'A'
        } else {
            'B'
        }
        val hkdfExtractOutput2 = String(hkdfExtractedOutputCharArray)
        val testInfo = "hkdfExpandReturnsCorrectLength"
        val outputLength = 64
        val hkdfExpandOutput1 = DoubleRatchet.hkdfExpand(hkdfExtractOutput1, testInfo, outputLength)
        val hkdfExpandOutput2 = DoubleRatchet.hkdfExpand(hkdfExtractOutput2, testInfo, outputLength)
        assert(hkdfExpandOutput1 != hkdfExpandOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExpand changes if the info is different.
     */
    @Test
    fun hkdfExpandReturnsDifferentOutputOnChangedInfo() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo1 = "hkdfExpandReturnsDifferentOutputOnChangedInfo"
        val testInfo2 = "gkdfExpandReturnsDifferentOutputOnChangedInfo"
        val outputLength = 64
        val hkdfExpandOutput1 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo1, outputLength)
        val hkdfExpandOutput2 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo2, outputLength)
        assert(hkdfExpandOutput1 != hkdfExpandOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExpand changes if the outputLength is
     * different.
     */
    @Test
    fun hkdfExpandReturnsDifferentOutputOnChangedOutputLength() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo = "hkdfExpandReturnsDifferentOutputOnChangedOutputLength"
        val outputLength1 = 64
        val outputLength2 = 65
        val hkdfExpandOutput1 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength1)
        val hkdfExpandOutput2 = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength2)
        assert(hkdfExpandOutput1 != hkdfExpandOutput2)
    }

    /**
     * This test makes sure that the output of the hkdfExpand has the correct length.
     */
    @Test
    fun hkdfExpandReturnsCorrectLength() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo = "hkdfExpandReturnsCorrectLength"
        val outputLength = 64
        val hkdfExpandOutput = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength)
        assert(hkdfExpandOutput.asBytes.size == outputLength)
    }

    /**
     * This test makes sure that the output of the hkdfExpand has the correct length, even for long,
     * atypical values.
     */
    @Test
    fun hkdfExpandReturnsCorrectLengthLong() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo = "hkdfExpandReturnsCorrectLength"
        val outputLength = 1027
        val hkdfExpandOutput = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength)
        assert(hkdfExpandOutput.asBytes.size == outputLength)
    }

    /**
     * This test makes sure that the output of the hkdfExpand has the correct length, even for
     * short, atypical values.
     */
    @Test
    fun hkdfExpandReturnsCorrectLengthShort() {
        val generatedKeyPair = DoubleRatchet.generateDH()
        val hkdfExtractOutput =
            DoubleRatchet.hkdfExtract(generatedKeyPair.publicKey, generatedKeyPair.secretKey)
        val testInfo = "hkdfExpandReturnsCorrectLength"
        val outputLength = 12
        val hkdfExpandOutput = DoubleRatchet.hkdfExpand(hkdfExtractOutput, testInfo, outputLength)
        assert(hkdfExpandOutput.asBytes.size == outputLength)
    }

    /**
     * This test is used to make sure the generateDH function does not always generate the same
     * result.
     */
    @Test
    fun generateDHHasUniqueResults() {
        val aliceDiffieHellmanKeypair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeypair != bobDiffieHellmanKeyPair)
    }

    /**
     * This test checks that a first message can be decrypted properly.
     */
    @Test
    fun checkEncryptDecryptMessageForFirstMessageFromAlice() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceAssociatedData =
            base64Encoder.encode("To: Bob, from: Alice".toByteArray(StandardCharsets.UTF_8))
                .toString(StandardCharsets.UTF_8)
        val aliceCiphertext = aliceRatchet.encryptMessage(aliceMessage, aliceAssociatedData)
        // String is sent to Bob here.
        val aliceCiphertextJSONObject = JSONObject(aliceCiphertext)
        val aliceHeader = aliceCiphertextJSONObject.getString(DoubleRatchet.HEADER)
        val aliceEncodedEncryptedMessage =
            aliceCiphertextJSONObject.getString(DoubleRatchet.ENCODED_ENCRYPTED_MESSAGE)
        val bobDecrypted =
            bobRatchet.decryptMessage(
                aliceHeader,
                aliceEncodedEncryptedMessage,
                aliceAssociatedData
            )
        assert(aliceMessage == bobDecrypted)
    }

    /**
     * This test checks that a first reply can be decrypted properly.
     */
    @Test
    fun checkEncryptDecryptMessageForFirstReplyFromBob() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceAssociatedData =
            base64Encoder.encode("To: Bob, from: Alice".toByteArray(StandardCharsets.UTF_8))
                .toString(StandardCharsets.UTF_8)
        val aliceCiphertext = aliceRatchet.encryptMessage(aliceMessage, aliceAssociatedData)
        // String is sent to Bob here.
        val aliceCiphertextJSONObject = JSONObject(aliceCiphertext)
        val aliceHeader = aliceCiphertextJSONObject.getString(DoubleRatchet.HEADER)
        val aliceEncodedEncryptedMessage =
            aliceCiphertextJSONObject.getString(DoubleRatchet.ENCODED_ENCRYPTED_MESSAGE)
        val bobDecrypted =
            bobRatchet.decryptMessage(
                aliceHeader,
                aliceEncodedEncryptedMessage,
                aliceAssociatedData
            )
        assert(aliceMessage == bobDecrypted)
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobAssociatedData =
            base64Encoder.encode("To: Alice, from: Bob".toByteArray(StandardCharsets.UTF_8))
                .toString(StandardCharsets.UTF_8)
        val bobCiphertext = bobRatchet.encryptMessage(bobMessage, bobAssociatedData)
        // String is sent to Alice here.
        val bobCiphertextJSONObject = JSONObject(bobCiphertext)
        val bobHeader = bobCiphertextJSONObject.getString(DoubleRatchet.HEADER)
        val bobEncodedEncryptedMessage =
            bobCiphertextJSONObject.getString(DoubleRatchet.ENCODED_ENCRYPTED_MESSAGE)
        val aliceDecrypted =
            aliceRatchet.decryptMessage(
                bobHeader,
                bobEncodedEncryptedMessage,
                bobAssociatedData
            )
        assert(bobMessage == aliceDecrypted)
    }

    /**
     * This test checks that a first message can be decrypted properly.
     */
    @Test
    fun checkEncryptDecryptStringForFirstMessageFromAlice() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
    }

    /**
     * This test checks that a first reply can be decrypted properly.
     */
    @Test
    fun checkEncryptDecryptStringForFirstReplyFromBob() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobCiphertext = bobRatchet.encryptString(bobMessage)
        // String is sent to Alice here.
        val aliceDecrypted = aliceRatchet.decryptString(bobCiphertext)
        assert(bobMessage == aliceDecrypted)
    }

    /**
     * This test sends multiple messages from A to B and back, checking that the encryption and
     * decryption work.
     */
    @Test
    fun checkEncryptDecryptStringForMultipleMessagesFromBoth() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobCiphertext = bobRatchet.encryptString(bobMessage)
        // String is sent to Alice here.
        val aliceDecrypted = aliceRatchet.decryptString(bobCiphertext)
        assert(bobMessage == aliceDecrypted)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet.encryptString(aliceMessage2)
        // String is sent to Bob here.
        val bobDecrypted2 = bobRatchet.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)
        // Bob prepares the reply to be sent.
        val bobMessage2 = "Of course! Meet up at 12?"
        val bobCiphertext2 = bobRatchet.encryptString(bobMessage2)
        val aliceDecrypted2 = aliceRatchet.decryptString(bobCiphertext2)
        assert(bobMessage2 == aliceDecrypted2)
    }

    /**
     * This test sends multiple messages from A to B and back, checking that the encryption and
     * decryption work. This makes sure that the chains work.
     */
    @Test
    fun checkEncryptDecryptStringForMultipleMessagesFromAlice() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet.encryptString(aliceMessage2)
        // String is sent to Bob here.
        val bobDecrypted2 = bobRatchet.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)
        // Another message from Alice.
        val aliceMessage3 = "At the park maybe?"
        val aliceCiphertext3 = aliceRatchet.encryptString(aliceMessage3)
        // String is sent to Bob here.
        val bobDecrypted3 = bobRatchet.decryptString(aliceCiphertext3)
        assert(aliceMessage3 == bobDecrypted3)
    }

    /**
     * Here the messages from alice arrive in the wrong order. They should still be decrypted
     * properly.
     */
    @Test
    fun messagesOutOfOrderAreDecryptedProperly() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet.encryptString(aliceMessage2)
        // Another message from Alice.
        val aliceMessage3 = "At the park maybe?"
        val aliceCiphertext3 = aliceRatchet.encryptString(aliceMessage3)
        // String is sent to Bob here.
        val bobDecrypted3 = bobRatchet.decryptString(aliceCiphertext3)
        assert(aliceMessage3 == bobDecrypted3)
        // String is sent to Bob here.
        val bobDecrypted2 = bobRatchet.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
    }

    /**
     * This test sends multiple messages from A to B and back, checking that the encryption and
     * decryption work. Meanwhile it serializes and deserializes the DoubleRatchets between all
     * messages to assure it does not impede the functionality.
     */
    @Test
    fun checkSerializeDeserializeWorks() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet1 = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet1 = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet2 = DoubleRatchet(aliceRatchet1.serialize())
        val bobRatchet2 = DoubleRatchet(bobRatchet1.serialize())
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet2.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet2.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet3 = DoubleRatchet(aliceRatchet2.serialize())
        val bobRatchet3 = DoubleRatchet(bobRatchet2.serialize())
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobCiphertext = bobRatchet3.encryptString(bobMessage)
        // String is sent to Alice here.
        val aliceDecrypted = aliceRatchet3.decryptString(bobCiphertext)
        assert(bobMessage == aliceDecrypted)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet4 = DoubleRatchet(aliceRatchet3.serialize())
        val bobRatchet4 = DoubleRatchet(bobRatchet3.serialize())
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet4.encryptString(aliceMessage2)
        // String is sent to Bob here.
        val bobDecrypted2 = bobRatchet4.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet5 = DoubleRatchet(aliceRatchet4.serialize())
        val bobRatchet5 = DoubleRatchet(bobRatchet4.serialize())
        // Bob prepares the reply to be sent.
        val bobMessage2 = "Of course! Meet up at 12?"
        val bobCiphertext2 = bobRatchet5.encryptString(bobMessage2)
        val aliceDecrypted2 = aliceRatchet5.decryptString(bobCiphertext2)
        assert(bobMessage2 == aliceDecrypted2)
    }

    /**
     * This checks that if too many messages are to be skipped, an exception is thrown.
     */
    @Test
    fun tooManySkippedMessagesCauseException() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        // Create a huge load of messages, and skip a lot, but not too many.
        var hugeMessageNumberAliceCiphertext = ""
        for (i in 0..(DoubleRatchet.MAX_SKIP + 1)) {
            hugeMessageNumberAliceCiphertext = aliceRatchet.encryptString(aliceMessage2)
        }
        var exceptionWasCaught = false
        try {
            bobRatchet.decryptString(hugeMessageNumberAliceCiphertext)
        } catch (exception: Exception) {
            exceptionWasCaught = true

        }
        assert(exceptionWasCaught)
    }

    /**
     * This checks that an exception is not thrown if not enough messages are skipped.
     * An AEADBadTagException is still thrown, since the message number was changed and the wrong key is
     * used.
     */
    @Test
    fun noExceptionWithNotTooManySkippedMessages() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        // Create a huge load of messages, and skip a lot, but not too many.
        var hugeMessageNumberAliceCiphertext = ""
        for (i in 0..DoubleRatchet.MAX_SKIP) {
            hugeMessageNumberAliceCiphertext = aliceRatchet.encryptString(aliceMessage2)
        }
        var exceptionWasCaught = false
        try {
            bobRatchet.decryptString(hugeMessageNumberAliceCiphertext)
        } catch (exception: Exception) {
            exceptionWasCaught = true

        }
        assert(!exceptionWasCaught)
    }

    /**
     * This tests that if a decryption fails, it is still able to decrypt the next correct message
     * without any issues.
     */
    @Test
    fun resetsStateCorrectlyAfterReceivingCorruptedMessage() {
        val aliceDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        val bobDiffieHellmanKeyPair = DoubleRatchet.generateDH()
        assert(aliceDiffieHellmanKeyPair != bobDiffieHellmanKeyPair)
        val aliceSharedSecret = DoubleRatchet.diffieHellman(
            aliceDiffieHellmanKeyPair,
            bobDiffieHellmanKeyPair.publicKey
        )
        val bobSharedSecret = DoubleRatchet.diffieHellman(
            bobDiffieHellmanKeyPair,
            aliceDiffieHellmanKeyPair.publicKey
        )
        assert(aliceSharedSecret == bobSharedSecret)
        // Alice sent the first message, creates ratchets accordingly.
        val aliceRatchet = DoubleRatchet(aliceSharedSecret, bobDiffieHellmanKeyPair.publicKey)
        val bobRatchet = DoubleRatchet(bobSharedSecret, bobDiffieHellmanKeyPair)
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobCiphertext = bobRatchet.encryptString(bobMessage)
        // String is sent to Alice here.
        val aliceDecrypted = aliceRatchet.decryptString(bobCiphertext)
        assert(bobMessage == aliceDecrypted)
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet.encryptString(aliceMessage2)
        // Get the first character of the ciphertext, replace it
        val corruptedCharacter = if (aliceCiphertext2[167] == 'a') {
            'b'
        } else {
            'a'
        }
        val corruptedAliceCiphertext2 = aliceCiphertext2.slice(0..166) + corruptedCharacter +
                aliceCiphertext2.slice(168..aliceCiphertext2.lastIndex)
        assert(aliceCiphertext2 != corruptedAliceCiphertext2)
        println("original:  $aliceCiphertext2")
        println("corrupted: $corruptedAliceCiphertext2")
        // Corrupted String is sent to Bob here.
        var exceptionWasThrown = false
        try {
            bobRatchet.decryptString(corruptedAliceCiphertext2)
        } catch (e: AEADBadTagException) { // This is expected to be thrown.
            exceptionWasThrown = true
        }
        assert(exceptionWasThrown)
        // Correct String is sent to Bob here.
        val bobDecrypted2 = bobRatchet.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)
        // Bob prepares the reply to be sent.
        val bobMessage2 = "Of course! Meet up at 12?"
        val bobCiphertext2 = bobRatchet.encryptString(bobMessage2)
        val aliceDecrypted2 = aliceRatchet.decryptString(bobCiphertext2)
        assert(bobMessage2 == aliceDecrypted2)
    }

    companion object {
        /**
         * This contains the object which does the entire crypto. It calls the native libSodium
         * implementation when used.
         */
        @JvmStatic
        private val lazySodium = LazySodiumAndroid(SodiumAndroid(), StandardCharsets.UTF_8)

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for AEAD
         * encryption.
         */
        private val aeadLazy: AEAD.Lazy = lazySodium

        /**
         * This object is a cast of the lazySodium object to use its lazy functions for
         * authentication.
         */
        private val authLazy: Auth.Lazy = lazySodium

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