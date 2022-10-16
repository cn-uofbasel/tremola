package nz.scuttlebutt.tremola.ssb.core

import java.security.SecureRandom
import android.util.Base64
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.KeyPair
import org.json.JSONObject

import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.signDetached
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64
import nz.scuttlebutt.tremola.utils.JSONPrettyPrint

/**
 * Public/private key SSB identity.
 * Register the keys if given as arg, otherwise create an identity.
 */
class SSBid { // ed25519

    /**
     * TODO Add documentation.
     */
    constructor(secret: ByteArray, public: ByteArray) {
        signingKey = secret
        verifyKey = public
    }

    /**
     * TODO Add documentation.
     */
    constructor(key: ByteArray) {
        if (key.size == Sign.ED25519_PUBLICKEYBYTES) {
            signingKey = null
            verifyKey = key
        } else { // secret key
            signingKey = key
            verifyKey = ByteArray(Sign.ED25519_PUBLICKEYBYTES)
            lazySodiumInst.cryptoSignEd25519SkToPk(verifyKey, signingKey)
        }
    }

    /**
     * TODO Add documentation.
     */
    constructor(str: String) {
        val s = str.slice(1..str.lastIndex).removeSuffix(".ed25519")
        verifyKey = Base64.decode(s, Base64.NO_WRAP)
    }

    /**
     * TODO Add documentation.
     */
    constructor(k: KeyPair) {
        signingKey = k.secretKey.asBytes
        verifyKey = k.publicKey.asBytes
    }

    /**
     * TODO Add documentation.
     */
    constructor() { // generate new ID
        val keypair = lazySodiumInst.cryptoSignKeypair()
        signingKey = keypair.secretKey.asBytes
        verifyKey = keypair.publicKey.asBytes
    }

    /** Private key */
    var signingKey: ByteArray? = null

    /** Public key i.e., the SSB ID proper */
    var verifyKey: ByteArray

    /**
     * TODO Add documentation.
     */
    private val lazySodiumInst = Crypto.lazySodiumInst

    // ------------------------------------------------------------------------

    /**
     * Return the public key in the form of identity
     * TODO Add @param descriptions.
     */
    fun toRef(): String {
        return "@" + verifyKey.toBase64() + ".ed25519"
    }

    /**
     * TODO Add documentation.
     */
    fun toExportString(): String? {
        if (signingKey == null) return null
        val s = Base64.encode(signingKey, Base64.NO_WRAP).decodeToString()
        return "{\"curve\":\"ed25519\",\"secret\":\"${s}\"}"
    }

    /**
     * Sign the data with own private key, returning the signature.
     * TODO Add @param and @return documentation.
     */
    fun sign(data: ByteArray): ByteArray {
        val signature = ByteArray(Sign.BYTES)
        if (lazySodiumInst.cryptoSignDetached(signature, data, data.size.toLong(), signingKey!!))
            return signature
        throw Exception("Could not sign with Identity.")
    }

    /**
     * Verify that the signature comes from self.
     * For other signatories, see {@link Crypto.Companion.verifySignDetached}
     * TODO Add @param and @return documentation.
     */
    fun verify(signature: ByteArray, data: ByteArray): Boolean {
        return lazySodiumInst.cryptoSignVerifyDetached(signature, data, data.size, verifyKey)
    }

    /**
     * TODO Add documentation.
     */
    private fun ed25519PktoCurve(pk: ByteArray): ByteArray {
        val c = ByteArray(Sign.CURVE25519_PUBLICKEYBYTES)
        lazySodiumInst.convertPublicKeyEd25519ToCurve25519(c, pk)
        return c
    }

    /**
     * TODO Add documentation.
     */
    private fun ed25519SktoCurve(sk: ByteArray): ByteArray {
        val c = ByteArray(Sign.CURVE25519_SECRETKEYBYTES)
        lazySodiumInst.convertSecretKeyEd25519ToCurve25519(c, sk)
        return c
    }

    /**
     * TODO Add documentation.
     */
    fun deriveSharedSecretAb(publicKey: ByteArray): ByteArray {
        val curve_sk = ed25519SktoCurve(signingKey!!)
        val shared = ByteArray(32)
        lazySodiumInst.cryptoScalarMult(shared, curve_sk, publicKey)
        return shared
    }

