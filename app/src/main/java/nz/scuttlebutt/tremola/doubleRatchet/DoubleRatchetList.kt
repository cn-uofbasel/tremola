package nz.scuttlebutt.tremola.doubleRatchet

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject

/**
 * This class contains the list of all SSBDoubleRatchets. Each SSBDoubleRatchet object corresponds
 * to a certain (unique) chat.
 * @param context The context of the app. Used to read from and write to files.
 * @property list The actual list is a HashMap of chat names and the SSBDoubleRatchet objects.
 * Typically, the chat names are derived using [deriveChatName].
 * TODO Handle case where both participants send the first message.
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
    fun get(key: String): SSBDoubleRatchet? {
        return list[key]
    }

    /**
     * This is the method used to put a new entry the list for a chat.
     * @param key The string symbolizing the chat. Typically derived using [deriveChatName].
     * @param doubleRatchet The SSBDoubleRatchet that should be put in the list.
     */
    fun set(key: String, doubleRatchet: SSBDoubleRatchet) {
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
        val outputStream = context.openFileOutput(FILENAME, Context.MODE_PRIVATE)
        try {
            try {
                context.deleteFile(FILENAME)
            } catch (e: java.lang.Exception) {
                Log.e("DoubleRatchetList", "Error when deleting DoubleRatchetList file.")
            }
            outputStream.write(stringifiedList.encodeToByteArray())
            outputStream.close()
        } catch (e: Exception) {
            Log.e("DoubleRatchetList", "Unable to persist.")
            e.printStackTrace()
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
                Log.e("DoubleRatchetList", "Empty value in list.")
            }
        }
        return listJSONObject.toString()
    }

    /**
     * Reads the list from file. Should the file be empty, returns an empty HashMap.
     * @return A HashMap which is filled with all the entries that could be found. Might be empty.
     */
    private fun deserializeList(): HashMap<String, SSBDoubleRatchet> {
        val inputStream = context.openFileInput(FILENAME)
        // TODO Using a buffer like this might not be enough to read the file in rare cases, where
        //  the available() call returns a small number. Fix this.
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        inputStream.close()
        val listJSONObject = JSONObject(buffer.decodeToString())
        val listHashMap = HashMap<String, SSBDoubleRatchet>()
        for (key in listJSONObject.keys()) {
            val serializedSSBDoubleRatchet = listJSONObject.getString(key)
            // Use the deserialization constructor to create a new SSBDoubleRatchet
            listHashMap[key] = SSBDoubleRatchet(serializedSSBDoubleRatchet)
        }
        return listHashMap
    }

    companion object {
        const val FILENAME = "SSBDoubleRatchetList.json"
    }
}