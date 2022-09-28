package nz.scuttlebutt.tremola.utils

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class HelperFunctions {

    companion object {

        /**
         * Create a shortname from the hash of the public key. <br>
         * We are using
         * [z-base-32](https://philzimmermann.com/docs/human-oriented-base-32-encoding.txt)
         * for an easier relay of the shortname. <br>
         * With a 12 character shortname we have a probability of 1% that 2 people have the same
         * shortname with 152'231'720 users and 50% for 1'264'234'390 users (in accordance with the
         * birthday paradox). <br>
         * For a 10 character shortname, those numbers are resp. 4'757'241 and 39'507'325. Using the
         * subjective point of view of SSB, we assume that 10 characters are enough, with a thought
         * on keeping it short enough for ease of use.
         * @param key The public key, which is also the SSB ID.
         * @return The computed shortname.
         */
        fun id2(key: String): String {
            val shortnameLength = 10
            val dictionary = "ybndrfg8ejkmcpqxot1uwisza345h769"
            val shortname = StringBuilder(shortnameLength)
            try {
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(key.substring(1, key.length - 9).toByteArray(StandardCharsets.UTF_8))
                for (i in 0 until shortnameLength) {
                    val value = (hash[i].toInt() + 128) % 32
                    shortname.append(dictionary[value])
                }
                shortname.insert(shortnameLength / 2, "-")
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
            return shortname.toString()
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun ByteArray.toInt32(): Int { // Big endian.
            var v = 0
            for (i in 0..3) {
                v = (v shl 8) or (this[i].toInt() and 0xFF)
            }
            return v
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun Int.toByteArray(): ByteArray { // Big endian.
            val a = ByteArray(4)
            var v = this
            for (i in 3 downTo 0) {
                a[i] = (v and 0xFF).toByte()
                v = v shr 8
            }
            return a
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun String.decodeHex(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }

            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        /**
         * Extract the public key from the log ID. Only works for IDs, but not msg keys or blob
         * hashes.
         */
        @JvmStatic
        fun String.deRef(): ByteArray { //
            // Log.d("deRef", "<" + this + ">")
            val s = this.slice(1..this.lastIndex).removeSuffix(".ed25519")
            return Base64.decode(s, Base64.NO_WRAP)
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun ByteArray.toHex(): String = joinToString("") { b ->
            "%02x".format(b)
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun ByteArray.toBase64(): String {
            return Base64.encodeToString(this, Base64.NO_WRAP)
        }

        /**
         * TODO add documentation
         */
        @JvmStatic
        fun ByteArray.utf8(): String {
            return this.toString(Charsets.UTF_8)
        }

    }
}

/**
 * This class is responsible for producing pretty strings for printing JSON objects
 * TODO add documentation
 */
class JSONPrettyPrint {
    private var output = ""

    /**
     * TODO add documentation
     */
    fun makePretty(str: String): String {
        output = ""
        // val todo =
        pp(0, str)
        // println("<${todo}>")

        return output
    }

    /**
     * TODO add documentation
     */
    private fun skipWhite(s: String): String {
        var offs = 0
        while (s[offs] == ' ' || s[offs] == '\t' || s[offs] == '\n')
            offs++
        return s.slice(offs..s.lastIndex)
    }

    /**
     * TODO add documentation
     */
    private fun pp(lev: Int, str: String): String { // JSON pretty printing
        var s = skipWhite(str)
        if (s[0] == '{') {
            if (s[1] == '}') {
                output += "{}"
                return s.slice(2..s.lastIndex)
            }
            output += "{\n"
            s = skipWhite(s.slice(1..s.lastIndex))
            while (true) {
                for (i in 0..lev)
                    output += "  "
                s = skipWhite(pp(lev + 1, s))
                if (s[0] != ':')
                    return "?:?"
                output += ": "
                s = skipWhite(pp(lev + 1, s.slice(1..s.lastIndex)))
                if (s[0] == '}')
                    break
                if (s[0] == ',') {
                    output += ",\n"
                    s = s.slice(1..s.lastIndex)
                } else {
                    output += "?!?"
                    return s.slice(1..s.lastIndex)
                }
            }
            output += "\n"
            for (i in 1..lev)
                output += "  "
            output += "}"
            return s.slice(1..s.lastIndex)
        }
        if (s[0] == '[') {
            if (s[1] == ']') {
                output += "[]"
                return s.slice(2..s.lastIndex)
            }
            output += "[\n"
            s = skipWhite(s.slice(1..s.lastIndex))
            while (true) {
                for (i in 0..lev)
                    output += "  "
                s = skipWhite(pp(lev + 1, s))
                if (s[0] == ']')
                    break
                if (s[0] == ',') {
                    output += ",\n"
                    s = s.slice(1..s.lastIndex)
                } else {
                    output += "?!?"
                    return s.slice(1..s.lastIndex)
                }
            }
            output += "\n"
            for (i in 1..lev)
                output += "  "
            output += "]"
            return s.slice(1..s.lastIndex)
        }
        if (s[0] == '"') {
            output += "\""
            var offs = 1
            while (true) {
                if (s[offs] == '\\') {
                    output += s.slice(offs..offs + 1)
                    offs += 2
                    continue
                }
                if (s[offs] == '"') {
                    output += "\""
                    return s.slice(offs + 1..s.lastIndex)
                }
                output += s.slice(offs..offs)
                offs++
            }
        }
        if (s.slice(0..3) == "null") {
            output += "null"
            return s.slice(4..s.lastIndex)
        }
        if (s.slice(0..3) == "true") {
            output += "true"
            return s.slice(4..s.lastIndex)
        }
        if (s.slice(0..4) == "false") {
            output += "false"
            return s.slice(5..s.lastIndex)
        }
        if (s[0] in '0'..'9') {
            var offs = 0
            while ((s[offs] in '0'..'9') || s[offs] == '.')
                offs++
            output += s.slice(0 until offs)
            return s.slice(offs..s.lastIndex)
        }
        return "!!??"
    }
}