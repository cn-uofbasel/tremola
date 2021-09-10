package nz.scuttlebutt.tremola.utils

import android.util.Base64

class HelperFunctions {

    companion object {

        @JvmStatic
        fun ByteArray.toInt32(): Int { // big endian
            var v = 0
            for (i in 0..3) {
                v = (v shl 8) or (this[i].toInt() and 0xFF)
            }
            return v
        }

        @JvmStatic
        fun Int.toByteArray(): ByteArray { // big endian
            val a = ByteArray(4)
            var v = this
            for (i in 3 downTo 0) {
                a[i] = (v and 0xFF).toByte()
                v = v shr 8
            }
            return a
        }

        @JvmStatic
        fun String.decodeHex(): ByteArray {
            check(length % 2 == 0) { "Must have an even length" }

            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        @JvmStatic
        fun String.deRef(): ByteArray { // only works for IDs, but not msg keys or blob hashes
            // Log.d("deRef", "<" + this + ">")
            val s = this.slice(1..this.lastIndex).removeSuffix(".ed25519")
            return Base64.decode(s, Base64.NO_WRAP)
        }

        @JvmStatic
        fun ByteArray.toHex(): String = joinToString("") { b ->
            "%02x".format(b)
        }

        @JvmStatic
        fun ByteArray.toBase64(): String {
            return Base64.encodeToString(this, Base64.NO_WRAP)
        }

        @JvmStatic
        fun ByteArray.utf8(): String {
            return this.toString(Charsets.UTF_8)
        }

    }
}

class Json_PP() { // pretty printing for JSON output
    var output = ""

    fun makePretty(str: String): String {
        output = ""
        // val todo =
        pp(0, str)
        // println("<${todo}>")

        return output
    }

    private fun skipWhite(s: String): String {
        var offs = 0
        while (s[offs] == ' ' || s[offs] == '\t' || s[offs] == '\n')
            offs++
        return s.slice(offs .. s.lastIndex)
    }

    private fun pp(lev: Int, str: String): String { // JSON pretty printing
        var s = skipWhite(str)
        if (s[0] == '{') {
            if (s[1] == '}') {
                output += "{}"
                return s.slice(2 .. s.lastIndex)
            }
            output += "{\n"
            s = skipWhite(s.slice(1 .. s.lastIndex))
            while (true) {
                for (i in 0 .. lev)
                    output += "  "
                s = skipWhite(pp(lev+1, s))
                if (s[0] != ':')
                    return "?:?"
                output += ": "
                s = skipWhite(pp(lev+1, s.slice(1 .. s.lastIndex)))
                if (s[0] == '}')
                    break
                if (s[0] == ',') {
                    output += ",\n"
                    s = s.slice(1 .. s.lastIndex)
                } else {
                    output += "?!?"
                    return s.slice(1 .. s.lastIndex)
                }
            }
            output += "\n"
            for (i in 1 .. lev)
                output += "  "
            output += "}"
            return s.slice(1 .. s.lastIndex)
        }
        if (s[0] == '[') {
            if (s[1] == ']') {
                output += "[]"
                return s.slice(2 .. s.lastIndex)
            }
            output += "[\n"
            s = skipWhite(s.slice(1 .. s.lastIndex))
            while (true) {
                for (i in 0 .. lev)
                    output += "  "
                s = skipWhite(pp(lev+1, s))
                if (s[0] == ']')
                    break
                if (s[0] == ',') {
                    output += ",\n"
                    s = s.slice(1 .. s.lastIndex)
                } else {
                    output += "?!?"
                    return s.slice(1 .. s.lastIndex)
                }
            }
            output += "\n"
            for (i in 1 .. lev)
                output += "  "
            output += "]"
            return s.slice(1 .. s.lastIndex)
        }
        if (s[0] == '"') {
            output += "\""
            var offs = 1
            while (true) {
                if (s[offs] == '\\') {
                    output += s.slice(offs .. offs+1)
                    offs += 2
                    continue
                }
                if (s[offs] == '"') {
                    output += "\""
                    return s.slice(offs+1 .. s.lastIndex)
                }
                output += s.slice(offs .. offs)
                offs++
            }
        }
        if (s.slice(0..3) == "null") {
            output += "null"
            return s.slice(4 .. s.lastIndex)
        }
        if (s.slice(0..3) == "true") {
            output += "true"
            return s.slice(4 .. s.lastIndex)
        }
        if (s.slice(0..4) == "false") {
            output += "false"
            return s.slice(5 .. s.lastIndex)
        }
        if (s[0] >= '0' && s[0] <= '9') {
            var offs = 0
            while ((s[offs] >= '0' && s[offs] <= '9') || s[offs] == '.')
                offs++
            output += s.slice(0 .. offs-1)
            return s.slice(offs .. s.lastIndex)
        }
        return "!!??"
    }
}