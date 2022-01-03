package nz.scuttlebutt.tremola.ssb.peering.discovery;


import android.content.Context;
import android.util.Log;
import nz.scuttlebutt.tremola.MainActivity;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.utils.Constants;

import java.io.IOException;
import java.net.*;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class LookupUDP extends LookupClient {

    private final String broadcastAddress;
    private final int port;

    public LookupUDP(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock, int port, String broadcastAddress) {
        super(lookup, context, ed25519KeyPair, lock);
        this.port = port;
        this.broadcastAddress = broadcastAddress;
    }

    @Override
    public void sendQuery(String broadcastMessage) {
        try {
            final DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            InetAddress receiverAddress = InetAddress.getByName(broadcastAddress);

            final DatagramPacket datagramPacket = new DatagramPacket(
                    broadcastMessage.getBytes(),
                    broadcastMessage.length(),
                    receiverAddress,
                    port
            );

            Log.d("Send Packet", new String(datagramPacket.getData()));

            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (IOException e) {
            Log.e("SendMessage", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[512];
        DatagramPacket ingram = new DatagramPacket(buf, buf.length);
        while (true) {
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
                if (i.startsWith("{\"targetName")) {
                    lookup.processQuery(i);
                } else if (i.startsWith("{\"targetId")) {
                    lookup.processReply(i);
                }
            }
        }
    }
}