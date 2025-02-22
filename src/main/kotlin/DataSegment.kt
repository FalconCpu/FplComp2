package falcon

object DataSegment {
    val strings = mutableListOf<String>()
    val allClasses = mutableListOf<ClassType>()
    val globalVariables = mutableListOf<GlobalVarSymbol>()

    fun clear() {
        strings.clear()
        globalVariables.clear()
        allClasses.clear()
    }

    fun getStringRef(string: String) : String {
        val index = strings.indexOf(string)
        if (index != -1)
            return "string_${index}"
        val ret = "string_${strings.size}"
        strings += string
        return ret
    }

    private fun ClassType.emitDescriptor(sb: StringBuilder) {
        val nameString = toString()
        val nameRef = getStringRef(nameString)
        sb.append("Class/$nameString:\n");
        sb.append("dcw $nameRef\n")
        sb.append("dcw $classSize\n")
        for(vm in virtualMethods)
            sb.append("dcw /${vm.function.name}\n")
        sb.append("\n")
    }

    fun output(sb:StringBuilder) {
        // Output class descriptors
        for(klass in allClasses)
            klass.emitDescriptor(sb)

        // Output all string literals
        for((index,string) in strings.withIndex()) {
            val comment = string.take(20).filter{it.code>=32}
            sb.append("dcw ${string.length}\n")
            sb.append("string_$index: # $comment\n")
            for (c in string.chunked(4)) {
                val data = c[0].code +
                        (if (c.length > 1) (c[1].code shl 8) else 0) +
                        (if (c.length > 2) (c[2].code shl 16) else 0) +
                        (if (c.length > 3) (c[3].code shl 24) else 0)
                sb.append("dcw $data\n")
            }
            sb.append("\n")
        }
    }
}