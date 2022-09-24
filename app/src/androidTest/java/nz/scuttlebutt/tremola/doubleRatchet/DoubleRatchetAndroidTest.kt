package nz.scuttlebutt.tremola.doubleRatchet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class DoubleRatchetAndroidTest {

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
}