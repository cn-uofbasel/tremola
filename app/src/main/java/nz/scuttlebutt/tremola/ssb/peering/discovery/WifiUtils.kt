package nz.scuttlebutt.tremola.utils

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.util.Log
import java.math.BigInteger
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteOrder
import java.io.IOException


fun getLocalIpAddress(context: Context): String? {
    val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
    var ipAddress = wifiManager.connectionInfo.ipAddress

    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
        ipAddress = Integer.reverseBytes(ipAddress)
    }

    val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()

    var ipAddressString: String?
    try {
        ipAddressString = InetAddress.getByAddress(ipByteArray).hostAddress
    } catch (ex: UnknownHostException) {
        Log.e("WIFIIP", "Unable to get host address.")
        ipAddressString = null
    }

    return ipAddressString
}

@Throws(IOException::class)
fun getBroadcastAddress(context: Context): InetAddress {
    val wifi = context.getSystemService(WIFI_SERVICE) as WifiManager
    val dhcp = wifi.dhcpInfo

    //https://code.google.com/archive/p/boxeeremote/wikis/AndroidUDP.wiki
    val broadcast = dhcp.ipAddress and dhcp.netmask or dhcp.netmask.inv()
    val quads = ByteArray(4)
    for (k in 0..3)
        quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
    return InetAddress.getByAddress(quads)
}