package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.content.Context;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

import java.util.concurrent.locks.ReentrantLock;

public abstract class LookupClient extends Thread {

    protected final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    protected final Context context;
    protected final Lookup lookup;
    protected final ReentrantLock lock;

    public LookupClient(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock) {
        this.ed25519KeyPair = ed25519KeyPair;
        this.context = context;
        this.lookup = lookup;
        this.lock = lock;
    }

    abstract void sendQuery(String broadcastMessage);
}