    /**
     * TODO Add documentation.
     */
    fun encryptPrivateMessage(message: String, recps: List<ByteArray>): String {
        val txt = message.encodeToByteArray()
        val nonce = SecureRandom().generateSeed(24)
        val cdek = SecureRandom().generateSeed(33) // count plus data encryption key
        cdek[0] = recps.size.toByte()
        val dek = cdek.sliceArray(1..32)
        val aKeyPair = lazySodiumInst.cryptoSignKeypair()
        val secret = ed25519SktoCurve(aKeyPair.secretKey.asBytes)
        val public = ed25519PktoCurve(aKeyPair.publicKey.asBytes)
        var boxes = ByteArray(0)
        val kek = ByteArray(32)
        for (k in recps) {
            val sbox = ByteArray(cdek.size + 16)
            lazySodiumInst.cryptoScalarMult(kek, secret, ed25519PktoCurve(k))
            lazySodiumInst.cryptoSecretBoxEasy(sbox, cdek, cdek.size.toLong(), nonce, kek)
            boxes += sbox
        }
        val lastbox = ByteArray(txt.size + 16)
        lazySodiumInst.cryptoSecretBoxEasy(lastbox, txt, txt.size.toLong(), nonce, dek)
        val total = nonce + public + boxes + lastbox
        return Base64.encodeToString(total, Base64.NO_WRAP) + ".box"
    }

    /**
     * TODO Add documentation.
     */
    fun decryptPrivateMessage(message: String): ByteArray? {
        val raw = Base64.decode(message.removeSuffix(".box"), Base64.NO_WRAP)
        val nonce = raw.sliceArray(0..23)
        val pubkey = raw.sliceArray(24..55)
        val kek = ByteArray(32)
        lazySodiumInst.cryptoScalarMult(kek, ed25519SktoCurve(signingKey!!), pubkey)
        var recipients = raw.sliceArray(56..raw.lastIndex)

        for (i in 0..6) {
            if (recipients.size < 49) return null
            val cdek = Crypto.secretUnbox(recipients.copyOfRange(0, 49), nonce, kek)
            if (cdek != null) {
                val numberRecipients = cdek[0].toInt()
                val data = raw.sliceArray(56 + numberRecipients * 49..raw.lastIndex)
                return Crypto.secretUnbox(data, nonce, cdek.sliceArray(1..32))
            }
            recipients = raw.sliceArray(56 + (i + 1) * 49..raw.lastIndex)
        }
        return null
    }

    /**
     * TODO Add documentation.
     */
    fun formatEvent(
        prev: String?, seq: Int, auth: String, ts: String,
        hash: String, cont: Any, sig: ByteArray?
    ): String {
        // returns SSB-compliant JSON string, cont is either JSONObject/dict or a string
        var cstr = if (cont is String) "\"${cont}\"" else ((cont as JSONObject)).toString(2)
        cstr = cstr.replace("\n", "\n  ")
        cstr = cstr.replace("\\/", "/") // argh, silly json.org formatting
        var estr = if (prev == null) "{\n  \"previous\": null," else
            "{\n  \"previous\": \"${prev}\","
        estr += """
  "sequence": ${seq},
  "author": "${auth}",
  "timestamp": ${ts},
  "hash": "${hash}",
  "content": ${cstr}"""
        if (sig != null)
            estr += ",\n  \"signature\": \"{sig}\"\n}"
        else
            estr += "\n}"
        return JSONPrettyPrint().makePretty(estr)
    }

    /**
     * TODO Add documentation.
     */
    fun signSSBEvent(prev: String?, seq: Int, content: Any): String {
        val estr = formatEvent(
            prev, seq, this.toRef(), System.currentTimeMillis().toString(),
            "sha256", content, null
        )
        val sig = Base64.encode(signDetached(estr.encodeToByteArray(), signingKey!!), Base64.NO_WRAP)
        return (estr.slice(0..(estr.lastIndex - 2)) +
                ",\n  \"signature\": \"${sig.decodeToString()}.sig.ed25519\"\n}")
    }
}
