package falcon

// ----------------------------------------------------------------------------
//                       Type Checking
// ----------------------------------------------------------------------------
// Transforms the AST into a TAST (Typed AST)

private lateinit var currentFunction : Function

// ----------------------------------------------------------------------------
//                       Types
// ----------------------------------------------------------------------------

private fun AstType.resolveType(scope: SymbolTable): Type {
    return when (this) {
        is AstTypeIdentifier -> {
            val symbol = predefinedSymbols.lookup(name) ?:
                scope.lookup(this.name) ?:
                TypeSymbol(location,name,makeErrorType(location, "Undefined variable: '$name'")).also { scope.add(it) }
            return when (symbol) {
                is TypeSymbol -> symbol.type
                is FieldSymbol -> makeErrorType(location, "Type '$name' is a field not a type")
                is FunctionSymbol -> makeErrorType(location, "Type '$name' is a function not a type")
                is VarSymbol -> makeErrorType(location, "Type '$name' is a variable not a type")
                is UndefinedSymbol -> error("Undefined symbol")
            }
        }

        is AstArrayType -> {
            val elementType = this.astType.resolveType(scope)
            ArrayType.make(elementType)
        }

        is AstTypePointer -> {
            val elementType = expr.resolveType(scope)
            if (nullable)
                NullablePointerType.make(elementType)
            else
                PointerType.make(elementType)
        }
    }
}

// ----------------------------------------------------------------------------
//                       BinaryOperations
// ----------------------------------------------------------------------------

private class Binop(val op:TokenKind, val left:Type, val right:Type, val aluOp:AluOp, val resultType: Type)
private val binopTable = listOf(
    Binop(TokenKind.PLUS,      IntType, IntType, AluOp.ADDI, IntType),
    Binop(TokenKind.MINUS,     IntType, IntType, AluOp.SUBI, IntType),
    Binop(TokenKind.STAR,      IntType, IntType, AluOp.MULI, IntType),
    Binop(TokenKind.SLASH,     IntType, IntType, AluOp.DIVI, IntType),
    Binop(TokenKind.PERCENT,   IntType, IntType, AluOp.MODI, IntType),
    Binop(TokenKind.AMPERSAND, IntType, IntType, AluOp.ANDI, IntType),
    Binop(TokenKind.BAR,       IntType, IntType, AluOp.ORI,  IntType),
    Binop(TokenKind.CARET,     IntType, IntType, AluOp.XORI, IntType),
    Binop(TokenKind.LSL,       IntType, IntType, AluOp.LSLI, IntType),
    Binop(TokenKind.LSR,       IntType, IntType, AluOp.LSRI, IntType),
    Binop(TokenKind.ASR,       IntType, IntType, AluOp.ASRI, IntType),
    Binop(TokenKind.EQ,        IntType, IntType, AluOp.EQI,  BoolType),
    Binop(TokenKind.NE,        IntType, IntType, AluOp.NEI,  BoolType),
    Binop(TokenKind.LT,        IntType, IntType, AluOp.LTI,  BoolType),
    Binop(TokenKind.LE,        IntType, IntType, AluOp.LEI,  BoolType),
    Binop(TokenKind.GT,        IntType, IntType, AluOp.GTI,  BoolType),
    Binop(TokenKind.GE,        IntType, IntType, AluOp.GEI,  BoolType),

    Binop(TokenKind.PLUS,      RealType, RealType, AluOp.ADDR, RealType),
    Binop(TokenKind.MINUS,     RealType, RealType, AluOp.SUBR, RealType),
    Binop(TokenKind.STAR,      RealType, RealType, AluOp.MULR, RealType),
    Binop(TokenKind.SLASH,     RealType, RealType, AluOp.DIVR, RealType),
    Binop(TokenKind.PERCENT,   RealType, RealType, AluOp.MODR, RealType),
    Binop(TokenKind.EQ,        RealType, RealType, AluOp.EQR,  BoolType),
    Binop(TokenKind.NE,        RealType, RealType, AluOp.NER,  BoolType),
    Binop(TokenKind.LT,        RealType, RealType, AluOp.LTR,  BoolType),
    Binop(TokenKind.LE,        RealType, RealType, AluOp.LER,  BoolType),
    Binop(TokenKind.GT,        RealType, RealType, AluOp.GTR,  BoolType),
    Binop(TokenKind.GE,        RealType, RealType, AluOp.GER,  BoolType),

    Binop(TokenKind.PLUS,      StringType, StringType, AluOp.ADDS, StringType),
    Binop(TokenKind.EQ,        StringType, StringType, AluOp.EQS,  BoolType),
    Binop(TokenKind.NE,        StringType, StringType, AluOp.NES,  BoolType),
    Binop(TokenKind.LT,        StringType, StringType, AluOp.LTS,  BoolType),
    Binop(TokenKind.LE,        StringType, StringType, AluOp.LES,  BoolType),
    Binop(TokenKind.GT,        StringType, StringType, AluOp.GTS,  BoolType),
    Binop(TokenKind.GE,        StringType, StringType, AluOp.GES,  BoolType),

    Binop(TokenKind.AND,       BoolType, BoolType, AluOp.ANDB,  BoolType),
    Binop(TokenKind.OR,        BoolType, BoolType, AluOp.ORB,  BoolType)
    )

