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
var pathContextBreak : MutableList<PathContext>? = null    // list of path contexts for break statements
                                                    // null to indicate not in a loop


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
                is GlobalVarSymbol -> makeErrorType(location, "Type '$name' is a global variable not a type")
                is FieldAccessSymbol -> error("Got a field access symbol when resolving type")
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
    return this is TastIntLiteral || this is TastCharLiteral
}

fun TastExpression.getCompileTimeConstant() : Int {
    return when (this) {
        is TastIntLiteral -> value
        is TastCharLiteral -> value.code
        else -> error("Not a compile time constant")
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

    Binop(TokenKind.PLUS,      RealType, RealType, AluOp.ADDR, RealType),
    Binop(TokenKind.MINUS,     RealType, RealType, AluOp.SUBR, RealType),
    Binop(TokenKind.STAR,      RealType, RealType, AluOp.MULR, RealType),
    Binop(TokenKind.SLASH,     RealType, RealType, AluOp.DIVR, RealType),
    Binop(TokenKind.PERCENT,   RealType, RealType, AluOp.MODR, RealType),

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
                is FieldSymbol -> {
                    val thisSym = scope.lookup("this") ?: error("No 'this' in scope at $location")
                    val thisExpr = TastVariable(location, thisSym as VarSymbol , thisSym.type)
                    TastMember(location, thisExpr, sym, sym.type)
                }
                is FunctionSymbol -> TastFunctionLiteral(location, sym.function, sym.type)
                is TypeSymbol -> TastTypeDescriptor(location, sym.type)
                is VarSymbol -> TastVariable(location, sym,
                    if (allowRefinedType) pathContext.getType(sym) else sym.type)
                is UndefinedSymbol -> error("Got undefined symbol in type checking")
                is ConstantSymbol -> when(sym.type) {
                    is BoolType,
                    is CharType,
                    is NullType,
                    is IntType -> TastIntLiteral(location, sym.value as Int, sym.type)
                    is StringType -> TastStringLiteral(location, sym.value as String)
                    else -> error("Unhandled constant type: ${sym.type}")
                }
                is GlobalVarSymbol -> TastGlobalVariable(location, sym,
                     if (allowRefinedType) pathContext.getType(sym) else sym.type)

                is FieldAccessSymbol -> error("Got field access symbol in expression")
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
            TastCompareOp(location, binop.aluOp, lhs, rhs, binop.resultType)
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
            val lhsType = if (lhs.type== CharType || lhs.type== ShortType) IntType else lhs.type
            val rhsType = if (rhs.type== CharType || rhs.type== ShortType) IntType else rhs.type

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
            val expr = expr.typeCheck(scope)
            if (expr is TastTypeDescriptor)
                staticMember(expr.type)
            else if (expr.type is ClassType)
                instanceMember(expr, expr.type)
            else if (expr.type is NullableType && expr.type.elementType is ClassType) {
                Log.error(location, "Value may be null")
                instanceMember(expr, expr.type.elementType)
            } else if ((expr.type is StringType || expr.type is ArrayType) && name == "length")
                TastMember(location, expr, lengthSymbol, IntType)
            else if (expr.type== ErrorType)
                expr
            else
                TastError(location,"Got type '${expr.type}' when expecting class")
        }

        is AstUnaryOp -> {
            when(op) {
                TokenKind.MINUS -> {
                    val arg = expr.typeCheckRvalue(scope)
                    if (arg.type== IntType)
                        if (arg is TastIntLiteral)
                            TastIntLiteral(location, -arg.value, arg.type)
                        else
                            TastBinaryOp(location, AluOp.SUBI, TastIntLiteral(location, 0, IntType), arg, IntType)
                    else
                        TastError(location,"No operation defined for unary minus '${arg.type}'")
                }

                TokenKind.NOT -> {
                    val arg = expr.typeCheckRvalue(scope)
                    arg.checkType(BoolType)
                    TastBinaryOp(location, AluOp.XORI, arg, TastIntLiteral(location, 1, IntType), BoolType)
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

        is AstIfExpression -> {
            val cond = cond.typeCheckBool(scope)
            val pathContextElse = pathContextFalse
            pathContext = pathContextTrue
            val thenExpr = thenExpr.typeCheckRvalue(scope)
            val pathContextThenOut = pathContext
            pathContext = pathContextElse
            val elseExpr = elseExpr.typeCheckRvalue(scope)
            pathContext = listOf(pathContext, pathContextThenOut).merge()
            elseExpr.checkType(thenExpr.type)
            TastIfExpression(location, cond, thenExpr, elseExpr, thenExpr.type)

        }

        is AstIsExpression -> {
            val lhs = expr.typeCheckRvalue(scope)
            val type = compType.resolveType(scope)
            val lhsType = if (lhs.type is NullableType) lhs.type.elementType else lhs.type

            if (lhs.type== ErrorType)
                lhs
            else if (type== ErrorType)
                TastIntLiteral(location, 1, BoolType)
            else if (type !is ClassType )
                TastError(location,"Got type '$type' when expecting class")
            else if (lhsType !is ClassType)
                TastError(location,"Got type '${lhs.type}' when expecting class")
            else {
                if (lhsType.isSubtypeOf(type) && lhs.type !is NullableType)
                    if (isnot)
                        Log.error(location, "isnot expression is always false")
                    else
                        Log.error(location, "is expression is always true")
                if (!type.isSubtypeOf(lhsType) && !lhsType.isSubtypeOf(type))
                    if (isnot)
                        Log.error(location, "isnot expression is always true")
                    else
                        Log.error(location, "is expression is always false")

                TastIs(location, lhs, type, this.isnot)
            }
        }

        is AstConstArray -> {
            val elements = this.elements.map { it.typeCheckRvalue(scope) }
            val elementType = elType?.resolveType(scope) ?: elements[0].type
            for(element in elements) {
                element.checkType(elementType)
                if (!element.isCompileTimeConstant() && element !is TastStringLiteral)
                    Log.error(element.location, "Value is not compile time constant")
            }
            val ret = TastConstArray(location, elements, DataSegment.constArrays.size, ArrayType.make(elementType))
            DataSegment.constArrays.add(ret)
            ret
        }

        is AstNullAssert -> {
            val expr = expr.typeCheckRvalue(scope)
            return if (expr.type !is NullableType)
                TastError(location, "Got type ${expr.type} when expecting nullable type")
            else
                TastNullAssert(location, expr, expr.type.elementType)
        }
    }
}

// ----------------------------------------------------------------------------
//                        Extract Fields
// -----------------------------------------------------------------------------

private fun AstMember.instanceMember(expr:TastExpression, klass:ClassType) : TastExpression {
    val sym = klass.lookup(name) ?:
        FieldSymbol(location,name,makeErrorType(location, "Class '${expr.type} has no field named '$name'"),false)
    val ret =  when(sym) {
        is FieldSymbol ->  {
            val fieldAccess = getFieldAccessSymbol(expr, sym)
            val type = if (fieldAccess!=null) pathContext.getType(fieldAccess) else sym.type
            TastMember(location, expr, sym, type)
        }
        is FunctionSymbol -> TastMethodLiteral(location, sym.function, expr, sym.type)
        else ->              TastError(location,"Got type ${sym.javaClass} in AstMember symbol")
    }
    return ret
}

private fun AstMember.staticMember(type:Type) : TastExpression {
    return when (type) {
        is EnumType -> {
            val sym = type.lookup(name) ?:
                ConstantSymbol(location,name,makeErrorType(location, "Enum has no field named '$name'"),0)
            return when (sym) {
                is ConstantSymbol -> TastIntLiteral(location, sym.value as Int, type)
                else -> TastError(location, "Got type ${sym.javaClass} in AstMember symbol")
            }
        }

        else -> {
            TastError(location, "Got type '$type' when expecting class")
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

        is TastGlobalVariable -> {
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
            if (lhs.type.isIntegerType() && rhs.type.isIntegerType())
                return TastCompareOp(location, op, lhs, rhs, BoolType)

            if (lhs.type== StringType && rhs.type== StringType)
                return TastCompareOp(location, if (this.op==TokenKind.EQ) AluOp.EQS else AluOp.NES, lhs, rhs, BoolType)

            if (lhs.type is ClassType && rhs.type==lhs.type)
                return TastCompareOp(location, op, lhs, rhs, BoolType)
            if (lhs.type is NullableType && lhs.type.elementType.isAssignableFrom(rhs.type))
                return TastCompareOp(location, op, lhs, rhs, BoolType)
            if (rhs.type is NullableType && rhs.type.elementType.isAssignableFrom(lhs.type))
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
            val rhs = right.typeCheckBool(scope)
            pathContextFalse = listOf(pathContextFalse, pathContext1).merge()
            TastAndOp(location, lhs, rhs, BoolType)
        }

        is AstOrOp -> {
            val lhs = left.typeCheckBool(scope)
            val pathContext1 = pathContextTrue
            pathContext = pathContextFalse
            val rhs = right.typeCheckBool(scope)
            pathContextTrue = listOf(pathContextTrue, pathContext1).merge()
            TastOrOp(location, lhs, rhs, BoolType)
        }

        is AstUnaryOp -> {
            if (op == TokenKind.NOT) {
                val ret = expr.typeCheckBool(scope)
                val tmp = pathContextTrue
                pathContextTrue = pathContextFalse
                pathContextFalse = tmp
                TastNot(location, ret, BoolType)
            } else if (op == TokenKind.MINUS){
                val e = typeCheckRvalue(scope)
                if (e.type.isIntegerType())
                    TastBinaryOp(location, AluOp.SUBI, TastIntLiteral(location, 0, IntType), e, e.type)
                else
                    TastError(location, "Unary minus is only defined for integer types")
            } else {
                error("Unknown unary operator")
            }
        }

        is AstIsExpression -> {
            val ret = typeCheckRvalue(scope)
            if (ret is TastIs) {
                if (isnot)
                    pathContextFalse = pathContext.addRefinedType(ret.expr, ret.compType)
                else
                    pathContextTrue = pathContext.addRefinedType(ret.expr, ret.compType)
            }
            ret
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
            val oldPathContextBreak = pathContextBreak
            pathContextBreak = mutableListOf<PathContext>()
            val cond = expr.typeCheckBool(scope)
            if (!(cond is TastIntLiteral && cond.value==1) ) // unless we have a 'while true'
                pathContextBreak!! += pathContextFalse       // then add a path context for terminating the loop
            pathContext = pathContextTrue
            val ret = TastWhile(location, cond, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))

            pathContext = pathContextBreak!!.merge()    // path context is merge of condition fallthrough and any break statements in the loop
            pathContextBreak = oldPathContextBreak      // restore the break context
            ret
        }

        is AstRepeat -> {
            val oldPathContextBreak = pathContextBreak
            pathContextBreak = mutableListOf()
            val pathContext0 = pathContext
            val pathContext1 = pathContextTrue
            pathContext = listOf(pathContext0, pathContextFalse).merge()
            val ret = TastRepeat(location, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            ret.expr = expr.typeCheckBool(scope)
            pathContext = pathContext1
            if (!(ret.expr.isCompileTimeConstant() && ret.expr.getCompileTimeConstant()==0))  // if the loop can terminate then add the fallthrough context
                pathContextBreak!! += pathContext
            pathContext = pathContextBreak!!.merge()
            pathContextBreak = oldPathContextBreak
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

        is AstCompoundAssign -> {
            val rhs = rhs.typeCheckRvalue(scope)
            val lhs = lhs.typeCheckLvalue(scope)
            rhs.checkType(lhs.type)
            if (rhs.type == RealType)
                TODO("Real type not yet supported")
            else if (!rhs.type.isIntegerType())
                Log.error(location, "Invalid type for compound assignment")
            TastCompoundAssign(location, op, lhs, rhs)
        }

        is AstFunction -> {
            val ret = TastFunction(location, symbolTable, function)
            val oldFunction = currentFunction
            val oldPathContext = pathContext
            pathContextBreak = null
            currentFunction = function
            for(stmt in statements)
                ret.add(stmt.typeCheck(symbolTable))
            if (currentFunction.returnType!= UnitType && !pathContext.unreachable && !qualifiers.contains(TokenKind.EXTERN))
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
            if (tcExpr!=null && tcExpr.type != tcType)
                pathContext = pathContext.addRefinedType(tcExpr, tcExpr.type)
            scope.add(sym)
            tcExpr?.checkType(sym.type)
            TastDeclareVar(this.location, sym, tcExpr)
        }

        is AstDeclareGlobalVar -> {
            val tcExpr = expr?.typeCheck(scope)
            val tcType = type?.resolveType(scope) ?: tcExpr?.type ?:
            makeErrorType(id.location,"Cannot determine type for '$id'")
            val mutable = this.decl == TokenKind.VAR
            val offset = DataSegment.globalVariables.size*4
            val sym = GlobalVarSymbol(this.id.location, this.id.name, tcType, mutable, offset)
            DataSegment.globalVariables += sym
            if (tcExpr==null)
                pathContext = pathContext.addUninitialized(sym)
            scope.add(sym)
            tcExpr?.checkType(sym.type)
            TastDeclareGlobalVar(this.location, sym, tcExpr)
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
                expr.checkType(currentFunction.returnType)
            pathContext = pathContext.setUnreachable()
            TastReturn(location, expr)
        }

        is AstForRange -> {
            val oldPathContextBreak = pathContextBreak
            pathContextBreak = mutableListOf()
            val from = from.typeCheck(scope)
            val to = to.typeCheck(scope)
            from.checkType(IntType)
            to.checkType(IntType)
            val sym = VarSymbol(id.location, id.name, IntType, false)
            symbolTable.add(sym)
            val ret = TastForRange(location, sym, from, to, comparator, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            pathContextBreak!! += pathContext
            pathContext = pathContextBreak!!.merge()
            pathContextBreak = oldPathContextBreak
            ret
        }

        is AstForArray -> {
            val oldPathContextBreak = pathContextBreak
            pathContextBreak = mutableListOf()
            val array = expr.typeCheck(scope)
            val elementType = when(array.type) {
                is ArrayType -> array.type.elementType
                is StringType -> CharType
                is ErrorType -> ErrorType
                else -> makeErrorType(array.location, "Got type '${array.type}' when expecting array")
            }

            val sym = VarSymbol(id.location, id.name, elementType, false)
            symbolTable.add(sym)
            val ret = TastForArray(location, sym, array, symbolTable)
            for (stmt in statements)
                ret.add(stmt.typeCheck(ret.symbolTable))
            pathContextBreak!! += pathContext
            pathContext = pathContextBreak!!.merge()
            pathContextBreak = oldPathContextBreak
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
            val superclassConstructorArgs = superclass?.args?.map{it.typeCheck(constructorSymbolTable)} ?: emptyList()
            if (klass.superClass!=null) {
                val superclassConstructorParams = klass.superClass.constructor.parameters.map { it.type }
                typeCheckArgList(location, superclassConstructorArgs, superclassConstructorParams, false)
            }

            val ret = TastClass(location,symbolTable,klass, superclassConstructorArgs)
            val oldFunction = currentFunction
            currentFunction = constructor
            pathContextBreak = null
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

        is AstDeleteStatement -> {
            val expr = expr.typeCheck(scope)
            val type = expr.type
            if (type !is ClassType && !(type is NullableType && type.elementType is ClassType) && type !is ErrorType )
                Log.error(location, "Cannot delete expression of type $type")
            return TastDelete(location, expr)
        }

        is AstWhenClause -> TODO()

        is AstWhenStatement -> {
            val expr = expr.typeCheckRvalue(scope)
            return when (expr.type) {
                is IntType ->     TastWhen(location, expr, extractWhenClausesInt(expr.type, scope))
                is StringType ->  TastWhenString(location, expr, extractWhenClausesString(expr.type, scope))
                is EnumType -> {
                    val ret = TastWhen(location, expr, extractWhenClausesInt(expr.type, scope))
                    ret.checkCompleteness()
                    ret
                }
                else -> {
                    Log.error(expr.location,"Got type '${expr.type}' when expecting Int, String or Enum")
                    TastWhen(location, expr, emptyList())
                }
            }
        }

        is AstBreakStatement -> {
            if (pathContextBreak==null)
                Log.error(location, "Break statement outside of loop")
            else {
                pathContextBreak!! += pathContext
                pathContext = pathContext.setUnreachable()
            }
            TastBreak(location)
        }

        is AstContinueStatement -> {
            if (pathContextBreak==null)
                Log.error(location, "Continue statement outside of loop")
            else
                pathContext = pathContext.setUnreachable()
            TastContinue(location)
        }

        is AstConstStatement -> {
            val expr = value.typeCheckRvalue(scope)
            val type = astType?.resolveType(scope) ?: expr.type
            expr.checkType(type)

            val value = if (expr.type== ErrorType) 0
                else when (expr) {
                is TastIntLiteral -> expr.value
                is TastStringLiteral -> expr.value
                else -> {
                    Log.error(location, "Expression is not compile time constant")
                    0
                }
            }

            val sym = ConstantSymbol(location, name, type, value)
            scope.add(sym)
            TastNullStatement(location)
        }

        is AstEnum -> {
            TastNullStatement(location)
        }
    }
}

// ----------------------------------------------------------------------------
//                          helper function for when clauses
// ----------------------------------------------------------------------------

fun AstWhenStatement.extractWhenClausesInt(exprType:Type, scope: SymbolTable) : List<TastWhenClause> {
    val tastClauses = mutableListOf<TastWhenClause>()
    val allClauses = mutableSetOf<Int>()
    val pathContextIn = pathContext
    val pathContextOut = mutableListOf<PathContext>()
    var seenElse = false
    for (clause in clauses) {
        val values = mutableListOf<Int>()
        for (ce in clause.exprs) {
            val tast = ce.typeCheckRvalue(scope)
            tast.checkType(exprType)
            if (tast.isCompileTimeConstant()) {
                val value = tast.getCompileTimeConstant()
                values.add(value)
                if (value in allClauses)
                    Log.error(tast.location, "Duplicate value '$value' in when")
                allClauses.add(value)
            } else
                Log.error(tast.location, "Cannot use non-constant expression in when")
        }
        if (clause.isElse) {
            if (seenElse)
                Log.error(clause.location, "Duplicate else clause in when")
            seenElse = true
        }
        val tc = TastWhenClause(location, values, clause.isElse, scope)
        pathContext = pathContextIn
        for (stmt in clause.statements)
            tc.add(stmt.typeCheck(tc.symbolTable))
        pathContextOut.add(pathContext)
        tastClauses.add(tc)
    }
    pathContext = pathContextOut.merge()
    return tastClauses
}

fun AstWhenStatement.extractWhenClausesString(exprType:Type, scope: SymbolTable) : List<TastWhenClauseString> {
    val tastClauses = mutableListOf<TastWhenClauseString>()
    val allClauses = mutableSetOf<String>()
    val pathContextIn = pathContext
    val pathContextOut = mutableListOf<PathContext>()
    var seenElse = false
    for (clause in clauses) {
        val values = mutableListOf<String>()
        for (ce in clause.exprs) {
            val tast = ce.typeCheckRvalue(scope)
            tast.checkType(exprType)
            if (tast is TastStringLiteral) {
                values.add(tast.value)
                if (tast.value in allClauses)
                    Log.error(tast.location, "Duplicate value '${tast.value}' in when")
                allClauses.add(tast.value)
            } else
                Log.error(tast.location, "Cannot use non-constant expression in when")
        }
        if (clause.isElse) {
            if (seenElse)
                Log.error(clause.location, "Duplicate else clause in when")
            seenElse = true
        }
        val tc = TastWhenClauseString(location, values, clause.isElse, scope)
        pathContext = pathContextIn
        for (stmt in clause.statements)
            tc.add(stmt.typeCheck(tc.symbolTable))
        pathContextOut.add(pathContext)
        tastClauses.add(tc)
    }
    pathContext = pathContextOut.merge()
    return tastClauses
}

private fun TastWhen.checkCompleteness() {
    if (expr.type !is EnumType)
        return
    if (clauses.any{it.isElse})
        return
    for(case in expr.type.fields) {
        if (clauses.none{it.values.contains(case.value)})
            Log.error(location, "No case defined for '$case'")
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

private fun AstBlock.identifyFunctions(scope: SymbolTable, parentClass:ClassType?) {
    for (stmt in statements) {
        if (stmt is AstFunction)
            stmt.identifyFunctionParameters(scope, parentClass)
        else if (stmt is AstClass)
            stmt.identifyConstructorParams()
    }
}

private fun AstClass.identifyConstructorParams() {

    // If the class has a super class, we need to copy the fields and methods from the super class
    if (klass.superClass!=null) {
        // Copy the fields from the super class
        for (field in klass.superClass.fields) {
            klass.add(field)
            symbolTable.add(field)
        }

        for (method in klass.superClass.methods) {
            klass.add(method)
            symbolTable.add(method)
        }

        klass.virtualMethods.addAll(klass.superClass.virtualMethods)
    }

    // Identify the parameters for the constructor
    val tcParams = params.generateSymbols(symbolTable)
    constructor = Function(location, name, tcParams, params.isVariadic, UnitType, klass, false)
    klass.constructor = constructor
    allFunctions.add(constructor)
    constructorSymbolTable.add(constructor.thisSymbol!!)
    for (param in tcParams)
        constructorSymbolTable.add(param)
    identifyFunctions(symbolTable, klass)
}

private fun AstFunction.identifyFunctionParameters(scope: SymbolTable, parentClass:ClassType?) {
    val tcParams = params.generateSymbols(symbolTable)
    val resultType = retType?.resolveType(scope) ?: UnitType
    val functionType = FunctionType.make(tcParams.map { it.type }, params.isVariadic, resultType)
    val qualifiedName = if (parentClass==null) name else "$parentClass/$name"
    function = Function(location, qualifiedName, tcParams, params.isVariadic, resultType, parentClass, qualifiers.contains(TokenKind.EXTERN))
    allFunctions.add(function)
    val sym = FunctionSymbol(location, name, functionType, function)
    tcParams.forEach { symbolTable.add(it) }

    if (qualifiers.contains(TokenKind.OVERRIDE)) {
        // Got an override method
        checkOverride(sym, parentClass)

    } else if (qualifiers.contains(TokenKind.VIRTUAL)) {
        // Got a virtual method
        if (parentClass==null)
            return Log.error(location, "Virtual is only allowed in classes")
        parentClass.add(sym)
        scope.add(sym)
        parentClass.addVirtualMethod(sym)


    } else if (parentClass!=null) {
        // Got a class method
        scope.add(sym)
        parentClass.add(sym)

    } else {
        // Got a global function
        scope.add(sym)
    }

    if (function.thisSymbol!=null)
        symbolTable.add(function.thisSymbol!!)
}

private fun checkOverride(sym: FunctionSymbol,  parentClass:ClassType?) {
    if (parentClass==null)
        return Log.error(sym.location, "Override is only allowed in classes")

    val superFunc = parentClass.methods.find{ it.name == sym.name }
    if (superFunc==null)
        return Log.error(sym.location, "No function '$sym' found to override")
    if (superFunc.function.virtualFunctionNumber==-1)
        return Log.error(sym.location, "Function '${sym.name}' is not virtual")

    val superParams = superFunc.function.parameters
    val symParams = sym.function.parameters
    if (superParams.size != symParams.size)
        return Log.error(sym.location, "Function '${sym.name}' has a ${symParams.size} parameters but the override function has ${superParams.size}")
    for((sup, sym) in superParams.zip(symParams))
        if (sup.type != sym.type)
            Log.error(sym.location, "Parameter ${sup.name} has type ${sup.type} but the override function has type ${sym.type}")
    if (superFunc.function.returnType != sym.function.returnType)
        Log.error(sym.location, "Return type ${superFunc.function.returnType} does not match the override function return type ${sym.function.returnType}")

    sym.function.virtualFunctionNumber = superFunc.function.virtualFunctionNumber
    parentClass.virtualMethods[superFunc.function.virtualFunctionNumber] = sym
}


// ---------------------------------------------------------------------------
//                             Identify Fields
// ---------------------------------------------------------------------------
// Before the main type checking, we do a pass through the AST to identify all fields
// and their types

private fun AstClass.identifyFields() {
    if (klass.superClass!=null) {
        klass.superClass.hasSubclasses = true
        // Copy the fields from the super class
        for (field in klass.superClass.fields) {
            klass.add(field)
            symbolTable.add(field)
        }
    }

    // add any fields defined in the constructor parameter list
    for ((index, param) in constructor.parameters.withIndex()) {
        if (params.parameters[index].kind != TokenKind.EOL) {
            // We have a field, not just a parameter
            val field = FieldSymbol(
                param.location, param.name, param.type,
                (params.parameters[index].kind == TokenKind.VAR)
            )
            klass.add(field)
            symbolTable.add(field)
        }
    }

    // and add any fields from this class
    for (stmt in statements) {
        if (stmt is AstDeclareField) {
            val tcExpr = stmt.expr?.typeCheck(constructorSymbolTable)

            val type = stmt.type?.resolveType(symbolTable) ?:
                       tcExpr?.type ?:
                       makeErrorType(stmt.id.location,"Cannot determine type for '${stmt.id.name}'")

            val sym = FieldSymbol(stmt.id.location, stmt.id.name, type, stmt.decl== TokenKind.VAR)
            tcExpr?.checkType(sym.type)
            symbolTable.add(sym)
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
    identifyFunctions(symbolTable, null)

    for(cls in statements.filterIsInstance<AstClass>())
        cls.identifyFields()

    currentFunction = ret.function
    pathContext = PathContext()
    for (stmt in statements)
        ret.add(stmt.typeCheck(symbolTable))
    return ret
}
