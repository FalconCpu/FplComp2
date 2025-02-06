package falcon

/**
 * Generate the header for the assembly file
 */

val allStrings = mutableListOf<String>()

fun genAssemblyHeader(sb: StringBuilder) {
    sb.append("# Generated by Falcon Compiler\n")
    sb.append("sub $31, 4\n")
    sb.append("stw $30, $31[0]\n")
    sb.append("jsr init\n")
    sb.append("jsr /main\n")
    sb.append("ldw $30, $31[0]\n")
    sb.append("add $31, 4\n")
    sb.append("ret\n\n")
}


private fun InstrAlu.genAssembly() : String = when(op) {
    AluOp.ADDI -> "add $dest, $left, $right"
    AluOp.SUBI -> "sub $dest, $left, $right"
    AluOp.MULI -> "mul $dest, $left, $right"
    AluOp.DIVI -> "divs $dest, $left, $right"
    AluOp.MODI -> "mods $dest, $left, $right"
    AluOp.ANDI -> "and $dest, $left, $right"
    AluOp.ORI -> "or $dest, $left, $right"
    AluOp.XORI -> "xor $dest, $left, $right"
    AluOp.EQI -> "xor $dest, $left, $right\ncltu $dest, $dest, 1"
    AluOp.NEI -> "xor $dest, $left, $right\ncltu $dest, 0, $dest"
    AluOp.LTI -> "clt $dest, $left, $right"
    AluOp.GTI -> "clt $dest, $right, $left"
    AluOp.LEI -> "clt $dest, $right, $left\nxor $dest, $dest, 1"
    AluOp.GEI -> "clt $dest, $left, $right\nxor $dest, $dest, 1"
    AluOp.LSLI -> "lsl $dest, $left, $right"
    AluOp.LSRI -> "lsr $dest, $left, $right"
    AluOp.ASRI -> "asr $dest, $left, $right"
    AluOp.ADDR -> TODO()
    AluOp.SUBR -> TODO()
    AluOp.MULR -> TODO()
    AluOp.DIVR -> TODO()
    AluOp.MODR -> TODO()
    AluOp.EQR -> TODO()
    AluOp.NER -> TODO()
    AluOp.LTR -> TODO()
    AluOp.GTR -> TODO()
    AluOp.LER -> TODO()
    AluOp.GER -> TODO()
    AluOp.ADDS -> TODO()
    AluOp.EQS,
    AluOp.NES,
    AluOp.LTS,
    AluOp.GTS,
    AluOp.LES,
    AluOp.GES,
    AluOp.ANDB,
    AluOp.ORB -> error("Should have been lowered before genAssembly")
    AluOp.ERROR -> TODO()
}

private fun InstrAlui.genAssembly() : String = when(op) {
    AluOp.ADDI -> "add $dest, $left, $value"
    AluOp.SUBI -> "sub $dest, $left, $value"
    AluOp.MULI -> "mul $dest, $left, $value"
    AluOp.DIVI -> "divs $dest, $left, $value"
    AluOp.MODI -> "mods $dest, $left, $value"
    AluOp.ANDI -> "and $dest, $left, $value"
    AluOp.ORI -> "or $dest, $left, $value"
    AluOp.XORI -> "xor $dest, $left, $value"
    AluOp.EQI -> "xor $dest, $left, $value\ncltu $dest, $dest, 1"
    AluOp.NEI -> "xor $dest, $left, $value\ncltu $dest, 0, $dest"
    AluOp.LTI -> "clt $dest, $left, $value"
    AluOp.GTI -> "clt $dest, $value, $left"
    AluOp.LEI -> "clt $dest, $value, $left\nxor $dest, $dest, 1"
    AluOp.GEI -> "clt $dest, $left, $value\nxor $dest, $dest, 1"
    AluOp.LSLI -> "lsl $dest, $left, $value"
    AluOp.LSRI -> "lsr $dest, $left, $value"
    AluOp.ASRI -> "asr $dest, $left, $value"
    AluOp.ADDR -> TODO()
    AluOp.SUBR -> TODO()
    AluOp.MULR -> TODO()
    AluOp.DIVR -> TODO()
    AluOp.MODR -> TODO()
    AluOp.EQR -> TODO()
    AluOp.NER -> TODO()
    AluOp.LTR -> TODO()
    AluOp.GTR -> TODO()
    AluOp.LER -> TODO()
    AluOp.GER -> TODO()
    AluOp.ADDS -> TODO()
    AluOp.EQS,
    AluOp.NES,
    AluOp.LTS,
    AluOp.GTS,
    AluOp.LES,
    AluOp.GES,
    AluOp.ANDB,
    AluOp.ORB -> error("Should have been lowered before genAssembly")
    AluOp.ERROR -> TODO()
}

