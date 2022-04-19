package nz.scuttlebutt.tremola.ssb.peering.discovery

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.verifySignDetached
import nz.scuttlebutt.tremola.ssb.core.SSBid
import nz.scuttlebutt.tremola.ssb.db.entities.Contact
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.id2
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class Lookup(
    private val localAddress: String?,
    private val context: Context,
    private val tremolaState: TremolaState,
    private val udpBroadcastAddress: String
) {
    private val ed25519KeyPair: SSBid = tremolaState.idStore.identity
    private var lookupClients: LinkedList<LookupClient> = LinkedList()
    private var port = 0
    private val logOfReceivedQueries = LinkedList<Query>()
    private val logOfReceivedReplies = LinkedList<Query>()
    private var sentQuery: MutableMap<String, Boolean> = HashMap()

    /**
     * Instantiate the lookupClients and start the listening loop.
     *
     * @param port the UDP port used by this protocol
     * @param lock the lock to wait in case of an exception
     */
    fun listen(port: Int, lock: ReentrantLock) {
        this.port = port
        val lookupUDP = LookupUDP(this, context, ed25519KeyPair, lock, port, udpBroadcastAddress)
        lookupClients.add(lookupUDP)
        for (client in lookupClients) {
            client.start()
        }
    }

    private fun closeQuery(message: String) {
        for (client in lookupClients) {
            if (client.active) {
                client.closeQuery(message)
                Log.e("LOOKUP", "Close query for " + client.subClass)
            }
        }
    }

    /**
     * Store the needed information and starts a timer to notify the user
     * if no valid reply is received.
     * Handles the notifications if the contact is known.
     *
     * @param targetName the target name written by the user
     * @return true if the contact is not known
     */
    fun prepareQuery(targetName: String): String? {
        for (client in lookupClients) {
            if (!client.active) {
                Log.e("PREPARE_QUERY", "Client is not active!!!")
                try {
                    client.reactivate()
                    client.start()
                    Log.e("CLIENTS", "Client reactivated " + client.subClass)
                } catch (e: Exception) {
                    client.close()
                    e.printStackTrace()
                }
            }
        }
        val broadcastMessage = createMessage(targetName)
        val databaseSearch = searchDataBase(targetName)
        if (databaseSearch != null) {
            Log.e("NOTIFY", databaseSearch)
            if (databaseSearch == ed25519KeyPair.toRef()) {
                notify(targetName, "Shortname \"$targetName\" is your own shortname.", false)
                return null
            }
            var alias: String?
            try {
                alias = tremolaState.contactDAO.getContactByLid(databaseSearch)!!.alias
                alias = if (alias == "null") targetName else alias
            } catch (e: Exception) {
                alias = targetName
            }
            notify(
                targetName, "Shortname \"" + targetName
                        + "\" is in your contacts as " + alias + ".", false
            )
            return null
        }
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val text = "No result found for \"$targetName\""
                notify(targetName, text, false)
            }
        }, DELAY)
        sentQuery[targetName] = false
        return broadcastMessage
    }

    /**
     * Send a query that comes from front end.
     * Must be done for each available medium.
     */
    fun sendQuery(broadcastMessage: String) {
        for (client in lookupClients) {
            if (client.active) {
                client.sendQuery(broadcastMessage)
            } else {
                Log.e("SEND_QUERY", "Client is not active!!!")
            }
        }
    }

    /**
     * Process an incoming request by discarding, answering or forwarding it.
     */
    fun processQuery(incomingRequest: String) {
        Log.d("INPUT", id2(ed25519KeyPair.toRef()) + " " + incomingRequest)
        try {
            val data = JSONObject(incomingRequest)
            val msa = data["msa"].toString()
            if (msa.endsWith(Objects.requireNonNull(ed25519KeyPair.toRef()))) {
                Log.d("QUERY", "I am the initiator")
                return  // I am the initiator
            }
            val multiServerAddress = msa.split("~").toTypedArray()
            val initId = multiServerAddress[1].split(":").toTypedArray()[1]
            val queryId = data.getInt("queryId")
            if (checkLog(logOfReceivedQueries, initId, queryId)) {
                Log.d("QUERY", "Already in db")
                return  // the query is already in the database
            }
            val shortName = data.getString("targetName")
            var hopCount = data.getInt("hop")
            val sig = data.getString("signature")
            val message = JSONObject()
            val signature = ByteArray(0)
            try {
                message.put("targetName", shortName)
                message.put("msa", msa)
                message.put("queryId", queryId)
                if (signatureIsWrong(initId, message, sig)) {
                    Log.e("VERIFY", "Verification failed")
                }
            } catch (e: Exception) {
                Log.e("VERIFY", msa)
            }
            val targetPublicKey = searchDataBase(shortName)
            if (targetPublicKey != null) {
                replyStep2(initId, queryId, shortName, hopCount, targetPublicKey)
            } else {
                if (hopCount-- > 0) {
                    val msg = createMessage(shortName, msa, queryId, hopCount, signature)
                    for (client in lookupClients) {
                        if (client.active) {
                            client.sendQuery(msg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PROCESS_QUERY", "Problem in process.")
        }
        Log.d("QUERY", "Exciting query processor")
    }

    /**
     * Process a reply from an initiated query by discarding it or adding a new Contact.
     *
     * @param incomingAnswer the received answer
     */
    fun processReply(incomingAnswer: String) {
        Log.d("REPLY", incomingAnswer)
        try {
            val data = JSONObject(incomingAnswer)
            val initId = data.getString("initiatorId")
            val queryId = data.getInt("queryId")
            val targetShortName = data.getString("targetName")
            if (checkLog(logOfReceivedReplies, initId, queryId)) {
                Log.d("REPLY", "Already in: $incomingAnswer")
                return  // the reply is already in the database
            }
            if (initId != ed25519KeyPair.toRef()) {
                Log.d("REPLY", "Forwarded: $incomingAnswer")
                sendQuery(incomingAnswer)
                return  // Reply is not for me
            }
            val targetId = data.getString("targetId")
            val friendId = data.getString("friendId")
            val hopCount = data.getInt("hop")
            val sig = data.getString("signature")
            val message = JSONObject()
            try {
                message.put("targetId", targetId)
                message.put("targetName", targetShortName)
                message.put("initiatorId", initId)
                message.put("queryId", queryId)
                message.put("friendId", friendId)
                message.put("hop", hopCount)
                if (signatureIsWrong(friendId, message, sig)) {
                    Log.e("VERIFY", "Verification failed")
                    return
                }
            } catch (e: Exception) {
                Log.e("VERIFY", "failed : $friendId")
            }
            val pattern = Regex("^@[a-zA-Z0-9+/]{43}=.ed25519\$")
            if (!pattern.matches(targetId)) {
                Log.e("VERIFY", "$targetId : public key is not valid")
                return  // public key is not valid
            }
            val targetPublicKey = searchDataBase(targetShortName)
            if (targetPublicKey != null) {
                Log.e("OLD CONTACT", "$targetShortName already exists in database : $targetPublicKey")
                // Contact already exists in database
                return
            }
            addNewContact(targetId, targetShortName)
            notify(targetShortName, "\"$targetShortName\" added to your contacts.", true)
        } catch (e: Exception) {
            Log.e("PROCESS_REPLY", "Problem in process")
            e.printStackTrace()
        }
    }

    private fun checkLog(log: LinkedList<Query>, initId: String, queryId: Int): Boolean {
        val reply = Query(initId, queryId)
        for (`object` in log.toTypedArray()) {
            if (`object`.isOutDated) {
                log.remove(`object`)
            } else if (`object`.isEqualTo(initId, queryId)) {
                return true
            }
        }
        log.add(reply)
        return false
    }

    /**
     * Notify the user of the result of the query.
     * Make sure that each query produces only one reply, except if
     * a positive reply comes in after the timer.
     *
     * @param targetName the name for the query, as id not to notify the
     * user more than once
     * @param text       the text to display
     * @param force      true if a contact was added, to make sure
     * the user is notified
     */
    private fun notify(targetName: String, text: String, force: Boolean) {
        closeQuery(targetName)
        if (java.lang.Boolean.FALSE == sentQuery.remove(targetName) || force) {
            (context as MainActivity).runOnUiThread { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
            sentQuery[targetName] = true
        }
    }

    /**
     * Create the payload of the query.
     *
     * @param targetName the queried shortName
     * @return the payload
     */
    private fun createMessage(targetName: String): String {
        val selfMultiServerAddress = "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toRef()
        val hopCount = 4
        return createMessage(targetName, selfMultiServerAddress, queryIdentifier++, hopCount, null)
    }

    /**
     * Create a message to broadcast
     *
     * @param targetName         the name of the target
     * @param multiServerAddress the address of the query initiator
     * @param queryId            an id to identify the query
     * @param hopCount           the decrementing number of hop as a time-to-live
     * @return the message ready to send
     */
    private fun createMessage(
        targetName: String,
        multiServerAddress: String,
        queryId: Int,
        hopCount: Int,
        sig: ByteArray?
    ): String {
        var signature = sig
        val message = JSONObject()
        try {
            message.put("targetName", targetName)
            message.put("msa", multiServerAddress)
            message.put("queryId", queryId)
            if (signature == null) {
                signature = ed25519KeyPair.sign(message.toString().toByteArray(StandardCharsets.UTF_8))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                message.put("signature", Base64.getEncoder().encodeToString(signature))
            }
            message.put("hop", hopCount)
        } catch (e: JSONException) {
            Log.e("LOOKUP_JSON", e.message!!)
        }
        return message.toString()
    }

    /**
     * Add a new contact in database in case of a successful lookup.
     *
     * @param targetId        the public key of the Target
     * @param targetShortName the ShortName of the Target
     */
    private fun addNewContact(targetId: String, targetShortName: String) {
        tremolaState.addContact(targetId, null)
        val rawStr = tremolaState.msgTypes.mkFollow(targetId, false)
        val event = tremolaState.msgTypes.jsonToLogEntry(
            rawStr,
            rawStr.toByteArray(StandardCharsets.UTF_8)
        )!!
        tremolaState.wai.rx_event(event)
        tremolaState.peers.newContact(targetId) // inform online peers via EBT
        val eval = "b2f_new_contact_lookup('$targetShortName','$targetId')"
        tremolaState.wai.eval(eval)
    }

    /**
     * Search in the database if the targetName is known.
     *
     * @param targetShortName the target 10 char shortName
     * @return the public key if found, else null
     */
    private fun searchDataBase(targetShortName: String): String? {
        if (keyIsTarget(targetShortName, ed25519KeyPair.toRef())) {
            return ed25519KeyPair.toRef()
        } else {
            tremolaState.contactDAO.getAll().listIterator().forEach { lid: Contact ->
                if (keyIsTarget(targetShortName, lid.lid)) {
                    return lid.lid
                }
            }
        }
        return null
    }

    /**
     * Verify the authenticity of a message with its signature and author's key.
     *
     * @param initId  the author's public key
     * @param message the message to be verified
     * @param sig     the signature
     * @return true if the signature is correct
     */
    private fun signatureIsWrong(initId: String, message: JSONObject, sig: String): Boolean {
        val verifyKey = initId.substring(1, initId.length - 8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signature = Base64.getDecoder().decode(sig)
            val verify = Base64.getDecoder().decode(verifyKey)
            return !verifySignDetached(
                signature,
                message.toString().toByteArray(StandardCharsets.UTF_8),
                verify
            )
        }
        return true
    }

    /**
     * Send a reply to the Initiator in case of a successful lookup.
     *
     * @param initId          the Initiator's public key
     * @param queryId         the query identity
     * @param targetShortName the Target's ShortName
     * @param hopCount        the final hop count
     * @param targetId        the Target's public key
     */
    private fun replyStep2(
        initId: String, queryId: Int, targetShortName: String, hopCount: Int,
        targetId: String
    ) {
        val reply = JSONObject()
        try {
            reply.put("targetId", targetId)
            reply.put("targetName", targetShortName)
            reply.put("initiatorId", initId)
            reply.put("queryId", queryId)
            reply.put("friendId", ed25519KeyPair.toRef())
            reply.put("hop", hopCount)
            val signature = ed25519KeyPair.sign(reply.toString().toByteArray(StandardCharsets.UTF_8))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reply.put("signature", Base64.getEncoder().encodeToString(signature))
            }
        } catch (e: JSONException) {
            Log.e("LOOKUP_JSON", e.message!!)
        }
        logOfReceivedReplies.add(Query(initId, queryId))
        val answer = reply.toString()
        sendQuery(answer)
    }

    /**
     * Compare a public key with a short name.
     *
     * @param receivedShortName The 11 char (including a '-') short name
     * @param publicKey         The public key in the form "@[...].ed25519
     * @return true if the 2 match
     */
    private fun keyIsTarget(receivedShortName: String, publicKey: String): Boolean {
        val computedShortName = id2(publicKey)
        return receivedShortName == computedShortName
    }

    companion object {
        const val DELAY = 5000L
        private var queryIdentifier = 0
    }
}