package nz.scuttlebutt.tremola.doubleRatchet

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.goterl.lazysodium.utils.Key
import nz.scuttlebutt.tremola.ssb.core.SSBid
import org.json.JSONObject
import org.junit.*
import java.io.FileNotFoundException
import javax.crypto.AEADBadTagException
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

/**
 * This class tests the timing of several operations in the Double Ratchet algorithm.
 * TODO Improve these tests by testing with unique values each time. This will slow down execution
 *  but return more realistic results.
 */
class TimingAndroidTest {

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
     * Runs the encryptString() function a hundred times to determine how long it takes a message on
     * average to be encrypted.
     */
    @Test
    fun measureTimeToEncrypt() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
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
        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"

        // Measure the execution time
        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                aliceRatchet.encryptString(aliceMessage)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToEncrypt", timesNano)
    }


    /**
     * Runs the encryptString() function a hundred times to determine how long it takes a message on
     * average to be successfully decrypted.
     */
    @Test
    fun measureTimeToSuccessfulDecrypt() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
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
        val bobRatchet =
            SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))

        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val ciphertextList = MutableList(SAMPLE_SIZE) { DEFAULT_STRING_VALUE }

        // Produce the values to decrypt
        for (i in 0 until SAMPLE_SIZE) {
            ciphertextList[i] = aliceRatchet.encryptString(aliceMessage)
            assert(ciphertextList[i] != DEFAULT_STRING_VALUE)
        }
        // Measure the execution time
        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                bobRatchet.decryptString(ciphertextList[i])
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToSuccessfulDecrypt", timesNano)
    }


    /**
     * Runs the encryptString() function a hundred times to determine how long it takes a message on
     * average to be successfully decrypted.
     */
    @Test
    fun measureTimeToSuccessfulDecryptWith50SkippedMessages() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
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

        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        var ciphertextWithSkippedMessages = ""

        // Produce the value to decrypt
        for (i in 0 until 50) {
            ciphertextWithSkippedMessages = aliceRatchet.encryptString(aliceMessage)
        }
        assert(ciphertextWithSkippedMessages != DEFAULT_STRING_VALUE)

        // Measure the execution time
        for (i in 0 until SAMPLE_SIZE) {
            val bobRatchet =
                SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
            timesNano[i] = measureNanoTime {
                bobRatchet.decryptString(ciphertextWithSkippedMessages)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToSuccessfulDecryptWith50SkippedMessages", timesNano)
    }


    /**
     * Runs the encryptString() function a hundred times to determine how long it takes a message on
     * average to be unsuccessfully decrypted.
     */
    @Test
    fun measureTimeToUnsuccessfulDecrypt() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
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
        val bobRatchet =
            SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))

        // Alice prepares the message to be sent.
        val aliceMessage = "Bob, you are my best friend!"
        val ciphertextList = MutableList(SAMPLE_SIZE) { DEFAULT_STRING_VALUE }
        val corruptCiphertextList = ciphertextList.toMutableList()

        // Produce the faulty values to decrypt
        for (i in 0 until SAMPLE_SIZE) {
            val ciphertext = aliceRatchet.encryptString(aliceMessage)
            ciphertextList[i] = ciphertext
            assert(ciphertext != DEFAULT_STRING_VALUE)
            val wholeJSONObject = JSONObject(ciphertext)
            val encodedEncryptedMessageJSONObjectString =
                wholeJSONObject.getString(DoubleRatchet.ENCODED_ENCRYPTED_MESSAGE)
            val encodedEncryptedMessageJSONObject =
                JSONObject(encodedEncryptedMessageJSONObjectString)
            val encodedEncryptedMessage =
                encodedEncryptedMessageJSONObject.getString(DoubleRatchet.CIPHERTEXT)
            val modifiedMessage = if (encodedEncryptedMessage[0] != 'a') {
                "a" + encodedEncryptedMessage.slice(1 until encodedEncryptedMessage.length)
            } else {
                "b" + encodedEncryptedMessage.slice(1 until encodedEncryptedMessage.length)
            }
            assert(modifiedMessage != encodedEncryptedMessage)
            encodedEncryptedMessageJSONObject.put(
                DoubleRatchet.CIPHERTEXT,
                modifiedMessage
            )
            wholeJSONObject.put(
                DoubleRatchet.ENCODED_ENCRYPTED_MESSAGE,
                encodedEncryptedMessageJSONObject.toString()
            )
            corruptCiphertextList[i] = wholeJSONObject.toString()
        }

        // Measure the execution time
        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                try {
                    bobRatchet.decryptString(corruptCiphertextList[i])
                    throw Exception("Encryption did not fail.")
                } catch (e: AEADBadTagException) {
                }
            }
            bobRatchet.decryptString(ciphertextList[i])
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToUnsuccessfulDecrypt", timesNano)
    }


    /**
     * Measures the average time it takes to initialize a sending SSBDoubleRatchet.
     *
     */
    @Test
    fun measureTimeToInitializeSendingRatchet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet(
                    aliceSharedSecret,
                    SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey)
                )
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToInitializeSendingRatchet", timesNano)
    }


    /**
     * Measures the average time it takes to initialize a receiving SSBDoubleRatchet.
     *
     */
    @Test
    fun measureTimeToInitializeReceivingRatchet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val alicePublicEDKey = Key.fromBytes(aliceSSBid.verifyKey)
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val aliceSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
        val bobSharedSecret =
            SSBDoubleRatchet.calculateSharedSecretEd(bobSSBid, alicePublicEDKey)
        assert(aliceSharedSecret == bobSharedSecret)

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet(bobSharedSecret, SSBDoubleRatchet.ssbIDToCurve(bobSSBid))
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToInitializeReceivingRatchet", timesNano)
    }


    /**
     * Measures the average time it takes to calculate the shared secret of Ed keys.
     */
    @Test
    fun measureTimeToCalculateSharedSecretEd() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicEDKey)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToCalculateSharedSecretEd", timesNano)
    }


    /**
     * Measures the average time it takes to calculate the shared secret of Curve keys.
     */
    @Test
    fun measureTimeToCalculateSharedSecretCurve() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)
        val bobPublicCurveKey = SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey)

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet.calculateSharedSecretCurve(aliceSSBid, bobPublicCurveKey)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToCalculateSharedSecretCurve", timesNano)
    }


    /**
     * Measures the average time it takes to transform a Ed25519 key to a Curve25519 key.
     */
    @Test
    fun measureTimeToTransformEd25519ToCurve25519() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }
        val bobSSBid = SSBid()
        val bobPublicEDKey = Key.fromBytes(bobSSBid.verifyKey)

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet.publicEDKeyToCurve(bobPublicEDKey)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToTransformEd25519ToCurve25519", timesNano)
    }


    /**
     * Measures the average time it takes to transform a Ed25519 key to a Curve25519 key.
     */
    @Test
    fun measureTimeToTransformSSBIDToCurve25519() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

        val bobSSBid = SSBid()

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet.ssbIDToCurve(bobSSBid)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToTransformSSBIDToCurve25519", timesNano)
    }


    /**
     * Measures the average time it takes to serialize a SSBDoubleRatchet.
     */
    @Test
    fun measureTimeToSerializeSSBDoubleRatchet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

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

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                aliceRatchet.serialize()
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToSerializeSSBDoubleRatchet", timesNano)
    }


    /**
     * Measures the average time it takes to deserialize a SSBDoubleRatchet.
     */
    @Test
    fun measureTimeToDeserializeSSBDoubleRatchet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

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
        val serializedAliceRatchet = aliceRatchet.serialize()

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                SSBDoubleRatchet(serializedAliceRatchet)
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeToDeserializeSSBDoubleRatchet", timesNano)
    }

    /**
     * Creates a DoubleRatchetList and serializes it. Measures the time it takes to do so.
     */
    @Test
    fun measureTimeToInitializeDoubleRatchetList() {
        if (EXECUTE_IO_INTENSIVE_TESTS) {
            val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

            val context = instrumentationContext

            for (i in 0 until SAMPLE_SIZE) {
                timesNano[i] = measureNanoTime {
                    DoubleRatchetList(context)
                }
                assert(timesNano[i] != DEFAULT_TIME_VALUE)
            }

            logMeasurements("measureTimeToInitializeDoubleRatchetList", timesNano)
        } else {
            Log.d(
                "measureTimeToInitializeDoubleRatchetList",
                "Skipped because EXECUTE_IO_INTENSIVE_TEST is false\n"
            )
        }
    }

    /**
     * Creates a DoubleRatchetList and tries to get an argument that it does not contain.
     * Measures the time it takes to do so.
     */
    @Test
    fun measureTimeForFailedDoubleRatchetListGet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

        val context = instrumentationContext
        val doubleRatchetList = DoubleRatchetList(context)
        val testString1 = ALICE_RATCHET_ID
        val testString2 = BOB_RATCHET_ID

        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
        val sharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
        val originalDoubleRatchet = SSBDoubleRatchet(sharedSecret, bobPublicKeyEd)
        doubleRatchetList[testString1] = originalDoubleRatchet

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                doubleRatchetList[testString2]
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeForFailedDoubleRatchetListGet", timesNano)
    }

    /**
     * Creates a DoubleRatchetList and serializes it. Measures the time it takes to do so.
     */
    @Test
    fun measureTimeForSuccessfulDoubleRatchetListGet() {
        val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

        val context = instrumentationContext
        val doubleRatchetList = DoubleRatchetList(context)
        val testString1 = ALICE_RATCHET_ID

        val aliceSSBid = SSBid()
        val bobSSBid = SSBid()
        val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
        val sharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
        val originalDoubleRatchet = SSBDoubleRatchet(sharedSecret, bobPublicKeyEd)
        doubleRatchetList[testString1] = originalDoubleRatchet

        for (i in 0 until SAMPLE_SIZE) {
            timesNano[i] = measureNanoTime {
                doubleRatchetList[testString1]
            }
            assert(timesNano[i] != DEFAULT_TIME_VALUE)
        }

        logMeasurements("measureTimeForSuccessfulDoubleRatchetListGet", timesNano)
    }


    /**
     * Creates a DoubleRatchetList and serializes it. Measures the time it takes to do so.
     */
    @Test
    fun measureTimeForDoubleRatchetListSet() {
        if (EXECUTE_IO_INTENSIVE_TESTS) {
            val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

            val context = instrumentationContext
            val doubleRatchetList = DoubleRatchetList(context)
            val testString1 = ALICE_RATCHET_ID

            val aliceSSBid = SSBid()
            val bobSSBid = SSBid()
            val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
            val sharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
            val originalDoubleRatchet = SSBDoubleRatchet(sharedSecret, bobPublicKeyEd)

            for (i in 0 until SAMPLE_SIZE) {
                timesNano[i] = measureNanoTime {
                    try {
                        doubleRatchetList[testString1] = originalDoubleRatchet
                    } catch (e: FileNotFoundException) {
                        if (SHOW_FILE_NOT_FOUND) {
                            if (e.message != null) {
                                Log.e("FileNotFoundException", e.message!!)
                            } else {
                                Log.e("FileNotFoundException", "no message")
                            }
                        }
                    }
                }
                assert(timesNano[i] != DEFAULT_TIME_VALUE)
            }

            logMeasurements("measureTimeForDoubleRatchetListSet", timesNano)
        } else {
            Log.d(
                "measureTimeForDoubleRatchetListSet",
                "Skipped because EXECUTE_IO_INTENSIVE_TEST is false\n"
            )
        }
    }


    /**
     * This function persists the DoubleRatchetList and measures the time to do so.
     */
    @Test
    fun measureTimeToPersistDoubleRatchetList() {
        if (EXECUTE_IO_INTENSIVE_TESTS) {
            val timesNano = MutableList(SAMPLE_SIZE) { DEFAULT_TIME_VALUE }

            val context = instrumentationContext
            val doubleRatchetList = DoubleRatchetList(context)
            val testString1 = ALICE_RATCHET_ID

            val aliceSSBid = SSBid()
            val bobSSBid = SSBid()
            val bobPublicKeyEd = Key.fromBytes(bobSSBid.verifyKey)
            val sharedSecret = SSBDoubleRatchet.calculateSharedSecretEd(aliceSSBid, bobPublicKeyEd)
            val originalDoubleRatchet = SSBDoubleRatchet(sharedSecret, bobPublicKeyEd)
            doubleRatchetList[testString1] = originalDoubleRatchet

            for (i in 0 until SAMPLE_SIZE) {
                timesNano[i] = measureNanoTime {
                    try {
                        doubleRatchetList.persist()
                    } catch (e: FileNotFoundException) {
                        if (SHOW_FILE_NOT_FOUND) {
                            if (e.message != null) {
                                Log.e("FileNotFoundException", e.message!!)
                            } else {
                                Log.e("FileNotFoundException", "no message")
                            }
                        }
                    }
                }
                assert(timesNano[i] != DEFAULT_TIME_VALUE)
            }

            logMeasurements("measureTimeToPersistDoubleRatchetList", timesNano)
        } else {
            Log.d(
                "measureTimeToPersistDoubleRatchetList",
                "Skipped because EXECUTE_IO_INTENSIVE_TEST is false\n"
            )
        }
    }


    companion object {

        /**
         * Takes a list of Long, calculating the standard deviation of it.
         * @param list A list of Long numbers.
         * @return The standard deviation as Double.
         */
        private fun standardDeviation(list: List<Long>): Double {
            val mean = list.average()
            var sumOfSquaredDeviation = 0.0
            for (i in list.indices) {
                val listValue = list[i].toDouble()
                sumOfSquaredDeviation += (listValue - mean) * (listValue - mean)
            }
            val variance = sumOfSquaredDeviation / list.size
            return sqrt(variance)
        }


        /**
         * Logs the given measurements.
         * @param title The tag for the logs.
         * @param measurementsListNano The list of long numbers of the measurements.
         */
        private fun logMeasurements(title: String, measurementsListNano: List<Long>) {
            Log.d(title, "Measurements")
            val averageTimeNano = measurementsListNano.average()
            val standardDeviationNano = standardDeviation(measurementsListNano)
            if (SHOW_NANOSECONDS) {
                val unit = "nanoseconds"
                Log.d(title, "Average time in $unit: $averageTimeNano")
                Log.d(title, "Standard deviation in $unit: $standardDeviationNano")
                if (PRINT_ALL_MEASUREMENTS) {
                    Log.d(title, "All times in $unit: $measurementsListNano")
                }
            }
            if (SHOW_MICROSECONDS) {
                val unit = "microseconds"
                Log.d(title, "Average time in $unit: ${averageTimeNano / 1000}")
                Log.d(title, "Standard deviation in $unit: ${standardDeviationNano / 1000}\n")
                if (PRINT_ALL_MEASUREMENTS) {
                    val measurementsListMicro = measurementsListNano.toMutableList()
                    for (i in measurementsListNano.indices) {
                        measurementsListMicro[i] = measurementsListNano[i] / 1000
                    }
                    Log.d(title, "All times in $unit: $measurementsListMicro")
                }
            }
            if (SHOW_MILLISECONDS) {
                val unit = "milliseconds"
                Log.d(title, "Average time in $unit: ${averageTimeNano / 1000000}")
                Log.d(title, "Standard deviation in $unit: ${standardDeviationNano / 1000000}")
                if (PRINT_ALL_MEASUREMENTS) {
                    val measurementsListMilli = measurementsListNano.toMutableList()
                    for (i in measurementsListNano.indices) {
                        measurementsListMilli[i] = measurementsListNano[i] / 1000000
                    }
                    Log.d(title, "All times in $unit: $measurementsListMilli")
                }
            }
        }

        /** Determines how many samples to take for time measurements. */
        private const val SAMPLE_SIZE = 1000

        /** What the timing array should be filled with. */
        private const val DEFAULT_TIME_VALUE: Long = -1

        /** What the message array is filled with. */
        private const val DEFAULT_STRING_VALUE = ""

        /** If true, times are shown in milliseconds. */
        private const val SHOW_MILLISECONDS = false

        /** If true, times are shown in microseconds. */
        private const val SHOW_MICROSECONDS = true

        /** If true, times are shown in nanoseconds. */
        private const val SHOW_NANOSECONDS = false

        /** True if all time measurements (the entire list) should be printed. */
        private const val PRINT_ALL_MEASUREMENTS = false

        /** If true, will print the FileNotFoundException. */
        private const val SHOW_FILE_NOT_FOUND = true

        /** If false, will not execute tests opening and closing files many times */
        private const val EXECUTE_IO_INTENSIVE_TESTS = true

        /** The string used to identify the first ratchet in the list. */
        private const val ALICE_RATCHET_ID = "abcdefghijklmnop"

        /** The string used to identify the first ratchet in the list. */
        private const val BOB_RATCHET_ID = "qrstuvwxyz"

    }
}