package nz.scuttlebutt.tremola.ssb.peering.discovery;

import nz.scuttlebutt.tremola.ssb.core.SSBid;

import java.util.LinkedList;

public abstract class LookUpClient {

    protected final int port;
    protected final String localAddress;
    protected final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;

    public LookUpClient(String ipAddress, int port, SSBid ed25519KeyPair) {
        assert ipAddress != null;
        this.port = port;
        this.localAddress = ipAddress;
        this.ed25519KeyPair = ed25519KeyPair;
    }

    abstract void sendQuery(String broadcastMessage);

    abstract void processQuery();
}
