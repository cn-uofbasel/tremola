package nz.scuttlebutt.tremola.doubleRatchet

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import nz.scuttlebutt.tremola.doubleRatchet.DoubleRatchet.Companion.DEBUG
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader

/**
 * This class contains the list of all SSBDoubleRatchets. Each SSBDoubleRatchet object corresponds
 * to a certain (unique) chat.
 * @param context The context of the app. Used to read from and write to files.
 * @property list The actual list is a HashMap of chat names and the SSBDoubleRatchet objects.
 * Typically, the chat names are derived using [deriveChatName].
 * TODO Handle case where both participants send the first message.
 *  Use more secure methods to save file.
 */
@RequiresApi(Build.VERSION_CODES.O)
class DoubleRatchetList(private val context: Context) {

    private var list: HashMap<String, SSBDoubleRatchet> = deserializeList()

    /**
     * Takes an array of users in a chat and returns a unique chat name, which it returns.
     * @param users An array of strings representing the users. Each string has to be unique (e.g.
     * an SSB identity.
     * @return The unique name of the chat. This is used to index the list.
     */
    fun deriveChatName(users: Array<String>): String {
        return users.sortedArray().joinToString("").replace(".ed25519", "")
    }

    /**
     * This is the method used to search the list for a chat.
     * @param key The string symbolizing the chat. Typically derived using [deriveChatName].
     * @return The SSBDoubleRatchet if found in the list, null otherwise.
     */
    operator fun get(key: String): SSBDoubleRatchet? {
        return list[key]
    }

    /**
     * This is the method used to put a new entry the list for a chat.
     * @param key The string symbolizing the chat. Typically derived using [deriveChatName].
     * @param doubleRatchet The SSBDoubleRatchet that should be put in the list.
     */
    operator fun set(key: String, doubleRatchet: SSBDoubleRatchet) {
        list[key] = doubleRatchet
        this.persist()
    }

    /**
     * Takes the current state of the DoubleRatchetList and writes it to a file.
     * This could be called periodically upon change to the state or only when app is closed.
     * Will be deserialized upon restart of app.
     */
    fun persist() {
        val stringifiedList = this.serialize()
        try {
            try {
                context.deleteFile(FILENAME)
            } catch (e: java.lang.Exception) {
                if (DEBUG) {
                    Log.e("DoubleRatchetList", "Error when deleting DoubleRatchetList file.")
                }
            }
            val outputStream = context.openFileOutput(FILENAME, Context.MODE_PRIVATE)
            outputStream.write(stringifiedList.encodeToByteArray())
            outputStream.close()
        } catch (e: Exception) {
            if (DEBUG) {
                Log.e("DoubleRatchetList", "Unable to persist.")
                e.printStackTrace()
            }
        }
    }

    /**
     * Takes the current state of the DoubleRatchetList and returns a JSON string representing it.
     * @return The JSON string of the list. Might be the empty string if the list was empty.
     */
    private fun serialize(): String {
        val listJSONObject = JSONObject()
        for (key in list.keys) {
            if (list[key] != null) {
                listJSONObject.put(key, list[key]!!.serialize())
            } else {
                if (DEBUG) {
                    Log.e("DoubleRatchetList", "Empty value in list.")
                }
            }
        }
        return listJSONObject.toString()
    }

    /**
     * Reads the list from file. Should the file be empty, returns an empty HashMap.
     * @return A HashMap which is filled with all the entries that could be found. Might be empty.
     */
    private fun deserializeList(): HashMap<String, SSBDoubleRatchet> {
        try {
            val fileInput = context.openFileInput(FILENAME)
            val inputStreamReader = InputStreamReader(fileInput)
            val bufferedReader = BufferedReader(inputStreamReader)
            val readText = bufferedReader.readText()
            bufferedReader.close()
            inputStreamReader.close()
            fileInput.close()
            val listJSONObject = JSONObject(readText)
            val listHashMap = HashMap<String, SSBDoubleRatchet>()
            for (key in listJSONObject.keys()) {
                val serializedSSBDoubleRatchet = listJSONObject.getString(key)
                // Use the deserialization constructor to create a new SSBDoubleRatchet
                listHashMap[key] = SSBDoubleRatchet(serializedSSBDoubleRatchet)
            }
            return listHashMap
        } catch (e: FileNotFoundException) { // No persisted list found, make new one.
            return HashMap()
        }
    }

    companion object {
        const val FILENAME = "SSBDoubleRatchetList.json"
    }
}