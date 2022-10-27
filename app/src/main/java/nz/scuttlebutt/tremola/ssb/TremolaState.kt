package nz.scuttlebutt.tremola.ssb

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.WebAppInterface
import nz.scuttlebutt.tremola.doubleRatchet.DoubleRatchetList
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
 * This class contains all the information about the local peer.
 * @param context The app's context, MainActivity.
 * @property numberOfCores The numbers of processing cores that are available.
 * @property executorPool A pool of threads to do things asynchronously.
 * @property db The database used for storing the relevant data.
 * @property logDAO The data access object for the logs.
 * @property pubDAO The data access object for the pubs.
 * @property contactDAO The data access object for the contacts.
 * @property idStore Contains the the cryptographic keys of the identity.
 * @property doubleRatchetList Contains the list of DoubleRatchets that are used to encrypt and
 * decrypt chat messages.
 * @property peers The peers that are currently active or known.
 * @property wai The WebAppInterface that the frontend uses to interact with the backend.
 * @property msgTypes The SSBmsgTypes object.
 */
@RequiresApi(Build.VERSION_CODES.O)
class TremolaState(val context: Context) {
    private val numberOfCores = Runtime.getRuntime().availableProcessors()
    private val executorPool: ScheduledExecutorService = Executors.newScheduledThreadPool(
        numberOfCores,
        threadFactory("SSB Pool", true)
    )

    // Load database.
    private val db = TremolaDatabase.getInstance(context)
    val logDAO: LogEntryDAO = db.logDAO()
    val pubDAO: PubDAO = db.pubDAO()
    val contactDAO: ContactDAO = db.contactDAO()

    // The identity, i.e. the crypto keys are stored in this field.
    val idStore = IdStore(context)

    // Contains the list of DoubleRatchets used for each chat.
    val doubleRatchetList = DoubleRatchetList(context)

    // The other known and/or active peers.
    val peers: PeeringPool = PeeringPool(this)

    // Interface to the frontend.
    lateinit var wai: WebAppInterface
    val msgTypes = SSBmsgTypes(this)

    init {
        addOwnIdentityAsFeed()

        executorPool.scheduleAtFixedRate({ // Connect to Wi-Fi peers that show up.
            for (markOfAvailablePeers in (context as MainActivity).udp!!.markOfLocalPeers.keys) {
                val s = markOfAvailablePeers.split("~")
                // Transform public key into log ID.
                val logID = "@" + s[1].substring(4) + ".ed25519"
                // 3 part address: 'net', ip address and port.
                val address = s[0].split(":")
                // Will filter out already connected peers.
                peers.add(address[1], address[2].toInt(), logID)
            }
        }, 3, WIFI_DISCOVERY_INTERVAL, TimeUnit.SECONDS)
        executorPool.scheduleAtFixedRate({ // Kick EBT (send current frontier) at regular intervals.
            peers.kick()
        }, 5, EBT_FORCE_FRONTIER_INTERVAL, TimeUnit.SECONDS)
    }

    companion object {
        /**
         * This function returns a thread to execute a certain task.
         * @param name The name of the ThreadFactory.
         * @param daemon Whether the thread should be considered a daemon thread.
         * @return A ThreadFactory that produces threads of the given name and daemon state.
         */
        @JvmStatic
        fun threadFactory(name: String, daemon: Boolean): ThreadFactory {
            return ThreadFactory { runnable ->
                val result = Thread(runnable, name)
                result.isDaemon = daemon
                result
            }
        }
    }

    /**
     * A function that adds all the people in all known pubs to the peers pool. Unused.
     */
    private fun addPubsToPool() {
        executorPool.execute {
            val pubs = pubDAO.getAll()
            for (pub in pubs) {
                peers.add(pub.host, pub.port, pub.lid)
            }
        }
    }

    /**
     * Updates the feed by kicking the Epidemic Broadcast Tree. Unused.
     */
    fun syncFeeds() {
        executorPool.execute { peers.kick() }
    }

    /**
     * Adds a pub to the pub database and adds their peers to the pool.
     * @param pub The pub to be added.
     */
    fun addPub(pub: Pub) {
        executorPool.submit { pubDAO.insert(pub) }
        peers.add(pub.host, pub.port, pub.lid)
    }

    /**
     * Adds a LogEntry to the database.
     * @param e The LogEntry to be added.
     */
    fun addLogEntry(e: LogEntry) {
        executorPool.submit { logDAO.insert(e) }
    }

    /**
     * Adds a contact to the database.
     * @param fid The friend ID to be added.
     * @param alias The alias given by the user to the contact.
     */
    fun addContact(fid: String, alias: String?) {
        contactDAO.insertContact(
            Contact(fid, alias, false, null, 1, 0, null)
        )
    }

    /**
     * Adds yourself to contact database.
     */
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

    /**
     * Unused. TODO Remove or implement.
     */
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