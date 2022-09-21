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

/**
 * This class is responsible for looking up Tremola users with their shortname to receive the actual
 * SSB identity.
 * This is done by sending out messages to peers if they know who the shortname corresponds to. If
 * they do, they will return the SSB identity of the user. If they do not know, they will forward
 * the look up message to their peers. If they already received the query, they
 * will discard it to prevent infinite loops.
 * If they receive a reply from one of their peers, they will forward this one like the initial
 * message.
 * @param localAddress The local IP address.
 * @param context The context object of the MainActivity.
 * @param tremolaState The object that saves the backend data of the app.
 * @param udpBroadcastAddress The address to send UDP broadcast messages to on the local network.
 * @property ed25519KeyPair The keypair of the user, of which the public key represents their SSB
 * identity and the private key is used to sign all their posts as well as for decryption.
 * @property lookupClients The list of clients used for look ups. Currently only has the UDP client.
 * @property port The UDP port used for the look up protocol.
 * @property logOfReceivedQueries List of all the queries that were received.
 * @property logOfReceivedReplies List of all the replies to queries that were received.
 * @property sentQuery Map which contains targetNames as keys and stores whether the user has been
 * notified about the result to this query.
 */
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
     * Should you add more protocols to use for look ups, add the clients here.
     * @param port The UDP port used by this protocol.
     * @param lock The lock to wait in case of an exception. Prevents race conditions.
     */
    fun listen(port: Int, lock: ReentrantLock) {
        this.port = port
        val lookupUDP = LookupUDP(
            this, context, ed25519KeyPair, lock, port, udpBroadcastAddress
        )
        lookupClients.add(lookupUDP)
        for (client in lookupClients) {
            client.start()
        }
    }

    /**
     * This causes all lookupClients to stop the given query [message].
     * @param message String which represents the query to close.
     */
    private fun closeQuery(message: String) {
        for (client in lookupClients) {
            if (client.active) {
                client.closeQuery(message)
                Log.e("LOOKUP", "Close query for " + client.subClass)
            }
        }
    }

    /**
     * Store the needed information and starts a timer to notify the user if no valid reply is
     * received.
     * Handles the notifications if the contact is known.
     * @param targetName The target shortname written by the user.
     * @return The broadcastMessage to look it up if the contact is not known, null if it is already
     * known. It is a stringified JSON object.
     */
    fun prepareQuery(targetName: String): String? {
        // Check that all clients are up, reactivate them if not.
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

        // Make the query message and check your own database for the shortname.
        val broadcastMessage = createMessage(targetName)
        val databaseSearch = searchDataBase(targetName)
        if (databaseSearch != null) {
            Log.e("NOTIFY", databaseSearch)
            if (databaseSearch == ed25519KeyPair.toRef()) {
                notify(
                    targetName, "Shortname \"$targetName\" is your own shortname.", false
                )
                return null
            }
            var alias: String?
            try {
                alias = tremolaState.contactDAO.getContactByLid(databaseSearch)!!.alias
                alias = if (alias == "null") targetName else alias
            } catch (e: Exception) {
                Log.e("PREPARE_QUERY", "Exception while getContactByLid was called.")
                alias = targetName
            }
            notify(
                targetName, "Shortname \"" + targetName
                        + "\" is in your contacts as " + alias + ".", false
            )
            return null
        }
        // If not found within DELAY ms, send error.
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val text = "No result found for \"$targetName\""
                // Notify will not print anything if a notification was already sent.
                notify(targetName, text, false)
            }
        }, DELAY)
        sentQuery[targetName] = false
        return broadcastMessage
    }

    /**
     * Send a look up query that comes from front end. Must be done for each available lookupClient.
     * @param broadcastMessage The message that was generated by [createMessage].
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
     * Process an incoming request by discarding, answering, forwarding or ignoring it.
     * @param incomingRequest The string that reached the backend from another peer, a stringified
     * JSON object.
     */
    fun processQuery(incomingRequest: String) {
        Log.d("INPUT", id2(ed25519KeyPair.toRef()) + " " + incomingRequest)
        try {
            val data = JSONObject(incomingRequest)
            val msa = data["msa"].toString() // The multi server address.
            // Did the user start the query?
            if (msa.endsWith(Objects.requireNonNull(ed25519KeyPair.toRef()))) {
                Log.d("QUERY", "I am the initiator")
                return  // I am the initiator, no need to resend message.
            }
            val multiServerAddress = msa.split("~").toTypedArray()
            // ID of the person that initiated the look up.
            val initId = multiServerAddress[1].split(":").toTypedArray()[1]
            // ID of the query itself.
            val queryId = data.getInt("queryId")
            if (checkLog(logOfReceivedQueries, initId, queryId)) {
                Log.d("QUERY", "Already in DB.")
                return  // The query is already in the database, no need to resend message.
            }

            // Special cases where resending is not necessary checked, so prepare for forwarding.
            val shortName = data.getString("targetName")
            var hopCount = data.getInt("hop")
            val sig = data.getString("signature")
            val message = JSONObject() // Recreate the message to verify the signature.
            // FIXME The original signature is not passed on to the next person, which leads to it
            //  getting rejected by the next peer.
            val signature = ByteArray(0)
            try {
                message.put("targetName", shortName)
                message.put("msa", msa)
                message.put("queryId", queryId)
                // Verify the provided signature against the original sender's public key.
                if (signatureIsWrong(initId, message, sig)) {
                    Log.e("VERIFY", "Verification failed")
                }
            } catch (e: Exception) {
                Log.e("VERIFY", msa)
            }
            val targetPublicKey = searchDataBase(shortName)
            if (targetPublicKey != null) { // Do we recognize the shortname? If yes, reply.
                replyStep2(initId, queryId, shortName, hopCount, targetPublicKey)
            } else { // We do not recognize the shortname, forward the message.
                // If the hopCount is greater than 0, decrease it and create a new query. Otherwise
                // drop the message.
                if (hopCount-- > 0) {
                    val msg = createMessage(shortName, msa, queryId, hopCount, signature)
                    // TODO use general sendQuery instead
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
        Log.d("QUERY", "Exiting query processor")
    }

    /**
     * Process a reply from an initiated query by forwarding it, discarding it or adding a new
     * contact.
     * @param incomingAnswer The received reply to a query, a stringified JSON object.
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
                return  // // The reply is already in the database, discard it.
            }
            if (initId != ed25519KeyPair.toRef()) {
                Log.d("REPLY", "Forwarded: $incomingAnswer")
                sendQuery(incomingAnswer)
                return  // Reply is not for us, so we forwarded it.
            }

            // Prepare for processing the request and adding a contact.
            val targetId = data.getString("targetId")
            // The ID of the person that sent the reply.
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
                    return // Invalid signature, discard.
                }
            } catch (e: Exception) {
                Log.e("VERIFY", "failed : $friendId")
            }
            val pattern = Regex("^@[a-zA-Z0-9+/]{43}=.ed25519\$")
            if (!pattern.matches(targetId)) {
                Log.e("VERIFY", "$targetId : public key is not valid")
                return  // Public key is not in a valid format, discard.
            }
            val targetPublicKey = searchDataBase(targetShortName)
            if (targetPublicKey != null) {
                Log.e(
                    "OLD CONTACT",
                    "$targetShortName already exists in database : $targetPublicKey"
                )
                return // Contact already exists in database, discard message.
                // TODO What if new reply contains a different ID? What if a reply was fake?
            }
            addNewContact(targetId, targetShortName)
            notify(targetShortName, "\"$targetShortName\" added to your contacts.", true)
        } catch (e: Exception) {
            Log.e("PROCESS_REPLY", "Problem in process")
            e.printStackTrace()
        }
    }

    /**
     * Checks if a query/reply entry is already in the respective log.
     * @param log The list to check for an existing entry.
     * @param initId The SSB ID to search for in an entry.
     * @param queryId The ID of the query. Used to differentiate multiple people asking for the
     * same person or the same person asking again after some time has passed.
     * @returns True if a query was already received with the same parameters, false if it is new.
     */
    private fun checkLog(log: LinkedList<Query>, initId: String, queryId: Int): Boolean {
        val reply = Query(initId, queryId)
        for (`object` in log.toTypedArray()) { // TODO Are the backticks required?
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
     * Notifies the user of the result of the query via toast.
     * Makes sure that each query produces only one reply, except if a reply comes in after the
     * timer has expired.
     * @param targetName The shortname used in the query. Used to make sure only one notification
     * reaches the user.
     * @param text The text that will be displayed on the toast.
     * @param force True if the user should be notified no matter whether there already was a
     * notification, for example if a contact was added, to make sure the user is notified.
     */
    private fun notify(targetName: String, text: String, force: Boolean) {
        closeQuery(targetName)
        // If force is true or the query is still in sentQuery and its value is false, sends the
        // toast and sets the sentQuery value to true. Otherwise just removes the sentQuery entry.
        if (java.lang.Boolean.FALSE == sentQuery.remove(targetName) || force) { // TODO Use false.
            (context as MainActivity).runOnUiThread {
                Toast.makeText(
                    context,
                    text,
                    Toast.LENGTH_LONG
                ).show()
            }
            sentQuery[targetName] = true
        }
    }

    /**
     * Creates the payload of the query, a stringified JSON object.
     * This calls the bigger createMessage function with the appropriate parameters.
     * @param targetName The queried shortname that will be looked up.
     * @return The string representing the JSON object.
     */
    private fun createMessage(targetName: String): String {
        val selfMultiServerAddress =
            "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toRef()
        val hopCount = 4
        return createMessage(
            targetName, selfMultiServerAddress, queryIdentifier++, hopCount, null
        )
    }

    /**
     * Creates the payload of the query, a stringified JSON object.
     * @param targetName The queried shortname that will be looked up.
     * @param multiServerAddress The multi server address of the query initiator.
     * @param queryId An ID to uniquely identify the query.
     * @param hopCount The decrementing number of hops as a time-to-live.
     * @param sig The signature of the message. Usually null or an empty ByteArray when given to
     * this function.
     * @return The string representing the JSON object.
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
                // FIXME This function never receives a valid signature and thus always produces its
                //  own. This will lead to any forwarded message being discarded whenever it is
                //  received by another peer, since it checks the message signature against the
                //  original senders public key. Thus no message will hop more than once.
                // TODO If the above assessment should turn out to be incorrect:
                //  Why would you sign every message you received yourself? Would it not be
                //  smarter to pass along the original signature? This way, no fake queries in the
                //  name of other people can be passed along. The current implementation allows
                //  making queries based on other people's identities, marking them as the origin of
                //  a possible denial-of-service attack. Another way would be to chain signatures on
                //  top of one another. Further consideration is required.
                signature =
                    ed25519KeyPair.sign(message.toString().toByteArray(StandardCharsets.UTF_8))
            }
            // TODO Why is this dependent on a version? Skipping this instruction will break the
            //  protocol, since messages with invalid or missing signatures are discarded.
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
     * Add a new contact in backend's database in case of a successful look up and also sends it to
     * the frontend.
     * @param targetId The SSB ID (public key) of the target.
     * @param targetShortName The shortname of the target.
     */
    private fun addNewContact(targetId: String, targetShortName: String) {
        // Add contact to tremolaState and do all other required actions.
        tremolaState.addContact(targetId, null)
        val rawStr = tremolaState.msgTypes.mkFollow(targetId, false)
        val event = tremolaState.msgTypes.jsonToLogEntry(
            rawStr,
            rawStr.toByteArray(StandardCharsets.UTF_8)
        )!!
        // Sends the entry to peers and to frontend.
        tremolaState.wai.rxEvent(event)
        // Inform online peers via EBT (Epidemic Broadcast Tree).
        tremolaState.peers.newContact(targetId)
        val eval = "b2f_new_contact_lookup('$targetShortName','$targetId')"
        tremolaState.wai.eval(eval)
    }

    /**
     * Search in the database if the [targetShortName] is known.
     * @param targetShortName The shortname of the target.
     * @return The public key if found, otherwise null.
     */
    private fun searchDataBase(targetShortName: String): String? {
        if (keyIsTarget(targetShortName, ed25519KeyPair.toRef())) { // You yourself are the target.
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
     * @param initId The message author's public key and SSB ID.
     * @param message The message to be verified.
     * @param sig The signature, encoded in Base64.
     * @return True if the signature is incorrect, false if it is valid.
     */
    private fun signatureIsWrong(initId: String, message: JSONObject, sig: String): Boolean {
        val verifyKey = initId.substring(1, initId.length - 8) // Remove unnecessary part.
        // TODO Why is this dependent on a version? This will disable the protocol on all versions
        //  below the desired one since it will reject all messages.
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
     * Send a reply to the initiator in case of a successful look up.
     * @param initId The initiator's public key.
     * @param queryId The query identifier.
     * @param targetShortName The target's shortname.
     * @param hopCount The final hop count.
     * @param targetId The target's public key.
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
            val signature =
                ed25519KeyPair.sign(reply.toString().toByteArray(StandardCharsets.UTF_8))
            // TODO Why is this dependent on a version? Skipping this instruction will break the
            //  protocol, since messages with invalid or missing signatures are discarded.
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
     * Compares the hash of a public key with a short name. If they match, the public key generated
     * the provided short name.
     * @param receivedShortName The 11 character (including a '-') shortname.
     * @param publicKey The public key in the form "@[...].ed25519".
     * @return True if the two match in the database, false if they do not.
     */
    private fun keyIsTarget(receivedShortName: String, publicKey: String): Boolean {
        val computedShortName = id2(publicKey)
        return receivedShortName == computedShortName
    }

    companion object {
        /** 5000ms delay until lookup request is considered unanswered */
        const val DELAY = 5000L

        /**
         * The current number which serves as the queryID for look ups. Is incremented with each
         * use.
         * TODO Use random, non-repeating numbers instead to decrease leaked information.
         * */
        private var queryIdentifier = 0
    }
}