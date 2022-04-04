package nz.scuttlebutt.tremola.ssb.peering.discovery;


import android.content.Context;
import android.util.Log;
import nz.scuttlebutt.tremola.MainActivity;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.utils.Constants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class LookupUDP extends LookupClient {

    private final String broadcastAddress;
    private final int port;
    private DatagramSocket datagramSocket;

    public LookupUDP(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock, int port, String broadcastAddress) {
        super(lookup, context, ed25519KeyPair, lock);
        this.port = port;
        this.broadcastAddress = broadcastAddress;
        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
        } catch (Exception e) {
            active = false;
        }
    }

    @Override
    public void sendQuery(String broadcastMessage) {
        try {
            if (datagramSocket.isClosed()) {
                datagramSocket = new DatagramSocket();
                datagramSocket.setBroadcast(true);
            }
            InetAddress receiverAddress = InetAddress.getByName(broadcastAddress);

            final DatagramPacket datagramPacket = new DatagramPacket(
                    broadcastMessage.getBytes(),
                    broadcastMessage.length(),
                    receiverAddress,
                    port
            );

            datagramSocket.send(datagramPacket);
            datagramSocket.close();
            Log.e("lu_wr", new String(datagramPacket.getData()));
        } catch (IOException e) {
            Log.e("SEND_ERROR", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[512];
        DatagramPacket ingram = new DatagramPacket(buf, buf.length);
        active = true;
        while (active) {
            try {
                Objects.requireNonNull(((MainActivity) context).getLookup_socket()).receive(ingram);
            } catch (Exception e) {
                synchronized (lock) {
                    try {
                        lock.wait(Constants.Companion.getLOOKUP_INTERVAL());
                    } catch (InterruptedException ex) {
                        Log.e("UDP LOCK", ex.getMessage());
                    }
                }
                continue;
            }
            String incoming = new String(ingram.getData(), 0, ingram.getLength());
            for (String i : incoming.split(";")) {
                Log.e("lu_rx " + ingram.getLength(), "<" + i + ">");
                if (i.contains("\"msa\"")) {
                    lookup.processQuery(i);
                } else if (i.contains("\"targetId")) {
                    lookup.processReply(i);
                }
            }
        }
    }
}