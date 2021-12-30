package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.content.Context;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

public abstract class LookUpClient {

    protected final int port;
    protected final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    protected final Context context;
    protected final LookUp lookUp;

    public LookUpClient(LookUp lookUp, Context context, int port, SSBid ed25519KeyPair) {
        this.port = port;
        this.ed25519KeyPair = ed25519KeyPair;
        this.context = context;
        this.lookUp = lookUp;

    }

    abstract void sendQuery(String broadcastMessage);

    // TODO Do I need this?
//    abstract void processQuery();
}
