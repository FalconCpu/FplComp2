package falcon

object DataSegment {
    val strings = mutableListOf<String>()
    val allClasses = mutableListOf<ClassType>()
    val globalVariables = mutableListOf<GlobalVarSymbol>()
    val constArrays = mutableListOf<TastConstArray>()

    fun clear() {
        strings.clear()
        globalVariables.clear()
        allClasses.clear()
        constArrays.clear()
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
        if (superClass==null)
            sb.append("dcw 0\n")
        else
            sb.append("dcw Class/$superClass\n")
        for(vm in virtualMethods)
            sb.append("dcw /${vm.function.name}\n")
        sb.append("\n")
    }

    private fun TastConstArray.emit(sb: StringBuilder) {
        sb.append("dcw ${elements.size}\n")
        sb.append("ConstArray_$index:\n")
        val elementType = (type as ArrayType).elementType
        when(elementType) {
            is IntType ->
                for(element in elements)
                    sb.append("dcw ${element.getCompileTimeConstant()}\n")
            is CharType ->
                for(element in elements.chunked(4)) {
                    sb.append("dcb ${element[0].getCompileTimeConstant()}")
                    if (element.size>1)
                        sb.append(", ${element[1].getCompileTimeConstant()}")
                    if (element.size>2)
                        sb.append(", ${element[2].getCompileTimeConstant()}")
                    if (element.size>3)
                        sb.append(", ${element[3].getCompileTimeConstant()}")
                    sb.append("\n")
                }
            is ShortType ->
                for(element in elements.chunked(2)) {
                    sb.append("dch ${element[0].getCompileTimeConstant()}")
                    if (element.size>1)
                        sb.append(", ${element[1].getCompileTimeConstant()}")
                    sb.append("\n")
                }
            is StringType ->
                for(element in elements)
                    sb.append("dcw ${getStringRef((element as TastStringLiteral).value)}\n")
            else -> error("Unknown array element type")
        }
        sb.append("\n")
    }

    fun output(sb:StringBuilder) {
        // Output class descriptors
        for(klass in allClasses)
            klass.emitDescriptor(sb)

        // Output constant arrays
        for(ca in constArrays)
            ca.emit(sb)

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