private val errorBinop = Binop(TokenKind.ERROR, ErrorType, ErrorType, AluOp.ERROR, ErrorType)

// ----------------------------------------------------------------------------
//                       Expressions
// ----------------------------------------------------------------------------

private fun AstExpression.typeCheck(scope: SymbolTable) : TastExpression {
    return when (this) {
        is AstCharLiteral ->
            TastCharLiteral(location, value[0])

        is AstIntLiteral ->
            TastIntLiteral(location, value)

        is AstRealLiteral ->
            TastRealLiteral(location, value)

        is AstStringLiteral ->
            TastStringLiteral(location, value)

        is AstIdentifier -> {
            val sym = predefinedSymbols.lookup(name) ?:
                scope.lookup(name) ?:
                VarSymbol(location,name,makeErrorType(location, "Undefined variable: '$name'"), false).also { scope.add(it) }
            when (sym) {
                is FieldSymbol -> TODO()
                is FunctionSymbol -> TastFunctionLiteral(location, sym.function, sym.type)
                is TypeSymbol -> TastTypeDescriptor(location, sym.type)
                is VarSymbol -> TastVariable(location, sym, sym.type)
                is UndefinedSymbol -> error("Got undefined symbol in type checking")
            }
        }

        is AstBinaryOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)
            val binop = if (lhs.type== ErrorType || rhs.type==ErrorType) errorBinop else
                binopTable.firstOrNull { it.op == op && it.left==lhs.type && it.right==rhs.type} ?:
                errorBinop.also { Log.error(location, "No operation '${op.text}' for types ${lhs.type} and ${rhs.type}") }
            when(binop.aluOp) {
                AluOp.ANDB -> TastAndOp(location, lhs, rhs, binop.resultType)
                AluOp.ORB -> TastOrOp(location, lhs, rhs, binop.resultType)
                AluOp.EQI,
                AluOp.NEI,
                AluOp.LTI,
                AluOp.GTI,
                AluOp.LEI,
                AluOp.GEI -> TastCompareOp(location, binop.aluOp, lhs, rhs, binop.resultType)

                else -> TastBinaryOp(location, binop.aluOp, lhs, rhs, binop.resultType)
            }
        }

        is AstCast -> {
            val expr = expr.typeCheckRvalue(scope)
            val type = type.resolveType(scope)
             TastCast(location, expr, type)
        }

        is AstIndex -> {
            val expr = expr.typeCheckRvalue(scope)
            val index = index.typeCheckRvalue(scope)
            IntType.checkType(index)
            val type = if (expr.type is ArrayType) expr.type.elementType else
                       if (expr.type is StringType) CharType else
                       if (expr.type== ErrorType) ErrorType else
                       makeErrorType(location, "Indexing on non-array type ${expr.type}")
            TastIndex(location, expr, index, type)
        }

        is AstFunctionCall -> {
            val expr = expr.typeCheck(scope)
            val args = args.map { it.typeCheckRvalue(scope) }

            if (expr.type is FunctionType) {
                typeCheckArgList(location, args, expr.type.parameterTypes, expr.type.isVariadic)
                TastFunctionCall(location, expr, args, expr.type.returnType)
            } else if (expr is TastTypeDescriptor && expr.type is ClassType) {
                typeCheckArgList(location, args, expr.type.constructor.parameters.map{it.type}, false)
                TastConstructor(location, args, expr.type)
            } else {
                TastError(location,"Call on non-function type ${expr.type}")
            }
        }

        is AstMember -> {
            val expr = expr.typeCheckRvalue(scope)
            if ((expr.type is StringType || expr.type is ArrayType) && name == "length")
                TastMember(location, expr, lengthSymbol, IntType)
            else if (expr.type is ClassType) {
                val sym = expr.type.fields.find { it.name == name } ?:
                    FieldSymbol(location,name,makeErrorType(location, "Class '${expr.type} has no field named '$name'"),false)
                TastMember(location, expr, sym, sym.type)
            } else if (expr.type is PointerType && expr.type.elementType is ClassType) {
                    val sym = expr.type.elementType.fields.find{it.name==name} ?:
                    FieldSymbol(location,name,makeErrorType(location, "Class '${expr.type} has no field named '$name'"), false)
                    TastMember(location,expr,sym, sym.type)
            } else {
                TastError(location,"Not a class")
            }
        }

        is AstUnaryOp -> TODO()

        is AstNew -> {
            val expr = expr.typeCheckRvalue(scope)
            val type = PointerType.make(expr.type)
            TastNew(location, expr, type)
        }


    }
}

