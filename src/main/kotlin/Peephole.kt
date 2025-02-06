package falcon
import falcon.AluOp.*

class Peephole(private val func: Function) {
    private val prog = func.prog
    private var def = mutableMapOf<IRVal,MutableList<Instr>>()
    private var use = mutableMapOf<IRVal,MutableList<Instr>>()
    private val labelUse = mutableMapOf<Label,MutableList<Instr>>()

    private val debug = false
    private var madeChange = false

    /**
     * Re-build indexes of the program.
     *
     * Remove useless instructions / Variables / Labels
     * Sets every Var and Instr to have the correct index
     * Sets index of Labels to point to the instruction index where they are defined
     * Build def- and use- information for all Vars
     */

    private fun rebuildIndex() {
        for(label in func.labels)
            label.index = -1

        def.clear()
        use.clear()
        for(v in func.vars) {
            def[v] = mutableListOf()
            use[v] = mutableListOf()
        }

        labelUse.clear()
        func.labels.forEach{ labelUse[it] = mutableListOf()}

        // Mark the start instruction as being a def of all the parameter registers
        for(i in func.parameters.indices)
            def.getValue(machineRegs[i+1]) += prog[0]

        for((index,instr) in prog.withIndex()) {
            instr.index = index
            val d = instr.getWrites()
            if (d!=null)
                def.getValue(d) += instr
            for(u in instr.getReads())
                use.getValue(u) += instr
            if (instr is InstrLabel)
                instr.label.index = index
            if (instr is InstrBranch)
                labelUse.getValue(instr.label) += instr
            if (instr is InstrJump)
                labelUse.getValue(instr.label) += instr
        }

        func.vars.removeIf { def.getValue(it).isEmpty() && use.getValue(it).isEmpty() && it.index>=32}
        func.labels.removeIf { it.index==-1 }

        for((index,v) in func.vars.withIndex())
            v.index = index
    }

    private fun changeToNop(instr: Instr) = changeToNop(instr.index)

    private fun changeToNop(index:Int) {
        val ins = prog[index]
        if (ins is InstrNop)
            return

        if (debug)
            println("Removing $index")

        val d = ins.getWrites()
            def[d]?.remove(ins)
        for(u in ins.getReads())
            use[u]?.remove(ins)
        prog[index] = InstrNop()
        madeChange = true
    }

    private fun removeNop() {
        prog.removeIf { it is InstrNop }
    }

    private fun Instr.replaceWith(newInstr: Instr) {
        if (debug)
            println("Replaced $index with $newInstr")
        newInstr.index = index
        def[getWrites()]?.remove(this)
        for(u in getReads())
            use[u]?.remove(this)

        def[newInstr.getWrites()]?.add(newInstr)
        for(u in newInstr.getReads())
            use.getOrPut(u){mutableListOf()}.add(newInstr)
        prog[index] = newInstr
        madeChange = true
    }


    /**
     * Invert a branch operation
     */
    private fun AluOp.invert() : AluOp = when(this) {
        EQI  -> NEI
        NEI  -> EQI
        LTI  -> GEI
        GTI  -> LEI
        LEI -> GTI
        GEI -> LTI
        else -> error("malformed branch instruction")
    }

    /**
     * Check to see if a Variable has a constant value
     */
    private fun IRVal.varHasConstantValue() : Boolean {
        val d = def[this]
        return (d!=null && d.size==1 && d[0] is InstrMovi)
    }

    private fun IRVal.isSmallInt() : Boolean {
        val d = def[this]
        return (d!=null && d.size==1 && d[0] is InstrMovi && (d[0] as InstrMovi).value in -0x1000..0xFFF)
    }

    private fun IRVal.hasValueZero() : Boolean {
        val d = def[this]
        return (d!=null && d.size==1 && d[0] is InstrMovi && (d[0] as InstrMovi).value==0)
    }

    private fun IRVal.getConstantValue() : Int {
        val d = def[this]
        check (d!=null && d.size==1 && d[0] is InstrMovi)
        return (d[0] as InstrMovi).value
    }

    private fun IRVal.isNeverUsed() : Boolean {
        val destUse = use.getValue(this)
        return destUse.isEmpty() && ! this.isRegister()
    }

    private fun Int.isPowerOf2() = (this and (this-1))==0

    private fun log2(x:Int) : Int {
        var ret = 0
        var n = x
        while (n>1) {
            ret++
            n /= 2
        }
        return ret
    }

    private fun IRVal.isRegister() = this is IRReg


    /**
     * Finds instruction specific peephole optimizations.
     */

