package falcon


// ----------------------------------------------------------------------------
//                       Type Checking
// ----------------------------------------------------------------------------
// Transforms the AST into a TAST (Typed AST)

private lateinit var currentFunction : Function


// ----------------------------------------------------------------------------
//                       Path Dependant Types
// ----------------------------------------------------------------------------
// Used to keep track of any type information that is known at this point in the
// control flow

var pathContext = PathContext()
var pathContextTrue = PathContext()
var pathContextFalse = PathContext()

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
                is ConstantSymbol -> makeErrorType(location, "Type '$name' is a constant not a type")
            }
        }

        is AstArrayType -> {
            val elementType = this.astType.resolveType(scope)
            ArrayType.make(elementType)
        }

        is AstTypeNullable -> {
            val elementType = expr.resolveType(scope)
            NullableType.make(elementType)
        }
    }
}

// ----------------------------------------------------------------------------
//                       Compile Time constants
// ----------------------------------------------------------------------------

fun TastExpression.isCompileTimeConstant() : Boolean {
    return this is TastIntLiteral
}

fun TastExpression.getCompileTimeConstant() : Int {
    check (this is TastIntLiteral)
    return this.value
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

    Binop(TokenKind.PLUS,      RealType, RealType, AluOp.ADDR, RealType),
    Binop(TokenKind.MINUS,     RealType, RealType, AluOp.SUBR, RealType),
    Binop(TokenKind.STAR,      RealType, RealType, AluOp.MULR, RealType),
    Binop(TokenKind.SLASH,     RealType, RealType, AluOp.DIVR, RealType),
    Binop(TokenKind.PERCENT,   RealType, RealType, AluOp.MODR, RealType),

    Binop(TokenKind.PLUS,      StringType, StringType, AluOp.ADDS, StringType),

    Binop(TokenKind.AND,       BoolType, BoolType, AluOp.ANDB,  BoolType),
    Binop(TokenKind.OR,        BoolType, BoolType, AluOp.ORB,  BoolType)
    )

private val compOpTable = listOf(
    Binop(TokenKind.EQ,        IntType,  IntType, AluOp.EQI,   BoolType),
    Binop(TokenKind.NE,        IntType,  IntType, AluOp.NEI,   BoolType),
    Binop(TokenKind.LT,        IntType,  IntType, AluOp.LTI,   BoolType),
    Binop(TokenKind.GT,        IntType,  IntType, AluOp.GTI,   BoolType),
    Binop(TokenKind.LE,        IntType,  IntType, AluOp.LEI,   BoolType),
    Binop(TokenKind.GE,        IntType,  IntType, AluOp.GEI,   BoolType),

    Binop(TokenKind.EQ,        CharType,  CharType, AluOp.EQI,   BoolType),
    Binop(TokenKind.NE,        CharType,  CharType, AluOp.NEI,   BoolType),
    Binop(TokenKind.LT,        CharType,  CharType, AluOp.LTI,   BoolType),
    Binop(TokenKind.GT,        CharType,  CharType, AluOp.GTI,   BoolType),
    Binop(TokenKind.LE,        CharType,  CharType, AluOp.LEI,   BoolType),
    Binop(TokenKind.GE,        CharType,  CharType, AluOp.GEI,   BoolType),

    Binop(TokenKind.EQ,        StringType, StringType, AluOp.EQS,   BoolType),
    Binop(TokenKind.NE,        StringType, StringType, AluOp.NES,   BoolType),
    Binop(TokenKind.LT,        StringType, StringType, AluOp.LTS,   BoolType),
    Binop(TokenKind.GT,        StringType, StringType, AluOp.GTS,   BoolType),
    Binop(TokenKind.LE,        StringType, StringType, AluOp.LES,   BoolType),
    Binop(TokenKind.GE,        StringType, StringType, AluOp.GES,   BoolType),

    Binop(TokenKind.LT,        RealType, RealType, AluOp.LTR,   BoolType),
    Binop(TokenKind.GT,        RealType, RealType, AluOp.GTR,   BoolType),
    Binop(TokenKind.LE,        RealType, RealType, AluOp.LER,   BoolType),
    Binop(TokenKind.GE,        RealType, RealType, AluOp.GER,   BoolType),
    )

// ----------------------------------------------------------------------------
//                       Expressions
// ----------------------------------------------------------------------------

