package falcon

object DataSegment {
    val strings = mutableListOf<String>()

    fun clear() {
        strings.clear()
    }

    fun getStringRef(string: String) : String {
        val index = strings.indexOf(string)
        if (index != -1)
            return "string_${index}"
        val ret = "string_${strings.size}"
        strings += string
        return ret

    }

    fun output(sb:StringBuilder) {
        for((index,string) in strings.withIndex()) {
            sb.append("dcw ${string.length}\n")
            sb.append("string_$index:\n")
            for (c in string.chunked(4)) {
                val data = c[0].code +
                        (if (c.length > 1) (c[1].code shl 8) else 0) +
                        (if (c.length > 2) (c[2].code shl 16) else 0) +
                        (if (c.length > 3) (c[3].code shl 24) else 0)
                sb.append("dcw $data\n")
            }
        }
    }
}