package nz.scuttlebutt.tremola.ssb.peering.discovery;


import android.util.Log;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

import java.io.IOException;
import java.net.*;

public class LookUpUDP extends LookUpClient {

    private int outgoingPort;
    private String broadcastAddress;

    public LookUpUDP(String ipAddress, int port, SSBid ed25519KeyPair) {
        super(ipAddress, port, ed25519KeyPair);
    }

    public void prepareQuery(String broadcastAddress) {
        this.broadcastAddress = broadcastAddress;
        this.outgoingPort = port; // Necessary?
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

            Log.d("Broadcast Address", receiverAddress.toString());

            // TODO: how many times should it send the broadcast?
            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (IOException e) {
            Log.e("SendMessage", e.getMessage());
            e.printStackTrace();
        }
    }
}

