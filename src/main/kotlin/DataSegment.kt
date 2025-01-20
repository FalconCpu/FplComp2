package falcon

object DataSegment {
    val data = mutableListOf<Int>()

    val strings = mutableMapOf<String,Int>()

    fun read(addr:Int) : Int {
        val i = addr / 4
        return data[i]
    }

    fun getStringAddress(string: String) : Int {
        return strings.getOrPut(string) {
            data += string.length
            val ret = -0x10000 + (data.size) * 4
            for (c in string.chunked(4))
                data += c[0].code +
                        (if (c.length > 1) (c[1].code shl 8) else 0) +
                        (if (c.length > 2) (c[2].code shl 16) else 0) +
                        (if (c.length > 3) (c[3].code shl 24) else 0)
            ret
        }
    }

    fun clear() {
        data.clear()
        strings.clear()
    }
}