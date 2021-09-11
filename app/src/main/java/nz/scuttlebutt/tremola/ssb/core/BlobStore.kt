package nz.scuttlebutt.tremola.ssb.core

import android.content.Context
import android.util.Log
import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toHex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*

class BlobStore(val context: Context) {
    lateinit var blobDir: File

    init {
        val cacheDir = context.cacheDir // filesDir
        if (!cacheDir.exists())
            cacheDir.mkdirs()
        blobDir = File(cacheDir, "blobs");
        if (!blobDir.exists())
            blobDir.mkdirs()
    }

    fun store(data: ByteArray, suffix: String): String {
        val fname = "${data.sha256().toHex()}.${suffix}"
        val f = File(blobDir, fname)
        Log.d("blobStore", "fname ${f.absolutePath}")
        f.createNewFile()
        f.writeBytes(data)
        return fname
    }

    fun fetch(fname: String): FileInputStream {
        val f = File(blobDir, fname)
        return FileInputStream(f)
    }
}