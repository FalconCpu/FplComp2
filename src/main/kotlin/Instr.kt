package falcon

// ----------------------------------------------------------------------------
//                       Instruction types
// ----------------------------------------------------------------------------
// This file describes the instruction types used in the Intermediate Representation.

sealed class Instr() {
    var index = 0
    override fun toString(): String = when(this) {
        is InstrNop -> "NOP"
        is InstrAlu -> "$op $dest, $left, $right"
        is InstrAlui -> "$op $dest, $left, $value"
        is InstrBranch -> "B$op $left, $right, $label"
        is InstrCall -> "CALL $func(${args.joinToString(", ")})"
        is InstrJump -> "JMP $label"
        is InstrLabel -> "$label:"
        is InstrLoad -> "LD$size $dest, $addr[$offset]"
        is InstrStore -> "ST$size $data, $addr[$offset]"
        is InstrMov -> "MOV $dest, $src"
        is InstrMovi -> "MOV $dest, $value"
        is InstrRet -> "RET $retval"
        is InstrStart -> "START"
        is InstrLea -> "LEA $dest, \"$data\""
        is InstrLoadField -> "LD$size $dest, $addr[$offset]"
        is InstrStoreField -> "ST$size $data, $addr[$offset]"
        is InstrLoadGlobal -> "LD$size $dest, GLOBAL[$offset]"
        is InstrStoreGlobal -> "ST$size $data, GLOBAL[$offset]"
        is InstrClassRef -> "LEA $dest, CLASS($data)"
        is InstrLeaFunc -> "LEA $dest, $func"
    }

    fun getReads() : List<IRVal> = when(this) {
        is InstrNop -> emptyList()
        is InstrAlu -> listOf(left, right)
        is InstrAlui -> listOf(left)
        is InstrBranch -> listOf(left, right)
        is InstrCall -> args
        is InstrJump -> emptyList()
        is InstrLabel -> emptyList()
        is InstrLoad -> listOf(addr)
        is InstrStore -> listOf(data,addr)
        is InstrMov -> listOf(src)
        is InstrMovi -> emptyList()
        is InstrRet -> retval
        is InstrStart -> emptyList()
        is InstrLea -> emptyList()
        is InstrClassRef -> emptyList()
        is InstrLoadField -> listOf(addr)
        is InstrStoreField -> listOf(data,addr)
        is InstrLoadGlobal -> emptyList()
        is InstrStoreGlobal -> listOf(data)
        is InstrLeaFunc -> emptyList()
    }

    fun getWrites() : IRVal? = when(this) {
        is InstrNop -> null
        is InstrAlu -> dest
        is InstrAlui -> dest
        is InstrBranch -> null
        is InstrCall -> null
        is InstrJump -> null
        is InstrLabel -> null
        is InstrLoad -> dest
        is InstrLoadField -> dest
        is InstrStore -> null
        is InstrMov -> dest
        is InstrMovi -> dest
        is InstrRet -> null
        is InstrStart -> null
        is InstrLea -> dest
        is InstrStoreField -> null
        is InstrLoadGlobal -> dest
        is InstrStoreGlobal -> null
        is InstrClassRef -> dest
        is InstrLeaFunc -> dest
    }
}

class InstrNop() : Instr()
class InstrMov(val dest:IRVal, val src:IRVal) : Instr()
class InstrMovi(val dest:IRVal, val value:Int) : Instr()
class InstrAlu(val dest: IRVal, val op:AluOp, val left:IRVal, val right:IRVal) : Instr()
class InstrAlui(val dest: IRVal, val op:AluOp, val left:IRVal, val value:Int) : Instr()
class InstrBranch(val op:AluOp, val left:IRVal, val right:IRVal, val label: Label) : Instr()
class InstrJump(val label:Label) : Instr()
class InstrLabel(val label:Label) : Instr()
class InstrCall(val func: Function, val args:List<IRVal>) : Instr()
class InstrRet(val retval:List<IRVal>) : Instr()
class InstrStart() : Instr()
class InstrStore(val size:Int, val data:IRVal, val addr:IRVal, val offset: Int) : Instr()
class InstrLoad(val size:Int, val dest:IRVal, val addr:IRVal, val offset: Int) : Instr()
class InstrLoadField(val size:Int, val dest:IRVal, val addr:IRVal, val offset: FieldSymbol) : Instr()
class InstrStoreField(val size:Int, val data:IRVal, val addr:IRVal, val offset: FieldSymbol) : Instr()
class InstrLoadGlobal(val size:Int, val dest:IRVal, val offset: GlobalVarSymbol) : Instr()
class InstrStoreGlobal(val size:Int, val data:IRVal, val offset: GlobalVarSymbol) : Instr()
class InstrLea(val dest:IRVal, val data:String) : Instr()
class InstrLeaFunc(val dest:IRVal, val func:Function) : Instr()
class InstrClassRef(val dest:IRVal, val data:ClassType) : Instr()

