package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import nz.scuttlebutt.tremola.ssb.TremolaState;
import nz.scuttlebutt.tremola.ssb.core.Crypto;
import nz.scuttlebutt.tremola.ssb.core.SSBid;
import nz.scuttlebutt.tremola.ssb.db.entities.Contact;
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry;
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
import java.util.concurrent.locks.ReentrantLock;

import static nz.scuttlebutt.tremola.ssb.core.Crypto.signDetached;

public class LookUp extends Thread {

    private final Context context;
    private final String localAddress;
    private int port;
    private nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private final int HOP_COUNT = 4;
    private static int queryIdentifier = 0;
    private final TremolaState tremolaState;
    private String incomingRequest = null;
    private LinkedList<ReceivedQuery> logOfReceivedQueries;
    private LookUpUDP lookUpUDP;
    private LookUpBluetooth lookUpBluetooth;
    private String udpBroadcastAddress;
    private String targetName;
    private final String B32ENC_MAP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private String incomingAnswer;


    public LookUp(String localAddress, Context context, TremolaState tremolaState) {
        this.tremolaState = tremolaState;
        this.context = context;
        this.localAddress = localAddress;
    }

    public void listen(int port, ReentrantLock lock) throws InterruptedException {
        this.port = port;
        this.ed25519KeyPair = tremolaState.getIdStore().getIdentity();

        lookUpUDP = new LookUpUDP(this, context, port, ed25519KeyPair);
        lookUpUDP.listen(lock);

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w("BLUETOOTH", "Bluetooth disabled");
        } else {
            lookUpBluetooth = new LookUpBluetooth(this, context, port, ed25519KeyPair, bluetoothAdapter);
        }
    }

    /**
     * Store the needed information before launching the Thread.
     *
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
     *
     * @param incomingRequest the received query
     */
    public void acceptQuery(@NotNull String incomingRequest) {
        this.incomingRequest = incomingRequest;
    }

    /**
     * Store the value received from the second step (to close a query).
     *
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
            message.put("queryId", queryId);
            if (signature == null) {
                signature = signDetached(message.toString().getBytes(StandardCharsets.UTF_8),
                        Objects.requireNonNull(ed25519KeyPair.getSigningKey()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                message.put("signature", Base64.getEncoder().encodeToString(signature));
            }
            message.put("hop", hopCount);
        } catch (JSONException e) {
            Log.e("LOOKUP_JSON", e.getMessage());
        }
        return message.toString();
    }

    /**
     * Process an incoming request by discarding, answering or forwarding it.
     */
    public void processQuery() {
        Log.e("INPUT", incomingRequest);
        if (logOfReceivedQueries == null)
            logOfReceivedQueries = new LinkedList<>();
        try {
            JSONObject data = new JSONObject(incomingRequest);
            String msa = data.get("msa").toString();
            if (msa.endsWith(Objects.requireNonNull(ed25519KeyPair.toRef())))
                return; // I'm the initiator
            int queryId = data.getInt("queryId");
            String[] multiServerAddress = msa.split("~");
            String initId = multiServerAddress[1].split(":")[1];
            Log.e("INPUT2", incomingRequest);
            for (Object object : logOfReceivedQueries.toArray()) {
                ReceivedQuery query = (ReceivedQuery) object;
                if (query.isOutDated()) {
                    logOfReceivedQueries.remove(query);
                } else if (query.isEqualTo(initId, queryId)) {
                    Log.e("QUERY", "Already in db");
                    return; // the query is already in the database
                }
            }
            Log.e("INPUT3", incomingRequest);

            String shortName = data.getString("targetName");
            int hopCount = data.getInt("hop");
            String sig = data.getString("signature");
            JSONObject message = new JSONObject();
            byte[] signature = new byte[0];
            try {
                message.put("targetName", shortName);
                message.put("msa", msa);
                message.put("queryId", queryId);
                String verifyKey = initId.substring(1, initId.length() - 8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    signature = Base64.getDecoder().decode(sig);
                    byte[] verify = Base64.getDecoder().decode(verifyKey);
                    if (!Crypto.verifySignDetached(signature,
                            message.toString().getBytes(StandardCharsets.UTF_8),
                            verify)) {
                        Log.e("VERIFY", "verify failure");
                        return;
                    }
                }
                Log.d("VERIFY", "Verified successfully!!!");
            } catch (Exception e) {
                Log.e("VERIFY", msa);
            }
            logOfReceivedQueries.add(
                    new ReceivedQuery(shortName, initId, hopCount, queryId));

            String targetPublicKey = searchDataBase(shortName);
            if (targetPublicKey != null) {
                replyStep2(initId, queryId, shortName, hopCount, targetPublicKey, multiServerAddress[0]);
            } else {
                if (hopCount-- > 0) {
                    String msg = createMessage(shortName, msa, queryId, hopCount, signature);
                    if (lookUpUDP != null) {
                        Log.e("OUTPUT", msg);
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

    public void processReply() {
        Log.d("REPLY", incomingAnswer);
        try {
            JSONObject data = new JSONObject(incomingAnswer);
            String targetId = data.getString("targetId");
            String targetShortName = data.getString("targetName");
            String initId = data.getString("initiatorId");
            int queryId = data.getInt("queryId");
            String friendId = data.getString("friendId");
            int hopCount = data.getInt("hop");
            String sig = data.getString("signature");
            JSONObject message = new JSONObject();
            byte[] signature;
            try {
                message.put("targetId", targetId);
                message.put("targetName", targetShortName);
                message.put("initiatorId", initId);
                message.put("queryId", queryId);
                message.put("friendId", friendId);
                message.put("hop", hopCount);
                String verifyKey = friendId.substring(1, friendId.length() - 8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    signature = Base64.getDecoder().decode(sig);
                    byte[] verify = Base64.getDecoder().decode(verifyKey); if (!Crypto.verifySignDetached(signature,
                            message.toString().getBytes(StandardCharsets.UTF_8),
                            verify)) {
                        Log.e("VERIFY", "verify failure");
                         return;
                    }
                }
                Log.d("VERIFY", "Verified successfully!!!");
            } catch (Exception e) {
                Log.e("VERIFY", friendId);
            }
            if (!initId.equals(ed25519KeyPair.toRef())) {
                Log.e("VERIFY", initId + " : " + ed25519KeyPair.toRef());
                return; // I am not the initiator of this request
            }

            Log.e("PROCESS_REPLY", "LOOKUP accepted!!!");
            String targetPublicKey = searchDataBase(targetShortName);
            if (targetPublicKey != null) {
                // TODO Contact already exists in database; I have to choose what I want
//                return;
            }
            addNewContact(targetId, targetShortName);

            Log.e("PROCESS_REPLY", "I DID IT!!!");
        } catch (Exception e) {
            Log.e("PROCESS_REPLY", "Problem in process");
            e.printStackTrace();
        }
    }

    private void addNewContact(String targetId, String targetShortName) {
        tremolaState.addContact(targetId, null);

        String rawStr = tremolaState.getMsgTypes().mkFollow(targetId, false);
        LogEntry evnt = tremolaState.getMsgTypes().jsonToLogEntry(rawStr,
                rawStr.getBytes(StandardCharsets.UTF_8));

        tremolaState.wai.rx_event(evnt);
        tremolaState.getPeers().newContact(targetId); // inform online peers via EBT
        String eval = "b2f_new_contact_lookup('" + targetShortName + "','" + targetId + "')";
        tremolaState.wai.eval(eval);
        Log.e("EVAL", eval);
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
                    String alias = contact.getAlias();
                    Log.d("CONTACT", (alias == null ? "no alias" : alias));
                    return contact.getLid();
                }
            }
        }
        return null;
    }

    /**
     * Send a query that comes from front end.
     * Must be done for each available medium.
     */
    public void sendQuery() {
        String broadcastMessage = createMessage(targetName);
        Log.d("SIGNING", broadcastMessage);
        if (lookUpUDP != null) {
            lookUpUDP.prepareQuery(udpBroadcastAddress);
            lookUpUDP.sendQuery(broadcastMessage);
        }
//        if (lookUpBluetooth != null) {
//            lookUpBluetooth.scanLeDevice();
//            lookUpBluetooth.sendQuery(broadcastMessage);
//        }
    }

    private void replyStep2(String initId, int queryId, String targetShortName, int hopCount, String targetId, String initAddress) {
        // TODO check
        JSONObject reply = new JSONObject();
        try {
            reply.put("targetId", targetId);
            reply.put("targetName", targetShortName);
            reply.put("initiatorId", initId);
            reply.put("queryId", queryId);
            reply.put("friendId", ed25519KeyPair.toRef());
            reply.put("hop", hopCount);
            byte[] signature = signDetached(reply.toString().getBytes(StandardCharsets.UTF_8),
                    Objects.requireNonNull(ed25519KeyPair.getSigningKey()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reply.put("signature", Base64.getEncoder().encodeToString(signature));
            }
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