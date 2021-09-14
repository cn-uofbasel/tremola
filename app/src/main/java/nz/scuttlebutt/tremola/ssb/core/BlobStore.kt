package nz.scuttlebutt.tremola.ssb.core

import android.content.Context
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64
import java.io.File
import java.io.FileInputStream

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

    fun fetch(ref: String): FileInputStream {
        // Log.d("blobStore.fetch", "fname=${fname}")
        val f = File(blobDir, ref2fname(ref))
        // Log.d("blobStore.fetch", "path=${f.absolutePath}")
        return FileInputStream(f)
    }

    fun delete(ref: String) {
        try { File(blobDir, ref2fname(ref)).delete() } catch (e: Exception) {}
    }

    private fun ref2fname(ref: String): String {
        return ref.substring(1, ref.length-7).replace("/", "_")
    }
}