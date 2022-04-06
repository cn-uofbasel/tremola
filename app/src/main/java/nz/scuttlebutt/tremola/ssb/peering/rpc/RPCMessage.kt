package nz.scuttlebutt.tremola.ssb.peering.rpc


data class RPCMessage(
    val stream: Boolean = true,
    val endError: Boolean = false,
    // JSON, UTF8 or BINARY
    val bodyType: RPCserialization.Companion.RPCBodyType = RPCserialization.Companion.RPCBodyType.JSON,
    val bodyLength: Int,
    val requestNumber: Int,
    val rawEvent: ByteArray // body
)