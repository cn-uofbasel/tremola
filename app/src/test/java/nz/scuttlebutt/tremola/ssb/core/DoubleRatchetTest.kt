package nz.scuttlebutt.tremola.ssb.core

import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import nz.scuttlebutt.tremola.ssb.DoubleRatchet
import org.junit.Test
import java.lang.reflect.Method

class DoubleRatchetTest {

    @Test
    fun dhIsSymmetric() {
        val generateDH: Method = DoubleRatchet.javaClass.getDeclaredMethod("generateDH", null)
        generateDH.isAccessible = true
        var keyPair1 = generateDH.invoke(null)
        var keyPair2 = generateDH.invoke(null)
        assert(keyPair1 is KeyPair && keyPair2 is KeyPair)
        keyPair1 = keyPair1 as KeyPair
        keyPair2 = keyPair2 as KeyPair
        val diffieHellman: Method = DoubleRatchet.javaClass.getDeclaredMethod(
            "diffieHellman",
            KeyPair::class.java,
            Key::class.java
        )
        diffieHellman.isAccessible = true
        assert(diffieHellman.invoke(null, keyPair1, keyPair2.publicKey) ==
                diffieHellman.invoke(null, keyPair2, keyPair1.publicKey))
    }



}