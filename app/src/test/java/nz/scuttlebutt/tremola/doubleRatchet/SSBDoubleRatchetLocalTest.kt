package nz.scuttlebutt.tremola.doubleRatchet

import com.goterl.lazysodium.utils.Key
import nz.scuttlebutt.tremola.ssb.core.SSBid
import org.json.JSONObject
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * This class tests specific cases which arise when using the new constructors in SSBDoubleRatchet.
 */
class SSBDoubleRatchetLocalTest {

    /**
     * This test that the Diffie-Hellman key exchange still is symmetric, even with keys received
     * through conversion.
     */
    @Test
    fun diffieHellmanWorksWithConversion() {
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val aliceCurveKeyPair = SSBDoubleRatchet.ssbIDToCurve(aliceSSBid)
        val bobCurveKeyPair = SSBDoubleRatchet.ssbIDToCurve(bobSSBid)
        val aliceProduct = DoubleRatchet.diffieHellman(aliceCurveKeyPair, bobCurveKeyPair.publicKey)
        val bobProduct = DoubleRatchet.diffieHellman(bobCurveKeyPair, aliceCurveKeyPair.publicKey)
        assert(aliceProduct == bobProduct)
    }

    /**
     * Makes sure the function has the desired property.
     */
    @Test
    fun calculateSharedSecretIsSymmetric() {
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val aliceProduct =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, Key.fromBytes(bobSSBid.verifyKey))
        val bobProduct =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, Key.fromBytes(aliceSSBid.verifyKey))
        assert(aliceProduct == bobProduct)
    }

    /**
     * This test makes sure that the ssbIDToCurve function works on the expected parameter.
     */
    @Test
    fun ssbIDToCurveDoesNotThrowException() {
        val ssbID = SSBid()
        SSBDoubleRatchet.ssbIDToCurve(ssbID)
    }

    /**
     * Test that the publicEDKeyToCurve does not throw an exception with an expected parameter.
     */
    @Test
    fun publicEDKeyToCurveDoesNotNormallyThrowException() {
        val aliceSSB = SSBid()
        val aliceEDSecretKey = Key.fromBytes(aliceSSB.verifyKey)
        SSBDoubleRatchet.publicEDKeyToCurve(aliceEDSecretKey)
    }

    /**
     * This tests that with an illegal key, the publicEDKeyToCurve functions throws an exception.
     */
    @Test(expected = com.goterl.lazysodium.exceptions.SodiumException::class)
    fun publicEDKeyToCurveThrowsExceptionForIllegalKey() {
        val aliceSSB = SSBid()
        val aliceEDPublicKey = Key.fromBytes(aliceSSB.signingKey)
        SSBDoubleRatchet.publicEDKeyToCurve(aliceEDPublicKey)
    }

    /**
     * This test checks that a first message can be decrypted properly.
     */
    @Test
    fun checkEncryptDecryptMessageForFirstMessageFromAlice() {
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceAssociatedData =
            base64Encoder.encode(
                "To: Bob, from: Alice".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
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
    fun checkEncryptDecryptStringForFirstReplyFromBob() {
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
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
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
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
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
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
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
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
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        // Alice sent the first message, create ratchets accordingly.
        val aliceRatchet1 =
            SSBDoubleRatchet(aliceSharedSecret, SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey))
        val bobRatchet1 = SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet2 = SSBDoubleRatchet(aliceRatchet1.serialize())
        val bobRatchet2 = SSBDoubleRatchet(bobRatchet1.serialize())
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val aliceCiphertext = aliceRatchet2.encryptString(aliceMessage)
        // String is sent to Bob here.
        val bobDecrypted = bobRatchet2.decryptString(aliceCiphertext)
        assert(aliceMessage == bobDecrypted)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet3 = SSBDoubleRatchet(aliceRatchet2.serialize())
        val bobRatchet3 = SSBDoubleRatchet(bobRatchet2.serialize())
        // Bob prepares the reply to be sent.
        val bobMessage = "Alice, you are also my best friend!"
        val bobCiphertext = bobRatchet3.encryptString(bobMessage)
        // String is sent to Alice here.
        val aliceDecrypted = aliceRatchet3.decryptString(bobCiphertext)
        assert(bobMessage == aliceDecrypted)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet4 = SSBDoubleRatchet(aliceRatchet3.serialize())
        val bobRatchet4 = SSBDoubleRatchet(bobRatchet3.serialize())
        // Another message from Alice.
        val aliceMessage2 = "Do you want to hang out some time?"
        val aliceCiphertext2 = aliceRatchet4.encryptString(aliceMessage2)
        // String is sent to Bob here.
        val bobDecrypted2 = bobRatchet4.decryptString(aliceCiphertext2)
        assert(aliceMessage2 == bobDecrypted2)

        // Here we serialize and deserialize the two DoubleRatchets.
        val aliceRatchet5 = SSBDoubleRatchet(aliceRatchet4.serialize())
        val bobRatchet5 = SSBDoubleRatchet(bobRatchet4.serialize())
        // Bob prepares the reply to be sent.
        val bobMessage2 = "Of course! Meet up at 12?"
        val bobCiphertext2 = bobRatchet5.encryptString(bobMessage2)
        val aliceDecrypted2 = aliceRatchet5.decryptString(bobCiphertext2)
        assert(bobMessage2 == aliceDecrypted2)
    }

    companion object {
        /**
         * The object to encode Strings or ByteArrays to Base64 ByteArrays.
         */
        private val base64Encoder = java.util.Base64.getEncoder()
    }
}