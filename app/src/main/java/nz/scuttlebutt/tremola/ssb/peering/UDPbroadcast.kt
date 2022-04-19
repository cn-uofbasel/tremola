package nz.scuttlebutt.tremola.ssb.peering

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter.formatIpAddress
import android.util.Base64
import android.util.Log
import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.WebAppInterface
import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.utils.Constants.Companion.UDP_BROADCAST_INTERVAL

import java.lang.Thread.sleep
import java.net.DatagramPacket
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock

/**
 * Handle broadcasting for connecting with peers, both for sending and listening.
 * TremolaState regularly fetches markOfLocalPeers to add it to the PeeringPool.
 * See 3rd panel ('connex') in the GUI.
 */
class UDPbroadcast(val context: MainActivity, val wai: WebAppInterface?) {

    /**
     * Tuple <multiaddr, last_seen> where
     * multiaddr is the SSB multiserver address of the form:
     *    'net:' ip_address ':' port 'shs:~' publicKey :
     *     net:192.168.121.169:8008~shs:uA2qyrA6OaSeDuSUjGtrxHU9nibaajIfVcY07cIrONc=
     * last_seen is the time of last action, in millisecond
     */
    var myMark: String? = null

    /**
     * A map of the marks of other peers available.
     */
    val markOfLocalPeers: MutableMap<String, Long> = HashMap<String, Long>()

    /**
     * Send broadcast
     */
    fun beacon(publicKey: ByteArray, lck: ReentrantLock, myTcpPort: Int) {

        /** Make datagram to send */
        fun mkDgram(): DatagramPacket {
            // get the current address (for each beacon msg)
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.getDhcpInfo() // assumes IPv4?
            val broadcast =
                dhcp.ipAddress or (0xff shl 24) // (-1 and dhcp.netmask.inv()) // dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3)
                quads[k] = (broadcast shr k * 8).toByte()
            // val dest = InetAddress.getByName("255.255.255.255")
            val bcastAddr = InetAddress.getByAddress(quads)
            val myAddr = wifiManager.connectionInfo.ipAddress
            // blocking?? Log.d("UDP BEACON", "my=${formatIpAddress(myAddr)}, broadcast=${bcastAddr}, mask=${dhcp.netmask.inv()}")
            myMark = "net:${formatIpAddress(myAddr)}:${myTcpPort}~shs:" +
                    Base64.encodeToString(publicKey, Base64.NO_WRAP)
            val buf = myMark!!.encodeToByteArray()
            return DatagramPacket(buf, buf.size, bcastAddr, Constants.SSB_IPV4_UDPPORT)
        }

        while (true) {
            try {
                val datagramPacket = mkDgram()
                val socket = context.broadcast_socket
                socket?.send(datagramPacket)
                // blocking?? Log.d("beacon sock", "bound=${s?.isBound}, ${s?.localAddress}/${s?.port}/${s?.localPort} brcast${s?.broadcast}")
                Log.d("beacon", "sent ${myMark}")
            } catch (e: Exception) { // wait for better times
                Log.d("Beacon exc", e.toString())
                sleep(UDP_BROADCAST_INTERVAL)
                // dgram = mkDgram()
                continue
            }
            sleep(3000)

            // Delete mark of inactive peers
            val now = System.currentTimeMillis()
            lck.lock()
            try {
                val toDelete: MutableList<String> = mutableListOf<String>()
                for (mark in markOfLocalPeers)
                    if (mark.value + 15000 < now) toDelete.add(mark.key)
                for (k in toDelete) {
                    markOfLocalPeers.remove(k)
                    wai?.eval("b2f_local_peer('${k}', 'offline')") // announce disappearance
                }
            } finally {
                lck.unlock()
            }
        }
        // Log.d("BEACON", "ended")
    }

    /**
     * Listen to broadcasts
     */
    fun listen(lck: ReentrantLock) {
        val buf = ByteArray(256)
        val ingram = DatagramPacket(buf, buf.size)
        while (true) {
            // val s = context.broadcast_socket
            // blocks?? Log.d("listen", "${s}, ports=${s?.port}/${s?.localPort} closed=${s?.isClosed} bound=${s?.isBound}")
            try {
                context.broadcast_socket?.receive(ingram)
            } catch (e: Exception) {
                // Log.d("Broadcast listen", "e=${e}, bsock=${context.broadcast_socket}")
                sleep(UDP_BROADCAST_INTERVAL) // wait for better conditions
                continue
            }
            val incoming = ingram.data.decodeToString(0, ingram.length)
            for (incomingMark in incoming.split(";")) {
                if (incomingMark == myMark || incomingMark.substring(0, 3) != "net")
                    continue
                Log.d("rx " + ingram.length, "<${incomingMark}>")
                if (incomingMark !in markOfLocalPeers) // if new, announce it to the frontend
                    wai?.eval("b2f_local_peer('${incomingMark}', 'online')")
                lck.lock()
                try {
                    markOfLocalPeers.put(incomingMark, System.currentTimeMillis())
                } finally {
                    lck.unlock()
                }
            }
        }
    }
}