package nz.scuttlebutt.tremola.ssb.peering.discovery

import android.content.Context
import nz.scuttlebutt.tremola.ssb.core.SSBid
import java.util.concurrent.locks.ReentrantLock

abstract class LookupClient(
    protected val lookup: Lookup,
    protected val context: Context,
    protected val ed25519KeyPair: SSBid,
    protected val lock: ReentrantLock
) :
    Thread() {
    var active: Boolean = false
        protected set

    abstract fun sendQuery(broadcastMessage: String)

    /**
     * Close a client when the communication mean is not available.
     */
    fun close() {
        active = false
    }

    /**
     * Allow a client to be used when the communication mean becomes available.
     */
    fun reactivate() {
        active = true
    }

    fun closeQuery(message: String) {}
    val subClass: String
        get() = this.javaClass.toString()
}