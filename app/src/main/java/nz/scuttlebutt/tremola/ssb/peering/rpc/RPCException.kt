package nz.scuttlebutt.tremola.ssb.peering.rpc

import java.lang.RuntimeException

class RPCException(message: String?) : RuntimeException(message)