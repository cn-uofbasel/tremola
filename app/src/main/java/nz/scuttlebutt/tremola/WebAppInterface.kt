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
import org.json.JSONObject

import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.ssb.db.entities.Pub
import nz.scuttlebutt.tremola.ssb.peering.RpcInitiator
import nz.scuttlebutt.tremola.ssb.peering.RpcServices
import nz.scuttlebutt.tremola.ssb.peering.discovery.LookUpThread
import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.utils.getBroadcastAddress
import nz.scuttlebutt.tremola.utils.getLocalIpAddress
import java.io.IOException
import java.util.concurrent.Executors


// pt 3 in https://betterprogramming.pub/5-android-webview-secrets-you-probably-didnt-know-b23f8a8b5a0c

class WebAppInterface(val act: Activity, val tremolaState: TremolaState, val webView: WebView) {

    private var lookUpThread: LookUpThread? = null;

    @JavascriptInterface
    fun onFrontendRequest(s: String) {
        //handle the data captured from webview}
        Log.d("FrontendRequest", s)
        val args = s.split(" ")
        when (args[0]) {
            "onBackPressed" -> {
                (act as MainActivity)._onBackPressed()
            }
            "ready" -> {
                eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
            }
            "reset" -> { // UI reset
                // erase DB content
                eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
            }
            "restream" -> {
                for (e in tremolaState.logDAO.getAllAsList())
                    if (e.pri != null) // only private chat msgs
                        sendEventToFrontend(e)
            }
            "qrscan.init" -> {
                val intentIntegrator = IntentIntegrator(act)
                intentIntegrator.setBeepEnabled(false)
                intentIntegrator.setCameraId(0)
                intentIntegrator.setPrompt("SCAN")
                intentIntegrator.setBarcodeImageEnabled(false)
                intentIntegrator.initiateScan()
                return
            }
            "secret:" -> {
                if (importIdentity(args[1])) {
                    tremolaState.logDAO.wipe()
                    tremolaState.contactDAO.wipe()
                    tremolaState.pubDAO.wipe()
                    act.finishAffinity()
                }
                return
            }
            "exportSecret" -> {
                val json = tremolaState.idStore.identity.toExportString()!!
                eval("b2f_showSecret('${json}');")
                val clipboard = tremolaState.context.getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newPlainText("simple text", json)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(act, "secret key was also\ncopied to clipboard",
                    Toast.LENGTH_LONG).show()
            }
            "sync" -> {
                addPub(args[1])
                return
            }
            "wipe" -> {
                tremolaState.logDAO.wipe()
                tremolaState.contactDAO.wipe()
                tremolaState.pubDAO.wipe()
                tremolaState.idStore.setNewIdentity(null) // creates new identity
                // eval("b2f_initialize(\"${tremolaState.idStore.identity.toRef()}\")")
                // FIXME: should kill all active connections, or better then the app
                act.finishAffinity()
            }
            "add:contact" -> { // ID and alias
                tremolaState.addContact(args[1],
                    Base64.decode(args[2], Base64.NO_WRAP).decodeToString())
                val rawStr = tremolaState.msgTypes.mkFollow(args[1])
                val evnt = tremolaState.msgTypes.jsonToLogEntry(rawStr,
                    rawStr.encodeToByteArray())
                evnt?.let {
                    rx_event(it) // persist it, propagate horizontally and also up
                    tremolaState.peers.newContact(args[1]) // inform online peers via EBT
                }
                    return
            }
            "priv:post" -> { // atob(text) rcp1 rcp2 ...
                val rawStr = tremolaState.msgTypes.mkPost(
                                 Base64.decode(args[1], Base64.NO_WRAP).decodeToString(),
                                 args.slice(2..args.lastIndex))
                val evnt = tremolaState.msgTypes.jsonToLogEntry(rawStr,
                                            rawStr.encodeToByteArray())
                evnt?.let { rx_event(it) } // persist it, propagate horizontally and also up
                return
            }
            "invite:redeem" -> {
                try {
                    val i = args[1].split("~")
                    val h = i[0].split(":")
                    val remoteKey = Base64.decode(h[2].slice(1..-8), Base64.NO_WRAP)
                    val seed = Base64.decode(i[1], Base64.NO_WRAP)
                    val rpcStream = RpcInitiator(tremolaState, remoteKey)
                    val ex = Executors.newSingleThreadExecutor() // one thread per peer
                    ex?.execute {
                        rpcStream.defineServices(RpcServices(tremolaState))
                        rpcStream.startPeering(h[0], h[1].toInt(), seed)
                    }
                    Toast.makeText(act, "Pub is being contacted ..",
                        Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(act, "Problem parsing invite code",
                        Toast.LENGTH_LONG).show()
                }
            }
            "look_up" -> {
                val shortname = args[1];
//                Toast.makeText(act, "I DID IT!!!: $shortname", Toast.LENGTH_SHORT).show()
                try {
                    if (lookUpThread == null) {
                        lookUpThread = LookUpThread(getLocalIpAddress(act),
                            Constants.SSB_IPV4_UDPPORT, tremolaState.idStore.identity)
                    }
                    lookUpThread!!.sendQuery(getBroadcastAddress(act).hostAddress, shortname)
                } catch (e: IOException) {
                    Log.e("BROADCAST", "Failed to obtain broadcast address")
                } catch (e: Exception) {
                    Log.e("BROADCAST", e.toString())
                }
            }
            else -> {
                Log.d("onFrontendRequest", "unknown")
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

    fun eval(js: String) { // send JS string to webkit frontend for execution
        webView.post(Runnable {
            webView.evaluateJavascript(js, null)
        })
    }

    private fun importIdentity(secret: String): Boolean {
        Log.d("D/importIdentity", secret)
        if (tremolaState.idStore.setNewIdentity(Base64.decode(secret, Base64.DEFAULT))) {
            // FIXME: remove all decrypted content in the database, try to decode new one
            Toast.makeText(act, "Imported of ID worked. You must restart the app.",
                Toast.LENGTH_SHORT).show()
            return true
        }
        Toast.makeText(act, "Import of new ID failed.", Toast.LENGTH_LONG).show()
        return false
    }

    private fun addPub(pubstring: String) {
        Log.d("D/addPub", pubstring)
        val components = pubstring.split(":")
        tremolaState.addPub(
            Pub(lid = "@" + components[3] + ".ed25519",
                host = components[1],
                port = components[2].split('~')[0].toInt())
        )
    }

    fun rx_event(entry: LogEntry) {
        // when we come here we assume that the event is legit (chaining and signature)
        tremolaState.addLogEntry(entry)       // persist the log entry
        sendEventToFrontend(entry)            // notify the local app
        tremolaState.peers.newLogEntry(entry) // stream it to peers we are currently connected to
    }

    fun sendEventToFrontend(evnt: LogEntry) {
        // Log.d("MSG added", evnt.ref.toString())
        var hdr = JSONObject()
        hdr.put("ref", evnt.hid)
        hdr.put("fid", evnt.lid)
        hdr.put("seq", evnt.lsq)
        hdr.put("pre", evnt.pre)
        hdr.put("tst", evnt.tst)
        var cmd = "b2f_new_event({header:${hdr.toString()},"
        cmd += "public:" + (if (evnt.pub == null) "null" else evnt.pub) + ","
        cmd += "confid:" + (if (evnt.pri == null) "null" else evnt.pri)
        cmd += "});"
        Log.d("CMD", cmd)
        eval(cmd)
    }

    fun acceptLookUp(incomingRequest: String) {
        if (lookUpThread == null) {
            lookUpThread = LookUpThread(
                getLocalIpAddress(act),
                Constants.SSB_IPV4_UDPPORT,
                tremolaState.idStore.identity
            )
        }
        lookUpThread?.storeIncomingLookup(incomingRequest)
        lookUpThread?.start()
    }
}