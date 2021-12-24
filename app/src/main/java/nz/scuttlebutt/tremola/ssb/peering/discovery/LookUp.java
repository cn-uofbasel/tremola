package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.utils.Constants;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

// TODO Store LookUpClients as List (?)
public class LookUp extends Thread {

    private final int port;
    private final String localAddress;
    private final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private final int HOP_COUNT = 4;
    private static int queryIdentifier = 0;
    private String incomingRequest = null;
    private LinkedList<ReceivedQuery> logOfReceivedQueries;
    private final LookUpUDP lookUpUDP;
    private final LookUpBluetooth lookUpBluetooth;
    private String udpBroadcastAddress;
    private String targetName;

    public LookUp(String ipAddress, int port, SSBid ed25519KeyPair, Context act) {
        assert ipAddress != null;
        this.localAddress = ipAddress;
        this.port = port;
        this.ed25519KeyPair = ed25519KeyPair;
        lookUpUDP = new LookUpUDP(ipAddress, port, ed25519KeyPair);

        BluetoothManager bluetoothManager = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e("BLUETOOTH", "Bluetooth disabled");
        }
        assert bluetoothAdapter != null;
        lookUpBluetooth = new LookUpBluetooth(ipAddress, port, ed25519KeyPair, bluetoothAdapter);
    }

    public void prepareQuery(String broadcastAddress, String targetName) {
        this.udpBroadcastAddress = broadcastAddress;
        this.targetName = targetName;
    }

    private String createMessage(String targetName) {
        JSONObject message = new JSONObject();
        String selfMultiServerAddress = "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toExportString();
        try {
            message.put("msa", selfMultiServerAddress);
            message.put("queryID", queryIdentifier++);
            message.put("hop", HOP_COUNT);
            message.put("targetName", targetName);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        return message.toString();
    }

    /**
     * Implemented as a thread not to block udp.listen()
     */
    public void run() {
        if (incomingRequest == null) {
            sendQuery();
        } else {
            processQuery();
            incomingRequest = null;
        }
    }

    private void processQuery() {
        Log.d("INPUT", incomingRequest);
        if (logOfReceivedQueries == null)
            logOfReceivedQueries = new LinkedList<>();
        try {
            JSONObject data = new JSONObject(incomingRequest);
            String msa = data.get("msa").toString();
            if (msa.endsWith(ed25519KeyPair.toExportString()))
                return; //I'm the initiator
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
            logOfReceivedQueries.add(
                    new ReceivedQuery(shortName, initID, hopCount, queryID));

            if (selfIsTarget(shortName)) {
                replyStep2(initID, queryID, shortName, hopCount);
            } else {
                // TODO Decrement hop count
                if (lookUpUDP != null) {
                    lookUpUDP.sendQuery(createMessage(targetName));
//                    lookUpUDP.processQuery();
                }
                if (lookUpBluetooth != null) {
                    // TODO processQuery()
                    lookUpBluetooth.scanLeDevice();
                }
            }
        } catch (Exception e) {
            Log.e("PROCESS_QUERY", e.getMessage());
        }
    }

    private void sendQuery() {
        String broadcastMessage = createMessage(targetName);
        if (lookUpUDP != null) {
            // TODO port is a constant, and always the same for in and out?
            lookUpUDP.prepareQuery(udpBroadcastAddress);
            lookUpUDP.sendQuery(broadcastMessage);
        }
        if (lookUpBluetooth != null) {
            lookUpBluetooth.scanLeDevice();
            lookUpBluetooth.sendQuery(broadcastMessage);
        }
    }

    public void acceptQuery(@NotNull String incomingRequest) {
        this.incomingRequest = incomingRequest;
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
}