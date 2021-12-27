package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import nz.scuttlebutt.tremola.ssb.TremolaState;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.ssb.db.entities.Contact;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Objects;

public class LookUp extends Thread {

    private final int port;
    private final String localAddress;
    private final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private final int HOP_COUNT = 4;
    private static int queryIdentifier = 0;
    private final TremolaState tremolaState;
    private String incomingRequest = null;
    private LinkedList<ReceivedQuery> logOfReceivedQueries;
    private final LookUpUDP lookUpUDP;
    private final LookUpBluetooth lookUpBluetooth;
    private String udpBroadcastAddress;
    private String targetName;
    private final String B32ENC_MAP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";


    public LookUp(String ipAddress, int port, SSBid ed25519KeyPair, Context act, TremolaState tremolaState) {
        assert ipAddress != null;
        this.localAddress = ipAddress;
        this.port = port;
        this.ed25519KeyPair = ed25519KeyPair;
        this.tremolaState = tremolaState;

        lookUpUDP = new LookUpUDP(ipAddress, port, ed25519KeyPair);

        BluetoothManager bluetoothManager = (BluetoothManager) act.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e("BLUETOOTH", "Bluetooth disabled");
        }
        assert bluetoothAdapter != null;
        lookUpBluetooth = new LookUpBluetooth(ipAddress, port, ed25519KeyPair, bluetoothAdapter);
    }

    /**
     * Store the needed information before launching the Thread.
     * @param broadcastAddress  the udp address to broadcast the query
     * @param targetName        the target name written by the user
     */
    public void prepareQuery(String broadcastAddress, String targetName) {
        this.udpBroadcastAddress = broadcastAddress;
        this.targetName = targetName;
        searchDataBase(targetName);
        Log.e("COMPARE", "Result is " + keyIsTarget(targetName, ed25519KeyPair.toRef()));
    }

    /**
     * Create the payload of the query.
     * @param targetName  the queried shortName
     * @return            the payload
     */
    private String createMessage(String targetName) {
        String selfMultiServerAddress = "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toExportString();

        return createMessage(targetName, selfMultiServerAddress, queryIdentifier++, HOP_COUNT);
    }
    private String createMessage(String targetName, String multiServerAddress, int queryId, int hopCount) {
        JSONObject message = new JSONObject();
        try {
            message.put("targetName", targetName);
            message.put("msa", multiServerAddress);
            message.put("queryID", queryId);
            message.put("hop", hopCount);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        return message.toString();
    }

        /**
         * Implemented as a thread not to block udp.listen().
         * IncomingRequest is null if the query was received  from the front-end
         */
    public void run() {
        if (incomingRequest == null) {
            sendQuery();
        } else {
            processQuery();
            incomingRequest = null;
        }
    }

    /**
     * Process an incoming request by discarding, answering or forwarding it.
     */
    private void processQuery() {
        Log.d("INPUT", incomingRequest);
        if (logOfReceivedQueries == null)
            logOfReceivedQueries = new LinkedList<>();
        try {
            JSONObject data = new JSONObject(incomingRequest);
            String msa = data.get("msa").toString();
            if (msa.endsWith(Objects.requireNonNull(ed25519KeyPair.toExportString())))
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

            String targetPublicKey = searchDataBase(shortName);
            if (targetPublicKey != null) {
                replyStep2(initID, queryID, shortName, hopCount, targetPublicKey);
            } else {
                if (hopCount > 0) {
                    String msg = createMessage(targetName, msa, queryID, hopCount - 1);
                    if (lookUpUDP != null) {
                        lookUpUDP.sendQuery(msg);
                    }
                    if (lookUpBluetooth != null) {
                        // TODO sendQuery()
                        lookUpBluetooth.scanLeDevice();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PROCESS_QUERY", e.getMessage());
        }
    }

    /**
     * Search in the database if the targetName is known.
     * @param targetShortName  the target 10 char shortName
     * @return                 the public key if found, else null
     */
    private String searchDataBase(String targetShortName) {
        if (keyIsTarget(targetShortName, ed25519KeyPair.toRef())) {
            return ed25519KeyPair.toExportString();
        } else {
            for (Contact contact :tremolaState.getContactDAO().getAll()) {
                if (keyIsTarget(targetShortName, contact.getLid())) {
                    Log.e("CONTACT", contact.getAlias());
                    return "@" + contact.getLid() + ".ed25519";
                }
            }
        }
        return null;
    }

    /**
     * Send a query that comes from front end.
     * Must be done for each available medium.
     */
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

    /**
     * Store a request received by any mean (for now udp or bluetooth)
     * @param incomingRequest  the received query
     */
    public void acceptQuery(@NotNull String incomingRequest) {
        this.incomingRequest = incomingRequest;
    }

    private void replyStep2(String initID, int queryID, String targetShortName, int hopCount, String targetID) {
        // TODO
//        reply = [initiatorId, queryId, targetShortName, targetId, hopCount].asJSON
//        send(reply, to: initiator)
    }

    /**
     * Compare a public key with a short name.
     * @param receivedShortName  The 11 char (including a '-') short name
     * @param publicKey          The public key in the form "@[...].ed25519
     * @return                   true if the 2 match
     */
    private boolean keyIsTarget(String receivedShortName, String publicKey) {
        String computedShortName = id2b32(publicKey);
        return receivedShortName.equals(computedShortName);
    }

    private String id2b32(String str) {

        try {
            String b = str.substring(1, str.length() - 9);
            byte[] bytes = new byte[0];
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                bytes = Base64.getDecoder().decode(b);
            }
            b = b32encode(Arrays.copyOfRange(bytes, 0, 7));
            b = b.substring(0, 10);
            return b.substring(0, 5) + '-' + b.substring(5);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e);
        }
        return "??";
    }

    private String b32encode(byte[] bytes) {
        StringBuilder b32 = new StringBuilder();
        int cnt = bytes.length % 5;
        int[] buf;
        if (cnt == 0)
            buf = new int[bytes.length];
        else
            buf = new int[bytes.length + 5 - cnt];

        for (int i = 0; i < bytes.length; i++)
            buf[i] = ((bytes[i] + 256) % 256);
        for (int i = bytes.length; i < buf.length; i++)
            buf[i] = 0;

        while (buf.length >= 5) {
            b32.append(b32enc_do40bits(Arrays.copyOfRange(buf, 0, 5)));
            buf = Arrays.copyOfRange(buf, 5, buf.length);
        }
        if (cnt != 0) {
            cnt = (int) Math.floor(8 * (5 - cnt) / 5);
            b32 = new StringBuilder(b32.substring(0, b32.length() - cnt) + "======".substring(0, cnt));
        }
        return b32.toString();
    }

    private String b32enc_do40bits(int[] b40) {
        long number = 0;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 5; i++)
            number = number * 256 + b40[i];

        for (int i = 0; i < 8; i++, number /= 32)
            s.insert(0, B32ENC_MAP.charAt((int) number & 0x1f));

        return s.toString();
    }
}