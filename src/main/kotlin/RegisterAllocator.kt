package falcon

class RegisterAllocator(private val func: Function, private val livemap: Livemap) {

    // reg numbers  0..31 represent the CPU registers,
    // CPU register number 0, 29,30 and 31 have dedicated uses. leaving numbers 1..28 for allocation
    // reg numbers 32 and up are the user variables that need to be assigned to cpu registers.
    // Register numbers 1..8 potentially get clobbered by function calls
    // Register number -1 is used to indicate currently unallocated
    private val all_cpu_regs = 0..31
    private val cpu_regs = 1..28
    private val num_vars = func.vars.size
    private val user_vars = 32..<num_vars
    private val UNALLOCATED = -1
    private val caller_save_regs = 1..8


    // Array of which registers are allocated to each variable
    private val alloc = Array(num_vars){ if (it<=31) it else UNALLOCATED}

    // Array of which Args interfere with each arg
    private val interfere = Array(num_vars){mutableSetOf<Int>() }

    // List of MOV statements in the prog, where both operands are variables
    private val movStatements = func.prog.filterIsInstance<InstrMov>()

    private val debug = false


    /**
     * Build a map listing every Arg that interferes with an arg
     */
    private fun buildInterfere() {
        for (instr in func.prog) {
            // Args written by an instruction interfere with everything live at that point
            // (Except for a MOV statement - no interference is created between its Dest and Src)
            val dest = instr.getWrites()
            if (dest != null)
                for (liveIndex in livemap.live[instr.index + 1].stream()) {
                    if (liveIndex != dest.index && !(instr is InstrMov && liveIndex == instr.src.index)) {
                        interfere[dest.index] += liveIndex
                        interfere[liveIndex] += dest.index
                    }
                }

            // A call statement could potentially clobber registers %1-%8, so mark those
            if (instr is InstrCall ) {
                for (liveIndex in livemap.live[instr.index + 1].stream()) {
                    for (dest in caller_save_regs)
                        if (liveIndex != dest) {
                            interfere[dest] += liveIndex
                            interfere[liveIndex] += dest
                        }
                }
            }
        }
    }

    private fun dumpInterfere() {
        println("Interfere Graph:")
        for(index in interfere.indices)
            if (interfere[index].isNotEmpty())
                println("${func.vars[index]} = ${interfere[index].joinToString { func.vars[it].name }}")
    }

    /**
     * Assign variable 'v' to register 'r'
     */
    private fun assign(v:Int, r:Int) {
        if (debug)
            println("Assigning ${func.vars[v]} to ${func.vars[r]}")
        assert(r in all_cpu_regs)
        assert(v in user_vars)
        assert(! interfere[r].contains(v))
        if (func.vars[v] !is IRTemp)
            func.regAssignmentComments += "${func.vars[v]} = ${func.vars[r]}"

        alloc[v] = r
        interfere[r] += interfere[v]
        if (r>func.maxRegister && r<=28)
            func.maxRegister = r
    }

    private fun lookForCoalesce() {
        for(mov in movStatements) {
            val a = mov.src.index
            val d = mov.dest.index
            if (alloc[a]==UNALLOCATED && alloc[d]!=UNALLOCATED && (a !in interfere[alloc[d]]))
                assign(a,alloc[d])
            if (alloc[d]==UNALLOCATED && alloc[a]!=UNALLOCATED && (d !in interfere[alloc[a]]))
                assign(d,alloc[a])
        }
    }

    /**
     * Find a register which does not have an interference with 'v'
     */
    private fun findAssignFor(v:Int) : Int{
        for (r in cpu_regs) {
            if (v !in interfere[r])
                return r
        }
        error("Unable to find a register for ${func.vars[v]}")
    }

    private fun replace(a: IRVal) = machineRegs[alloc[a.index]]

    private fun Instr.replaceVars() : Instr {
        val new = when(this) {
            is InstrNop -> this
            is InstrAlu -> InstrAlu(replace(dest), op, replace(left), replace(right))
            is InstrAlui -> InstrAlui(replace(dest), op, replace(left), value)
            is InstrBranch -> InstrBranch(op, replace(left), replace(right), label)
            is InstrCall -> this
            is InstrJump -> this
            is InstrLabel -> this
            is InstrLea -> InstrLea(replace(dest), data)
            is InstrLoad -> InstrLoad(size, replace(dest), replace(addr), offset)
            is InstrMov -> InstrMov(replace(dest), replace(src))
            is InstrMovi -> InstrMovi(replace(dest), value)
            is InstrRet -> this
            is InstrStart -> this
            is InstrStore -> InstrStore(size, replace(data), replace(addr), offset)
            is InstrLoadField -> InstrLoadField(size, replace(dest), replace(addr), offset)
            is InstrStoreField -> InstrStoreField(size, replace(data), replace(addr), offset)
            is InstrLoadGlobal -> InstrLoadGlobal(size, replace(dest), offset)
            is InstrStoreGlobal -> InstrStoreGlobal(size, replace(data), offset)
            is InstrClassRef -> InstrClassRef(replace(dest), data)
        }
        new.index = index
        return new
    }

    fun run() {
        if (debug)
            livemap.dump()
        buildInterfere()
        if (debug)
            dumpInterfere()

        // Perform the allocation starting with the most difficult vars
        val vars = user_vars.sortedByDescending { interfere[it].size }
        lookForCoalesce()

        for(v in vars) {
            if (alloc[v] == UNALLOCATED) {
                val r = findAssignFor(v)
                assign(v, r)
                lookForCoalesce()
            }
        }

        for(index in func.prog.indices)
            func.prog[index] = func.prog[index].replaceVars()
    }
}