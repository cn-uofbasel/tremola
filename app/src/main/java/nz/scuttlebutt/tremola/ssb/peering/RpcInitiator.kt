package nz.scuttlebutt.tremola.ssb.peering

import android.util.Base64
import android.util.Log
import com.goterl.lazysodium.interfaces.Sign
import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.core.SSBid
import nz.scuttlebutt.tremola.ssb.db.entities.Contact
import nz.scuttlebutt.tremola.ssb.db.entities.Pub
import nz.scuttlebutt.tremola.ssb.peering.boxstream.SHSClient
import nz.scuttlebutt.tremola.utils.Constants
import java.net.Socket

/**
 * Initiate a connection (handshake) with a peer
 * Only called for a peer once. RCPResponder will be called for the next connections
 */
class RpcInitiator(
    val tremolaState: TremolaState,
    val remoteKey: ByteArray,
    val networkIdentifier: ByteArray = Constants.SSB_NETWORKIDENTIFIER
) : RpcLoop() {

    init {
        peerFid = "@" + Base64.encodeToString(remoteKey, Base64.NO_WRAP) + ".ed25519"
    }

    fun startPeering(host: String, port: Int, seed: ByteArray? = null) {
        if (socket != null && socket!!.isConnected && shs != null && shs!!.completed)
            return
        if (seed == null)
            shs = SHSClient(tremolaState.idStore.identity, remoteKey, networkIdentifier)
        else {
            Log.d("seed len should be", "${Sign.ED25519_SECRETKEYBYTES}")
            Log.d("seed len is", "${seed.size}")
            shs = SHSClient(SSBid(seed), remoteKey, networkIdentifier)
        }
        // TODO: have a separate writer thread: wrExec.execute {} ?
        Log.d("initiate socket", "${host}:${port}")
        try {
            Socket(host, port).run {
                socket = this
                outstr = this.getOutputStream() // source = source().buffer()
                instr = this.getInputStream() // sink = sink().buffer()
            }
            boxStream = (shs!! as SHSClient).performHandshake(instr!!, outstr!!)
            Log.d("initiatePeering", "worked for ${peerFid}")
            peerMark = "net:${socket!!.remoteSocketAddress.toString().substring(1)}~shs:"
            peerMark += peerFid!!.substring(1, peerFid!!.length - 8)
        } catch (e: Exception) {
            Log.d("initiatePeering", "failed for ${peerFid}")
            socket?.close()
            socket = null
            return
        }
        val peerMarkRef = peerMark
        tremolaState.wai.eval("b2f_local_peer('${peerMarkRef}', 'connected')")
        try {
            if (seed == null) {
                openEBTstream() // only initiator launches the duplex stream
                for (c in tremolaState.contactDAO.getAll()) {
                    val latest = tremolaState.logDAO.getMostRecentEventFromLogId(c.lid)
                    rpcService?.sendEBTnote(
                        rpcService!!.ebtRpcNr,
                        c.lid,
                        if (latest == null) 0 else latest.lsq
                    )
                }
                // TODO: sendWantsRequest()
                rx_loop()
            } else {
                sendInvite(tremolaState.idStore.identity.toRef())
                rpcService!!.endStream(myRequestCount)
                close()
                // if everything came so far we assume that the pub redeemed the invite code
                tremolaState.contactDAO.insertContact(
                    Contact(
                        peerFid!!, null, true,
                        null, 0, 0, null
                    )
                )
                tremolaState.pubDAO.insert(Pub(peerFid!!, host, port))
            }
            Log.d("Initiator", "ended")
        } catch (e: Exception) {
            Log.d("Initiator", "problem with ${e}")
            socket?.close()
            socket = null
        } finally {
            tremolaState.wai.eval("b2f_local_peer('" + peerMarkRef + "', 'disconnected')")
        }
    }
}