sealed class IRVal(val name:String) {
    var index = 0
    override fun toString() = name
}
class IRVar(name:String) : IRVal(name)
class IRReg(name:String) : IRVal(name)
class IRTemp(name: String) : IRVal(name)

class Label(val name:String) {
    var index = 0
    override fun toString() = name
}

val machineRegs = listOf(
    IRReg("0"),
    IRReg("$1"),
    IRReg("$2"),
    IRReg("$3"),
    IRReg("$4"),
    IRReg("$5"),
    IRReg("$6"),
    IRReg("$7"),
    IRReg("$8"),
    IRReg("$9"),
    IRReg("$10"),
    IRReg("$11"),
    IRReg("$12"),
    IRReg("$13"),
    IRReg("$14"),
    IRReg("$15"),
    IRReg("$16"),
    IRReg("$17"),
    IRReg("$18"),
    IRReg("$19"),
    IRReg("$20"),
    IRReg("$21"),
    IRReg("$22"),
    IRReg("$23"),
    IRReg("$24"),
    IRReg("$25"),
    IRReg("$26"),
    IRReg("$27"),
    IRReg("$28"),
    IRReg("$29"),
    IRReg("$30"),
    IRReg("$31")
)

enum class AluOp {
    ADDI,
    SUBI,
    MULI,
    DIVI,
    MODI,
    LSLI,
    ASRI,
    LSRI,
    ANDI,
    ORI,
    XORI,
    EQI,
    NEI,
    GTI,
    GEI,
    LTI,
    LEI,

    ADDR,
    SUBR,
    MULR,
    DIVR,
    MODR,
    EQR,
    NER,
    GTR,
    GER,
    LTR,
    LER,

    EQS,
    NES,
    GTS,
    GES,
    LTS,
    LES,

    ANDB,
    ORB,

    ERROR;

    fun isCommutative() : Boolean = when(this) {
        ADDI, MULI, ANDI, ORI, XORI, EQI, NEI -> true
        ADDR, MULR, EQR, NER, GTR, GER, LTR, LER -> true
        else -> false
    }

    fun eval(lhs:Int, rhs:Int) : Int = when(this) {
        ADDI -> lhs + rhs
        SUBI -> lhs - rhs
        MULI -> lhs * rhs
        DIVI -> lhs / rhs
        MODI -> lhs % rhs
        LSLI -> lhs shl (rhs and 31)
        ASRI -> lhs shr (rhs and 31)
        LSRI -> lhs ushr (rhs and 31)
        ANDI -> lhs and rhs
        ORI -> lhs or rhs
        XORI -> lhs xor rhs
        EQI -> if (lhs == rhs) 1 else 0
        NEI -> if (lhs != rhs) 1 else 0
        GTI -> if (lhs > rhs) 1 else 0
        GEI -> if (lhs >= rhs) 1 else 0
        LTI -> if (lhs < rhs) 1 else 0
        LEI -> if (lhs <= rhs) 1 else 0
        ADDR -> TODO()
        SUBR -> TODO()
        MULR -> TODO()
        DIVR -> TODO()
        MODR -> TODO()
        EQR -> TODO()
        NER -> TODO()
        GTR -> TODO()
        GER -> TODO()
        LTR -> TODO()
        LER -> TODO()
        EQS -> TODO()
        NES -> TODO()
        GTS -> TODO()
        GES -> TODO()
        LTS -> TODO()
        LES -> TODO()
        ANDB -> TODO()
        ORB -> TODO()
        ERROR -> TODO()
    }
}