package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import nz.scuttlebutt.tremola.MainActivity;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

public class LookupBluetooth extends LookupClient {

    private final BluetoothAdapter bluetoothAdapter;

    public static final int BLUETOOTH_ENABLE = 1;
    public static final int BLUETOOTH_DISCOVERABLE = 2;
    public static final UUID TREMOLA_UUID = UUID.fromString("044c55e3-a93c-4d5d-b53d-b3d0a3a942d5");

    private String message;
    private final LinkedList<BTSocket> paired = new LinkedList<>();

    public LookupBluetooth(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock) {
        super(lookup, context, ed25519KeyPair, lock);
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((MainActivity) context).startActivityForResult(enableIntent, BLUETOOTH_ENABLE);

            Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            ((MainActivity) context).startActivityForResult(discoveryIntent, BLUETOOTH_DISCOVERABLE);
        }
        active = enable();
    }

    @Override
    public void run() {
        active = true;
        BluetoothServerSocket bss;
        BluetoothSocket socket = null;
        try {
            bss = bluetoothAdapter.
                    listenUsingRfcommWithServiceRecord("tremola_lookup", TREMOLA_UUID);
            while (true) {
                Log.e("BLUETOOTH", " server is waiting for connection");
                try {
                    socket = bss.accept();
                } catch (IOException e) {
                    Log.e("BLUETOOTH", "Socket's accept() method failed", e);
                    active = false;
                    return;
                }
                if (socket != null) {
                    Log.e("BLUETOOTH", "Connected to " + socket.getRemoteDevice().getAddress() + "!!!!!!!");
                    BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    parseMessage(br.readLine());
                    BTSocket bTS = new BTSocket(socket);
                    bTS.start();
                    paired.add(bTS);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeSocket(socket);
            active = false;
        }
    }

    /**
     * Parse an incoming message.
     *
     * @param i the received query or reply.
     */
    private void parseMessage(String i) {
        Log.e("bt_rx " + i.length(), "<" + i + ">");
        if (i.contains("\"msa\"")) {
            lookup.processQuery(i);
        } else if (i.contains("\"targetId\"")) {
            lookup.processReply(i);
        }
    }

    /**
     * Prepare the query and launch the lookup for devices.
     * Trying to make it faster by sending to the paired sockets first,
     * then
     *
     * @param broadcastMessage the message to send
     * @return true if a connection is possible, otherwise don't try again.
     */
    @Override
    public void sendQuery(String broadcastMessage) throws FormatException {
        // Connexion oriented need a mark for the end of a block
        if (message.contains("\n"))
            throw new FormatException("Message must not contain line break char.");
        message = broadcastMessage + "\n";
        if (bluetoothAdapter == null) {
            active = false;
            return;
        }
        sendToSocket();
        // TODO Add connection to unpaired devices
        return;
    }

    @Override
    public void reactivate() {
        super.reactivate();
        bluetoothAdapter.enable();
        enable();
    }

    /**
     * Allows a swift closing of the Bluetooth client.
     */
    public void close() {
        super.close();
        Log.d("BLUETOOTH", "Closing");
        for (BTSocket s : paired) {
            closeSocket(s.getSocket());
            paired.remove(s);
        }
        bluetoothAdapter.cancelDiscovery();
    }

    /**
     * Send the current message to each socket that have an active connection.
     */
    private void sendToSocket() {
        paired.removeIf(socket -> !socket.sendMessage());
    }

    /**
     * Close a faulty socket, or if the Bluetooth access is no longer available.
     *
     * @param socket the socket to close.
     */
    private static void closeSocket(BluetoothSocket socket) {
        Log.d("BLUETOOTH", "Closing socket");
        try {
            socket.close();
        } catch (Exception e) {
            Log.d("BLUETOOTH", "Couldn't close socket");
            e.printStackTrace();
        }
    }

    @Override
    public void closeQuery(String message) {
        Log.e("BLUETOOTH", "Closing query " + message + " containing " + this.message);
        if (this.message != null && message.contains(this.message)) {
            this.message = null;
        }
    }

    private boolean enable() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            BTSocket bTS;
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                bTS = new BTSocket(device);
                bTS.start();
                if (!paired.contains(bTS))
                    paired.add(bTS);
            }
        }

        // Create a BroadcastReceiver for ACTION_FOUND. TODO
        BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.e("BLUETOOTH", "Received an answer: " + action);
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    BTSocket bTS = new BTSocket(device);
                    bTS.start();
                    if (!paired.contains(bTS))
                        paired.add(bTS);
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);

        return bluetoothAdapter.isEnabled();
    }

    private class BTSocket extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket = null;
        private boolean connected = false;
        private BufferedReader br;
        private OutputStream out;

        /**
         * Constructor for the client side.
         *
         * @param device the device to get the socket  from.
         */
        public BTSocket(BluetoothDevice device) {
            this.device = device;
            init();
        }

        /**
         * Constructor for server side.
         *
         * @param socket a socket to connect to.
         */
        public BTSocket(BluetoothSocket socket) {
            this.socket = socket;
            init();
        }

        private BluetoothSocket getSocket() {
            return socket;
        }

        /**
         * Send a message via the OutputStream.
         *
         * @return true if succeeded
         */
        private boolean sendMessage() {
            if (out == null) {
                //TODO delete that
                Log.e("OUT", "Not Working " + socket.getRemoteDevice().getAddress());
                InputStream in = null;
                try {
                    socket.connect();
                    out = socket.getOutputStream();
                    in = socket.getInputStream();
                    br = new BufferedReader(new InputStreamReader(in));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (out == null) {
                connected = false;
                return false;
            }
            try {
                out.write(message.getBytes(StandardCharsets.UTF_8));
                Log.e("_bt_wr", "Sent: " + message);
            } catch (IOException e) {
                Log.e("_BTS", "Failed to send message");
                connected = false;
            }

            return connected;
        }

        public boolean isConnected() {
            return connected;
        }

        /**
         * Initialise the In- and OutputStreams with BufferedReader.
         *
         * @return false if the connection is not possible.
         */
        private void init() {
            try {
                if (socket == null)
                    socket = device.createRfcommSocketToServiceRecord(TREMOLA_UUID);
                socket.connect();
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                br = new BufferedReader(new InputStreamReader(in));
            } catch (IOException e) {
                Log.e("_BTS", e.getMessage());
                connected = false;
                return;
            }
            connected = true;
        }

        @Override
        public void run() {
            Log.e("_BTS", "Trying to connect with" + device.getAddress());
            if (!connected)
                return;
            try {
                // TODO
                synchronized (lock) {
                    lock.wait(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    assert socket != null;
                    assert br != null;
                    assert socket.isConnected();
                    assert socket.getInputStream() != null;
                    parseMessage(br.readLine());
                } catch (IOException e) {
                    Log.e("_BTS", e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}