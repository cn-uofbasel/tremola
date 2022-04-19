package nz.scuttlebutt.tremola.utils

import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.decodeHex

class Constants {
    companion object {
        const val SSB_IPV4_TCPPORT = 8008 // default listening port
        const val SSB_IPV4_UDPPORT = 8008 // default destination port for LAN announcements (broadcasts)
        const val LOOKUP_IPV4_UDPPORT = 8009 // default destination port look up (broadcasts)
        val SSB_NETWORKIDENTIFIER = "d4a1cb88a66f02f8db635ce26441cc5dac1b08420ceaac230839b755845a9ffb".decodeHex()
        const val UDP_BROADCAST_INTERVAL = 3000L     // millisecond
        const val LOOKUP_INTERVAL = 3000L     // millisecond
        const val WIFI_DISCOVERY_INTERVAL = 5L       // check every X sec
        const val EBT_FORCE_FRONTIER_INTERVAL = 30L  // send frontier every X sec
        const val frontierWindow = 86400000
    }
}