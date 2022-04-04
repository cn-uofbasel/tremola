package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.content.Context;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

import java.util.concurrent.locks.ReentrantLock;

public abstract class LookupClient extends Thread {

    protected final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    protected final Context context;
    protected final Lookup lookup;
    protected final ReentrantLock lock;
    protected boolean active = false;

    public LookupClient(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock) {
        this.ed25519KeyPair = ed25519KeyPair;
        this.context = context;
        this.lookup = lookup;
        this.lock = lock;
    }

    public boolean isActive() {
        return active;
    }

    abstract void sendQuery(String broadcastMessage);

    /**
     * Close a client when the communication mean is not available.
     */
    public void close() {
        active = false;
    }

    /**
     * Allow a client to be used when the communication mean becomes available.
     */
    public void reactivate() {
        active = true;
    }

    public void closeQuery(String message) {
    }

    public String getSubClass() {
        return this.getClass().toString();
    }
}
