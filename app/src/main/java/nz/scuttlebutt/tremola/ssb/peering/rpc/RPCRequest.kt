package nz.scuttlebutt.tremola.ssb.peering.rpc

open class RPCRequest(
    open val name: List<String>,
    open val type: String
) {
    companion object {
        const val CREATE_HISTORY_STREAM = "createHistoryStream"
        const val CREATE_USER_STREAM = "createUserStream"

        // Epidemic Broadcast Tree: https://github.com/ssbc/ssb-ebt
        const val EBT = "ebt"
        const val EBT_REPLICATE = "replicate"

        const val BLOBS = "blobs"
        const val HAS = "has"
        const val CHANGES = "changes"
        const val GET = "get"
        const val GET_SLICE = "getSlice"
        const val CREATE_WANTS = "createWants"

        const val INVITE = "invite"
        const val USE = "use"

        const val RPC_TYPE_ASYNC  = "async"
        const val RPC_TYPE_SOURCE = "source"
        const val RPC_TYPE_DUPLEX = "duplex"
    }

    data class RequestCreateHistoryStream(
        override val name: List<String> = listOf(CREATE_HISTORY_STREAM),
        override val type: String = RPC_TYPE_SOURCE,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        data class Arg(
            val id: String,
            val sequence: Int? = 1,
            val limit: Int? = 10,
            val live: Boolean? = false,
            val keys: Boolean? = true
        )
    }

    data class RequestEBTreplicate(
        override val name: List<String> = listOf(EBT, EBT_REPLICATE),
        override val type: String = RPC_TYPE_DUPLEX,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        data class Arg(
            val version: Int = 3
        )
    }

    data class EBTnote(
        val fid: String,
        val front: Int
    )

    /*
    @JsonClass(generateAdapter = true)
    data class RequestBlobsGet(
        override val name: List<String> = listOf(BLOBS, GET),
        override val type: RequestType = RequestType.SOURCE,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        @JsonClass(generateAdapter = true)
        data class Arg(
            val hash: String,
            val size: Long,
            val max: Long
        )
    }

    @JsonClass(generateAdapter = true)
    data class RequestInviteUseSimple(
        override val name: List<String> = listOf(INVITE, USE),
        override val type: RequestType = RequestType.ASYNC,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        data class Arg(
            val feed: String
        )
    }

    @JsonClass(generateAdapter = true)
    data class RequestBlobsGetSimple(
        override val name: List<String> = listOf(BLOBS, GET),
        override val type: RequestType = RequestType.SOURCE,
        val args: List<String>
    ): RPCRequest(name, type)

    @JsonClass(generateAdapter = true)
    data class RequestBlobsGetSlice(
        override val name: List<String> = listOf(BLOBS, GET_SLICE),
        override val type: RequestType = RequestType.SOURCE,
        val hash: String,
        val start: Long,
        val end: Long,
        val size: Long,
        val max: Long
    ): RPCRequest(name, type)

    @JsonClass(generateAdapter = true)
    data class RequestBlobsHas(
        override val name: List<String> = listOf(BLOBS, HAS),
        override val type: RequestType = RequestType.ASYNC,
        val args: List<List<String>>
    ): RPCRequest(name, type)

    @JsonClass(generateAdapter = true)
    data class RequestBlobsChanges(
        override val name: List<String> = listOf(BLOBS, CHANGES),
        override val type: RequestType,
        val args: List<String>
    ): RPCRequest(name, type)

    @JsonClass(generateAdapter = true)
    data class RequestBlobsCreateWants(
        override val name: List<String> = listOf(BLOBS, CREATE_WANTS),
        override val type: RequestType = RequestType.SOURCE,
        val args: List<String>
    ): RPCRequest(name, type)

    */
}