private fun AstExpression.typeCheck(scope: SymbolTable, allowRefinedType:Boolean=true) : TastExpression {
    return when (this) {
        is AstCharLiteral ->
            TastCharLiteral(location, value[0])

        is AstIntLiteral ->
            TastIntLiteral(location, value, IntType)

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
                is VarSymbol -> TastVariable(location, sym,
                    if (allowRefinedType) pathContext.getType(sym) else sym.type)
                is UndefinedSymbol -> error("Got undefined symbol in type checking")
                is ConstantSymbol -> TastIntLiteral(location, sym.value, sym.type)
            }
        }

        is AstCompareOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)
            if (lhs.type == ErrorType)   return lhs
            if (rhs.type == ErrorType)   return rhs

            val binop = compOpTable.firstOrNull { it.op == op && it.left == lhs.type && it.right == rhs.type }
            if(binop==null)
                return TastError(location,"No operation '${op.text}' for types ${lhs.type} and ${rhs.type}")
            TastBinaryOp(location, binop.aluOp, lhs, rhs, binop.resultType)
        }

        is AstEqualsOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)
            if (lhs.type == ErrorType)   return lhs
            if (rhs.type == ErrorType)   return rhs

            val binop = compOpTable.firstOrNull { it.op == op && it.left == lhs.type && it.right == rhs.type }
            if(binop==null)
                return TastError(location,"No operation '${op.text}' for types ${lhs.type} and ${rhs.type}")
            TastBinaryOp(location, binop.aluOp, lhs, rhs, binop.resultType)

        }

        is AstAndOp -> TODO()

        is AstOrOp -> TODO()

        is AstBinaryOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)

            // Allow some special cases
            if (lhs.type == ErrorType)   return lhs
            if (rhs.type == ErrorType)   return rhs

            // Do type promotions
            val lhsType = if (lhs.type== CharType) IntType else lhs.type
            val rhsType = if (rhs.type== CharType) IntType else rhs.type

            val binop = binopTable.firstOrNull { it.op==op && it.left==lhsType && it.right==rhsType }
            if(binop==null)
                return TastError(location,"No operation '${op.text}' for types ${lhs.type} and ${rhs.type}")

            if (lhs.isCompileTimeConstant() && rhs.isCompileTimeConstant() && lhs.type== IntType) {
                val value = binop.aluOp.eval(lhs.getCompileTimeConstant(), rhs.getCompileTimeConstant())
                TastIntLiteral(location, value,IntType)
            } else
                TastBinaryOp(location, binop.aluOp, lhs, rhs, binop.resultType)
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
            val expr = expr.typeCheckRvalue(scope)
            val args = args.map { it.typeCheckRvalue(scope) }

            if (expr.type is FunctionType) {
                typeCheckArgList(location, args, expr.type.parameterTypes, expr.type.isVariadic)
                TastFunctionCall(location, expr, args, expr.type.returnType)
            } else if (expr.type is ErrorType)
                expr
            else
                TastError(location,"Call on non-function type ${expr.type}")
        }

        is AstMember -> {
            val expr = expr.typeCheckRvalue(scope)
            if ((expr.type is StringType || expr.type is ArrayType) && name == "length")
                TastMember(location, expr, lengthSymbol, IntType)
            else if (expr.type is ClassType) {
                val sym = expr.type.fields.find { it.name == name } ?:
                    FieldSymbol(location,name,makeErrorType(location, "Class '${expr.type} has no field named '$name'"),false)
                TastMember(location, expr, sym, sym.type)
            } else if (expr.type is NullableType && expr.type.elementType is ClassType) {
                    Log.error(location,"Value may be null")
                    val sym = expr.type.elementType.fields.find{it.name==name} ?:
                    FieldSymbol(location,name,makeErrorType(location, "Class '${expr.type} has no field named '$name'"), false)
                    TastMember(location,expr,sym, sym.type)
            } else if (expr.type== ErrorType)
                expr
            else
                TastError(location,"Got type '${expr.type}' when expecting class")
        }

        is AstUnaryOp -> {
            when(op) {
                TokenKind.MINUS -> {
                    val arg = expr.typeCheckRvalue(scope)
                    if (arg.type== IntType)
                        TastBinaryOp(location, AluOp.SUBI, TastIntLiteral(location, 0, IntType), arg, IntType)
                    else
                        TastError(location,"No operation defined for unary minus '${arg.type}'")
                }

                else -> {
                    TastError(location,"Invalid Unary operator $op")
                }
            }
        }

        is AstConstructor -> {
            val type = astType.resolveType(scope)
            if (type !is ClassType)
                return TastError(location,"Got type '$type' when expecting class")
            val args = args.map { it.typeCheckRvalue(scope) }
            typeCheckArgList(location, args, type.constructor.parameters.map{it.type}, false)
            TastConstructor(location, args, isLocal, type)
        }

        is AstNewArray -> {
            val elementType = elType.resolveType(scope)
            val size = size.typeCheckRvalue(scope)
            size.checkType(IntType)
            if (isLocal && !size.isCompileTimeConstant())
                Log.error(size.location, "Value is not compile time constant")
            TastNewArray(location, size, isLocal, ArrayType.make(elementType))
        }
    }
}

