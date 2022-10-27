package nz.scuttlebutt.tremola.doubleRatchet

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.goterl.lazysodium.utils.Key
import nz.scuttlebutt.tremola.ssb.core.SSBid
import org.junit.*

/**
 * This class is used to test the DoubleRatchetList class.
 * */
class DoubleRatchetListLocalTest {

    private lateinit var instrumentationContext: Context

    /**
     * Creates the mock object that is used as the context parameter for the list.
     */
    @Before
    fun createContext() {
        instrumentationContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    /**
     * This deletes a file that might have been generated from the list persisting.
     */
    @After
    fun deleteCreatedFile() {
        instrumentationContext.deleteFile(DoubleRatchetList.FILENAME)
    }


    /**
     * This test makes sure the dummy classes work.
     */
    @Test
    fun canInitializeDoubleRatchetListLocally() {
        val context = instrumentationContext
        DoubleRatchetList(context)
    }

    /**
     * Here we want an empty DoubleRatchetList to return null on a get.
     */
    @Test
    fun getWithUnknownArgumentReturnsNull() {
        val context = instrumentationContext
        val doubleRatchetList = DoubleRatchetList(context)
        val testString = ALICE_RATCHET_ID

        val unsuccessfulGet = doubleRatchetList[testString]
        assert(unsuccessfulGet == null)
    }

    /**
     * Here we want an empty DoubleRatchetList to return null on a get.
     */
    @Test
    fun canSetAndGet() {
        val context = instrumentationContext
        val doubleRatchetList = DoubleRatchetList(context)
        val testString1 = ALICE_RATCHET_ID
        val testString2 = BOB_RATCHET_ID
        val unsuccessfulGet = doubleRatchetList[testString1]
        assert(unsuccessfulGet == null)
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
        val sharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
        val originalDoubleRatchet = SSBDoubleRatchet(sharedSecret, bobPublicKeyEd)
        doubleRatchetList[testString1] = originalDoubleRatchet
        val wrongDoubleRatchet = doubleRatchetList[testString2]
        assert(wrongDoubleRatchet == null)
        val rightDoubleRatchet = doubleRatchetList[testString1]
        assert(originalDoubleRatchet == rightDoubleRatchet) // Reference equality.
    }

    /**
     * This test simulates two people passing messages between one another while persisting and
     * deserializing the list that their doubleRatchets are in over and over.
     */
    @Test
    fun canPersistAndDeserialize() {
        val context = instrumentationContext
        var doubleRatchetList = DoubleRatchetList(context)
        val aliceRatchetID = ALICE_RATCHET_ID
        val bobRatchetID = BOB_RATCHET_ID
        val unsuccessfulGet = doubleRatchetList[aliceRatchetID]
        assert(unsuccessfulGet == null)
        // Create DoubleRatchets.
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicKeyEd = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
        val bobSharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicKeyEd)
        assert(aliceSharedSecret == bobSharedSecret)
        val bobPublicKeyCurve = SSBDoubleRatchet.publicEDKeyToCurve(bobPublicKeyEd)
        val bobKeyPairCurve = SSBDoubleRatchet.ssbIDToCurve(bobSSBid)
        var aliceDoubleRatchet: SSBDoubleRatchet? =
            SSBDoubleRatchet(aliceSharedSecret, bobPublicKeyCurve)
        var bobDoubleRatchet: SSBDoubleRatchet? = SSBDoubleRatchet(bobSharedSecret, bobKeyPairCurve)
        // Add them to the list.
        doubleRatchetList[aliceRatchetID] = aliceDoubleRatchet!!
        doubleRatchetList[bobRatchetID] = bobDoubleRatchet!!

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Encrypt message.
        val alicePlaintext1 = "It's down there somewhere, let me take another look."
        val aliceCiphertext1 = aliceDoubleRatchet!!.encryptString(alicePlaintext1)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Decrypt message.
        val bobDecrypted1 = bobDoubleRatchet!!.decryptString(aliceCiphertext1)
        assert(bobDecrypted1 == alicePlaintext1)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Send a message back.
        val bobPlaintext1 = "It's pizza time!"
        val bobCiphertext1 = bobDoubleRatchet!!.encryptString(bobPlaintext1)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Decrypt message.
        val aliceDecrypted1 = aliceDoubleRatchet!!.decryptString(bobCiphertext1)
        assert(aliceDecrypted1 == bobPlaintext1)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Encrypt message.
        val alicePlaintext2 = "A Kansas City Shuffle is when everybody looks right, you go left."
        val aliceCiphertext2 = aliceDoubleRatchet!!.encryptString(alicePlaintext2)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Decrypt message.
        val bobDecrypted2 = bobDoubleRatchet!!.decryptString(aliceCiphertext2)
        assert(bobDecrypted2 == alicePlaintext2)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Send a message back.
        val bobPlaintext2 = "What's in the box?"
        val bobCiphertext2 = bobDoubleRatchet!!.encryptString(bobPlaintext2)

        // Persist and deserialize.
        doubleRatchetList.persist()
        doubleRatchetList = DoubleRatchetList(context)
        aliceDoubleRatchet = doubleRatchetList[aliceRatchetID]
        bobDoubleRatchet = doubleRatchetList[bobRatchetID]
        assert(aliceDoubleRatchet != null)
        assert(bobDoubleRatchet != null)

        // Decrypt message.
        val aliceDecrypted2 = aliceDoubleRatchet!!.decryptString(bobCiphertext2)
        assert(aliceDecrypted2 == bobPlaintext2)
    }

    // TODO Add more tests. What if decryption fails, ...?

    companion object {

        /** The string used to identify the first ratchet in the list. */
        private const val ALICE_RATCHET_ID = "abcdefghijklmnop"


        /** The string used to identify the first ratchet in the list. */
        private const val BOB_RATCHET_ID = "qrstuvwxyz"
    }

}