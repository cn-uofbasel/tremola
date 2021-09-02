package nz.scuttlebutt.tremola.ssb.peering.rpc

import android.util.Log
import com.squareup.moshi.*

@JsonClass(generateAdapter = false)
open class RPCRequest(
    open val name: List<String>,
    open val type: RequestType
) {
    companion object {
        const val CREATE_HISTORY_STREAM = "createHistoryStream"
        const val CREATE_USER_STREAM = "createUserStream"

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
    }

    @JsonClass(generateAdapter = true)
    data class RequestCreateHistoryStream(
        override val name: List<String> = listOf(CREATE_HISTORY_STREAM),
        override val type: RequestType = RequestType.SOURCE,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        @JsonClass(generateAdapter = true)
        data class Arg(
            val id: String,
            val sequence: Int? = 1,
            val limit: Int? = 10,
            val live: Boolean? = false,
            val keys: Boolean? = true
        )
    }

    @JsonClass(generateAdapter = true)
    data class RequestEBTreplicate(
        override val name: List<String> = listOf(EBT, EBT_REPLICATE),
        override val type: RequestType = RequestType.DUPLEX,
        val args: List<Arg>
    ): RPCRequest(name, type) {
        @JsonClass(generateAdapter = true)
        data class Arg(
            val version: Int = 3
        )
    }

    @JsonClass(generateAdapter = true)
    data class EBTnote(
        val fid: String,
        val front: Int
    )

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
}

enum class RequestType {
    @Json(name="async") ASYNC,
    @Json(name="source") SOURCE,
    @Json(name="duplex") DUPLEX,
}

class RPCRequestJsonAdapter(val moshi: Moshi): JsonAdapter<RPCRequest>() {

    override fun fromJson(reader: JsonReader): RPCRequest? {
        val nameFieldOptions = JsonReader.Options.of("name")
        val argsFieldOptions = JsonReader.Options.of("args")

        reader.peekJson()?.run {
            beginObject()
            while (hasNext()) {
                if (selectName(nameFieldOptions) == 0) {
                    beginArray()
                    while (hasNext()) {
                        when (nextString()) {
                            RPCRequest.CREATE_HISTORY_STREAM -> return moshi.adapter(RPCRequest.RequestCreateHistoryStream::class.java).fromJson(reader)
                            RPCRequest.BLOBS -> {
                                when (nextString()) {
                                    RPCRequest.GET_SLICE -> return moshi.adapter(RPCRequest.RequestBlobsGet::class.java).fromJson(reader)
                                    RPCRequest.HAS -> return moshi.adapter(RPCRequest.RequestBlobsHas::class.java).fromJson(reader)
                                    RPCRequest.CHANGES -> return moshi.adapter(RPCRequest.RequestBlobsChanges::class.java).fromJson(reader)
                                    RPCRequest.CREATE_WANTS -> return moshi.adapter(RPCRequest.RequestBlobsCreateWants::class.java).fromJson(reader)
                                    RPCRequest.GET -> {
                                        reader.peekJson()?.run {
                                            beginObject()
                                            while (hasNext()) {
                                                if (selectName(argsFieldOptions) == 0) {
                                                    beginArray()
                                                    try {
                                                        nextString()
                                                        return moshi.adapter(RPCRequest.RequestBlobsGetSimple::class.java).fromJson(reader)
                                                    } catch (e: JsonDataException) {
                                                        break
                                                    }
                                                }
                                                skipName()
                                                skipValue()
                                            }
                                            endObject()
                                        }
                                        return moshi.adapter(RPCRequest.RequestBlobsGet::class.java).fromJson(reader)
                                    }
                                }
                            }
                        }
                    }
                    endArray()
                    break
                }
                skipName()
                skipValue()
            }
            endObject()
        }
        Log.e("EXCEPTION: ", "Unknown request: ${reader.nextString()}")
        throw JsonDataException("Unknown request.")
    }

    override fun toJson(writer: JsonWriter, request: RPCRequest?) {
        request?.let {
            if (it.name.isNotEmpty()) {
                when (it.name[0]) {
                    RPCRequest.CREATE_HISTORY_STREAM -> moshi.adapter(RPCRequest.RequestCreateHistoryStream::class.java).toJson(writer, request as RPCRequest.RequestCreateHistoryStream)
                    RPCRequest.BLOBS -> {
                        if (it.name.size == 2) {
                            when (it.name[1]) {
                                RPCRequest.GET_SLICE -> moshi.adapter(RPCRequest.RequestBlobsGetSlice::class.java).toJson(writer, request as RPCRequest.RequestBlobsGetSlice)
                                RPCRequest.HAS -> moshi.adapter(RPCRequest.RequestBlobsHas::class.java).toJson(writer, request as RPCRequest.RequestBlobsHas)
                                RPCRequest.CHANGES -> moshi.adapter(RPCRequest.RequestBlobsChanges::class.java).toJson(writer, request as RPCRequest.RequestBlobsChanges)
                                RPCRequest.CREATE_WANTS -> moshi.adapter(RPCRequest.RequestBlobsCreateWants::class.java).toJson(writer, request as RPCRequest.RequestBlobsCreateWants)
                                RPCRequest.GET -> {
                                    if (request is RPCRequest.RequestBlobsGetSimple)
                                        moshi.adapter(RPCRequest.RequestBlobsGetSimple::class.java).toJson(writer, request)
                                    else
                                        moshi.adapter(RPCRequest.RequestBlobsGet::class.java).toJson(writer, request as RPCRequest.RequestBlobsGet)
                                }
                            }
                        }
                    }
                    RPCRequest.INVITE -> {
                        when (it.name[1]) {
                            RPCRequest.USE -> moshi.adapter(RPCRequest.RequestInviteUseSimple::class.java).toJson(writer, request as RPCRequest.RequestInviteUseSimple)
                        }
                    }
                }
            }
        }
    }
}