// ----------------------------------------------------------------------------
//                        Rvalue
// ----------------------------------------------------------------------------

private fun AstExpression.typeCheckRvalue(scope:SymbolTable) : TastExpression {
    val ret = typeCheck(scope)
    if (ret is TastTypeDescriptor)
        TastError(location,"Got type descriptor when expected rvalue")
    if (ret is TastVariable)
        if (ret.symbol in pathContext.uninitialized )
            Log.error(location,"'${ret.symbol}' is uninitialized")
        else if (ret.symbol in pathContext.possiblyUninitialized)
            Log.error(location,"'${ret.symbol}' is possibly uninitialized")
    return ret
}

// ----------------------------------------------------------------------------
//                        Lvalue
// ----------------------------------------------------------------------------
fun AstExpression.typeCheckLvalue(scope:SymbolTable) : TastExpression {
    val ret = typeCheck(scope, false)
    when(ret) {
        is TastVariable -> {
            if (!ret.symbol.mutable && (ret.symbol !in pathContext.uninitialized))
                Log.error(location, "Variable '${ret.symbol}' is not mutable")
        }

        is  TastIndex -> {}

        is TastMember -> {
            if (!ret.member.mutable)
                Log.error(location, "Member '${ret.member}' is not mutable")
        }

        else -> Log.error(location, "Expression is not an lvalue")
    }
    return ret
}

// ----------------------------------------------------------------------------
//                        typeCheckBool
// ----------------------------------------------------------------------------
// Type check an expression resulting in a boolean value
// Returns a Tast describing the operation
// Also sets pathContextTrue and pathContextFalse

