package nz.scuttlebutt.tremola.ssb.peering

import android.annotation.SuppressLint
import android.util.Log
import nz.scuttlebutt.tremola.ssb.TremolaState
import kotlin.collections.ArrayList

import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.deRef
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

class PeeringPool(val tremolaState: TremolaState) {
    val activePeers: MutableList<RpcLoop> = ArrayList()
    private val knownRemoteFrontier: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private val lck = ReentrantLock()

    // @SuppressLint("CheckResult")
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

    fun kick() { // because peer may have limited the # of entries sent to us (see handleEBTmsg())
        val contacts = tremolaState.contactDAO.getAll()
        for (p in activePeers) {
            Log.d("(Re-)trigger EBT", "for ${p.peerFid}")
            for (c in contacts) {
                val latest = tremolaState.logDAO.getMostRecentEventFromLogId(c.lid)
                // Log.d("getMostRecent", "${lid} at ${latest?.lid}/${latest?.lsq}")
                p.rpcService?.sendEBTnote(p.rpcService!!.ebtRpcNr, c.lid, if (latest == null) 0 else latest.lsq)
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
        for (p in activePeers) {
            Log.d("peers.propagate", "consider ${p.peerMark} ${p.rpcService?.ebtRpcNr}")
            if (p.peerFid in knownRemoteFrontier) {
                val fmap = knownRemoteFrontier.get(p.peerFid)!!
                Log.d("peers.propagate", "found map ${fmap}, ${evnt.lid in fmap}")
                if (evnt.lid in fmap && fmap.get(evnt.lid)!! < evnt.lsq) {
                    if (p.rpcService?.ebtRpcNr != 0)
                        p.rpcService?.sendLogEntry(p.rpcService!!.ebtRpcNr, evnt, false)
                } else
                    Log.d("peers.propagate", "${p.peerFid} no need: ${fmap.get(evnt.lid)} / evnt.lsq=${evnt.lsq}")
            } else
                Log.d("peers.propagate", "${p.peerFid} is not in knownRemoteFrontier")
        }
    }

    fun newContact(fid: String) {
        Log.d("peers.contact", "do it for ${activePeers.size} active peers")
        for (p in activePeers) {
            Log.d("peers.contact", "consider ${p.peerMark} ${p.rpcService?.ebtRpcNr}")
            if (p.rpcService?.ebtRpcNr != 0)
                p.rpcService?.sendEBTnote(p.rpcService!!.ebtRpcNr, fid, 0)
        }
    }

    fun closeAll() {
        for (peer in activePeers) {
            peer.close()
        }
    }

    fun addToActive(rpc: RpcLoop) {
        lck.lock()
        try {
            activePeers.add(rpc)
            Log.d("addToActive", "added ${rpc.peerFid}, now ${activePeers.size} entries")
            if (!(rpc.peerFid in knownRemoteFrontier))
                knownRemoteFrontier.put(rpc.peerFid!!, mutableMapOf())
        } finally { lck.unlock() }
    }
    fun removeFromActive(rpc: RpcLoop) {
        lck.lock()
        try {
            activePeers.remove(rpc)
            Log.d("addToActive", "removed, now ${activePeers.size} entries, ${this}/${activePeers}")
        } finally { lck.unlock() }
    }

    fun updateKnownFrontier(peerFid: String, fid: String, highest: Int) {
        lck.lock()
        try {
            if (!(peerFid in knownRemoteFrontier))
                knownRemoteFrontier.put(peerFid, mutableMapOf())
            val m = knownRemoteFrontier.get(peerFid)!!
            if (!(fid in m) || m.get(fid)!! < highest)
                m.put(fid, highest)
        } finally { lck.unlock() }
    }
}



