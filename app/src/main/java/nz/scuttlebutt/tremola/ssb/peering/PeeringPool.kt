package nz.scuttlebutt.tremola.ssb.peering

import android.util.Log
import nz.scuttlebutt.tremola.ssb.TremolaState
import kotlin.collections.ArrayList

import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.deRef
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

/**
 * The list of all the known peers, both active and known (active or not)
 */
class PeeringPool(val tremolaState: TremolaState) {
    val activePeers: MutableList<RpcLoop> = ArrayList()
    private val knownRemoteFrontier: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private val lck = ReentrantLock()

    // @SuppressLint("CheckResult")
    /**
     * Make contact with a peer if he is in the database.
     * @param host: the remote IP address
     * @param port: the remote IP port
     * @param remoteFid: the other peer's feed id, i.e. its public key
     */
    fun add(host: String, port: Int, remoteFid: String) {
        // only establish connection to trusted devices
        if (tremolaState.contactDAO.getContactByLid(remoteFid) == null) return
        // only initiate if not already connected
        for (peer in activePeers) if (peer.peerFid == remoteFid) return
        val remoteKey = remoteFid.deRef() // Base64.decode(remoteFid.slice(1..remoteFid.lastIndex-8), Base64.NO_WRAP)
        val rpcStream = RpcInitiator(tremolaState, remoteKey) // null, Constants.SSB_NETWORKIDENTIFIER)
        addToActive(rpcStream)
        val ex = Executors.newSingleThreadExecutor() // one thread per peer
        ex?.execute {
            rpcStream.defineServices(RpcServices(tremolaState))
            rpcStream.startPeering(host, port)
            removeFromActive(rpcStream)
        }
    }

    /**
     * Because peer may have limited the # of entries sent to us.
     * (see handleEBTmsg())
     */
    fun kick() {
        val contacts = tremolaState.contactDAO.getAll()
        for (peer in activePeers) {
            Log.d("(Re-)trigger EBT", "for ${peer.peerFid}")
            for (contact in contacts) {
                val latest = tremolaState.logDAO.getMostRecentEventFromLogId(contact.lid)
                // Log.d("getMostRecent", "${lid} at ${latest?.lid}/${latest?.lsq}")
                peer.rpcService?.sendEBTnote(
                    peer.rpcService!!.ebtRpcNr,
                    contact.lid,
                    if (latest == null) 0 else latest.lsq
                )
            }
        }
        /*
        for (ongoing in activePeers) {
            ongoing.requestHistories(peers)
        }
        */
    }

    fun newLogEntry(evnt: LogEntry) {
        Log.d("peers.propagate", "do it for ${activePeers.size} active peers")
        for (peer in activePeers) {
            Log.d("peers.propagate", "consider ${peer.peerMark} ${peer.rpcService?.ebtRpcNr}")
            if (peer.peerFid in knownRemoteFrontier) {
                val fmap = knownRemoteFrontier.get(peer.peerFid)!!
                Log.d("peers.propagate", "found map ${fmap}, ${evnt.lid in fmap}")
                if (evnt.lid in fmap && fmap.get(evnt.lid)!! < evnt.lsq) {
                    if (peer.rpcService?.ebtRpcNr != 0)
                        peer.rpcService?.sendLogEntry(peer.rpcService!!.ebtRpcNr, evnt, false)
                } else
                    Log.d("peers.propagate", "${peer.peerFid} no need: ${fmap.get(evnt.lid)} / evnt.lsq=${evnt.lsq}")
            } else
                Log.d("peers.propagate", "${peer.peerFid} is not in knownRemoteFrontier")
        }
    }

    /**
     * Add a new contact.
     * @param feedID, which is the peer SSB ID
     */
    fun newContact(feedID: String) {
        Log.d("peers.contact", "do it for ${activePeers.size} active peers")
        for (peer in activePeers) {
            Log.d("peers.contact", "consider ${peer.peerMark} ${peer.rpcService?.ebtRpcNr}")
            if (peer.rpcService?.ebtRpcNr != 0)
                peer.rpcService?.sendEBTnote(peer.rpcService!!.ebtRpcNr, feedID, 0)
        }
    }

    fun closeAll() {
        for (peer in activePeers) {
            peer.close()
        }
    }

    /**
     * Add to the list of active peers.
     * If not in the database yet, add it there
     */
    fun addToActive(rpc: RpcLoop) {
        lck.lock()
        try {
            activePeers.add(rpc)
            Log.d("addToActive", "added ${rpc.peerFid}, now ${activePeers.size} entries")
            if (rpc.peerFid !in knownRemoteFrontier)
                knownRemoteFrontier.put(rpc.peerFid!!, mutableMapOf())
        } finally {
            lck.unlock()
        }
    }

    fun removeFromActive(rpc: RpcLoop) {
        lck.lock()
        try {
            activePeers.remove(rpc)
            Log.d("addToActive", "removed, now ${activePeers.size} entries, ${this}/${activePeers}")
        } finally {
            lck.unlock()
        }
    }

    fun updateKnownFrontier(peerFid: String, fid: String, highest: Int) {
        lck.lock()
        try {
            if (!(peerFid in knownRemoteFrontier))
                knownRemoteFrontier.put(peerFid, mutableMapOf())
            val m = knownRemoteFrontier.get(peerFid)!!
            if (!(fid in m) || m.get(fid)!! < highest)
                m.put(fid, highest)
        } finally {
            lck.unlock()
        }
    }
}



