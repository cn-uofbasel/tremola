package nz.scuttlebutt.tremola.doubleRatchet

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.Sodium
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.interfaces.DiffieHellman
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
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
}