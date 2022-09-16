package nz.scuttlebutt.tremola.utils

import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.decodeHex

class Constants {
    companion object {
        /** Default listening port for SSB connections on TCP over IPv4. */
        const val SSB_IPV4_TCPPORT = 8008

        /** Default destination port for LAN announcements (broadcasts) on UDP over IPv4. */
        const val SSB_IPV4_UDPPORT = 8008

        /** Default destination port for look up (broadcast). */
        const val LOOKUP_IPV4_UDPPORT = 8009

        /** The network identifier used for SSB's normal network. */
        val SSB_NETWORKIDENTIFIER =
            "d4a1cb88a66f02f8db635ce26441cc5dac1b08420ceaac230839b755845a9ffb".decodeHex()

        /** The default interval between UDP broadcasts in milliseconds. */
        const val UDP_BROADCAST_INTERVAL = 3000L

        /** The default interval between look up broadcasts in milliseconds. */
        const val LOOKUP_INTERVAL = 3000L

        /** The default interval between Wi-Fi discoveries in seconds. */
        const val WIFI_DISCOVERY_INTERVAL = 5L

        /**
         * The default interval between sending frontiers in seconds.
         * TODO more accurate description
         * */
        const val EBT_FORCE_FRONTIER_INTERVAL = 30L

        /** The default value of bytes to get with each frontier. TODO more accurate description */
        const val frontierWindow = 86400000
    }
}