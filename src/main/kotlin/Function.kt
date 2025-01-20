package falcon

val allFunctions = mutableListOf<Function>()

class Function(
    val location: Location,
    val name : String,
    val parameters: List<VarSymbol>,
    val returnType: Type
) {
    val prog = mutableListOf<Instr>()
    val tempVars = mutableListOf<IRTemp>()
    val labels = mutableListOf<Label>()
    val symbols = mutableMapOf<VarSymbol, IRVar>()
    val retVal = if (returnType!= UnitType) newTemp() else null
    val retLabel = newLabel()

    init {
        allFunctions.add(this)
    }

    override fun toString() = name

    fun add(instr: Instr) {
        prog.add(instr)
    }

    fun newTemp() : IRTemp {
        val temp = IRTemp("#${tempVars.size}")
        tempVars.add(temp)
        return temp
    }

    fun newLabel() : Label {
        val label = Label("@${labels.size}")
        labels.add(label)
        return label
    }


    fun addMov(dest: IRVal, src: IRVal) {
        add(InstrMov(dest, src))
    }

    fun addMov(value:Int) : IRTemp {
        val ret = newTemp()
        add(InstrMovi(ret, value))
        return ret
    }

    fun addMov(value: IRVal) : IRTemp {
        val ret = newTemp()
        add(InstrMov(ret, value))
        return ret
    }

    fun addAlu(op:AluOp, left:IRVal, right:IRVal) : IRTemp {
        val ret = newTemp()
        add(InstrAlu(ret, op, left, right))
        return ret
    }

    fun addAlu(op:AluOp, left:IRVal, right:Int) : IRTemp {
        val ret = newTemp()
        add(InstrAlui(ret, op, left, right))
        return ret
    }

    fun addLoad(size:Int, addr: IRVal, offset:Int) : IRTemp {
        val ret = newTemp()
        add(InstrLoad(size, ret, addr, offset))
        return ret
    }

    fun addLoadField(size:Int, addr: IRVal, offset: FieldSymbol) : IRTemp {
        val ret = newTemp()
        add(InstrLoadField(size, ret, addr, offset))
        return ret
    }

    fun addStore(size:Int, value:IRVal, addr: IRVal, offset:Int) {
        add(InstrStore(size, value, addr, offset))
    }

    fun addBranch(op:AluOp, left:IRVal, right:IRVal, label:Label) {
        add(InstrBranch(op, left, right, label))
    }

    fun addJump(label:Label) {
        add(InstrJump(label))
    }

    fun addLabel(label:Label) {
        label.addr = prog.size
        add(InstrLabel(label))
    }

    fun addCall(func:Function, args:List<IRVal>) : IRVal {
        val ret = newTemp()
        add(InstrCall(func, args))
        return ret // TODO: return value
    }

    fun addLea(string:String) : IRVal {
        val ret = newTemp()
        add(InstrLea(ret, string))
        return ret
    }

    fun mapSymbol(symbol:VarSymbol) : IRVar {
        return symbols.getOrPut(symbol) { IRVar(symbol.name) }
    }
}

fun List<Function>.dump() : String {
    val sb = StringBuilder()
    for(func in allFunctions) {
        sb.append("Function ${func.name}:\n")
        for(instr in func.prog)
            sb.append("${instr}\n")
        sb.append("\n")
    }
    return sb.toString()
}