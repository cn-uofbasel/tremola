package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import nz.scuttlebutt.tremola.MainActivity;
import nz.scuttlebutt.tremola.ssb.TremolaState;
import nz.scuttlebutt.tremola.ssb.core.Crypto;
import nz.scuttlebutt.tremola.ssb.db.entities.Contact;
import nz.scuttlebutt.tremola.ssb.db.entities.LogEntry;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static nz.scuttlebutt.tremola.ssb.core.Crypto.signDetached;

public class Lookup {

    public static final long DELAY = 5000L;
    private final Context context;
    private final TremolaState tremolaState;
    private final nz.scuttlebutt.tremola.ssb.core.SSBid ed25519KeyPair;
    private LinkedList<LookupClient> lookupClients;

    private final String localAddress;
    private int port;
    private static int queryIdentifier = 0;
    private final LinkedList<Query> logOfReceivedQueries = new LinkedList<>();
    private final LinkedList<Query> logOfReceivedReplies = new LinkedList<>();
    private final String udpBroadcastAddress;
    private final Map<String, Boolean> sentQuery;

    public Lookup(String localAddress, Context context, TremolaState tremolaState, String udpBroadcastAddress) {
        this.tremolaState = tremolaState;
        this.context = context;
        this.localAddress = localAddress;
        this.udpBroadcastAddress = udpBroadcastAddress;
        this.ed25519KeyPair = tremolaState.getIdStore().getIdentity();
        sentQuery = new HashMap<>();
    }

    /**
     * Instantiate the lookupClients and start the listening loop.
     *
     * @param port the UDP port used by this protocol
     * @param lock the lock to wait in case of an exception
     */
    public void listen(int port, ReentrantLock lock) {
        this.port = port;
        lookupClients = new LinkedList<>();

        LookupUDP lookupUDP = new LookupUDP(this, context, ed25519KeyPair, lock, port, udpBroadcastAddress);
        lookupClients.add(lookupUDP);

        for (LookupClient client : lookupClients) {
            client.start();
        }
    }

    private void closeQuery(String message) {
        for (LookupClient client : lookupClients) {
            if (client.isActive()) {
                client.closeQuery(message);
                Log.e("LOOKUP", "Close query for " + client.getSubClass());
            }
        }
    }