// ----------------------------------------------------------------------------
//                        Rvalue
// ----------------------------------------------------------------------------

private fun AstExpression.typeCheckRvalue(scope:SymbolTable) : TastExpression {
    val ret = typeCheck(scope)
    if (ret is TastTypeDescriptor)
        Log.error(location,"Got type descriptor when expected rvalue")
    return ret
}

// ----------------------------------------------------------------------------
//                        ArgList
// ----------------------------------------------------------------------------

private fun typeCheckArgList(location:Location, args:List<TastExpression>, parameters:List<Type>, isVariadic:Boolean) {
    if (isVariadic) {
        val numNonVariadicArgs = parameters.size - 1
        val variadicType = (parameters.last() as ArrayType).elementType
        if (args.size < numNonVariadicArgs)
            Log.error(location, "Expected at least $numNonVariadicArgs arguments, got ${args.size}")
        else {
            for (i in 0 until numNonVariadicArgs)
                args[i].checkType(parameters[i])
            for (i in numNonVariadicArgs until args.size)
                args[i].checkType(variadicType)
        }

    } else {
        if (args.size != parameters.size)
            Log.error(location, "Expected ${parameters.size} arguments, got ${args.size}")
        else {
            for (i in args.indices)
                parameters[i].checkType(args[i])
        }
    }
}

// ----------------------------------------------------------------------------
//                       Statements
// ----------------------------------------------------------------------------

