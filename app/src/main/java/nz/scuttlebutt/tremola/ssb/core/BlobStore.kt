package nz.scuttlebutt.tremola.ssb.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

import nz.scuttlebutt.tremola.ssb.core.Crypto.Companion.sha256
import nz.scuttlebutt.tremola.utils.HelperFunctions.Companion.toBase64

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
        // Log.d("blobStore.fetch", "fname=${ref}")
        val f = File(blobDir, ref2fname(ref))
        // Log.d("blobStore.fetch", "path=${f.absolutePath}")
        return FileInputStream(f)
    }

    fun exists(ref: String): Long { // return size if we have the blob, else -1
        val f = File(blobDir, ref2fname(ref))
        if (f.exists())
            return f.length()
        return -1
    }

    fun delete(ref: String) {
        try { File(blobDir, ref2fname(ref)).delete() } catch (e: Exception) {}
    }

    private fun ref2fname(ref: String): String {
        return ref.substring(1, ref.length-7).replace("/", "_")
    }

    fun storeAsBlob(bitmap: Bitmap): String { // compress to <5MB if necessary
        var resized = bitmap
        while (true) {
            // Log.d("img dims", "w=${resized.width}, h=${resized.height}")
            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            // Log.d("img size", "${bytes.size}B")
            if (bytes.size < 5*1024*1024)
                return store(bytes, "jpg")
            val width = resized.width * 3 / 4
            val height = width * resized.height / resized.width
            resized = Bitmap.createScaledBitmap(resized, width, height,true)
        }
    }

    fun storeAsBlob(path: String): String {
        val resized = BitmapFactory.decodeFile(path)
        File(path).delete()
        return storeAsBlob(resized)
    }

}