    /**
     * Store the needed information and starts a timer to notify the user
     * if no valid reply is received.
     * Handles the notifications if the contact is known.
     *
     * @param targetName the target name written by the user
     * @return true if the contact is not known
     */
    public String prepareQuery(String targetName) {
        for (LookupClient client : lookupClients) {
            if (!client.isActive()) {
                Log.e("PREPARE_QUERY", "Client is not active!!!");
                try {
                    client.reactivate();
                    client.start();
                    Log.e("CLIENTS", "Client reactivated " + client.getSubClass());
                } catch (Exception e) {
                    client.close();
                    e.printStackTrace();
                }
            }
        }
        String broadcastMessage = createMessage(targetName);
        String databaseSearch = searchDataBase(targetName);
        if (databaseSearch != null) {
            Log.e("NOTIFY", databaseSearch);
            if (databaseSearch.equals(ed25519KeyPair.toRef())) {
                notify(targetName, "Shortname \"" + targetName + "\" is your own shortname.", false);
                return null;
            }
            String alias;
            try {
                alias = Objects.requireNonNull(tremolaState.getContactDAO().getContactByLid(databaseSearch)).getAlias();
                alias = Objects.equals(alias, "null") ? targetName : alias;
            } catch (Exception e) {
                alias = targetName;
            }
            notify(targetName, "Shortname \"" + targetName
                    + "\" is in your contacts as " + alias + ".", false);
            return null;
        }
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                String text = "No result found for \"" + targetName + "\"";
                Lookup.this.notify(targetName, text, false);
            }
        }, DELAY);
        sentQuery.put(targetName, false);
        return broadcastMessage;
    }

    /**
     * Send a query that comes from front end.
     * Must be done for each available medium.
     */
    public void sendQuery(String broadcastMessage) {
        for (LookupClient client : lookupClients) {
            if (client.isActive()) {
                client.sendQuery(broadcastMessage);
            } else {
                Log.e("SEND_QUERY", "Client is not active!!!");
            }
        }
    }

    /**
     * Process an incoming request by discarding, answering or forwarding it.
     */
    public void processQuery(@NotNull String incomingRequest) {
        Log.d("INPUT", id2b32(ed25519KeyPair.toRef()) + " " + incomingRequest);
        try {
            JSONObject data = new JSONObject(incomingRequest);
            String msa = data.get("msa").toString();
            if (msa.endsWith(Objects.requireNonNull(ed25519KeyPair.toRef()))) {
                Log.d("QUERY", "I am the initiator");
                return; // I am the initiator
            }
            String[] multiServerAddress = msa.split("~");
            String initId = multiServerAddress[1].split(":")[1];
            int queryId = data.getInt("queryId");
            if (checkLog(logOfReceivedQueries, initId, queryId)) {
                Log.d("QUERY", "Already in db");
                return; // the query is already in the database
            }

            String shortName = data.getString("targetName");
            int hopCount = data.getInt("hop");
            String sig = data.getString("signature");
            JSONObject message = new JSONObject();
            byte[] signature = new byte[0];
            try {
                message.put("targetName", shortName);
                message.put("msa", msa);
                message.put("queryId", queryId);
                if (signatureIsWrong(initId, message, sig)) {
                    Log.e("VERIFY", "Verification failed");
//                        return;
                }
            } catch (Exception e) {
                Log.e("VERIFY", msa);
            }

            String targetPublicKey = searchDataBase(shortName);
            if (targetPublicKey != null) {
                replyStep2(initId, queryId, shortName, hopCount, targetPublicKey, multiServerAddress[0]);
            } else {
                if (hopCount-- > 0) {
                    String msg = createMessage(shortName, msa, queryId, hopCount, signature);
                    for (LookupClient client : lookupClients) {
                        if (client.isActive()) {
                            client.sendQuery(msg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("PROCESS_QUERY", "Problem in process.");
        }
        Log.d("QUERY", "Exciting query processor");
    }

    /**
     * Process a reply from an initiated query by discarding it or adding a new Contact.
     *
     * @param incomingAnswer the received answer
     */
    public void processReply(@NotNull String incomingAnswer) {
        Log.d("REPLY", incomingAnswer);
        try {
            JSONObject data = new JSONObject(incomingAnswer);
            String initId = data.getString("initiatorId");
            int queryId = data.getInt("queryId");
            String targetShortName = data.getString("targetName");
            if (checkLog(logOfReceivedReplies, initId, queryId)) {
                Log.d("REPLY", "Already in: " + incomingAnswer);
                return; // the reply is already in the database
            }
            if (!initId.equals(ed25519KeyPair.toRef())) {
                Log.d("REPLY", "Forwarded: " + incomingAnswer);
                sendQuery(incomingAnswer);
                return; // Reply is not for me
            }
            String targetId = data.getString("targetId");
            String friendId = data.getString("friendId");
            int hopCount = data.getInt("hop");
            String sig = data.getString("signature");
            JSONObject message = new JSONObject();
            try {
                message.put("targetId", targetId);
                message.put("targetName", targetShortName);
                message.put("initiatorId", initId);
                message.put("queryId", queryId);
                message.put("friendId", friendId);
                message.put("hop", hopCount);
                if (signatureIsWrong(friendId, message, sig)) {
                    Log.e("VERIFY", "Verification failed");
                    return;
                }
            } catch (Exception e) {
                Log.e("VERIFY", "failed : " + friendId);
            }
            if (!targetId.matches("^@[a-zA-Z0-9+/]{43}=.ed25519$")) {
                Log.e("VERIFY", targetId + " : public key is not valid");
                return; // public key is not valid
            }

            String targetPublicKey = searchDataBase(targetShortName);
            if (targetPublicKey != null) {
                Log.e("OLD CONTACT", targetShortName + " already exists in database : " + targetPublicKey);
                // Contact already exists in database
                return;
            }
            addNewContact(targetId, targetShortName);
            notify(targetShortName, "\"" + targetShortName + "\" added to your contacts.", true);

        } catch (Exception e) {
            Log.e("PROCESS_REPLY", "Problem in process");
            e.printStackTrace();
        }
    }

    private boolean checkLog(LinkedList<Query> log, String initId, int queryId) {
        Query reply = new Query(initId, queryId);
        for (Object object : log.toArray()) {
            Query query = (Query) object;
            if (query.isOutDated()) {
                log.remove(query);
            } else if (query.isEqualTo(initId, queryId)) {
                return true;
            }
        }
        log.add(reply);
        return false;
    }

    /**
     * Notify the user of the result of the query.
     * Make sure that each query produces only one reply, except if
     * a positive reply comes in after the timer.
     *
     * @param targetName the name for the query, as id not to notify the
     *                   user more than once
     * @param text       the text to display
     * @param force      true if a contact was added, to make sure
     *                   the user is notified
     */
    private void notify(String targetName, String text, boolean force) {
        closeQuery(targetName);
        if (Boolean.FALSE.equals(sentQuery.remove(targetName)) || force) {
            ((MainActivity) context).runOnUiThread(
                    () -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
            sentQuery.put(targetName, true);
        }
    }

    /**
     * Create the payload of the query.
     *
     * @param targetName the queried shortName
     * @return the payload
     */
    private String createMessage(String targetName) {
        String selfMultiServerAddress = "net:" + localAddress + ":" + port + "~shs:" + ed25519KeyPair.toRef();
        int HOP_COUNT = 4;
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
     * Add a new contact in database in case of a successful lookup.
     *
     * @param targetId        the public key of the Target
     * @param targetShortName the ShortName of the Target
     */
    private void addNewContact(String targetId, String targetShortName) {
        tremolaState.addContact(targetId, null);

        String rawStr = tremolaState.getMsgTypes().mkFollow(targetId, false);
        LogEntry event = tremolaState.getMsgTypes().jsonToLogEntry(rawStr,
                rawStr.getBytes(StandardCharsets.UTF_8));

        assert event != null;
        tremolaState.wai.rx_event(event);
        tremolaState.getPeers().newContact(targetId); // inform online peers via EBT
        String eval = "b2f_new_contact_lookup('" + targetShortName + "','" + targetId + "')";
        tremolaState.wai.eval(eval);
    }

    /**
     * Search in the database if the targetName is known.
     *
     * @param targetShortName the target 10 char shortName
     * @return the public key if found, else null
     */
    private String searchDataBase(String targetShortName) {
        if (keyIsTarget(targetShortName, ed25519KeyPair.toRef())) {
            return ed25519KeyPair.toRef();
        } else {
            for (Contact contact : tremolaState.getContactDAO().getAll()) {
                if (keyIsTarget(targetShortName, contact.getLid())) {
                    return contact.getLid();
                }
            }
        }
        return null;
    }

    /**
     * Verify the authenticity of a message with its signature and author's key.
     *
     * @param initId  the author's public key
     * @param message the message to be verified
     * @param sig     the signature
     * @return true if the signature is correct
     */
    private boolean signatureIsWrong(String initId, JSONObject message, String sig) {
        String verifyKey = initId.substring(1, initId.length() - 8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            byte[] signature = Base64.getDecoder().decode(sig);
            byte[] verify = Base64.getDecoder().decode(verifyKey);
            return !Crypto.verifySignDetached(signature,
                    message.toString().getBytes(StandardCharsets.UTF_8),
                    verify);
        }
        return true;
    }

    /**
     * Send a reply to the Initiator in case of a successful lookup.
     *
     * @param initId          the Initiator's public key
     * @param queryId         the query identity
     * @param targetShortName the Target's ShortName
     * @param hopCount        the final hop count
     * @param targetId        the Target's public key
     * @param initAddress     the Initiator's Multi-server address, to reach him
     */
    private void replyStep2(String initId, int queryId, String targetShortName, int hopCount,
                            String targetId, String initAddress) {
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
        logOfReceivedReplies.add(new Query(initId, queryId));
        String answer = reply.toString();
        sendQuery(answer);
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

    /**
     * Compute a ShortName from a public key
     *
     * @param str a public key
     * @return a ShortName
     */
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

    /**
     * id2b32 helper function.
     *
     * @param bytes array of bytes from the public key
     * @return a ShortName
     */
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
            cnt = (int) Math.floor((double) 8 * (5 - cnt) / 5);
            b32 = new StringBuilder(b32.substring(0, b32.length() - cnt) + "======".substring(0, cnt));
        }
        return b32.toString();
    }

    /**
     * Computing and mapping of bytes for id2b32.
     *
     * @param b40 a 5 integer array, part of the public key
     * @return part of the ShortName
     */
    private String b32enc_do40bits(int[] b40) {
        long number = 0;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 5; i++)
            number = number * 256 + b40[i];

        String b32ENC_MAP = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        for (int i = 0; i < 8; i++, number /= 32)
            s.insert(0, b32ENC_MAP.charAt((int) number & 0x1f));

        return s.toString();
    }
}