package nz.scuttlebutt.tremola.ssb.peering

import android.util.Log
import org.json.JSONObject

import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.ssb.peering.rpc.RPCMessage
import nz.scuttlebutt.tremola.ssb.peering.rpc.RPCRequest
import nz.scuttlebutt.tremola.ssb.peering.rpc.RPCserialization
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.utf8

/*
data class RpcHistoryTrain(val reqNr: Int,
                           val lid: String,
                           var from_seq: Int,
                           var limit: Int,
                           val envelope: Boolean)
*/

class RpcServices(val tremolaState: TremolaState) {
    // val trains: MutableList<RpcHistoryTrain> = arrayListOf()
    var rpcLoop: RpcLoop? = null // will be set by the stream using this obj
    var ebtRpcNr: Int = 0 // 0=desactivated, positive=we initiated, negative=we responded

    fun rx_rpc(msg: RPCMessage) {
        // Log.d("RPC MSG raw", msg.rawEvent.toHex())
        val bodyString = msg.rawEvent.utf8()
        Log.d("RPC MSG ${msg.requestNumber}", bodyString)
        when {
            msg.endError -> { // closing
                if (ebtRpcNr != 0 && msg.requestNumber == -ebtRpcNr)
                    ebtRpcNr = 0
                Log.d("ENDERROR ${msg.requestNumber}", bodyString)
            }
            msg.requestNumber > 0 -> {
                if (ebtRpcNr < 0 && msg.requestNumber == -ebtRpcNr)
                    handleEBTmsg(ebtRpcNr, bodyString, msg.rawEvent)
                else
                    handleRequest(msg.requestNumber, bodyString, msg.rawEvent)
            }
            else -> { // a message or blob due to a CHS request ?
                if (ebtRpcNr > 0 && msg.requestNumber == -ebtRpcNr)
                    handleEBTmsg(ebtRpcNr, bodyString, msg.rawEvent)
                else {
                    val evnt =
                        tremolaState.msgTypes.jsonToLogEntry(bodyString, msg.rawEvent)
                    if (evnt != null) // parsing and signature checked passed
                        handleNewLogEntry(evnt)
                }
            }
        }
    }

    fun handleRequest(rpcNr: Int, bodyString: String, rawEvent: ByteArray) {
        /* bodystring can be:
           {"name":["ebt","replicate"],"args":[{"version":3}],"type":"duplex"}
           {"name":["gossip","ping"],"args":[{"timeout":300000}],"type":"duplex"}
           {"name":["room","metadata"],"args":[]}
           {"name":["blobs","createWants"],"args":[],"type":"source"}
           {"name":["blobs","createWants"],"args":[],"type":"source"}
           {"name":["createHistoryStream"],"type": "source",
            "args": [{
                "id": "@...=.ed25519",
                "sequence": 2,
                "limit": 1,
                "live": false,
                "old": true,
                "keys": true
                }]
            }
        */
        val request: JSONObject?
        Log.d("handleRequest", "nr=${rpcNr}, body=${bodyString}")
        try {
            request = JSONObject(bodyString) // sonParser.parseString(bodyString).asJsonObject
        } catch (e: Exception) {
            Log.d("RPC parsing problem", bodyString + " / " + e.toString())
            return
        }
        Log.d("new rpc", bodyString)
        val name = request.getJSONArray("name")
        val args = request.getJSONArray("args")
        when (name.getString(0)) {
            RPCRequest.CREATE_HISTORY_STREAM -> {
                val arg = args.getJSONObject(0)
                val fid = arg.getString("id")
                Log.d("REQ CREATEHISTSTREAM", "" + rpcNr + " " + fid)
                var seq = if (arg.has("sequence")) arg.getInt("sequence") else 1
                var lim = if (arg.has("limit"))    arg.getInt("limit") else -1
                val liv = if (arg.has("live"))     arg.getBoolean("live") else false
                while (true) {
                    Log.d("CHS loop", fid + ":" + seq)
                    val e = tremolaState.logDAO.getEventByLogIdAndSeq(fid, seq)
                    if (e == null)
                        break
                    val response = RPCMessage(
                        true,
                        false,
                        RPCserialization.Companion.RPCBodyType.JSON,
                        e.raw.size,
                        -rpcNr,
                        e.raw
                    )
                    rpcLoop?.tx(response)
                    if (lim > 0) {
                        if (--lim == 0)
                            break
                    }
                    seq += 1
                    e.raw
                }
                endStream(rpcNr)
                //  trains.add(RpcHistoryTrain(rpcNr, fid, seq, if (lim > 0) seq+lim else -1))
                // if (fid.toString() != idStore.getIdentity().asString())
                //    endStream(rpcNr)
                return
            }
            RPCRequest.EBT -> {
                if (ebtRpcNr > 0) { // already have a duplex EBT stream
                    endStream(rpcNr)
                    return
                }
                ebtRpcNr = -rpcNr
                for (c in tremolaState.contactDAO.getAll()) {
                    val latest = tremolaState.logDAO.getMostRecentEventFromLogId(c.lid)
                    sendEBTnote(ebtRpcNr, c.lid, if (latest == null) 0 else latest.lsq)
                }
                return
            }
            RPCRequest.CREATE_USER_STREAM -> { // ?
            }
            RPCRequest.BLOBS -> {
                when (name.getString(1) /* msg.name[1] */) {
                    RPCRequest.GET -> {

                    }
                    RPCRequest.GET_SLICE -> {

                    }
                    RPCRequest.HAS -> {

                    }
                    RPCRequest.CHANGES -> {

                    }
                    RPCRequest.CREATE_WANTS -> {
                        respondWithEmpty(rpcNr)
                    }
                }
            }
            else -> {
                endStream(rpcNr)
            }
        }
    }

