package nz.scuttlebutt.tremola.ssb.peering.discovery;


import android.util.Log;
import android.widget.Toast;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class LookUpThread extends Thread {

    private String selfMultiServerAddress;
    private int port;
    private String broadcastAddress;
    private nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private String broadcastMessage;
    private final int HOP_COUNT = 4;
    private static int queryIdentifier = 0;

    public LookUpThread(String broadcastAddress, String ipAddress, int port, SSBid ed25519KeyPair, String targetName) {
        assert ipAddress != null;
        this.port = port;
        this.broadcastAddress = broadcastAddress;
        this.ed25519KeyPair = ed25519KeyPair;
        createMessage(ipAddress, port, ed25519KeyPair, targetName);
    }

    private void createMessage(String ipAddress, int port, SSBid ed25519KeyPair, String targetName)  {
        JSONObject message = new JSONObject();
        selfMultiServerAddress = "net:" + ipAddress + ":" + port + "~shs:" + ed25519KeyPair.toExportString();
        try {
            message.put("msa", selfMultiServerAddress);
            message.put("queryID", queryIdentifier++);
            message.put("hop", HOP_COUNT);
            message.put("targetName", targetName);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        broadcastMessage = message.toString();
    }

    public void run() {
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
            datagramSocket.send(datagramPacket);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("LOOKUP", broadcastMessage);
/*
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
                        if (!data[3].equals(ed25519KeyPair.toExportString())) {
                            String port = data[2].split("~")[0];
                            Log.d("DISCOVERY", data[3]);
                        } else {
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
*/
    }

    public static String createBroadcastMessage(String shortName) {
        // TODO: implement
        return shortName;
    }
}
