package nz.scuttlebutt.tremola.utils

import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.decodeHex

class Constants{
    companion object{
        val SSB_IPV4_TCPPORT = 8008 // default listening port
        val SSB_IPV4_UDPPORT = 8008 // default destination port for LAN announcements (broadcasts)
        val SSB_NETWORKIDENTIFIER = "d4a1cb88a66f02f8db635ce26441cc5dac1b08420ceaac230839b755845a9ffb".decodeHex()
        val UDP_BROADCAST_INTERVAL = 3000L     // millisec
        val WIFI_DISCOVERY_INTERVAL = 5L       // check every X sec
        val EBT_FORCE_FRONTIER_INTERVAL = 30L  // send frontier every X sec
        val frontierWindow = 86400000
    }
}