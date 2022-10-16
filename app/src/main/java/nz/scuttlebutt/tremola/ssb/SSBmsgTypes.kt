package nz.scuttlebutt.tremola.ssb

import android.util.Base64
import android.util.Log
import nz.scuttlebutt.tremola.ssb.core.Crypto
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.ssb.core.SSBid
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.deRef
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64
import nz.scuttlebutt.tremola.utils.JSONPrettyPrint
import org.json.JSONArray
import org.json.JSONObject

/**
 * TODO Add documentation.
 * @param tremolaState Contains a TremolaState object which holds all local information about peers.
 * @property myWholeID The ID that is saved within [tremolaState]. Contains public and private keys.
 * @property myPublicID The public part of my ID. Is a string.
 */
class SSBmsgTypes(val tremolaState: TremolaState) {
    private val myWholeID: SSBid = tremolaState.idStore.identity
    private val myPublicID: String = myWholeID.toRef()

    /**
     * Creates the log entry bytes but does not persist them.
     * TODO Add better documentation.
     */
    private fun mkWire(ciphertext: Any): String {
        val prev = tremolaState.logDAO.getMostRecentEventFromLogId(myPublicID)
            ?: return myWholeID.signSSBEvent(null, 1, ciphertext)
        return myWholeID.signSSBEvent(prev.hid, prev.lsq + 1, ciphertext)
    }

    fun mkPost(text: String, toWhom: List<String>): String {
        val recipients = JSONArray()
        val keys: MutableList<ByteArray> = mutableListOf()
        for (r in toWhom)
            if (r != myPublicID) {
                recipients.put(r)
                keys.add(r.deRef())
            }
        recipients.put(myPublicID)
        keys.add(myPublicID.deRef())
        val post = JSONObject()
        post.put("type", "post")
        post.put("text", text)
        post.put("recps", recipients)
        post.put("mentions", JSONArray())
        Log.d("PRIV_POST", post.toString())
        val ciphertext = myWholeID.encryptPrivateMessage(post.toString(), keys)
        Log.d("PRIV_POST", ciphertext)
        return mkWire(ciphertext)
    }

    /**
     * TODO Add documentation.
     */
    fun mkFollow(target: String, following: Boolean = true): String {
        val contact = JSONObject()
        contact.put("type", "contact")
        contact.put("contact", target)
        contact.put("following", following.toString())
        return mkWire(contact)
    }

    /**
     * TODO Add documentation.
     */
    fun jsonToLogEntry(json: String, raw: ByteArray): LogEntry? {
        // converts log entry in JSON, with or without envelope, to internal data structure
        // but only if the signature is valid
        try {
            var value = json
            val eTree = JSONObject(json)

            var vTree = eTree // the "value" (SSB log entry)
            if (eTree.has("value")) { // eTree is an envelope, remove it
                value = eTree.getString("value")
                vTree = eTree.getJSONObject("value")
            }
            // Log.d("Value", value)
            if (!vTree.has("author")) { // not a message
                Log.d("parse", "no author?")
                return null
            }
            val msg = JSONPrettyPrint().makePretty(value)

            val key: String = if (eTree.has("key"))
                eTree.getString("key")
            else
                "%" + msg.encodeToByteArray().sha256().toBase64() + ".sha256"
            val author = vTree.getString("author")
            val seq = vTree.getInt("sequence")
            val pre = if (seq == 1) null else vTree.getString("previous")
            val signature = vTree.getString("signature").removeSuffix(".sig.ed25519")
            val sig = Base64.decode(signature, Base64.NO_WRAP)

            val msg2 = msg.slice(0 until msg.indexOf(",\n  \"signature\":", msg.length - 130)) + "\n}"
            // Log.d("FORMATTED2", msg2)
            if (!Crypto.verifySignDetached(sig, msg2.encodeToByteArray(), author.deRef())) {
                Log.d("SIGNATURE2", "**invalid** for ${author}/${seq}")
                return null
            }
            // Log.d("SIGNATURE2", "valid for ${author}/${seq}")

            // if has timestamp: we could compare it, move internal tst backwards if in the future
            // val tst = historyEvent.getAsJsonObject("timestamp").asString
            var public: String? = null
            var confid: String?
            try {
                confid = vTree.getString("content")
                confid = myWholeID.decryptPrivateMessage(confid!!)?.decodeToString()
            } catch (ex: Exception) {
                public = vTree.getJSONObject("content").toString()
                confid = null
            }
            return LogEntry(
                key, author,
                seq, pre, vTree.getLong("timestamp"),
                null, null,
                public, confid, raw
            )

        } catch (e: Exception) {
            Log.d("MSG NOT PARSED", "$e / $json")
            return null
        }
    }

}