fun AstExpression.typeCheckBool(scope:SymbolTable) : TastExpression {
    pathContextTrue = pathContext
    pathContextFalse = pathContext

    return when(this) {
        is AstCompareOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)
            if (lhs.type== ErrorType)    return lhs
            if (rhs.type== ErrorType)    return rhs

            val binop = compOpTable.firstOrNull { it.op == op && it.left == lhs.type && it.right == rhs.type }
            if(binop==null)
                return TastError(location,"No operation '${op.text}' for types ${lhs.type} and ${rhs.type}")
            TastCompareOp(location, binop.aluOp, lhs, rhs, binop.resultType)
        }

        is AstEqualsOp -> {
            val lhs = left.typeCheckRvalue(scope)
            val rhs = right.typeCheckRvalue(scope)
            if (lhs.type== ErrorType)    return lhs
            if (rhs.type== ErrorType)    return rhs

            val op = if (op==TokenKind.NE) AluOp.NEI else AluOp.EQI
            if ((lhs.type==IntType || lhs.type== CharType) && (rhs.type==IntType || rhs.type== CharType))
                return TastCompareOp(location, op, lhs, rhs, BoolType)

            if (lhs.type is ClassType && rhs.type==lhs.type)
                return TastCompareOp(location, op, lhs, rhs, BoolType)

            if (lhs.type is NullableType && rhs.type== NullType) {
                if (op == AluOp.EQI) {
                    pathContextTrue = pathContext.addRefinedType(lhs, NullType)
                    pathContextFalse = pathContext.addRefinedType(lhs, lhs.type.elementType)
                } else {
                    pathContextFalse = pathContext.addRefinedType(lhs, NullType)
                    pathContextTrue = pathContext.addRefinedType(lhs, lhs.type.elementType)
                }
                return TastCompareOp(location, op, lhs, rhs, BoolType)
            }

            if (rhs.type is NullableType && lhs.type== NullType) {
                if (op == AluOp.EQI) {
                    pathContextTrue = pathContext.addRefinedType(rhs, NullType)
                    pathContextFalse = pathContext.addRefinedType(rhs, rhs.type.elementType)
                } else {
                    pathContextFalse = pathContext.addRefinedType(rhs, NullType)
                    pathContextTrue = pathContext.addRefinedType(rhs, rhs.type.elementType)
                }
                return TastCompareOp(location, op, lhs, rhs, BoolType)
            }

            if (rhs.type is NullableType && lhs.type is NullableType && lhs.type.elementType==rhs.type.elementType)
                return TastCompareOp(location, op, lhs, rhs, BoolType)

            return TastError(location, "Incompatible types for equality '${lhs.type}' and '${rhs.type}'")
        }

        is AstAndOp -> {
            val lhs = left.typeCheckBool(scope)
            val pathContext1 = pathContextFalse
            pathContext = pathContextTrue
            val rhs = right.typeCheckRvalue(scope)
            pathContextFalse = listOf(pathContextFalse, pathContext1).merge()
            TastAndOp(location, lhs, rhs, BoolType)
        }

        is AstOrOp -> {
            val lhs = left.typeCheckBool(scope)
            val pathContext1 = pathContextTrue
            pathContext = pathContextFalse
            val rhs = right.typeCheckRvalue(scope)
            pathContextTrue = listOf(pathContextTrue, pathContext1).merge()
            TastOrOp(location, lhs, rhs, BoolType)
        }

        is AstUnaryOp -> {
            if (op == TokenKind.NOT) {
                val ret = expr.typeCheckBool(scope)
                val tmp = pathContextTrue
                pathContextTrue = pathContextFalse
                pathContextFalse = tmp
                ret
            } else {
                val ret = typeCheckRvalue(scope)
                ret.checkType(BoolType)
                ret
            }
        }

        else -> {
            val ret = typeCheckRvalue(scope)
            ret.checkType(BoolType)
            ret
        }
    }
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
    if (pathContext.unreachable)
        Log.error(location,"Unreachable code")

    return when (this) {
        is AstTopLevel -> error("TopLevel statement should not be in a statement list")

        is AstWhile -> {
            val cond = expr.typeCheckBool(scope)
            val pathContext1 = pathContextFalse
            pathContext = pathContextTrue
            val ret = TastWhile(location, cond, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            pathContext = pathContext1
            if (cond is TastIntLiteral && cond.value!=0)  // Check for while(true)... makes path unreachable
                pathContext = pathContext.setUnreachable()
            ret
        }

        is AstRepeat -> {
            val pathContext0 = pathContext
            val cond = expr.typeCheckBool(scope)
            val pathContext1 = pathContextTrue
            pathContext = listOf(pathContext0, pathContextFalse).merge()
            val ret = TastRepeat(location, cond, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            pathContext = pathContext1
            if (cond.isCompileTimeConstant() && cond.getCompileTimeConstant()==0)  // Check for while(true)... makes path unreachable
                pathContext = pathContext.setUnreachable()
            ret
        }

        is AstAssign -> {
            val rhs = rhs.typeCheckRvalue(scope)
            val lhs = lhs.typeCheckLvalue(scope)
            rhs.checkType(lhs.type)
            if (lhs is TastVariable)
                pathContext = pathContext.initialize(lhs.symbol)
            pathContext = pathContext.addRefinedType(lhs, rhs.type)
            TastAssign(location, lhs, rhs)
        }

        is AstFunction -> {
            val ret = TastFunction(location, symbolTable, function)
            val oldFunction = currentFunction
            val oldPathContext = pathContext
            currentFunction = function
            for(stmt in statements)
                ret.add(stmt.typeCheck(symbolTable))
            if (currentFunction.returnType!= UnitType && !pathContext.unreachable)
                Log.error(location,"Function does not return a value along all paths")
            currentFunction = oldFunction
            pathContext = oldPathContext
            ret
        }

        is AstDeclareVar -> {
            val tcExpr = expr?.typeCheck(scope)
            val tcType = type?.resolveType(scope) ?: tcExpr?.type ?:
                makeErrorType(id.location,"Cannot determine type for '$id'")
            val mutable = this.decl == TokenKind.VAR
            val sym = VarSymbol(this.id.location, this.id.name, tcType, mutable)
            if (tcExpr==null)
                pathContext = pathContext.addUninitialized(sym)
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
            pathContext = pathContext.setUnreachable()
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
            pathContextTrue = pathContext
            val expr = expr?.typeCheckBool(scope)
            val pathContextNextClause = pathContextFalse
            pathContext = pathContextTrue

            val ret = TastIfClause(location, expr, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            this.pathContextOut = pathContext
            pathContext = pathContextNextClause
            ret
        }

        is AstIfStatement -> {
            val ret = mutableListOf<TastIfClause>()
            val contexts = mutableListOf<PathContext>()
            for(clause in clauses) {
                ret += clause.typeCheck(scope) as TastIfClause
                contexts += clause.pathContextOut
            }
            if (clauses.last().expr!=null)  // if there is no else clause
                contexts += pathContext     // then handle the case where execution falls through
            pathContext = contexts.merge()
            TastIf(location, ret)
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
    pathContext = PathContext()
    for (stmt in statements)
        ret.add(stmt.typeCheck(symbolTable))
    return ret
}