private fun AstStatement.typeCheck(scope: SymbolTable) : TastStatement{
    return when (this) {
        is AstTopLevel -> error("TopLevel statement should not be in a statement list")

        is AstWhile -> {
            val cond = expr.typeCheck(scope)
            cond.checkType(BoolType)
            val ret = TastWhile(location, cond, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            ret
        }

        is AstAssign -> {
            val lhs = lhs.typeCheck(scope)
            val rhs = rhs.typeCheck(scope)
            lhs.checkIsLValue()
            rhs.checkType(lhs.type)
            TastAssign(location, lhs, rhs)
        }

        is AstFunction -> {
            val ret = TastFunction(location, symbolTable, function)
            val oldFunction = currentFunction
            currentFunction = function
            for(stmt in statements)
                ret.add(stmt.typeCheck(symbolTable))
            currentFunction = oldFunction
            ret
        }

        is AstDeclareVar -> {
            val tcExpr = expr?.typeCheck(scope)
            val tcType = type?.resolveType(scope) ?: tcExpr?.type ?:
                makeErrorType(id.location,"Cannot determine type for '$id'")
            val mutable = this.decl == TokenKind.VAR
            val sym = VarSymbol(this.id.location, this.id.name, tcType, mutable)
            scope.add(sym)
            tcExpr?.checkType(sym.type)
            TastDeclareVar(this.location, sym, tcExpr)
        }

        is AstDeclareField -> {
            TastDeclareField(location, symbol, tcExpr)
        }

        is AstExpressionStatement -> {
            val expr = expr.typeCheck(scope)
            TastExpressionStatement(location, expr)
        }

        is AstReturn -> {
            val expr = expr?.typeCheck(scope)
            if (expr==null) {
                if (currentFunction.returnType != UnitType)
                    Log.error(location, "Function should return a value of type ${currentFunction.returnType}")
            } else
                currentFunction.returnType.checkType(expr)
            TastReturn(location, expr)
        }

        is AstForRange -> {
            val from = from.typeCheck(scope)
            val to = to.typeCheck(scope)
            from.checkType(IntType)
            to.checkType(IntType)
            val sym = VarSymbol(id.location, id.name, IntType, false)
            symbolTable.add(sym)
            val ret = TastForRange(location, sym, from, to, comparator, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            ret
        }

        is AstIfClause -> {
            val expr = expr?.typeCheck(scope)
            expr?.checkType(BoolType)
            val ret = TastIfClause(location, expr, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            ret
        }

        is AstIfStatement -> {
            val clauses = clauses.map { it.typeCheck(scope) as TastIfClause }
            TastIf(location, clauses)
        }

        is AstClass -> {
            val ret = TastClass(location,symbolTable,constructor)
            val oldFunction = currentFunction
            currentFunction = constructor
            // Add code to assign fields defined in parameter list
            for((index,param) in params.parameters.withIndex())
                if (param.kind!=TokenKind.EOL) {
                    val field = klass.fields.find { it.name == param.id.name }  ?: error("Field ${param.id.name} not found")
                    val thisExpr = TastVariable(location, constructor.thisSymbol!!, constructor.thisSymbol!!.type)
                    val lhs = TastMember(location, thisExpr, field, field.type)
                    val rhs = TastVariable(location, constructor.parameters[index], constructor.parameters[index].type)
                    ret.add(TastAssign(location, lhs, rhs))
                }
            for(stmt in statements)
                ret.add(stmt.typeCheck(symbolTable))
            currentFunction = oldFunction
            ret
        }
    }
}

// ----------------------------------------------------------------------------
//                          generate Symbols
// ----------------------------------------------------------------------------

private fun AstParameter.generateSymbol(scope: SymbolTable, asArray:Boolean) : VarSymbol{
    val tcType = type.resolveType(scope)
    val tcType2 = if (asArray) ArrayType.make(tcType) else tcType
    return VarSymbol(id.location, id.name, tcType2, false)
}

// ----------------------------------------------------------------------------
//                          Parameter List
// ----------------------------------------------------------------------------

private fun AstParameterList.generateSymbols(scope: SymbolTable) : List<VarSymbol> {
    return parameters.map { it.generateSymbol(scope, isVariadic && it==parameters.last()) }
}

// ----------------------------------------------------------------------------
//                          Identify Functions
// ----------------------------------------------------------------------------
// Before the main type checking, we do a pass through the AST to identify all functions
// and their parameters, adding them to the symbol table. This is done to allow
// forward references.

private fun AstBlock.identifyFunctions(scope: SymbolTable) {
    for (stmt in statements)
        if (stmt is AstFunction) {
            val tcParams = stmt.params.generateSymbols(stmt.symbolTable)
            val resultType = stmt.retType?.resolveType(scope) ?: UnitType
            val functionType = FunctionType.make(tcParams.map { it.type }, stmt.params.isVariadic, resultType)
            stmt.function = Function(stmt.location, stmt.name, tcParams, stmt.params.isVariadic, resultType, null)
            val sym = FunctionSymbol(stmt.location, stmt.name, functionType, stmt.function)
            tcParams.forEach{ stmt.symbolTable.add(it) }
            scope.add(sym)

        } else if (stmt is AstClass) {
            val tcParams = stmt.params.generateSymbols(stmt.symbolTable)
            stmt.constructor = Function(stmt.location, stmt.name, tcParams, stmt.params.isVariadic, UnitType, stmt.klass)
            stmt.klass.constructor = stmt.constructor
            for((index,param) in tcParams.withIndex()) {
                if (stmt.params.parameters[index].kind== TokenKind.EOL)
                    stmt.symbolTable.add(param)
                else {
                    val field = FieldSymbol(param.location, param.name, param.type, (stmt.params.parameters[index].kind== TokenKind.VAR))
                    stmt.klass.add(field)
                    stmt.symbolTable.add(field)
                }
            }

        } else if (stmt is AstBlock) {
            stmt.identifyFunctions(scope)
        }
}

// ---------------------------------------------------------------------------
//                             Identify Fields
// ---------------------------------------------------------------------------
// Before the main type checking, we do a pass through the AST to identify all fields
// and their types

private fun AstClass.identifyFields(scope:SymbolTable) {
//    for (param in params) {
//        val sym = VarSymbol(param.location, param.id.name, param.type.resolveType(scope), false)
//        scope.add(sym)
//    }

    for (stmt in statements) {
        if (stmt is AstDeclareField) {
            val tcExpr = stmt.expr?.typeCheck(scope)

            val type = stmt.type?.resolveType(scope) ?:
                       tcExpr?.type ?:
                       makeErrorType(stmt.id.location,"Cannot determine type for '${stmt.id.name}'")

            val sym = FieldSymbol(stmt.id.location, stmt.id.name, type, stmt.decl== TokenKind.VAR)
            tcExpr?.checkType(sym.type)
            scope.add(sym)
            klass.add(sym)
            stmt.symbol = sym
            stmt.tcExpr = tcExpr
        }
    }
}


// ----------------------------------------------------------------------------
//                       Top level
// ----------------------------------------------------------------------------

fun AstTopLevel.typeCheck() : TastTopLevel {
    val ret = TastTopLevel(location, symbolTable)
    identifyFunctions(symbolTable)

    for(cls in statements.filterIsInstance<AstClass>())
        cls.identifyFields(symbolTable)

    currentFunction = ret.function
    for (stmt in statements)
        ret.add(stmt.typeCheck(symbolTable))
    return ret
}
