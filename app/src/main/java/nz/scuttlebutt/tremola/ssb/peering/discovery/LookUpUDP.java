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

public class LookUpUDP extends LookUpClient {

    private String broadcastAddress;

    public LookUpUDP(LookUp lookUp, Context context, int port, SSBid ed25519KeyPair) {
        super(lookUp, context, port, ed25519KeyPair);
    }

    public void prepareQuery(String broadcastAddress) {
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


    public void listen(ReentrantLock lock) throws InterruptedException {
        byte[] buf = new byte[512];
        DatagramPacket ingram = new DatagramPacket(buf, buf.length);
        while (true) {
            try {
                Objects.requireNonNull(((MainActivity) context).getLookup_socket()).receive(ingram);
            } catch (Exception e) {
                synchronized (lock) {
                    lock.wait(Constants.Companion.getLOOKUP_INTERVAL());
                }
                continue;
            }
            String incoming = new String(ingram.getData(), 0, ingram.getLength());
            for (String i : incoming.split(";")) {
                Log.e("lu_rx " + ingram.getLength(), "<" + i + ">");
                if (i.startsWith("{\"targetName")) {
                    lookUp.acceptQuery(i);
                    lookUp.processQuery();
                } else if (i.startsWith("{\"targetId")) {
                    lookUp.acceptReply(i);
                    lookUp.processReply();
                }
            }
        }
    }
}

