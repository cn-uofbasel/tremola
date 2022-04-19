package nz.scuttlebutt.tremola.ssb

import android.content.Context
import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.WebAppInterface
import nz.scuttlebutt.tremola.ssb.core.IdStore
import nz.scuttlebutt.tremola.ssb.db.TremolaDatabase
import nz.scuttlebutt.tremola.ssb.db.daos.ContactDAO
import nz.scuttlebutt.tremola.ssb.db.daos.LogEntryDAO
import nz.scuttlebutt.tremola.ssb.db.daos.PubDAO
import nz.scuttlebutt.tremola.ssb.db.entities.Contact
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry
import nz.scuttlebutt.tremola.ssb.db.entities.Pub
import nz.scuttlebutt.tremola.ssb.peering.PeeringPool
import nz.scuttlebutt.tremola.utils.Constants.Companion.EBT_FORCE_FRONTIER_INTERVAL
import nz.scuttlebutt.tremola.utils.Constants.Companion.WIFI_DISCOVERY_INTERVAL

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * All information about the local peer
 */
class TremolaState(val context: Context) {
    private val numberOfCores = Runtime.getRuntime().availableProcessors()
    private val executorPool: ScheduledExecutorService

    // Load database
    private val db = TremolaDatabase.getInstance(context)
    val logDAO: LogEntryDAO = db.logDAO()
    val pubDAO: PubDAO = db.pubDAO()
    val contactDAO: ContactDAO = db.contactDAO()

    // The identity, i.e. the crypto keys
    val idStore = IdStore(context)

    // The other known and/or active peers
    val peers: PeeringPool

    // Interface to the frontend
    lateinit var wai: WebAppInterface
    val msgTypes = SSBmsgTypes(this)

    init {
        executorPool = Executors.newScheduledThreadPool(
            numberOfCores,
            threadFactory("SSB Pool", true)
        )
        peers = PeeringPool(this)
        addOwnIdentityAsFeed()

        executorPool.scheduleAtFixedRate({ // connect to wiFi peers that show up
            for (markOfAvailablePeers in (context as MainActivity).udp!!.markOfLocalPeers.keys) {
                val s = markOfAvailablePeers.split("~")
                // Transform public key into log ID
                val logID = "@" + s[1].substring(4) + ".ed25519"
                // 3 part address: 'net', ip address and port
                val address = s[0].split(":")
                peers.add(address[1], address[2].toInt(), logID) // will filter out already connected peers
            }
        }, 3, WIFI_DISCOVERY_INTERVAL, TimeUnit.SECONDS)
        executorPool.scheduleAtFixedRate({ // kick EBT (send current frontier) on regular intervals
            peers.kick()
        }, 5, EBT_FORCE_FRONTIER_INTERVAL, TimeUnit.SECONDS)
    }

    companion object {
        @JvmStatic
        fun threadFactory(name: String, daemon: Boolean): ThreadFactory {
            return ThreadFactory { runnable ->
                val result = Thread(runnable, name)
                result.isDaemon = daemon
                result
            }
        }
    }

    private fun addPubsToPool() {
        executorPool.execute {
            val pubs = pubDAO.getAll()
            for (pub in pubs) {
                peers.add(pub.host, pub.port, pub.lid)
            }
        }
    }

    fun syncFeeds() {
        executorPool.execute { peers.kick() }
    }

    fun addPub(pub: Pub) {
        executorPool.submit { pubDAO.insert(pub) }
        peers.add(pub.host, pub.port, pub.lid)
    }

    fun addLogEntry(e: LogEntry) {
        executorPool.submit { logDAO.insert(e) }
    }

    fun addContact(fid: String, alias: String?) {
        contactDAO.insertContact(
            Contact(fid, alias, false, null, 1, 0, null)
        )
    }

    private fun addOwnIdentityAsFeed() {
        executorPool.submit {
            val lid = idStore.identity.toRef()
            if (contactDAO.getContactByLid(lid) == null)
                contactDAO.insertContact(
                    Contact(
                        lid, null, false, null,
                        scan_low = 1, front_seq = 0, front_prev = null
                    )
                )
        }
    }

    fun processMessages() {
/*
        Log.d("PROCESSING: ", "STARTED")

        updateFeeds()
        updateNames()
        generateFeedsByFollows()
        cleanMessages()
        Log.d("PROCESSING: ", "FINISHED")
 */
    }

    /*
    private fun updateFeeds() {
        val feedIDs = processDAO.getAllFeeds()

        for (id in feedIDs) {
            val pubkey = processDAO.getPubkeyById(id)
            val sequence = processDAO.getFrontSequence(pubkey)
            val previous = processDAO.getFrontPrevious(pubkey)
            processDAO.updateFeed(id, sequence, previous)
        }
    }

    private fun updateNames() {
        val feedIDs = processDAO.getAllFeeds()
        for (id in feedIDs) {
            val names = processDAO.getNamesFromMessages(id)
            if (names.isNotEmpty()) {
                val oldNames = processDAO.getNamesById(id)
                if (oldNames == null) {
                    processDAO.insertIntoAbout(id, names.toString())
                } else {
                    processDAO.updateNamesOfId(id, names.union(oldNames.toList()).toString())
                }
            }
        }
    }

    private fun generateFeedsByFollows() {
        val follows =
            processDAO.getFollowingsByPubkey(SSBRef.fromString(idStore.getIdentity().asString())!!)
        val ownID = processDAO.getFeedIDByKey(SSBRef.fromString(idStore.getIdentity().asString())!!)
        if (follows.isNotEmpty()) {
            for (follow in follows) {
                var idDB = processDAO.getFeedIDByKey(follow).toLong()
                if (idDB == 0L) {
                    idDB = feedDAO.insertPeer(
                        Feed(null, follow, 1, 0, null)
                    )
                }
                followDAO.insert(Follow(ownID, idDB.toInt(), 1))
            }
        }
    }

    private fun cleanMessages() {
        processDAO.deleteMessagesLeaveOnlyType(
            listOf("post", "private")
        )
        val limit = Date(System.currentTimeMillis() - Constants.frontierWindow)
        processDAO.forgetMessagesByTimeLimit(limit)
    }
    */
}