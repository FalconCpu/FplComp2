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
            val args = args.map { it.codeGen() }
            if (func is TastFunctionLiteral) {
                currentFunc.addCall(func.function, args)
            } else {
                TODO("Function expressions are not yet supported")
            }
        }

        is TastMember -> {
            val expr = expr.codeGen()
            currentFunc.addLoadField(type.getSize(), expr, member)
        }

        is TastFunctionLiteral -> TODO()
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
        else -> TODO()
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

        is TastDeclareVar ->
            if (expr != null) {
                val rhs = expr.codeGen()
                currentFunc.add(InstrMov(currentFunc.mapSymbol(symbol), rhs))
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
                currentFunc.addJump(labelEnd)
            }
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
    currentFunc.add( InstrStart(function.parameters.map{currentFunc.mapSymbol(it)}) )
    for(statement in statements)
        statement.codeGen()
    currentFunc.addLabel( currentFunc.retLabel)
    currentFunc.add( InstrRet(currentFunc.retVal) )
}


// ---------------------------------------------------------------------------
//                            Top level
// ---------------------------------------------------------------------------

fun TastTopLevel.codeGen() : List<Function>{
    println("Generating code for ${function.name}")
    currentFunc = function
    currentFunc.add( InstrStart(emptyList()) )
    for (statement in statements.filterNot { it is TastFunction })
        statement.codeGen()
    currentFunc.add( InstrRet(null) )

    for (statement in statements.filterIsInstance<TastFunction>())
        statement.codeGen()
    return allFunctions
}