package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.util.Log;

import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class LocalDiscovery extends Thread {
/*
    private String broadcastMessage;
    private int port;
    private String broadcastAddress;
    private nz.scuttlebutt.tremola.ssb.core.identity.Identity ed25519KeyPair;

    public LocalDiscovery(String broadcastAddress, String ipAddress, int port, nz.scuttlebutt.tremola.ssb.core.identity.Identity ed25519KeyPair) {
        this.port = port;
        this.broadcastAddress = broadcastAddress;
        this.ed25519KeyPair = ed25519KeyPair;
        if (ipAddress != null) {
            broadcastMessage = "net:" + ipAddress + ":" + port + "~shs:" + ed25519KeyPair.asString();
        }
    }

    public void run() {
        try {
            final DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            InetAddress receiverAddress = InetAddress.getByName(broadcastAddress);

            final DatagramPacket datagramPacket = new DatagramPacket(broadcastMessage.getBytes(), broadcastMessage.length(), receiverAddress, port);

            Log.d("Broadcast Address", receiverAddress.toString());

            Timer sendTimer = new Timer();
            sendTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        datagramSocket.send(datagramPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }, 0, 1000);


        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


        DatagramSocket receiverSocket = null;
        try {
            receiverSocket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
            receiverSocket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        while (true) {
            byte[] receiverBuffer = new byte[150];
            DatagramPacket datagramPacket = new DatagramPacket(receiverBuffer, receiverBuffer.length);
            try {
                if (receiverSocket != null) {
                    receiverSocket.receive(datagramPacket);
                    String[] data = new String(datagramPacket.getData()).trim().split(":");
                    try {
                        if (!data[3].equals(ed25519KeyPair.asString())) {
                            String port = data[2].split("~")[0];
                            Log.d("DISCOVERY", data[3]);
                        }
                        else{
                            Log.d("DISCOVERY", "discovered self");
                        }
                    } catch (NullPointerException npe) {
                        Log.d("FAILED DATA", "" + data[0] + ", " + data[1] + ", " + data[2] + ", " + data[3]);
                    }


                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

*/
}
