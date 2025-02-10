package falcon

// ----------------------------------------------------------------------------
//                       Code Generation
// ----------------------------------------------------------------------------
// Convert a type-checked AST into the IR code.

private lateinit var currentFunc : Function
private var breakLabel : Label? = null
private var continueLabel : Label? = null

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

        is TastGlobalVariable -> {
            val ret = currentFunc.newTemp()
            currentFunc.add(InstrLoadGlobal(type.getSize(), ret, symbol))
            ret
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
                val funcThis = func.function.thisSymbol
                val thisExpr = if (funcThis!=null) currentFunc.mapSymbol(funcThis) else null
                val numRegs = codeGenFuncArgs(args, numParams, function.isVararg, thisExpr)
                currentFunc.addCall(func.function, numRegs)
            } else if (func is TastMethodLiteral) {
                val function = func.function
                val thisArg = func.thisExpr.codeGen()
                val numParams = function.parameters.size
                val numRegs = codeGenFuncArgs(args, numParams, function.isVararg, thisArg)
                currentFunc.addCall(func.function, numRegs)
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
                val size = 4 + numElements * (type as ArrayType).elementType.getSize()
                val alloc = currentFunc.stackAlloc(size)
                val numElementsIRval = currentFunc.addMov(numElements)
                val ret = currentFunc.addAlu(AluOp.ADDI, machineRegs[31], alloc.startAddress+4)
                currentFunc.addStoreField(4,numElementsIRval, ret, lengthSymbol)
                return ret
            } else {
                val numElements = size.codeGen()
                val size = currentFunc.addMov( (type as ArrayType).elementType.getSize() )
                currentFunc.addMov( machineRegs[1], numElements)
                currentFunc.addMov( machineRegs[2], size)
                return currentFunc.addCall(Stdlib.mallocArray, listOf(machineRegs[1], machineRegs[2]))
            }
        }

        is TastConstructor -> {
            val ret : IRVal
            val klass = (type as ClassType)
            if (isLocal) {
                val size = klass.classSize + 4
                val alloc = currentFunc.stackAlloc(size)
                ret = currentFunc.addAlu(AluOp.ADDI, machineRegs[31], alloc.startAddress+4)
            } else {
                currentFunc.add( InstrClassRef(machineRegs[1], type))
                currentFunc.addCall(Stdlib.mallocObject, listOf(machineRegs[1]))
                ret = currentFunc.addMov(machineRegs[8])
            }
            val numRegs = codeGenFuncArgs(args, klass.constructor.parameters.size, klass.constructor.isVararg, ret)
            currentFunc.addCall(type.constructor, numRegs)
            ret
        }


        is TastFunctionLiteral -> TODO()
        is TastTypeDescriptor -> TODO()
        is TastNeg -> TODO()
        is TastMethodCall -> TODO()
        is TastMethodLiteral -> TODO()

        is TastIfExpression -> {
            val ret = currentFunc.newTemp()
            val labelTrue = currentFunc.newLabel()
            val labelFalse = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            this.cond.codeGenBool(labelTrue,labelFalse)
            currentFunc.addLabel(labelTrue)
            val thenValue = this.thenExpr.codeGen()
            currentFunc.addMov(ret, thenValue)
            currentFunc.addJump(labelEnd)
            currentFunc.addLabel(labelFalse)
            val elseValue = this.elseExpr.codeGen()
            currentFunc.addMov(ret, elseValue)
            currentFunc.addLabel(labelEnd)
            ret
        }
    }
}

// ---------------------------------------------------------------------------
//                            Argument Lists
// --------------------------------------------------------------------------
// Generate the code for an argument list.
// Returns the list of registers used

fun codeGenFuncArgs(args:List<TastExpression>, numParameters:Int, isVariadic: Boolean, thisArg:IRVal?) : List<IRVal> {
    if (!isVariadic) {
        check(args.size == numParameters) { "Wrong number of parameters" }
        val args = args.map { it.codeGen() }
        var regIndex = 1
        if (thisArg!=null)
            currentFunc.add(InstrMov(machineRegs[regIndex++], thisArg))
        for(arg in args)
            currentFunc.add(InstrMov(machineRegs[regIndex++], arg))
        return machineRegs.slice(1..<numParameters+1)

    } else {
        var regIndex = 1
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
        if (thisArg!=null)
            currentFunc.add(InstrMov(machineRegs[regIndex++], thisArg))
        for(arg in args)
            currentFunc.add(InstrMov(machineRegs[regIndex++], arg))

        // And add the pointer to the varargs to the stack
        currentFunc.add(InstrAlui(machineRegs[regIndex++], AluOp.ADDI, machineRegs[31], stackAlloc.startAddress + 4))
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
            when(op) {
                AluOp.EQI,
                AluOp.NEI,
                AluOp.GEI,
                AluOp.GTI,
                AluOp.LEI,
                AluOp.LTI -> {
                    currentFunc.add(InstrBranch(op, lhs, rhs, labelTrue))
                    currentFunc.add(InstrJump(labelFalse))
                }

                AluOp.EQS -> genCodeStrcmp(Stdlib.strequals, AluOp.NEI, labelTrue, labelFalse)
                AluOp.NES -> genCodeStrcmp(Stdlib.strequals, AluOp.EQI, labelTrue, labelFalse)
                AluOp.LTS -> genCodeStrcmp(Stdlib.strcmp,    AluOp.LTI, labelTrue, labelFalse)
                AluOp.LES -> genCodeStrcmp(Stdlib.strcmp,    AluOp.LEI, labelTrue, labelFalse)
                AluOp.GTS -> genCodeStrcmp(Stdlib.strcmp,    AluOp.GTI, labelTrue, labelFalse)
                AluOp.GES -> genCodeStrcmp(Stdlib.strcmp,    AluOp.GEI, labelTrue, labelFalse)

                else -> error("Invalio op in TastExpression.codeGenBool $op")
            }
        }

        is TastAndOp -> {
            val label = currentFunc.newLabel()
            left.codeGenBool(label, labelFalse)
            currentFunc.addLabel(label)
            right.codeGenBool(labelTrue, labelFalse)
        }

        is TastOrOp -> {
            val label = currentFunc.newLabel()
            left.codeGenBool(labelTrue, label)
            currentFunc.addLabel(label)
            right.codeGenBool(labelTrue, labelFalse)
        }

        else -> {
            val e = codeGen()
            currentFunc.addBranch(AluOp.NEI, e, machineRegs[0], labelTrue)
            currentFunc.addJump(labelFalse)
        }
    }
}

