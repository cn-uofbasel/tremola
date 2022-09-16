package nz.scuttlebutt.tremola

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.*
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.text.format.Formatter
import android.util.Log
import android.view.Window
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import nz.scuttlebutt.tremola.ssb.TremolaState
import nz.scuttlebutt.tremola.ssb.peering.RpcResponder
import nz.scuttlebutt.tremola.ssb.peering.RpcServices
import nz.scuttlebutt.tremola.ssb.peering.UDPbroadcast
import nz.scuttlebutt.tremola.ssb.peering.discovery.Lookup
import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.utils.getBroadcastAddress
import nz.scuttlebutt.tremola.utils.getLocalIpAddress
import java.lang.Thread.sleep
import java.net.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class MainActivity : Activity() {
    /** Documents the backend state of the app, mostly information about local peer. */
    private lateinit var tremolaState: TremolaState

    /** Contains the UDP socket that sends broadcasts. */
    var broadcastSocket: DatagramSocket? = null

    /** Contains the UDP socket that sends lookup broadcasts. */
    var lookupSocket: DatagramSocket? = null

    /** Contains the socket that is the server. */
    private var serverSocket: ServerSocket? = null

    /** The object responsible for UDP broadcasts to find peers and get found by peers. */
    var udp: UDPbroadcast? = null

    /** Contains the object responsible for the lookup queries. */
    var lookup: Lookup? = null

    /** The object responsible for Wi-Fi network requests. */
    private val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    /** The object responsible for NetworkRequests callbacks. */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Part of the Android standard library, called when the app is launched.
     * @property savedInstanceState The saved state of the app. Unused.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the GUI.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        // Initialize the tremolaState variable and the sockets.
        tremolaState = TremolaState(this)
        mkSockets()

        // Create an SSB Identity (i.e. a private/public key pair) and print the public key.
        Log.d("IDENTITY", "is ${tremolaState.idStore.identity.toRef()}")

        // Initialize the WebView used for the GUI.
        // This is also important for the communication between front- and backend.
        val webView = findViewById<WebView>(R.id.webView)
        tremolaState.wai = WebAppInterface(this, tremolaState, webView)
        webView.setBackgroundColor(0) // Color.parseColor("#FFffa0a0"))
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(tremolaState.wai, "Android")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl("file:///android_asset/web/tremola.html")
        // webSettings?.javaScriptCanOpenWindowsAutomatically = true

        // Create the networkCallback object to react to connectivity changes if it does not exist:
        if (networkCallback == null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    Log.d("onLost", "$network")
                    super.onLost(network)
                    /*
                    try { broadcast_socket?.close() } catch (e: Exception) {}
                    broadcast_socket = null
                    try { server_socket?.close() } catch (e: Exception) {}
                    server_socket = null
                    */
                }

                override fun onLinkPropertiesChanged(nw: Network, prop: LinkProperties) {
                    Log.d("onLinkPropertiesChanged", "$nw $prop")
                    super.onLinkPropertiesChanged(nw, prop)
                    /*
                    server_socket?.let {
                        if (it.inetAddress in prop.linkAddresses) {
                            Log.d("onLinkPropertiesChanged", "no need for new sock")
                            return
                        }
                    }
                    */
                    mkSockets()
                }
                /*
                override fun onAvailable(network: Network) {
                    Log.d("onAvailable", "${network}")
                    super.onAvailable(network)
                }
                */
            }
        }

        // Look for connected peers and advertise itself on network.
        // The peers are shown in the third (rightmost) pane of the GUI, the connex scenario.
        udp = UDPbroadcast(this, tremolaState.wai)
        val lck = ReentrantLock()

        // Thread that broadcasts the user's SSB public key.
        val t0 = thread(isDaemon = true) {
            try {
                udp!!.beacon(
                    tremolaState.idStore.identity.verifyKey,
                    lck,
                    Constants.SSB_IPV4_TCP_PORT // FIXME Shouldn't this be SSB_IPV4_UDP_PORT?
                )
            } catch (e: Exception) {
                Log.d("beacon thread", "died $e")
            }
        }

        // Thread that listens to peers broadcasting their SSB public key.
        val t1 = thread(isDaemon = true) {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val mLock = wifi.createMulticastLock("lock")
            mLock.acquire()
            try {
                udp!!.listen(lck)
            } catch (e: Exception) {
                Log.e("listen thread", "died $e" + e.stackTraceToString())
            }
        }

        // Thread that accepts SSB handshakes, it is robust against a reassigned serverSocket.
        val t2 = thread(isDaemon = true) {
            while (true) {
                var socket: Socket?
                try {
                    socket = serverSocket!!.accept()
                } catch (e: Exception) {
                    sleep(3000)
                    continue
                }
                thread(isDaemon = true) { // One thread per connection.
                    val rpcStream = RpcResponder(
                        tremolaState, socket,
                        Constants.SSB_NETWORK_IDENTIFIER
                    )
                    rpcStream.defineServices(RpcServices(tremolaState))
                    rpcStream.startStreaming()
                }
            }
        }


        // Initialize object to let a user send, relay or respond to a lookup request.
        lookup = Lookup(
            getLocalIpAddress(this),
            this,
            tremolaState,
            getBroadcastAddress(this).hostAddress
        )
        val lookupLock = ReentrantLock()

        // Thread that concerns itself with sending, relaying and responding to lookup requests.
        val t3 = thread(isDaemon = true) {
            try {
                lookup!!.listen(Constants.LOOKUP_IPV4_UDP_PORT, lookupLock)
            } catch (e: Exception) {
                Log.e("lookup thread", "died $e" + e.stackTraceToString())
            }
        }

        t0.priority = 10
        t1.priority = 10

        t2.priority = 6
        t3.priority = 1

        Log.d(
            "Thread priorities",
            "${t0.priority} ${t1.priority} ${t2.priority} ${t3.priority}"
        )
    }

    /**
     * Override the default function when pressing the back button to work on WebKit.
     */
    override fun onBackPressed() {
        tremolaState.wai.eval("onBackPressed();")
    }

    /**
     * Used when the frontend sends the command "onBackPressed" to the backend. Closes the app.
     */
    fun trueBackPress() {
        Handler(this.mainLooper).post {
            super.onBackPressed()
        }
    }

    // pt 5 in
    // https://betterprogramming.pub/5-android-webview-secrets-you-probably-didnt-know-b23f8a8b5a0c

    /**
     * Called when a child [Activity] is closed. This is used after the camera is opened to scan a
     * QR code. Handles the scanned data. TODO read
     */
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        Log.e("ACT_RESULT", "Result for $resultCode")
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) { // Normal result code on return
            Log.d("activityResult", result.toString())
            val cmd: String = if (result.contents == null) { // No data returned, scan aborted
                "qr_scan_failure();"
            } else { // Scan successful
                "qr_scan_success('" + result.contents + "');"
            }
            tremolaState.wai.eval(cmd)
        } else {
            Toast.makeText(
                this,
                "Activity result: $requestCode", Toast.LENGTH_LONG
            ).show()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Tries to restart the network after an interruption of the app by something else.
     * After the app was brought to the background (e.g. by a Wi-Fi selection menu popping up) and
     * the app is brought back into the foreground, this is called.
     */
    override fun onResume() {
        Log.d("onResume", "")
        super.onResume()
        try {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.d("onResume", "Exception: $e")
        }
    }

    /**
     * Tries to stop the network when the app is backgrounded by something else.
     */
    override fun onPause() {
        Log.d("onPause", "")
        super.onPause()
        try {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.e("onPause", "Exception: $e")
        }
    }

    /**
     * When the instance state is saved, this prints debug info. Unused.
     */
    fun onSaveInstanceState() {
        Log.d("onSaveInstanceState", "")
    }

    /**
     * When the app is exited out of (not fully closed), this is used. Prints debug info.
     */
    override fun onStop() {
        Log.d("onStop", "")
        super.onStop()
    }

    /**
     * When the app is fully closed, this is used. Closes the network.
     * TODO potentially incomplete. Not all sockets are closed, but this is probably handled by the
     *  system anyway.
     */
    override fun onDestroy() {
        Log.d("onDestroy", "")
        try {
            broadcastSocket?.close()
            lookupSocket?.close()
        } catch (e: Exception) {
        }
        broadcastSocket = null
        lookupSocket = null
        super.onDestroy()
    }

    /**
     * Initializes the sockets (on create or network change).
     * Tries to close the network sockets first, then sets new ones up.
     */
    private fun mkSockets() {
        try { // Close broadcast and lookup sockets
            broadcastSocket?.close()
            lookupSocket?.close()
        } catch (e: Exception) {
            Log.e("mkSockets", ": ${e.localizedMessage}")
        }
        try { // Reopen broadcast socket
            broadcastSocket = DatagramSocket(
                Constants.SSB_IPV4_UDP_PORT, // Where to listen
                InetAddress.getByName("0.0.0.0")
            )
            broadcastSocket!!.reuseAddress = true
            broadcastSocket?.broadcast = true
            Log.d(
                "mkSockets: new broadcast socket",
                "${broadcastSocket}, UDP port ${broadcastSocket?.localPort}"
            )
        } catch (e: BindException) {
            Log.e("mkSockets", ": broadcast ${e.localizedMessage}")
        }
        try { // Reopen lookup socket
            lookupSocket = DatagramSocket(
                Constants.LOOKUP_IPV4_UDP_PORT, // Where to listen
                InetAddress.getByName("0.0.0.0")
            )
            lookupSocket!!.reuseAddress = true
            lookupSocket?.broadcast = true
            Log.d(
                "mkSockets: new lookup socket",
                "${lookupSocket}, UDP port ${lookupSocket?.localPort}"
            )
        } catch (e: BindException) {
            Log.e("mkSockets", ": lookup ${e.localizedMessage}")
        }
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        try { // Restart server socket
            serverSocket?.close()
            serverSocket = ServerSocket(Constants.SSB_IPV4_TCP_PORT)
            Log.d(
                "Server TCP address",
                // FIXME deprecated, should be replaced
                Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress) +
                        ":${serverSocket!!.localPort}"
            )
        } catch (e: Exception) {
            Log.e("mkSockets", ": server ${e.localizedMessage}")
        }
    }
}