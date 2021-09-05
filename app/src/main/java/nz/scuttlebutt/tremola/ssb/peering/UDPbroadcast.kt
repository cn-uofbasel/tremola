package nz.scuttlebutt.tremola.ssb.peering

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter.formatIpAddress
import android.util.Base64
import android.util.Log
import java.lang.Thread.sleep
import java.net.DatagramPacket
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock

import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.WebAppInterface
import nz.scuttlebutt.tremola.utils.Constants
import nz.scuttlebutt.tremola.utils.Constants.Companion.UDP_BROADCAST_INTERVAL

class UDPbroadcast(val context: MainActivity, val wai: WebAppInterface?) {

    val local: MutableMap<String,Long> = HashMap<String,Long>() // multiaddr ~ last_seen
    var myMark: String? = null

    fun beacon(pubkey: ByteArray, lck: ReentrantLock, myTcpPort: Int) {

        fun mkDgram(): DatagramPacket {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // val dhcp = wifiManager.getDhcpInfo() // assumes IPv4?
            /*
            val broadcast = dhcp.ipAddress or (0xff shl 24) // (-1 and dhcp.netmask.inv()) // dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3)
                quads[k] = (broadcast shr k * 8).toByte()
            val bcastAddr = InetAddress.getByAddress(quads)
            */
            val bcastAddr = InetAddress.getByName("255.255.255.255")
            val myAddr = wifiManager.connectionInfo.ipAddress
            // Log.d("UDP BEACON", "my=${formatIpAddress(myAddr)}, broadcast=${bcastAddr}, mask=${dhcp.netmask.inv()}")
            myMark = "net:${formatIpAddress(myAddr)}:${myTcpPort}~shs:" +
                    Base64.encodeToString(pubkey, Base64.NO_WRAP)
            val buf = myMark!!.encodeToByteArray()
            return DatagramPacket(buf, buf.size, bcastAddr, Constants.SSB_IPV4_UDPPORT)
        }

        while (true) {
            try {
                val dgram = mkDgram()
                // val s = context.broadcast_socket
                // Log.d("beacon sock", "bound=${s?.isBound}, ${s?.inetAddress}/${s?.port}/${s?.localPort} brcast${s?.broadcast}")
                context.broadcast_socket!!.send(dgram)
                Log.d("beacon", "sent") }
            catch (e: Exception) { // wait for better times
                Log.d("Beacon exc", e.toString())
                sleep(UDP_BROADCAST_INTERVAL)
                // dgram = mkDgram()
                continue
            }
            sleep(UDP_BROADCAST_INTERVAL)
            val now = System.currentTimeMillis()
            lck.lock()
            try {
                val todelete: MutableList<String> = mutableListOf<String>()
                for (k in local)
                    if (k.value + 15000 < now) todelete.add(k.key)
                for (k in todelete) {
                    local.remove(k)
                    wai?.eval("b2f_local_peer('${k}', 'offline')") // announce disappearance
                }
            } finally { lck.unlock() }
        }
    }

    fun listen(lck: ReentrantLock) {
        val buf = ByteArray(256)
        val ingram = DatagramPacket(buf, buf.size)
        while (true) {

            // val s = context.broadcast_socket
            // Log.d("listen", "${s}, a=${s?.inetAddress}|${s?.localAddress}|${s?.localSocketAddress} br=${s?.broadcast}, ports=${s?.port}/${s?.localPort} closed=${s?.isClosed} bound=${s?.isBound} reuse=${s?.reuseAddress}")
            try { context.broadcast_socket!!.receive(ingram) }
            catch (e: Exception) {
                // Log.d("Broadcast listen", "e=${e}, bsock=${context.broadcast_socket}")
                sleep(UDP_BROADCAST_INTERVAL) // wait for better conditions
                continue
            }
            val incoming = ingram.data.decodeToString(0, ingram.length)
            for (i in incoming.split(";")) {
                if (i == myMark || i.substring(0, 3) != "net")
                    continue
                Log.d("rx " + ingram.length, "<${i}>")
                if (!(i in local)) // if new, announce it to the frontend
                    wai?.eval("b2f_local_peer('${i}', 'online')")
                lck.lock()
                try {
                    local.put(i, System.currentTimeMillis())
                } finally {
                    lck.unlock()
                }
            }
        }
    }
}