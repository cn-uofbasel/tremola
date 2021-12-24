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
        if (lookUpUDP != null)
            lookUpUDP.processQuery();
        if (lookUpBluetooth != null) {
            // TODO processQuery()
            lookUpBluetooth.scanLeDevice();
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
}
