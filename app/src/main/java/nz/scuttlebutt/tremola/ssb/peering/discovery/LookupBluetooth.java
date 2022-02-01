package nz.scuttlebutt.tremola.ssb.peering.discovery;

import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import nz.scuttlebutt.tremola.MainActivity;
import nz.scuttlebutt.tremola.ssb.core.SSBid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class LookupBluetooth extends LookupClient {


    private final BluetoothAdapter bluetoothAdapter;

    public static final int BLUETOOTH_ENABLE = 1;
    public static final int BLUETOOTH_DISCOVERABLE = 2;
    public static final UUID TREMOLA_UUID = UUID.fromString("044c55e3-a93c-4d5d-b53d-b3d0a3a942d5");

    // Create a BroadcastReceiver for ACTION_FOUND.
    private BroadcastReceiver receiver;
    private String message;
    private boolean isActiveQuery = false;

    public LookupBluetooth(Lookup lookup, Context context, SSBid ed25519KeyPair, ReentrantLock lock) {
        super(lookup, context, ed25519KeyPair, lock);
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    public void run() {
        try {
            BluetoothServerSocket bss = bluetoothAdapter.
                    listenUsingRfcommWithServiceRecord("tremola_lookup", TREMOLA_UUID);
            BluetoothSocket socket;
            while (true) {
                try {
                    socket = bss.accept();
                } catch (IOException e) {
                    Log.e("BLUETOOTH", "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    new LookupBTSocket(socket).start();
                    bss.close();
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prepare the query and launch the lookup for devices.
     *
     * @param broadcastMessage the message to send
     */
    @Override
    public void sendQuery(String broadcastMessage) {
        //TODO
        Log.e("BLUETOOTH", "Trying to send " + broadcastMessage);
        message = broadcastMessage;
        isActiveQuery = true;
        // activate search
    }

    private void sendMessage(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.e("BLUETOOTH", "Trying to send a message to " + device.getAlias());
        }
        bluetoothAdapter.cancelDiscovery();
        BluetoothSocket socket;
        try {
            socket = device.createRfcommSocketToServiceRecord(TREMOLA_UUID);
            new LookupBTSocket(socket, message);
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        bluetoothAdapter.enable();
    }

    @Override
    void closeQuery(String message) {
        Log.e("BLUETOOTH", "Closing query " + message + " containing " + this.message);
        if (message.contains(this.message)) {
            isActiveQuery = false;
            this.message = null;
        }
    }

    public boolean enable() {
        if (bluetoothAdapter == null)
            return false;
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((MainActivity) context).startActivityForResult(enableIntent, BLUETOOTH_ENABLE);

            Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            ((MainActivity) context).startActivityForResult(discoveryIntent, BLUETOOTH_DISCOVERABLE);
        }

        receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.e("BLUETOOTH", "Received an answer: " + action);
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (isActiveQuery)
                        sendMessage(device);
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);

        return bluetoothAdapter.isEnabled();
    }

    private class LookupBTSocket extends Thread {

        private final BluetoothSocket socket;
        private final boolean isServer;
        private final Handler handler;
        private byte[] message;

        public LookupBTSocket(BluetoothSocket socket) {
            this.socket = socket;
            this.isServer = true;
            handler = new Handler(Looper.myLooper());
        }

        public LookupBTSocket(BluetoothSocket socket, String message) {
            this.socket = socket;
            this.isServer = false;
            handler = new Handler(Looper.myLooper());
            this.message = message.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void run() {
            byte[] buf = new byte[512];
            if (isServer) {
                try {
                    InputStream in = socket.getInputStream();
                    int numberOfBytes = in.read(buf);
                    String i = String.valueOf(handler.obtainMessage(0, numberOfBytes, -1,
                            buf));
                    Log.e("bt_rx " + numberOfBytes, "<" + i + ">");
                    if (i.contains("\"msa\"")) {
                        lookup.processQuery(i);
                    } else if (i.contains("\"targetId\"")) {
                        lookup.processReply(i);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(message);
                    Message sent = handler.obtainMessage(1, -1, -1, buf);
                    sent.sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}