package nz.scuttlebutt.tremola.ssb.peering

import android.telephony.mbms.StreamingServiceCallback
import android.util.Log
import java.io.OutputStream
import java.io.InputStream
import java.io.Closeable
import java.io.IOException
import java.net.Socket

import nz.scuttlebutt.tremola.ssb.db.entities.Contact
import nz.scuttlebutt.tremola.ssb.peering.rpc.RPCMessage
import nz.scuttlebutt.tremola.ssb.peering.rpc.RPCserialization
import nz.scuttlebutt.tremola.ssb.peering.boxstream.BoxStream
import nz.scuttlebutt.tremola.ssb.peering.boxstream.SHS
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.utf8
import org.json.JSONObject
import java.util.concurrent.Executor

abstract class RpcLoop ()
{
    var socket: Socket? = null
    var ostr: OutputStream? = null
    var istr: InputStream? = null
    var shs: SHS? = null
    var boxStream: BoxStream? = null
    var myRequestCount = 0
    var rpcService: RpcServices? = null
    var ex: Executor? = null
    var peerFid: String? = null
    var peerMark: String? = null
    // var host = ""

    fun defineServices(srvc: RpcServices?) { // mutually link the two objects
        rpcService = srvc
        srvc!!.rpcLoop = this
    }

    fun tx(msg: RPCMessage) {
        Log.d("TX", "nr=${msg.requestNumber}, body=${msg.rawEvent.utf8()}")
        val raw = RPCserialization.toByteArray(msg)
        tx(raw)
    }
    fun tx(buf: ByteArray) {
        val boxStreamEncoded = boxStream!!.encryptForServer(buf)
        ostr!!.write(boxStreamEncoded)
    }

    fun rx_loop() {
        var buf = ByteArray(0)
        var len = -1 // expected length of header+body
        var seg = boxStream!!.readFromServer(istr!!) // read first segment
        while (seg != null) {
            buf += seg
            // read header
            if (len == -1 && buf.size >= RPCserialization.HEADER_SIZE) {
                val hdr = buf.sliceArray(0 until RPCserialization.HEADER_SIZE)
                len = RPCserialization.getBodyLength(hdr) + RPCserialization.HEADER_SIZE
            }
            // read and dispatch body (or bodies, if enough data is available)
            while (len != -1 && buf.size >= len) {
                val msg = RPCserialization.fromByteArray(buf.sliceArray(0 until len))
                rpcService?.rx_rpc(msg)
                buf = buf.copyOfRange(len, buf.size)
                if (buf.size >= RPCserialization.HEADER_SIZE) {
                    val hdr = buf.sliceArray(0 until RPCserialization.HEADER_SIZE)
                    len = RPCserialization.getBodyLength(hdr) + RPCserialization.HEADER_SIZE
                } else
                    len = -1
            }
            seg = boxStream!!.readFromServer(istr!!) // read more segments
        }
        Log.d("rd loop", "decoded was null")
    }

    fun sendWantsRequest() {
        socket?.run {
            if (isConnected && shs!!.completed) {
                val s = "{\"name\":[\"blobs\",\"createWants\"],\"args\":[],\"type\":\"source\"}"
                val body = JSONObject(s).toString().encodeToByteArray()
                val rpcMessage = RPCMessage(
                    true,
                    false,
                    RPCserialization.Companion.RPCBodyType.JSON,
                    body.size,
                    ++myRequestCount,
                    body
                )
                tx(rpcMessage) // writeQueue.add(rpcMessage)
            }
        }
    }

    fun sendHistoryRequest(lidStr: String, seq: Int) {
        val args = "{\"id\":\"${lidStr}\",\"sequence\":${seq},\"limit\":10,\"live\":false}"
        /*
                "      \"limit\": 1,\n" +
                "      \"live\": false,\n" +
                "      \"old\": true,\n" +
                "      \"keys\": true\n"
                            val limit: Int? = 10,
            val live: Boolean? = false,
            val keys: Boolean? = true

        */
        val s = "{\"name\":[\"createHistoryStream\"],\"args\":${args},\"type\":\"source\"}"
        val body = JSONObject(s).toString().encodeToByteArray()

        val rpcMessage = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            body.size,
            ++myRequestCount,
            body
        )
        tx(rpcMessage) //writeQueue.add()
    }

    fun requestHistories(contactList: List<Contact>) {
        for (c in contactList) {
            // fetch highest seq number
            sendHistoryRequest(c.lid.toString(), 1)
        }
    }

    fun openEBTstream() {
        val s = "{\"name\":[\"ebt\",\"replicate\"],\"args\":[{\"version\":3}],\"type\":\"duplex\"}"
        val body = JSONObject(s).toString().encodeToByteArray()
        val rpcMessage = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            body.size,
            ++myRequestCount,
            body
        )
        rpcService?.ebtRpcNr = myRequestCount
        tx(rpcMessage) //writeQueue.add()
    }

    fun sendInvite(me: String) {
        val s = "{\"name\":[\"invite\",\"use\"],\"args\":[{\"feed\":"+ me +"}],\"type\":\"async\"}"
        val body = JSONObject(s).toString().encodeToByteArray()
        val rpcMessage = RPCMessage(
            true,
            false,
            RPCserialization.Companion.RPCBodyType.JSON,
            body.size,
            ++myRequestCount,
            body
        )
        rpcService?.ebtRpcNr = myRequestCount
        tx(rpcMessage) //writeQueue.add()
    }
    fun close() {
        socket?.run {
            closeQuietly(this)
        }
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            Log.d("SOCKET", "closing")
            closeable.close()
        } catch (ignored: IOException) {
        }
    }

}
