package nz.scuttlebutt.tremola.ssb.peering.rpc

import androidx.annotation.Nullable
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

class RPCJsonAdapterFactory: JsonAdapter.Factory {
    @Nullable
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type !== RPCRequest::class.java || annotations.isNotEmpty()) {
            return null
        }

        return RPCRequestJsonAdapter(moshi)
    }
}