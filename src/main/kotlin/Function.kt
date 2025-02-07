package falcon

val allFunctions = mutableListOf<Function>()

class StackAlloc(val startAddress:Int, val endAddress:Int)

class Function(
    val location: Location,
    val name : String,
    val parameters: List<VarSymbol>,
    val isVararg: Boolean,
    val returnType: Type,
    methodOf : ClassType?
) {
    val prog = mutableListOf<Instr>()
    val vars = machineRegs.toMutableList<IRVal>()
    val labels = mutableListOf<Label>()

    val symbols = mutableMapOf<VarSymbol, IRVar>()
    val retVal = if (returnType!= UnitType) machineRegs[8] else null
    val retLabel = newLabel()
    var maxRegister = 0
    var stackVarsSize = 0
    val thisSymbol = if (methodOf!=null) VarSymbol(location, "this", methodOf, false) else null
    val regAssignmentComments = mutableListOf<String>()
    private val stackAllocations = mutableListOf<StackAlloc>()

    init {
        allFunctions.add(this)
    }

    override fun toString() = name

    fun add(instr: Instr) {
        prog.add(instr)
    }

    var numTemps = 0
    fun newTemp() : IRTemp {
        val temp = IRTemp("#${numTemps++}")
        vars += temp
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

    fun addStoreField(size:Int, value:IRVal, addr: IRVal, offset: FieldSymbol) {
        add(InstrStoreField(size, value, addr, offset))
    }

    fun addBranch(op:AluOp, left:IRVal, right:IRVal, label:Label) {
        add(InstrBranch(op, left, right, label))
    }

    fun addJump(label:Label) {
        add(InstrJump(label))
    }

    fun addLabel(label:Label) {
        label.index = prog.size
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

    fun mapSymbol(symbol:VarSymbol) : IRVar = symbols.getOrPut(symbol) {
        IRVar(symbol.name).also{vars+=it}
    }

    fun stackAlloc(size: Int) : StackAlloc {
        val roundedSize = (size+3) and (0xFFFFC)   // round size up to multiple of 4
        val startAddress = if (stackAllocations.isEmpty()) 0 else stackAllocations.last().endAddress
        val endAddress = startAddress+roundedSize
        if (endAddress > stackVarsSize)
            stackVarsSize = endAddress
        val ret = StackAlloc(startAddress, endAddress)
        stackAllocations += ret
        return ret
    }

    fun freeStackAlloc(stackAlloc: StackAlloc) {
        require(stackAlloc in stackAllocations)
        stackAllocations -= stackAlloc
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