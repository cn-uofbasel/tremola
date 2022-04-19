package nz.scuttlebutt.tremola.ssb.peering

import android.util.Base64
import android.util.Log
import nz.scuttlebutt.tremola.ssb.TremolaState

import nz.scuttlebutt.tremola.ssb.db.entities.Contact
import nz.scuttlebutt.tremola.ssb.peering.boxstream.SHSServer
import nz.scuttlebutt.tremola.utils.Constants
import java.net.Socket

/**
 * Manages handshake between two peers.
 * It will keep a connection only if the remote peer is already in the contact list.
 * RpcInitiator is used for the first connection.
 */
class RpcResponder(
    val tremolaState: TremolaState,
    val sock: Socket,
    networkIdentifier: ByteArray = Constants.SSB_NETWORKIDENTIFIER,
) : RpcLoop() {
    // val fidStr = tremolaState.idStore.identity.toRef()
    // val me = tremolaState.idStore.identity

    init {
        this.socket = sock
        shs = SHSServer(tremolaState.idStore.identity, networkIdentifier)
    }

    fun startStreaming() {
        instr = socket!!.getInputStream()
        outstr = socket!!.getOutputStream()
        val remote = socket!!.remoteSocketAddress.toString()

        peerMark = null
        peerFid = "??"
        try {
            boxStream = (shs!! as SHSServer).performHandshake(instr!!, outstr!!)
            peerFid = "@" + Base64.encodeToString(shs!!.remoteKey, Base64.NO_WRAP) + ".ed25519"
            // check that we trust the peer
            if (tremolaState.contactDAO.getContactByLid(peerFid!!) != null) {
                peerMark = "net:${socket!!.remoteSocketAddress.toString().substring(1)}~shs:"
                peerMark += peerFid!!.substring(1, peerFid!!.length - 8)
                Log.d("respondPeering", "SHS ok, initiator is ${peerMark} at ${remote}")
                tremolaState.wai.eval("b2f_local_peer('" + peerMark + "', 'connected')")
                tremolaState.contactDAO.insertContact(
                    Contact(
                        peerFid!!, null, false, null,
                        0, 0, null
                    )
                )
                // this.host = remote.split('/')[1].split(':')[0]
                tremolaState.peers.addToActive(this)
                rx_loop()
            } else { // untrusted, close the connection
                Log.d("SHS", "failed or connection refused for ${peerFid} (not in contacts)")
            }
        } catch (e: Exception) {
            tremolaState.peers.removeFromActive(this)
            Log.d("respondPeering", "for ${remote} ended with ${e}")
        }
        socket?.close()
        if (peerMark != null)
            tremolaState.wai.eval("b2f_local_peer('" + peerMark + "', 'disconnected')")
        else
            Log.d("server", "why is peerMark null?")
    }
}
