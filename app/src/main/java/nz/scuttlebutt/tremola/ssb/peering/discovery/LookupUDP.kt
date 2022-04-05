package nz.scuttlebutt.tremola.ssb.peering.discovery

import android.content.Context
import android.util.Log
import nz.scuttlebutt.tremola.MainActivity
import nz.scuttlebutt.tremola.ssb.core.SSBid
import nz.scuttlebutt.tremola.utils.Constants.Companion.LOOKUP_INTERVAL
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class LookupUDP(
    lookup: Lookup,
    context: Context,
    ed25519KeyPair: SSBid,
    lock: ReentrantLock,
    private val port: Int,
    private val broadcastAddress: String
) :
    LookupClient(lookup, context, ed25519KeyPair, lock) {
    private var datagramSocket: DatagramSocket? = null

    init {
        try {
            datagramSocket = DatagramSocket()
            datagramSocket!!.broadcast = true
        } catch (e: Exception) {
            active = false
        }
    }

    override fun sendQuery(broadcastMessage: String) {
        try {
            if (datagramSocket!!.isClosed) {
                datagramSocket = DatagramSocket()
                datagramSocket!!.broadcast = true
            }
            val receiverAddress = InetAddress.getByName(broadcastAddress)
            val datagramPacket = DatagramPacket(
                broadcastMessage.toByteArray(),
                broadcastMessage.length,
                receiverAddress,
                port
            )
            datagramSocket!!.send(datagramPacket)
            datagramSocket!!.close()
            Log.e("lu_wr ", "<${String(datagramPacket.data)}>")
        } catch (e: IOException) {
            Log.e("SEND_ERROR", e.message!!)
            e.printStackTrace()
        }
    }

    override fun run() {
        val buf = ByteArray(512)
        val ingram = DatagramPacket(buf, buf.size)
        active = true
        while (active) {
            try {
                (context as MainActivity).lookup_socket!!.receive(ingram)
            } catch (e: Exception) {
                synchronized(lock) {
                    try {
                        lock.tryLock(LOOKUP_INTERVAL, TimeUnit.MILLISECONDS)
                    } catch (ex: InterruptedException) {
                        Log.e("UDP LOCK", ex.message!!)
                    }
                }
                continue
            }
            val incoming = String(ingram.data, 0, ingram.length)
            for (i in incoming.split(";").toTypedArray()) {
                Log.e("lu_rx " + ingram.length, "<$i>")
                if (i.contains("\"msa\"")) {
                    lookup.processQuery(i)
                } else if (i.contains("\"targetId")) {
                    lookup.processReply(i)
                }
            }
        }
    }
}