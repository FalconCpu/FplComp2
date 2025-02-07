package falcon

// ----------------------------------------------------------------------------
//                       Code Generation
// ----------------------------------------------------------------------------
// Convert a type-checked AST into the IR code.

private lateinit var currentFunc : Function

// ---------------------------------------------------------------------------
//                            Expressions
// ---------------------------------------------------------------------------

fun TastExpression.codeGen() : IRVal {
    return when (this) {
        is TastBinaryOp -> currentFunc.addAlu(op, left.codeGen(), right.codeGen())
        is TastCharLiteral -> currentFunc.addMov(value.code)
        is TastIntLiteral -> currentFunc.addMov(value)
        is TastVariable -> currentFunc.mapSymbol(symbol)
        is TastRealLiteral -> TODO()
        is TastStringLiteral -> currentFunc.addLea(value)
        is TastError -> TODO()
        is TastAndOp -> TODO()
        is TastCompareOp -> TODO()
        is TastOrOp -> TODO()
        is TastCast -> {
            val value = expr.codeGen()
            currentFunc.addMov(value)
        }

        is TastIndex -> {
            val expr = expr.codeGen()
            val index = index.codeGen()
            val indexScaled = currentFunc.addAlu(AluOp.MULI, index, type.getSize())
            val addr = currentFunc.addAlu(AluOp.ADDI, expr, indexScaled)
            currentFunc.addLoad(type.getSize(), addr, 0)
        }

        is TastFunctionCall -> {
            if (func is TastFunctionLiteral) {
                val function = func.function
                val numParams = function.parameters.size
                val numRegs = codeGenFuncArgs(args, numParams, function.isVararg)
                currentFunc.addCall(func.function, numRegs)
                // Fetch the return value
                if (function.returnType!= UnitType)
                    currentFunc.addMov(machineRegs[8])
                else
                    machineRegs[0]
            } else {
                TODO("Function expressions are not yet supported")
            }
        }

        is TastMember -> {
            val expr = expr.codeGen()
            currentFunc.addLoadField(type.getSize(), expr, member)
        }

        is TastNewArray -> {
            if (isLocal) {
                val numElements = size.getCompileTimeConstant()
                val size = 4 + numElements * (type as ArrayType).getSize()
                val alloc = currentFunc.stackAlloc(size)
                val numElementsIRval = currentFunc.addMov(numElements)
                val ret = currentFunc.addAlu(AluOp.ADDI, machineRegs[31], alloc.startAddress+4)
                currentFunc.addStoreField(4,numElementsIRval, ret, lengthSymbol)
                return ret
            } else {
                TODO("new array")
            }
            TODO()
        }

        is TastFunctionLiteral -> TODO()
        is TastTypeDescriptor -> TODO()
        is TastConstructor -> TODO()
        is TastNeg -> TODO()

    }
}

// ---------------------------------------------------------------------------
//                            Argument Lists
// --------------------------------------------------------------------------
// Generate the code for an argument list.
// Returns the list of registers used

fun codeGenFuncArgs(args:List<TastExpression>, numParameters:Int, isVariadic: Boolean) : List<IRVal> {
    if (!isVariadic) {
        check(args.size == numParameters) { "Wrong number of parameters" }
        val args = args.map { it.codeGen() }
        for((index,arg) in args.withIndex())
            currentFunc.add(InstrMov(machineRegs[index+1], arg))
        return machineRegs.slice(1..<numParameters+1)

    } else {
        val numVarargs = args.size - numParameters + 1
        val stackSize = 4 + 4*numVarargs
        val stackAlloc = currentFunc.stackAlloc(stackSize)
        val nv = currentFunc.addMov(numVarargs)

        // Store the varargs in the stack
        currentFunc.addStore(4, nv,  machineRegs[31], stackAlloc.startAddress)
        for(index in 0..<numVarargs) {
            val arg = args[numParameters + index - 1].codeGen()
            val offset = 4 + 4 * index
            currentFunc.addStore(4, arg, machineRegs[31], stackAlloc.startAddress + offset)
        }

        // and the rest of the arguments in registers
        val args = args.slice(0..<numParameters-1).map { it.codeGen() }
        for((index,arg) in args.withIndex())
            currentFunc.add(InstrMov(machineRegs[index+1], arg))

        // And add the pointer to the varargs to the stack
        currentFunc.add(InstrAlui(machineRegs[numParameters], AluOp.ADDI, machineRegs[31], stackAlloc.startAddress + 4))
        currentFunc.freeStackAlloc(stackAlloc)
        return machineRegs.slice(1..<numParameters+1)
    }
}

// ---------------------------------------------------------------------------
//                            CodeGenBool
// ---------------------------------------------------------------------------

fun TastExpression.codeGenBool(labelTrue:Label, labelFalse:Label) {
    when(this) {
        is TastCompareOp -> {
            val lhs = left.codeGen()
            val rhs = right.codeGen()
            currentFunc.add(InstrBranch(op, lhs, rhs, labelTrue))
            currentFunc.add(InstrJump(labelFalse))
        }

        is TastAndOp -> TODO()
        is TastOrOp -> TODO()
        else -> TODO("codeGenBool ${this.javaClass}")
    }
}

// ---------------------------------------------------------------------------
//                            CodeGenLvalue
// ---------------------------------------------------------------------------

fun TastExpression.codeGenLvalue(value: IRVal) {
    when(this) {
        is TastVariable -> {
            val sym = currentFunc.mapSymbol(symbol)
            currentFunc.addMov( sym, value)
        }

        is TastIndex -> {
            val expr = expr.codeGen()
            val index = index.codeGen()
            val indexScaled = currentFunc.addAlu(AluOp.MULI, index, type.getSize())
            val addr = currentFunc.addAlu(AluOp.ADDI, expr, indexScaled)
            currentFunc.addStore(type.getSize(), value, addr, 0 )
        }

        is TastMember -> {
            val expr = expr.codeGen()
            currentFunc.addStoreField(type.getSize(), value, expr, member)
        }

        else -> throw ParseError(location, "Not an lvalue in codeGen")
    }
}



