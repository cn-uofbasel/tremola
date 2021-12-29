package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import nz.scuttlebutt.tremola.ssb.TremolaState;
import nz.scuttlebutt.tremola.ssb.core.Crypto;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.ssb.db.entities.Contact;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Objects;

import static nz.scuttlebutt.tremola.ssb.core.Crypto.signDetached;

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
    private LookUpBluetooth lookUpBluetooth;
    private String udpBroadcastAddress;
    private String targetName;
    private final String B32ENC_MAP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private String incomingAnswer;


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
            Log.w("BLUETOOTH", "Bluetooth disabled");
        } else {
            lookUpBluetooth = new LookUpBluetooth(ipAddress, port, ed25519KeyPair, bluetoothAdapter);
        }
    }

    /**
     * Store the needed information before launching the Thread.
     * @param broadcastAddress the udp address to broadcast the query
     * @param targetName       the target name written by the user
     */
    public boolean prepareQuery(String broadcastAddress, String targetName) {
        this.udpBroadcastAddress = broadcastAddress;
        this.targetName = targetName;
        return searchDataBase(targetName) == null;
    }

    /**
     * Store a request received by any mean (for now udp or bluetooth)
     * @param incomingRequest the received query
     */
    public void acceptQuery(@NotNull String incomingRequest) {
        this.incomingRequest = incomingRequest;
    }

    /**
     * Store the value received from the second step (to close a query).
     * @param incomingAnswer a public key as answer
     */
    public void acceptReply(@NotNull String incomingAnswer) {
        this.incomingAnswer = incomingAnswer;
    }

    /**
     * Implemented as a thread not to block udp.listen().
     * IncomingRequest is null if the query was received  from the front-end
     */
    public void run() {
        if (incomingAnswer != null) {
            processReply();
            incomingAnswer = null;
        } else if (incomingRequest != null) {
            processQuery();
            incomingRequest = null;
        } else {
            sendQuery();
        }
        this.interrupt();
    }

    /**
     * Create the payload of the query.
     *
     * @param targetName the queried shortName
     * @return the payload
     */
    private String createMessage(String targetName) {
        String selfMultiServerAddress = "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toRef();
        return createMessage(targetName, selfMultiServerAddress, queryIdentifier++, HOP_COUNT, null);
    }

    /**
     * Create a message to broadcast
     *
     * @param targetName         the name of the target
     * @param multiServerAddress the address of the query initiator
     * @param queryId            an id to identify the query
     * @param hopCount           the decrementing number of hop as a time-to-live
     * @return the message ready to send
     */
    private String createMessage(String targetName, String multiServerAddress, int queryId, int hopCount, byte[] signature) {
        JSONObject message = new JSONObject();
        try {
            message.put("targetName", targetName);
            message.put("msa", multiServerAddress);
            message.put("queryID", queryId);
            if (signature == null)
                signature = signDetached(message.toString().getBytes(StandardCharsets.UTF_8),
                        Objects.requireNonNull(ed25519KeyPair.getSigningKey()));
            message.put("signature", signature);
            message.put("hop", hopCount);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        return message.toString();
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
                return; // I'm the initiator
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
            byte[] signature = data.getString("signature").getBytes(StandardCharsets.UTF_8);
            JSONObject message = new JSONObject();
            try {
                message.put("targetName", targetName);
                message.put("msa", msa);
                message.put("queryID", queryID);
                if (!Crypto.verifySignDetached(signature,
                        message.toString().getBytes(StandardCharsets.UTF_8),
                        initID.substring(1, initID.length() - 9).
                                getBytes(StandardCharsets.UTF_8))) {
                    Log.e("VERIFY", "verify failure");
                    // TODO fix signature
                    // return;
                }
                Log.d("VERIFY", "Verified successfully!!!");
            } catch (Exception e) {
                Log.e("VERIFY", msa);
            }
            logOfReceivedQueries.add(
                    new ReceivedQuery(shortName, initID, hopCount, queryID));

            String targetPublicKey = searchDataBase(shortName);
            if (targetPublicKey != null) {
                replyStep2(initID, queryID, shortName, hopCount, targetPublicKey, multiServerAddress[0]);
            } else {
                if (hopCount > 0) {
                    String msg = createMessage(targetName, msa, queryID, hopCount - 1, signature);
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
            Log.e("PROCESS_QUERY", "Problem in process");
            e.printStackTrace();
        }
    }

    private void processReply() {
        Log.d("REPLY", incomingAnswer);
        try {
            JSONObject data = new JSONObject(incomingAnswer);
            String initID = data.getString("initiatorId");
            int queryId = data.getInt("queryID");
            String targetShortName = data.getString("targetName");
            String targetID = data.getString("targetID");
            int hopCount = data.getInt("hop");
            byte[] signature = data.getString("signature").getBytes(StandardCharsets.UTF_8);
            String signingKey = data.getString("friendId");
            JSONObject message = new JSONObject();
            try {
                message.put("initiatorId", initID);
                message.put("queryId", queryId);
                message.put("targetName", targetShortName);
                message.put("targetId", targetID);
                message.put("hop", hopCount);
                message.put("friendId", signingKey);
                if (!Crypto.verifySignDetached(signature,
                        message.toString().getBytes(StandardCharsets.UTF_8),
                        signingKey.getBytes(StandardCharsets.UTF_8))) {
                    Log.e("VERIFY", "verify failure");
                    // TODO fix signature
                    // return;
                }
                Log.d("VERIFY", "Verified successfully!!!");
            } catch (Exception e) {
                Log.e("VERIFY", signingKey);
            }
            if (!initID.equals(ed25519KeyPair.toRef())) {
                return; // I am not the initiator of this request
            }

            String targetPublicKey = searchDataBase(targetShortName);
            if (targetPublicKey != null) {
                // TODO Contact already exists in database; I have to choose what I want
                return;
            }
            tremolaState.addContact(targetID, null);
        } catch (Exception e) {
            Log.e("PROCESS_QUERY", "Problem in process");
            e.printStackTrace();
        }
    }

    /**
     * Search in the database if the targetName is known.
     *
     * @param targetShortName the target 10 char shortName
     * @return the public key if found, else null
     */
    private String searchDataBase(String targetShortName) {
        if (keyIsTarget(targetShortName, ed25519KeyPair.toRef())) {
            return ed25519KeyPair.toExportString();
        } else {
            for (Contact contact : tremolaState.getContactDAO().getAll()) {
                if (keyIsTarget(targetShortName, contact.getLid())) {
                    Log.d("CONTACT", contact.getAlias());
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
        Log.d("SIGNING", broadcastMessage);
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

    private void replyStep2(String initID, int queryId, String targetShortName, int hopCount, String targetID, String initAddress) {
        // TODO check
        JSONObject reply = new JSONObject();
        try {
            reply.put("initiatorId", initID);
            reply.put("queryId", queryId);
            reply.put("targetName", targetShortName);
            reply.put("targetId", targetID);
            reply.put("hop", hopCount);
            reply.put("friendId", ed25519KeyPair.getSigningKey());
            byte[] signature = signDetached(reply.toString().getBytes(StandardCharsets.UTF_8),
                    Objects.requireNonNull(ed25519KeyPair.getSigningKey()));
            reply.put("signature", signature);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        String answer = reply.toString();
        try {
            String[] address = initAddress.split(":");
            final DatagramSocket datagramSocket = new DatagramSocket();
            InetAddress receiverAddress = InetAddress.getByName(address[1]);

            final DatagramPacket datagramPacket = new DatagramPacket(
                    answer.getBytes(),
                    answer.length(),
                    receiverAddress,
                    Integer.parseInt(address[2])
            );

            Log.d("Reply Address", receiverAddress.toString());

            datagramSocket.send(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compare a public key with a short name.
     *
     * @param receivedShortName The 11 char (including a '-') short name
     * @param publicKey         The public key in the form "@[...].ed25519
     * @return true if the 2 match
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
            Log.e("SHORTNAME", e.getMessage());
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