private fun TastCompareOp.genCodeStrcmp(func:Function, op:AluOp, labelTrue:Label, labelFalse:Label) {
    val lhs = left.codeGen()
    val rhs = right.codeGen()

    currentFunc.addMov(machineRegs[1], lhs)
    currentFunc.addMov(machineRegs[2], rhs)
    val cmp = currentFunc.addCall(func, listOf(machineRegs[1],machineRegs[2]))
    currentFunc.addBranch(op, cmp, machineRegs[0], labelTrue)
    currentFunc.addJump(labelFalse)
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

        is TastGlobalVariable -> {
            currentFunc.add(InstrStoreGlobal(type.getSize(), value, symbol))
        }

        else -> throw ParseError(location, "Not an lvalue in codeGen")
    }
}

// ---------------------------------------------------------------------------
//                            CodeGenLvalueOperator
// ---------------------------------------------------------------------------

fun TastExpression.codeGenLvalueOperator(op:TokenKind, value: IRVal) {
    check(type.isIntegerType())
    val op = when(op) {
        TokenKind.PLUSEQ -> AluOp.ADDI
        TokenKind.MINUSEQ -> AluOp.SUBI
        TokenKind.STAREQ -> AluOp.MULI
        TokenKind.SLASHEQ -> AluOp.DIVI
        else -> error("Invalid op $op")
    }
    when(this) {
        is TastVariable -> {
            val sym = currentFunc.mapSymbol(symbol)
            currentFunc.add(InstrAlu( sym, op, sym, value) )
        }

        is TastIndex -> {
            val expr = expr.codeGen()
            val index = index.codeGen()
            val indexScaled = currentFunc.addAlu(AluOp.MULI, index, type.getSize())
            val addr = currentFunc.addAlu(AluOp.ADDI, expr, indexScaled)
            val tmp = currentFunc.addLoad(type.getSize(), addr, 0)
            val tmp2 = currentFunc.addAlu(op, tmp, value)
            currentFunc.addStore(type.getSize(), tmp2, addr, 0 )
        }

        is TastMember -> {
            val expr = expr.codeGen()
            val tmp = currentFunc.addLoadField(type.getSize(), expr, member)
            val tmp2 = currentFunc.addAlu(op, tmp, value)
            currentFunc.addStoreField(type.getSize(), tmp2, expr, member)
        }

        is TastGlobalVariable -> {
            val tmp = currentFunc.newTemp()
            currentFunc.add(InstrLoadGlobal(type.getSize(), tmp, symbol))
            val tmp2 = currentFunc.addAlu(op, tmp, value)
            currentFunc.add(InstrStoreGlobal(type.getSize(), tmp2, symbol))
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

        is TastFunction -> {}
        is TastTopLevel -> TODO()
        is TastClass -> TODO()

        is TastWhile -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val oldBreakLabel = breakLabel
            breakLabel = labelEnd
            val oldContinueLabel = continueLabel
            continueLabel = labelStart
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelCond)
            expr.codeGenBool(labelStart, labelEnd)
            currentFunc.addLabel(labelEnd)
            breakLabel = oldBreakLabel
            continueLabel = oldContinueLabel
        }

        is TastRepeat -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val oldBreakLabel = breakLabel
            breakLabel = labelEnd
            val oldContinueLabel = continueLabel
            continueLabel = labelStart
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelCond)
            expr.codeGenBool(labelEnd, labelStart)
            currentFunc.addLabel(labelEnd)
            breakLabel = oldBreakLabel
            continueLabel = oldContinueLabel
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
            val labelContinue = currentFunc.newLabel()
            val oldBreakLabel = breakLabel
            breakLabel = labelEnd
            val oldContinueLabel = continueLabel
            continueLabel = labelContinue
            val iterVar = currentFunc.mapSymbol(this.sym)
            val from = from.codeGen()
            val to = to.codeGen()
            currentFunc.addMov(iterVar, from)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelContinue)
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
            breakLabel = oldBreakLabel
            continueLabel = oldContinueLabel
        }

        is TastForArray -> {
            val labelStart = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelContinue = currentFunc.newLabel()
            val oldBreakLabel = breakLabel
            breakLabel = labelEnd
            val oldContinueLabel = continueLabel
            continueLabel = labelContinue
            val sym = currentFunc.mapSymbol(this.sym)
            val ptr = currentFunc.addMov( array.codeGen() )
            val length = currentFunc.addLoadField(4, ptr, lengthSymbol)
            val elementSize = this.sym.type.getSize()
            val lengthScaled = currentFunc.addAlu(AluOp.MULI, length, elementSize)
            val endPtr = currentFunc.addAlu(AluOp.ADDI, ptr, lengthScaled)

            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            currentFunc.add( InstrLoad(elementSize, sym, ptr, 0) )
            for (stmt in statements)
                stmt.codeGen()
            currentFunc.addLabel(labelContinue)
            currentFunc.add( InstrAlui(ptr, AluOp.ADDI, ptr, elementSize) )
            currentFunc.addLabel(labelCond)
            currentFunc.addBranch(AluOp.NEI, ptr, endPtr, labelStart)
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

        is TastDeclareGlobalVar ->
            if (expr != null) {
                val rhs = expr.codeGen()
                currentFunc.add(InstrStoreGlobal(4, rhs, symbol))
            }

        is TastDelete -> {
            val rhs = expr.codeGen()
            val label = currentFunc.newLabel()

            // If the expression is nullable, add a null check
            val klass = if (expr.type is NullableType) {
                currentFunc.add(InstrBranch(AluOp.EQI, rhs, machineRegs[0], label))
                expr.type.elementType as ClassType
            } else
                expr.type as ClassType

            // If the class has a destructor, call it
            val destructorSymbol = klass.methods.find{it.name=="delete"}
            if (destructorSymbol!=null) {
                currentFunc.addMov(machineRegs[1], rhs)
                currentFunc.addCall(destructorSymbol.function, listOf(machineRegs[1]))
            }

            // Delete the object
            currentFunc.addMov(machineRegs[1], rhs)
            currentFunc.addCall(Stdlib.free, listOf(machineRegs[1]))
            currentFunc.addLabel(label)
        }

        is TastWhenClause -> TODO()

        is TastWhen -> {
            val expr = this.expr.codeGen()
            val clauseLabels = mutableMapOf<TastWhenClause, Label>()
            var labelEnd = currentFunc.newLabel()
            for (clause in clauses) {
                val clauseLabel = currentFunc.newLabel()
                clauseLabels[clause] = clauseLabel
                if (clause.isElse)
                    currentFunc.addJump(clauseLabel)
                else
                    for(v in clause.values) {
                        val vx = currentFunc.addMov(v)
                        currentFunc.addBranch(AluOp.EQI, expr, vx, clauseLabel)
                    }
            }
            currentFunc.addJump(labelEnd)
            for((clause, label) in clauseLabels) {
                currentFunc.addLabel(label)
                for (stmt in clause.statements)
                    stmt.codeGen()
                currentFunc.addJump(labelEnd)
            }
            currentFunc.addLabel(labelEnd)
        }

        is TastWhenClauseString -> error("TastWhenClauseString not inside when")

        is TastWhenString -> {
            val expr = this.expr.codeGen()
            val clauseLabels = mutableMapOf<TastWhenClauseString, Label>()
            var labelEnd = currentFunc.newLabel()
            for (clause in clauses) {
                val clauseLabel = currentFunc.newLabel()
                clauseLabels[clause] = clauseLabel
                if (clause.isElse)
                    currentFunc.addJump(clauseLabel)
                else
                    for(v in clause.values) {
                        currentFunc.add( InstrLea(machineRegs[1], v))
                        currentFunc.addMov(machineRegs[2], expr)
                        val cmp = currentFunc.addCall(Stdlib.strequals, listOf(machineRegs[1], machineRegs[2]))
                        currentFunc.addBranch(AluOp.NEI, cmp , machineRegs[0], clauseLabel)
                    }
            }
            currentFunc.addJump(labelEnd)
            for((clause, label) in clauseLabels) {
                currentFunc.addLabel(label)
                for (stmt in clause.statements)
                    stmt.codeGen()
                currentFunc.addJump(labelEnd)
            }
            currentFunc.addLabel(labelEnd)
        }

        is TastBreak -> {
            currentFunc.addJump(breakLabel!!)
        }

        is TastContinue -> {
            currentFunc.addJump(continueLabel!!)
        }

        is TastCompoundAssign -> {
            val rhs = rhs.codeGen()
            lhs.codeGenLvalueOperator(op,rhs)
        }

        is TastNullStatement -> {}
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

    // Generate code for all methods
    for (statement in statements.filterIsInstance<TastFunction>())
        statement.codeGen()

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