// ---------------------------------------------------------------------------
//                            Statements
// ---------------------------------------------------------------------------

fun TastStatement.codeGen() {
    when (this) {
        is TastAssign -> {
            val rhs = rhs.codeGen()
            lhs.codeGenLvalue(rhs)
        }

        is TastFunction -> TODO()
        is TastTopLevel -> TODO()
        is TastClass -> TODO()

        is TastWhile -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelCond)
            expr.codeGenBool(labelStart, labelEnd)
            currentFunc.addLabel(labelEnd)
        }

        is TastRepeat -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelCond)
            expr.codeGenBool(labelEnd, labelStart)
            currentFunc.addLabel(labelEnd)
        }

        is TastDeclareVar ->
            if (expr != null) {
                val rhs = expr.codeGen()
                currentFunc.add(InstrMov(currentFunc.mapSymbol(symbol), rhs))
            }

        is TastDeclareField -> {
            if (expr != null) {
                val rhs = expr.codeGen()
                val thisSym = currentFunc.mapSymbol(currentFunc.thisSymbol!!)
                currentFunc.addStoreField(symbol.type.getSize(), rhs, thisSym, symbol)
            }
        }

        is TastExpressionStatement -> {
            expr.codeGen()
        }

        is TastForRange -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val iterVar = currentFunc.mapSymbol(this.sym)
            val from = from.codeGen()
            val to = to.codeGen()
            currentFunc.addMov(iterVar, from)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            if (comparator== TokenKind.LT || comparator== TokenKind.LE)
                currentFunc.add(InstrAlui(iterVar, AluOp.ADDI, iterVar, 1))
            else
                currentFunc.add(InstrAlui(iterVar, AluOp.SUBI, iterVar, 1))
            currentFunc.addLabel(labelCond)
            val op = when(comparator) {
                TokenKind.LT -> AluOp.LTI
                TokenKind.LE -> AluOp.LEI
                TokenKind.GT -> AluOp.GTI
                TokenKind.GE -> AluOp.GEI
                else -> throw ParseError(location, "Invalid comparator in for range")
            }
            currentFunc.addBranch(op, iterVar, to, labelStart)
            currentFunc.addLabel(labelEnd)
        }

        is TastIf -> {
            val labelEnd = currentFunc.newLabel()
            // Generate the code for the conditions
            for (clause in clauses) {
                var labelNext = currentFunc.newLabel()
                clause.label = currentFunc.newLabel()
                if (clause.expr!=null)
                    clause.expr.codeGenBool(clause.label, labelNext)
                else
                    currentFunc.addJump(clause.label)
                currentFunc.addLabel(labelNext)
            }
            currentFunc.addJump(labelEnd)
            // Generate the code for the statements
            for (clause in clauses) {
                currentFunc.addLabel(clause.label)
                for (stmt in clause.statements)
                    stmt.codeGen()
                currentFunc.addJump(labelEnd)
            }
            currentFunc.addLabel(labelEnd)
        }

        is TastReturn -> {
            if (this.expr!=null)
                currentFunc.addMov(currentFunc.retVal!!, this.expr.codeGen() )
            currentFunc.addJump(currentFunc.retLabel)
        }

        is TastIfClause -> TODO()
    }
}

// ---------------------------------------------------------------------------
//                            Functions
// ---------------------------------------------------------------------------

fun TastFunction.codeGen() {
    currentFunc = function
    currentFunc.add( InstrStart())
    var regIndex = 1
    if (function.thisSymbol!=null)
        currentFunc.addMov(function.mapSymbol(function.thisSymbol), machineRegs[regIndex++])
    for(param in function.parameters)
        currentFunc.addMov(function.mapSymbol(param), machineRegs[regIndex++])
    for(statement in statements)
        statement.codeGen()
    currentFunc.addLabel( currentFunc.retLabel)
    val retval = if (currentFunc.retVal==null) emptyList() else listOf(currentFunc.retVal!!)
    currentFunc.add( InstrRet(retval) )
}

// ---------------------------------------------------------------------------
//                            Class
// ---------------------------------------------------------------------------

fun TastClass.codeGen() {
    currentFunc = constructor
    currentFunc.add( InstrStart())
    var regIndex = 1
    check(constructor.thisSymbol!=null)
    currentFunc.addMov(constructor.mapSymbol(constructor.thisSymbol), machineRegs[regIndex++])
    for(param in constructor.parameters)
        currentFunc.addMov(constructor.mapSymbol(param), machineRegs[regIndex++])
    for(statement in statements)
        statement.codeGen()
    currentFunc.addLabel( currentFunc.retLabel)
    val retval = if (currentFunc.retVal==null) emptyList() else listOf(currentFunc.retVal!!)
    currentFunc.add( InstrRet(retval) )
}

// ---------------------------------------------------------------------------
//                            Top level
// ---------------------------------------------------------------------------

fun TastTopLevel.codeGen() : List<Function>{
    currentFunc = function
    currentFunc.add( InstrStart() )
    for (statement in statements.filterNot { it is TastFunction || it is TastClass })
        statement.codeGen()
    currentFunc.add( InstrRet(emptyList()) )

    for (statement in statements.filterIsInstance<TastClass>())
        statement.codeGen()
    for (statement in statements.filterIsInstance<TastFunction>())
        statement.codeGen()
    return allFunctions
}