package nz.scuttlebutt.tremola.utils

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.io.IOException

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