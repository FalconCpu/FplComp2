package falcon

class Interpreter(val function: Function, val args : List<Int>) {
    var pc = 0
    var variables = mutableMapOf<IRVal, Int>()

    fun execute() : Int {
        while (true) {
            val ins = function.prog[pc++]
            when (ins) {
                is InstrAlu -> variables[ins.dest] =
                    AluOp.eval(ins.op, variables.getValue(ins.left), variables.getValue(ins.right))

                is InstrAlui -> variables[ins.dest] = AluOp.eval(ins.op, variables.getValue(ins.left), ins.right)
                is InstrBranch -> if (AluOp.eval(ins.op, variables.getValue(ins.left), variables.getValue(ins.right))!=0)
                    pc = ins.label.addr
                is InstrCall -> Interpreter(ins.func, ins.args.map{variables.getValue(it)} ).execute()
                is InstrJump -> pc = ins.label.addr
                is InstrLabel -> {}
                is InstrLoad -> variables[ins.dest] = readMemory(variables.getValue(ins.addr)+ins.offset, ins.size)
                is InstrLoadField -> variables[ins.dest] = readMemory(variables.getValue(ins.addr)+ins.offset.offset, ins.size)
                is InstrMov -> variables[ins.dest] = variables.getValue(ins.src)
                is InstrMovi -> variables[ins.dest] = ins.src
                is InstrRet -> return if (ins.retval!=null) variables.getValue(ins.retval) else 0
                is InstrStart -> ins.params.zip(args).toMap(variables)
                is InstrStore -> writeMemory(variables.getValue(ins.addr)+ins.offset, variables.getValue(ins.data),ins.size)
                is InstrLea -> variables[ins.dest] = DataSegment.getStringAddress(ins.data)
            }
        }
    }

    companion object {
        val output = StringBuilder()

        val memory = Array(0x1000000) { 0 }
        fun readWord(address: Int): Int {
            check((address and 3) == 0)
            return if (address in 0..0x3ffffff)
                memory[address / 4]
            else if (address in -0x10000..0)
                DataSegment.read(address and 0xffff)
            else
                throw Exception("Memory access out of bounds")
        }

        fun writeWord(address: Int, value: Int, mask:Int) {
            check((address and 3) == 0)
            if (address in 0..0x3ffffff)
                memory[address / 4] = (memory[address / 4] and mask.inv()) or (value and mask)
            else if (address == 0xe0000000.toInt())
                output.append(value.toChar())
            else
                throw Exception("Memory access out of bounds")
        }

        fun signExtend8(value: Int): Int {
            return if (value and 0x80 != 0)
                value or 0xff.inv()
            else
                value and 0xff
        }

        fun signExtend16(value: Int): Int {
            return if (value and 0x8000 != 0)
                value or 0xffff.inv()
            else
                value and 0xffff
        }


        fun readMemory(address: Int, size: Int): Int {
            val shift = 8 * (address and 3)
            val msb = address and 3.inv()
            return when (size) {
                1 -> signExtend8(readWord(msb) shr shift)
                2 -> if ((address and 1) == 0)
                    signExtend16(readWord(msb) shr shift)
                else
                    throw Exception("Misaligned Address")

                4 -> readWord(address)
                else -> throw Exception("Invalid size")
            }
            return readWord(address)
        }

        fun writeMemory(address: Int, value: Int, size: Int) {
            val shift = 8 * (address and 3)
            val msb = address and 3.inv()
            when (size) {
                1 -> writeWord(msb, value shl shift, 0xff shl shift)
                2 -> if ((address and 1) == 0)
                    writeWord(msb, value shl shift, 0xffff shl shift)
                else
                    throw Exception("Unaligned memory access")

                4 -> if ((address and 3) == 0)
                    writeWord(msb, value, -1)
                else
                    throw Exception("Unaligned memory access")

                else -> throw Exception("Invalid size")
            }
        }
    }
}