private fun InstrBranch.genAssembly() : String = when (op) {
    AluOp.EQI -> "beq $left, $right, $label"
    AluOp.NEI -> "bne $left, $right, $label"
    AluOp.LTI -> "blt $left, $right, $label"
    AluOp.GTI -> "blt $right, $left, $label"
    AluOp.LEI -> "bge $right, $left, $label"
    AluOp.GEI -> "bge $left, $right, $label"
    else -> error("Not a valid branch operand")
}

private fun escape(str:String) : String {
    val sb = StringBuilder()
    for(c in str)
        if (c=='\n')
            sb.append("\\n")
        else
            sb.append(c)
    return sb.toString()
}

private fun getStoreSize(size:Int) = when(size) {
    1 -> "stb"
    2 -> "sth"
    4 -> "stw"
    else -> error("Invalid store size $size")
}

private fun getLoadSize(size:Int) = when(size) {
    1 -> "ldb"
    2 -> "ldh"
    4 -> "ldw"
    else -> error("Invalid load size $size")
}

private fun Instr.genAssembly() = when(this) {
    is InstrAlu -> genAssembly()
    is InstrAlui -> genAssembly()
    is InstrBranch -> genAssembly()
    is InstrCall -> "jsr /${func.name}"
    is InstrRet -> ""
    is InstrJump -> "jmp $label"
    is InstrLabel -> "$label:"
    is InstrLea -> "ld $dest, ${DataSegment.getStringRef(data)}"
    is InstrLoad -> "${getLoadSize(size)} $dest,$addr[${offset}]"
    is InstrMov -> "ld $dest, $src"
    is InstrNop -> "nop"
    is InstrStart -> ""
    is InstrStore -> "${getStoreSize(size)} $data,$addr[${offset}]"
    is InstrLoadField -> "${getLoadSize(size)} $dest,$addr[${offset.offset}]"
    is InstrMovi -> "ld $dest,$value"
    is InstrStoreField -> "${getStoreSize(size)} $data,$addr[${offset.offset}]"
}



fun Function.genAssembly(sb:StringBuilder) {
    if (name=="<TopLevel>")
        sb.append("init:\n")
    else
        sb.append("/$name:\n")

    for(comment in regAssignmentComments)
        sb.append("# $comment\n")

    // setup stack frame
    val makesCalls = prog.any{it is InstrCall}
    val stackSize = stackVarsSize + (if (maxRegister>8) 4*(maxRegister-8) else 0) + (if (makesCalls) 4 else 0)
    if (stackSize!=0) {
        sb.append("sub $31, $31, $stackSize\n")
        for(r in 9..maxRegister)
            sb.append("stw $$r, $31[${stackVarsSize + 4*(r-9)}]\n")
        if (makesCalls)
            sb.append("stw $30, $31[${stackSize-4}]\n")
    }

    for(instr in prog) {
        if (instr is InstrStart || instr is InstrRet)
            continue
        sb.append(instr.genAssembly())
        sb.append("\n")
    }

    // teardown stack frame
    if (stackSize!=0) {
        for(r in 9..maxRegister)
            sb.append("ldw $$r, $31[${stackVarsSize + 4*(r-9)}]\n")
        if (makesCalls)
            sb.append("ldw $30, $31[${stackSize-4}]\n")
        sb.append("add $31, $31, $stackSize\n")
    }
    sb.append("ret\n\n")
}


// Strings have to be handled slightly differently to other data types
// as we leave their final locations up to the assembler. So maintain
// a map of which locations contain pointers to strings
private val stringLocations = mutableMapOf<Int,String>()

fun genAssemblyGlobalVars(sb:StringBuilder) {
////    if (globalVariablesSize==0)
////        return
//
//    sb.append("_section_data:\n")
//
//    val globalMem = Array<Int>(globalVariablesSize/4){0}
//    stringLocations.clear()
//
//    for(v in allGlobalVariable)
//        v.initialValue?.simEval(globalMem, v.offset)
//
//    for(index in 0..<globalVariablesSize/4) {
//        val addr = index * 4
//        val label = allGlobalVariable.find {it.offset==addr}
//        if (label!=null)
//            sb.append("${label.name}: ")
//        val stringVal = stringLocations[addr]
//        if (stringVal!=null)
//            sb.append("dcw \"$stringVal\"\n")
//        else
//            sb.append("dcw ${globalMem[index]}\n")
//    }
}

fun Array<Int>.write(addr:Int, size:Int, value:Int) {
    val a = addr / 4;
    val shift = (addr and 3) *8
    when(size) {
        1-> {
            // write byte
            val mask = 0xff shl shift
            this[a] = (this[a] and mask.inv()) or ((value and 0xff) shl shift)
        }

        2-> {
            // write half word
            assert(addr % 2 == 0)
            val mask = 0xffff shl shift
            this[a] = (this[a] and mask.inv()) or ((value and 0xffff) shl shift)
        }

        4-> {
            // write whole word
            assert(addr % 4 == 0)
            this[a] = value
        }

        else -> error("Invalid write size")
    }
}