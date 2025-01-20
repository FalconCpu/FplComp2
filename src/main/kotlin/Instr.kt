package falcon

// ----------------------------------------------------------------------------
//                       Instruction types
// ----------------------------------------------------------------------------
// This file describes the instruction types used in the Intermediate Representation.

sealed class Instr() {
    override fun toString(): String = when(this) {
        is InstrAlu -> "$op $dest, $left, $right"
        is InstrAlui -> "$op $dest, $left, $right"
        is InstrBranch -> "B$op $left, $right, $label"
        is InstrCall -> "CALL $func(${args.joinToString(", ")})"
        is InstrJump -> "JMP $label"
        is InstrLabel -> "$label:"
        is InstrLoad -> "LD$size $dest, $addr[$offset]"
        is InstrLoadField -> "LD$size $dest, $addr[$offset]"
        is InstrStore -> "ST$size $data, $addr[$offset]"
        is InstrMov -> "MOV $dest, $src"
        is InstrMovi -> "MOV $dest, $src"
        is InstrRet -> "RET $retval"
        is InstrStart -> "START ${params.joinToString(", ")}"
        is InstrLea -> "LEA $dest, \"$data\""
    }
}

class InstrMov(val dest:IRVal, val src:IRVal) : Instr()
class InstrMovi(val dest:IRVal, val src:Int) : Instr()
class InstrAlu(val dest: IRVal, val op:AluOp, val left:IRVal, val right:IRVal) : Instr()
class InstrAlui(val dest: IRVal, val op:AluOp, val left:IRVal, val right:Int) : Instr()
class InstrBranch(val op:AluOp, val left:IRVal, val right:IRVal, val label: Label) : Instr()
class InstrJump(val label:Label) : Instr()
class InstrLabel(val label:Label) : Instr()
    {var addr = 0}
class InstrCall(val func: Function, val args:List<IRVal>) : Instr()
class InstrRet(val retval:IRVal?) : Instr()
class InstrStart(val params:List<IRVal>) : Instr()
class InstrStore(val size:Int, val data:IRVal, val addr:IRVal, val offset: Int) : Instr()
class InstrLoad(val size:Int, val dest:IRVal, val addr:IRVal, val offset: Int) : Instr()
class InstrLoadField(val size:Int, val dest:IRVal, val addr:IRVal, val offset: FieldSymbol) : Instr()
class InstrLea(val dest:IRVal, val data:String) : Instr()

sealed class IRVal() {
    override fun toString() = when(this) {
        is IRReg -> name
        is IRVar -> name
        is IRTemp -> name
    }
}
class IRVar(val name:String) : IRVal()
class IRReg(val name:String) : IRVal()
class IRTemp(val name: String) : IRVal()

class Label(val name:String) {
    var addr = 0
    override fun toString() = name
}


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

    ADDS,
    EQS,
    NES,
    GTS,
    GES,
    LTS,
    LES,

    ANDB,
    ORB,

    ERROR;

    companion object {
        fun eval(op:AluOp, lhs:Int, rhs:Int) : Int = when(op) {
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
            ADDS -> TODO()
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
}