    private fun handleNewLogEntry(evnt: LogEntry) { // on reception
        if (evnt.pre != null) { // verify chaining
            val latest = tremolaState.logDAO.getMostRecentEventFromLogId(evnt.lid)
            if (latest == null) {
                Log.d("handleNewLogEvent", "No chain to extend for ${evnt.lid}")
                return
            }
            if (latest.lsq+1 != evnt.lsq || latest.hid != evnt.pre) {
                Log.d("handleNewLogEntry",
                    "invalid chain ${evnt.lid} - ${evnt.pre}/${evnt.lsq} vs ${latest. hid}/${latest. lsq}")
                return
            }
        }
        Log.d("newLogEntry", "valid ${rpcLoop?.peerFid}")
        // update knownFrontier for peer we receive this note from
        tremolaState.peers.updateKnownFrontier(rpcLoop?.peerFid!!, evnt.lid, evnt.lsq)

        // only pass on to application layer if new?
        // if (evnt.pri == null) return // only interested in private chat msgs ?
        // logDAO.add(evnt) is done in the rx_event() function
        tremolaState.wai.rx_event(evnt)
    }

    fun handleEBTmsg(rpcNr: Int, bodyString: String, rawEvent: ByteArray) {
        Log.d("incoming EBTmsg", bodyString)
        val map = JSONObject(bodyString)
        if (map.has("author")) {
            // FIXME: should avoid JSON parsing a second time..
            val evnt = tremolaState.msgTypes.jsonToLogEntry(bodyString, rawEvent)
            if (evnt != null) { // parsing and signature checked passed
                Log.d("handle", "it's a log entry - ${bodyString}")
                handleNewLogEntry(evnt)
            }
            return
        }
        for (k in map.keys()) {
            if (tremolaState.contactDAO.getContactByLid(k) == null) {
                // we should tell peer that we are not interested
                continue
            }
            var i = map.getInt(k)
            Log.d("frontier stmt ${rpcNr}", "${k} / ${i}")
            if (i == -1) { // we should remember which feeds are not of interest to peer
                continue
            }
            if (i >= 0 && (i % 2) == 0) { // it's a request for streaming
                i = i shr 1 // extract highest seq no
                tremolaState.peers.updateKnownFrontier(rpcLoop?.peerFid!!, k, i)
                val latest = tremolaState.logDAO.getMostRecentEventFromLogId(k)
                if (latest != null && latest.lsq > i) {
                    Log.d("yes, can provide", "${latest.lsq}")
                    for (j in 0..31) { // limit to some number, because lack of backpressure
                        i++
                        val evnt = tremolaState.logDAO.getEventByLogIdAndSeq(k, i)
                        if (evnt == null)
                            break
                        Log.d("frontier serving ${rpcNr}", "${k} / ${evnt.lsq}")
                        sendLogEntry(rpcNr, evnt, false)
                    }
                }
            }
        }
    }

    private fun respondWithEmpty(requestNumber: Int) {
        val payload = "{}".toByteArray()
        val response = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            payload.size,
            -requestNumber,
            payload
        )
        rpcLoop?.tx(response)
    }

    fun endStream(requestNumber: Int) {
        // RPCProtocol.goodbye(requestNumber)
        val payload = "true".toByteArray()
        val response = RPCMessage(
            true,
            true,
            RPCserialization.Companion.RPCBodyType.JSON,
            payload.size,
            -requestNumber,
            payload
        )
        // peerConnection.writeQueue.add(response)
        rpcLoop?.tx(response)
    }

    fun sendEBTnote(rpcNr: Int, fid: String, seqNr: Int) {
        val note = JSONObject()
        note.put(fid, seqNr * 2) // subscribe
        val body = note.toString().encodeToByteArray()
        val rpcMessage = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            body.size,
            rpcNr,
            body
        )
        rpcLoop?.tx(rpcMessage)
    }

    fun sendLogEntry(rpcNr: Int, evnt: LogEntry, envelope: Boolean) {
        Log.d("sendLogEntry", "${evnt.lid}/${evnt.lsq}")
        val eTree = JSONObject(evnt.raw.decodeToString())
        var body = evnt.raw
        if (eTree.has("value"))
            body = eTree.getString("value").encodeToByteArray()
        val rpcMessage = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            body.size,
            rpcNr,
            body
        )
        rpcLoop?.tx(rpcMessage)
    }

}