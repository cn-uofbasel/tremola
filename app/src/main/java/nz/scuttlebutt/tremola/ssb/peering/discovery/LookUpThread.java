package nz.scuttlebutt.tremola.ssb.peering.discovery;


import android.util.Log;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class LookUpThread extends Thread {

    private final int port;
    private final String localAddress;
    private final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private String broadcastMessage;
    private final int HOP_COUNT = 4;
    private static int queryIdentifier = 0;
    private String incomingRequest;
    private LinkedList<ReceivedQuery> logOfReceivedQueries;

    public LookUpThread(String ipAddress, int port, SSBid ed25519KeyPair) {
        assert ipAddress != null;
        this.port = port;
        this.localAddress = ipAddress;
        this.ed25519KeyPair = ed25519KeyPair;
    }

    private void createMessage(String ipAddress, int port, SSBid ed25519KeyPair, String targetName) {
        JSONObject message = new JSONObject();
        String selfMultiServerAddress = "net:" + ipAddress + ":" + port + "~shs:" + ed25519KeyPair.toExportString();
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

    public void sendQuery(String broadcastAddress, String targetName) {
        createMessage(localAddress, port, ed25519KeyPair, targetName);
        sendMessage(broadcastAddress, port, broadcastMessage);
        Log.d("LOOKUP", broadcastMessage);
    }

    private void sendMessage(String broadcastAddress, int port, String message) {
        Log.e("SEND", message);
        try {
            final DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            InetAddress receiverAddress = InetAddress.getByName(broadcastAddress);

            final DatagramPacket datagramPacket = new DatagramPacket(
                    message.getBytes(),
                    message.length(),
                    receiverAddress,
                    port
            );

            Log.d("Broadcast Address", receiverAddress.toString());

            // TODO: how many times should it send the broadcast?
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
        Log.e("SENT", "!!!" + message);
    }

    /**
     * Implemented as a thread not to block udp.listen()
     */
    public void run() {
        Log.d("INPUT", incomingRequest);
        if (logOfReceivedQueries == null)
            logOfReceivedQueries = new LinkedList<>();
        try {
            JSONObject data = new JSONObject(incomingRequest);
            String msa = data.get("msa").toString();
            if (msa.endsWith(ed25519KeyPair.toExportString()))
                return;
            int queryID = data.getInt("queryID");
            String[] multiServerAddress = msa.split("~");
            String initID = multiServerAddress[1].split(":")[1];
            for (Object object : logOfReceivedQueries.toArray()) {
                ReceivedQuery query = (ReceivedQuery) object;
                if (query.isOutDated()) {
                    logOfReceivedQueries.remove(query);
                } else if (query.isEqualTo(initID, queryID)) {
                    return; // the query is already in the database
                }
            }
            String shortName = data.getString("targetName");
            int hopCount = data.getInt("hop");
            logOfReceivedQueries.add(new ReceivedQuery(shortName, initID, hopCount, queryID));

            if (selfIsTarget(shortName)) {
                replyStep2(initID, queryID, shortName, hopCount);
            } else {
                String[] address = new String[0];
                try {
                    address = multiServerAddress[0].split(":");
                    sendMessage(address[1], Integer.parseInt(address[2]), incomingRequest);
//                    final DatagramSocket datagramSocket = new DatagramSocket(
//                            Integer.parseInt(address[2]),
//                            InetAddress.getByName(address[1])
//                    );
//                    datagramSocket.setBroadcast(false);
//                    // TODO decrease hop count
//                    final DatagramPacket datagramPacket = new DatagramPacket(
//                            incomingRequest.getBytes(),
//                            incomingRequest.length()
//                    );
//                    datagramSocket.send(datagramPacket);

                } catch (NullPointerException e) {
                    Log.e("PORT_PARSE", "error while parsing port :" + address[2] + ":");
                    Log.e("PORT_PARSE", e.getMessage());
                }
            }
        } catch (NullPointerException npe) {
            Log.d("FAILED DATA", incomingRequest);
        } catch (JSONException err) {
            Log.d("Error", err.toString());
        }
    }

    private void replyStep2(String initID, int queryID, String targetShortName, int hopCount) {
        // TODO
//        reply = [initiatorId, queryId, targetShortName, targetId, hopCount].asJSON
//        send(reply, to: initiator)
    }

    private boolean selfIsTarget(String receivedShortName) {
//        String b = b32encode(receivedShortName.substring(0, 7)).substring(0, 10);
//        String myShortName = b;
        return false; //!receivedShortName.equals(myShortName);
    }

    public void storeIncomingLookup(@NotNull String incomingRequest) {
        this.incomingRequest = incomingRequest;
        // TODO : check that it's not processing a request
    }

    private String b32encode(String bytes) {
        // TODO translate this to java
        return bytes;
    }
}

