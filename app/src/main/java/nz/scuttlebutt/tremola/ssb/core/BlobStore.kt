package nz.scuttlebutt.tremola.ssb.core

import android.content.Context
import android.util.Log
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toHex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.*

class BlobStore(val context: Context) {
    val blobDir: File

    init {
        val cacheDir = context.cacheDir // filesDir
        if (!cacheDir.exists())
            cacheDir.mkdirs()
        blobDir = File(cacheDir, "blobs");
        if (!blobDir.exists())
            blobDir.mkdirs()
    }

    fun store(data: ByteArray, suffix: String): String {
        val hash = data.sha256()
        var fname = hash.toBase64()
        val bname = "&${fname}.sha256" // SSB's blob name
        fname = fname.replace("/", "_")
        val f = File(blobDir, fname)
        // Log.d("blobStore.store", "path=${f.absolutePath}")
        f.createNewFile()
        f.writeBytes(data)
        return bname
    }

    fun fetch(fname: String): FileInputStream {
        // Log.d("blobStore.fetch", "fname=${fname}")
        val f = File(blobDir, fname.substring(1, fname.length-7).replace("/", "_"))
        // Log.d("blobStore.fetch", "path=${f.absolutePath}")
        return FileInputStream(f)
    }
}