    private fun Instr.peephole()  {
        when (this) {
            is InstrMov -> {
                // remove instructions when the result is never used
                if (dest.isNeverUsed())
                    return changeToNop(this)

                if (dest==src)
                    return changeToNop(this)

                if (src.varHasConstantValue())
                    return replaceWith( InstrMovi(dest, src.getConstantValue()))
            }

            is InstrMovi -> {
                // remove instructions when the result is never used
                if (dest.isNeverUsed())
                    return changeToNop(this)
            }

            is InstrAlu -> {
                // remove instructions when the result is never used
                if (dest.isNeverUsed())
                    return changeToNop(this)

                val aConst = left.isSmallInt()
                val bConst = right.isSmallInt()

                if (aConst && bConst) {
                    TODO("compute constant")
                }

                if (bConst)
                    return replaceWith( InstrAlui(dest, op, left, right.getConstantValue()))

                if (aConst && op.isCommutative())
                    return replaceWith( InstrAlui(dest, op, right, left.getConstantValue()))
            }

            is InstrAlui -> {
                // remove instructions when the result is never used
                if (dest.isNeverUsed())
                    return changeToNop(this)

                // perform some arithmetic simplifications
                if (value==0 && op in listOf(ADDI, SUBI, ORI, XORI, LSLI, LSRI, ASRI))
                    return replaceWith( InstrMov(dest, left))
                if (value==1 && op in listOf(MULI,DIVI))
                    return replaceWith( InstrMov(dest, left))
                if (value.isPowerOf2() && op==MULI)
                    return replaceWith(InstrAlui(dest, LSLI, left, log2(value)))
                if (value.isPowerOf2() && op==DIVI)
                    return replaceWith(InstrAlui(dest, ASRI, left, log2(value)))
                if (value.isPowerOf2() && value in 0..0xfff && op==MODI)
                    return replaceWith(InstrAlui(dest, ANDI, left, value-1))
            }


            is InstrJump -> {
                // Look for jumps to next instruction
                if (label.index==index+1)
                    changeToNop(this)
            }

            is InstrBranch -> {
                // Look for branch to next instruction
                if (label.index==index+1)
                    changeToNop(this)

                // look for a branch to a label that is immediately followed by a jump
                val instAtBranchDest = prog[label.index+1]
                if ( instAtBranchDest is InstrJump)
                    replaceWith( InstrBranch( op, left, right, instAtBranchDest.label))

                // Look for either of the arguments = 0
                if (left.hasValueZero())
                    return replaceWith( InstrBranch( op, machineRegs[0], right, label))
                if (right.hasValueZero())
                    return replaceWith( InstrBranch( op, left, machineRegs[0], label) )

                // Look for a branch over a jump.
                val nextInstr = prog[index+1]
                if (label.index==index+2 && nextInstr is InstrJump) {
                    replaceWith( InstrBranch( op.invert(), left, right, nextInstr.label))
                    changeToNop( index+1 )
                }
            }

            is InstrLabel -> {
                // labels that are never used
                if (labelUse.getValue(label).isEmpty())
                    changeToNop(this)
            }

            is InstrLoad -> {
                // Look for 'Add $t,$a,intval'  followed by 'ldx $dest,$t[offset]'
                val prevInstr = prog[index-1]
                if(prevInstr is InstrAlui && prevInstr.op==ADDI && prevInstr.dest==addr)
                    replaceWith( InstrLoad(size, dest, prevInstr.left, offset+prevInstr.value))
            }

            is InstrStore -> {
                // Look for 'Add $t,$a,intval'  followed by 'ldx $dest,$t[offset]'
                val prevInstr = prog[index-1]
                if(prevInstr is InstrAlui && prevInstr.op==ADDI && prevInstr.dest==addr)
                    replaceWith( InstrStore(size, data, prevInstr.left, offset+prevInstr.value))
            }


            else -> {}
        }
    }

    /**
     * Make a pass through the program, removing unreachable instructions
     * and calling the peephole function on others
     */

    private fun peepholePass() {
        var reachable = true
        for(instr in prog) {
            if (instr  is InstrLabel)
                reachable = true
            if (reachable)
                instr.peephole()
            else
                changeToNop(instr)
            if (instr is InstrJump)
                reachable = false
        }
    }

    fun run() {
        do {
            madeChange = false
            rebuildIndex()

            if (debug)
                for((i,instr)  in prog.withIndex())
                    println("$i $instr")

            peepholePass()
            removeNop()

        } while(madeChange)
    }
}
