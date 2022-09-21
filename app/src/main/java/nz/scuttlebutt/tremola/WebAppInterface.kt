package nz.scuttlebutt.tremola

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.ssb.db.entities.Pub
import nz.scuttlebutt.tremola.ssb.peering.RpcInitiator
import nz.scuttlebutt.tremola.ssb.peering.RpcServices
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.id2
import nz.scuttlebutt.tremola.utils.getBroadcastAddress
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors


// pt 3 in
// https://betterprogramming.pub/5-android-webview-secrets-you-probably-didnt-know-b23f8a8b5a0c

// TODO This file needs to be thoroughly checked on whether arbitrary code execution can be achieved
//  due to the fact that eval() statements are called with variables in them which could contain
//  malicious user input.

/**
 * This class is the interface to the frontend and is called from it. This is only a one way
 * connection, it cannot return anything when called. This is asynchronous. Functions in the backend
 * might call functions in the frontend via the eval() function to influence the frontend state.
 * [act] is MainActivity, it references the [tremolaState], which the WebAppInterface is a field of,
 * and the [webView], which is the app's element displaying the frontend, which calls these
 * functions.
 */
class WebAppInterface(
    private val act: Activity,
    val tremolaState: TremolaState,
    private val webView: WebView
) {

    /**
     * Receives commands from the frontend and affects the backend. These functions are called from
     * the frontend but cannot return anything. They can, however, affect the frontend state by
     * calling eval() statements. For detailed information, see the separate cases for the input
     * strings [s] in the code.
     * Please be very careful when using variables in the eval() statements, as unsanitized user
     * input might lead to arbitrary code execution in the frontend.
     * Note that there is no counterpart to this method in webView, {@see WebAppInterface::eval}
     * While AndroidStudio might not recognize it, this function is indeed used, so do not delete.
     * @param s The command string it received from the frontend.
     */
    @JavascriptInterface
    fun onFrontendRequest(s: String) {
        // Handle the data captured from webView.
        Log.d("FrontendRequest", s)
        val args = s.split(" ")

        // Execute different code based on what the first word of s is.
        when (args[0]) {

            // When 'back' is pressed while in the chats scenario, will close the app.
            "onBackPressed" -> {
                (act as MainActivity).trueBackPress()
            }

            // Initialisation is complete in the frontend, send localID to frontend.
            "ready" -> {
                eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
            }

            // The user decided to reset the data in the frontend, but keep their ID.
            "reset" -> {
                // TODO Erase DB content of backend. Update the documentation of menu_reset() in
                //  tremola.js once it actually deletes the backend.
                eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
            }

            // Resend all the log of private messages.
            "restream" -> {
                for (logEntry in tremolaState.logDAO.getAllAsList())
                    if (logEntry.pri != null) // Only private chat messages.
                        sendEventToFrontend(logEntry)
            }

            // Start scanning the QR code (opens the camera).
            "qrscan.init" -> {
                val intentIntegrator = IntentIntegrator(act)
                intentIntegrator.setBeepEnabled(false)
                intentIntegrator.setCameraId(0)
                intentIntegrator.setPrompt("SCAN")
                intentIntegrator.setBarcodeImageEnabled(false)
                intentIntegrator.initiateScan()
                return
            }

            // Import another ID.
            // TODO This feature is currently neither working nor used. Implement it and update the
            //  frontend to reflect this change.
            "secret:" -> {
                // The argument is the secret id.
                if (importIdentity(args[1])) {
                    tremolaState.logDAO.wipe()
                    tremolaState.contactDAO.wipe()
                    tremolaState.pubDAO.wipe()
                    act.finishAffinity()
                }
                return
            }

            // Show the secret key (both as string and QR code, also copied to clipboard).
            "exportSecret" -> {
                val json = tremolaState.idStore.identity.toExportString()!!
                eval("b2f_showSecret('${json}');")
                val clipboard = tremolaState.context.getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("simple text", json)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    act, "Secret key was also\ncopied to clipboard.",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Add a peer to a pub.
            // TODO This feature is currently neither working nor used. Implement it and update the
            //  frontend to reflect this change.
            "sync" -> {
                // The argument is the pub's address.
                addPub(args[1])
                return
            }

            // Delete all data about the peer, including ID (not revertible).
            // TODO Make this forensically strong. Right now, the database might not guarantee any
            //  kind of resistance against forensic analysis. Overwriting every value multiple times
            //  with random values would be the way to go. If you do so, also update the frontend's
            //  documentation to reflect this change.
            "wipe" -> {
                tremolaState.logDAO.wipe()
                tremolaState.contactDAO.wipe()
                tremolaState.pubDAO.wipe()
                tremolaState.idStore.setNewIdentity(null) // creates new identity
                // eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
                // FIXME: Should kill all active connections and if possible the app itself
                //  afterwards.
                act.finishAffinity()
            }

            // Adds a new contact.
            // Only store the contact in database and advertise it to connected peers via SSB event.
            // The peering with the new contact is automatically done by
            // /ssb/peering/PeeringPool::add, which is called in /ssb/TremolaState::init by a fixed
            // rate scheduled procedure.
            "add:contact" -> {
                // The arguments are: ID and alias (which is encoded).
                tremolaState.addContact(
                    args[1],
                    Base64.decode(args[2], Base64.NO_WRAP).decodeToString()
                )
                val rawStr = tremolaState.msgTypes.mkFollow(args[1])
                val event = tremolaState.msgTypes.jsonToLogEntry(
                    rawStr,
                    rawStr.encodeToByteArray()
                )
                event?.let {
                    rxEvent(it) // Persist it, propagate horizontally and also up.
                    tremolaState.peers.newContact(args[1]) // Inform online peers via EBT.
                }
                return
            }

            // Post a private chat message.
            "priv:post" -> {
                // The arguments are: atob(text) recipient1 recipient2 ...
                val rawStr = tremolaState.msgTypes.mkPost(
                    Base64.decode(args[1], Base64.NO_WRAP).decodeToString(),
                    args.slice(2..args.lastIndex)
                )
                val event = tremolaState.msgTypes.jsonToLogEntry(
                    rawStr,
                    rawStr.encodeToByteArray()
                )
                event?.let { rxEvent(it) } // Persist it, propagate horizontally and also up.
                return
            }

            // Compute the shortname from the public key.
            "priv:hash" -> {
                // The argument is the name of the method to call with the result of the hash.
                val shortname = id2(args[1])
                Log.e("SHORT", shortname + ": " + args[1] + " and " + args[2])
                eval("${args[2]}('" + shortname + "', '" + args[1] + "')")
            }

            // Join a pub with an invite code.
            // FIXME When used with an arbitrary correct invite code, nothing happens in the
            //  frontend.
            "invite:redeem" -> {
                // The argument is the invite code.
                try {
                    val invitation = args[1].split("~") // [pub_mark, invite_code]
                    val id = invitation[0].split(":") // [IP_address, port, pub_SSB_ID]
                    val remoteKey = Base64.decode(
                        id[2].slice(1..id[2].length - 8),
                        Base64.NO_WRAP
                    )
                    val seed = Base64.decode(invitation[1], Base64.NO_WRAP) // invite_code
                    val rpcStream = RpcInitiator(tremolaState, remoteKey)
                    val ex = Executors.newSingleThreadExecutor() // One thread per peer.
                    ex?.execute {
                        rpcStream.defineServices(RpcServices(tremolaState))
                        rpcStream.startPeering(id[0], id[1].toInt(), seed)
                    }
                    Toast.makeText(
                        act, "Pub is being contacted...",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        act, "Problem parsing invite code.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Start a lookup.
            "look_up" -> {
                // The argument is the shortname string.
                val shortname = args[1]
                try {
                    getBroadcastAddress(act).hostAddress
                    val lookup = (act as MainActivity).lookup
                    val send = lookup!!.prepareQuery(shortname)
                    if (send != null)
                        lookup.sendQuery(send)
                    else
                        Log.d("LOOKUP", "$shortname is already in contacts.")
                } catch (e: IOException) {
                    Log.e("BROADCAST", "Failed to obtain broadcast address.")
                } catch (e: Exception) {
                    Log.e("BROADCAST", e.stackTraceToString())
                }
            }
            else -> {
                Log.d("onFrontendRequest", "Unknown command string.")
            }
        }
        /*
        if (s == "btn:chats") {
            select(listOf("chats","contacts","profile"))
        }
        if (s == "btn:contacts") {
            select(listOf("contacts","chats","profile"))
        }
        if (s == "btn:profile") {
            select(listOf("profile","contacts","chats"))
        }
        */
    }

    /**
     * Indirectly but automatically calls any method in the frontend.
     * It evaluates the string [js] as JavaScript code.
     * Note that the args must be inside single quotes (') : <br>
     * eval("b2f_local_peer('" + arg + "', 'someText')") <br>
     * OR <br>
     * eval("b2f_local_peer('${arg}', 'someText')")
     * @param js The JavaScript code as string to be evaluated.
     */
    fun eval(js: String) {
        // Send JS string to webkit frontend for execution.
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Takes the [secret] key of a SSB identity and uses it as your own.
     * Only called (but commented out) from tremola.js::menu_import_id, which is never called
     * (menu item leading to it is commented out).
     * TODO Make this work and update the documentation and code to reflect this (do not forget the
     *  frontend).
     * @param secret The private key of the SSB identity encoded as a string in the usual format.
     * @return True if it worked, false if something failed.
     */
    private fun importIdentity(secret: String): Boolean {
        Log.d("D/importIdentity", secret)
        if (tremolaState.idStore.setNewIdentity(Base64.decode(secret, Base64.DEFAULT))) {
            // TODO: Remove all decrypted content in the database, try to decode new one.
            Toast.makeText(
                act, "Imported of ID worked. You must restart the app.",
                Toast.LENGTH_SHORT
            ).show()
            return true
        }
        Toast.makeText(act, "Import of new ID failed.", Toast.LENGTH_LONG).show()
        return false
    }

    /**
     * Adds a pub given the pub's address as [pubString].
     * TODO This feature is currently neither working nor used. Implement it and update the
     *  frontend to reflect this change.
     * @param pubString Contains the address of the pub.
     */
    private fun addPub(pubString: String) {
        Log.d("D/addPub", pubString)
        val components = pubString.split(":")
        tremolaState.addPub(
            Pub(
                lid = "@" + components[3] + ".ed25519",
                host = components[1],
                port = components[2].split('~')[0].toInt()
            )
        )
    }

    /**
     * Persists the LogEntry [entry], propagates it horizontally to peers and sends it to the
     * frontend.
     * @param entry The LogEntry that was just created and should be processed.
     */
    fun rxEvent(entry: LogEntry) {
        // When we come here we assume that the event is legit (chaining and signature).
        tremolaState.addLogEntry(entry) // Persist the log entry.
        sendEventToFrontend(entry) // Notify the local app.
        tremolaState.peers.newLogEntry(entry) // Stream it to peers we are currently connected to.
    }

    /**
     * This takes a LogEntry [event] and passes it to the frontend via an eval statement with the
     * appropriately formatted JSON string.
     * @param event The LogEntry that was just created and should go to the frontend.
     */
    private fun sendEventToFrontend(event: LogEntry) {
        // Log.d("MSG added", event.ref.toString())
        val hdr = JSONObject()
        hdr.put("ref", event.hid)
        hdr.put("fid", event.lid)
        hdr.put("seq", event.lsq)
        hdr.put("pre", event.pre)
        hdr.put("tst", event.tst)
        var cmd = "b2f_new_event({header:$hdr,"
        cmd += "public:" + (if (event.pub == null) "null" else event.pub) + ","
        cmd += "confid:" + (if (event.pri == null) "null" else event.pri)
        cmd += "});"
        Log.d("CMD", cmd)
        eval(cmd)
    }
}