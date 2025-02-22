package falcon

private val fieldAccessSymbols = mutableMapOf<Pair<Symbol, FieldSymbol>, FieldAccessSymbol> ()

fun getFieldAccessSymbol(lhs: Symbol, rhs: FieldSymbol) : Symbol? {
    return if (!lhs.mutable && !rhs.mutable)
        fieldAccessSymbols.getOrPut( lhs to rhs ) { FieldAccessSymbol(lhs.location, lhs, rhs, rhs.type, false) }
    else
        null
}

fun getFieldAccessSymbol(lhs: TastExpression, rhs:FieldSymbol) : Symbol? {
    val sym = lhs.getSymbol()
    return if (sym != null)
        getFieldAccessSymbol(sym, rhs)
    else
        null
}

// ----------------------------------------------------------------------------
//                       Type-Aware Syntax Trees
// ----------------------------------------------------------------------------
// This is the AST with type information.

sealed class Tast(val location: Location) {
    fun getSymbol() : Symbol? = when (this) {
        is TastVariable -> symbol
        is TastGlobalVariable -> symbol
        is TastMember -> {
            val lhsSym = expr.getSymbol() ?: return null
            fieldAccessSymbols.getOrPut( lhsSym to member ) { FieldAccessSymbol(location, lhsSym, member, member.type, false) }
        }
        else -> null
    }


    fun dump(indent: Int, sb: StringBuilder) {
        sb.append("  ".repeat(indent))
        when (this) {
            is TastIntLiteral -> sb.append("IntLit $value $type\n")
            is TastCharLiteral -> sb.append("CharLit $value $type\n")
            is TastRealLiteral -> sb.append("RealLit $value $type\n")
            is TastStringLiteral -> sb.append("StringLit \"${value.replace("\n","")}\" $type\n")
            is TastVariable -> sb.append("Variable $symbol $type\n")
            is TastGlobalVariable -> sb.append("Variable $symbol $type\n")
            is TastDeclareVar -> {
                sb.append("Declare $symbol ${symbol.type}\n")
                expr?.dump(indent + 1, sb)
            }

            is TastMethodLiteral -> {
                sb.append("MethodLiteral $function $type\n")
                thisExpr.dump(indent + 1, sb)
            }

            is TastDeclareGlobalVar -> {
                sb.append("Declare $symbol ${symbol.type}\n")
                expr?.dump(indent + 1, sb)
            }

            is TastTopLevel -> {
                sb.append("TopLevel\n")
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastBinaryOp -> {
                sb.append("Binop $op $type\n")
                left.dump(indent + 1, sb)
                right.dump(indent + 1, sb)
            }

            is TastCompareOp -> {
                sb.append("Compare $op $type\n")
                left.dump(indent + 1, sb)
                right.dump(indent + 1, sb)
            }

            is TastAndOp -> {
                sb.append("And $type\n")
                left.dump(indent + 1, sb)
                right.dump(indent + 1, sb)
            }

            is TastOrOp -> {
                sb.append("And $type\n")
                left.dump(indent + 1, sb)
                right.dump(indent + 1, sb)
            }

            is TastFunction -> {
                sb.append("Function ${function.name}\n")
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastClass -> {
                sb.append("Class $klass\n")
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastAssign -> {
                sb.append("Assign\n")
                lhs.dump(indent + 1, sb)
                rhs.dump(indent + 1, sb)
            }

            is TastCompoundAssign -> {
                sb.append("Assign $op\n")
                lhs.dump(indent + 1, sb)
                rhs.dump(indent + 1, sb)
            }

            is TastError -> sb.append("Error\n")

            is TastWhile -> {
                sb.append("While\n")
                expr.dump(indent + 1, sb)
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastRepeat -> {
                sb.append("Repeat\n")
                expr.dump(indent + 1, sb)
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastCast -> {
                sb.append("Cast ($type)\n")
                expr.dump(indent + 1, sb)
            }

            is TastIndex -> {
                sb.append("Index ($type)\n")
                expr.dump(indent + 1, sb)
                index.dump(indent + 1, sb)
            }

            is TastExpressionStatement -> {
                sb.append("ExpressionStatement\n")
                expr.dump(indent + 1, sb)
            }

            is TastFunctionCall -> {
                sb.append("FunctionCall ($type)\n")
                func.dump(indent + 1, sb)
                for (arg in args)
                    arg.dump(indent + 1, sb)
            }

            is TastMethodCall -> {
                sb.append("MethodCall ($type)\n")
                thisExpr.dump(indent + 1, sb)
                func.dump(indent + 1, sb)
                for (arg in args)
                    arg.dump(indent + 1, sb)
            }

            is TastFunctionLiteral -> {
                sb.append("FunctionLiteral $function\n")
            }

            is TastReturn -> {
                sb.append("Return\n")
                expr?.dump(indent + 1, sb)
            }

            is TastForRange -> {
                sb.append("ForRange $sym $comparator\n")
                from.dump(indent + 1, sb)
                to.dump(indent + 1, sb)
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastForArray -> {
                sb.append("ForArray $sym\n")
                array.dump(indent + 1, sb)
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastMember -> {
                sb.append("Member $member ($type)\n")
                expr.dump(indent + 1, sb)
            }

            is TastIfClause -> {
                sb.append("IfClause\n")
                expr?.dump(indent + 1, sb)
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastIf -> {
                sb.append("If\n")
                for (clause in clauses)
                    clause.dump(indent + 1, sb)
            }

            is TastTypeDescriptor -> {
                sb.append("Type Descriptor ($type)\n")
            }

            is TastConstructor -> {
                sb.append("Constructor ($type)\n")
                for(arg in args)
                    arg.dump(indent+1, sb)
            }

            is TastNeg -> {
                sb.append("Neg ($type)\n")
                expr.dump(indent + 1, sb)
            }

            is TastDeclareField -> {
                sb.append("DeclareField $symbol\n")
                expr?.dump(indent + 1, sb)
            }

            is TastNewArray -> {
                sb.append("NewArray ($type)\n")
                size.dump(indent + 1, sb)
            }

            is TastDelete -> {
                sb.append("Delete\n")
                expr.dump(indent + 1, sb)
            }

            is TastIfExpression -> {
                sb.append("IfExpression $type\n")
                cond.dump(indent + 1, sb)
                thenExpr.dump(indent + 1, sb)
                elseExpr.dump(indent + 1, sb)
            }

            is TastWhenClause -> {
                sb.append("WhenClause $values\n")
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastWhenClauseString -> {
                sb.append("WhenClause $values\n")
                for (stmt in statements)
                    stmt.dump(indent + 1, sb)
            }

            is TastWhen -> {
                sb.append("When\n")
                expr.dump(indent + 1, sb)
                for (clause in clauses)
                    clause.dump(indent + 1, sb)
            }

            is TastWhenString -> {
                sb.append("WhenString\n")
                expr.dump(indent + 1, sb)
                for (clause in clauses)
                    clause.dump(indent + 1, sb)
            }

            is TastBreak -> {
                sb.append("Break\n")
            }

            is TastContinue -> {
                sb.append("Continue\n")
            }

            is TastNullStatement -> {
                sb.append("NullStatement\n")
            }

            is TastNot -> {
                sb.append("Not\n")
                expr.dump(indent + 1, sb)
            }

            is TastIs -> {
                sb.append("Is $isNot $compType\n")
                expr.dump(indent + 1, sb)
            }
        }
    }
}


// Expressions
sealed class TastExpression(location: Location, val type: Type) : Tast(location) {
    fun checkType(expectedType: Type) {
        if (expectedType==ShortType && type==IntType && this is TastIntLiteral && value in -32768..32767)
            return
        if (expectedType==CharType && type==IntType && this is TastIntLiteral && value in -128..127)
            return
        if (!expectedType.isAssignableFrom(this.type))
            Log.error(location, "Type mismatch: expected $expectedType but got $type")
    }
}

class TastIntLiteral(location: Location, val value: Int, type:Type) : TastExpression(location, type)
class TastRealLiteral(location: Location, val value: Double) : TastExpression(location, RealType)
class TastStringLiteral(location: Location, val value: String) : TastExpression(location, StringType)
class TastCharLiteral(location: Location, val value: Char) : TastExpression(location, CharType)
class TastFunctionLiteral(location: Location, val function: Function, type:Type) : TastExpression(location, type)
class TastMethodLiteral(location: Location, val function: Function, val thisExpr: TastExpression, type:Type) : TastExpression(location, type)
class TastVariable(location: Location, val symbol: VarSymbol, type: Type) : TastExpression(location, type)
class TastGlobalVariable(location: Location, val symbol: GlobalVarSymbol, type: Type) : TastExpression(location, type)
class TastBinaryOp(location: Location, val op: AluOp, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastCompareOp(location: Location, val op: AluOp, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastAndOp(location: Location, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastOrOp(location: Location, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastCast(location: Location, val expr: TastExpression, type: Type) : TastExpression(location, type)
class TastIndex(location: Location, val expr: TastExpression, val index: TastExpression, type:Type) : TastExpression(location, type)
class TastFunctionCall(location: Location, val func: TastExpression, val args: List<TastExpression>, type:Type) : TastExpression(location, type)
class TastMethodCall(location: Location, val func: TastExpression, val args: List<TastExpression>, val thisExpr:TastExpression, type:Type) : TastExpression(location, type)
class TastTypeDescriptor(location: Location, type:Type) : TastExpression(location,type)
class TastConstructor(location: Location, val args:List<TastExpression>, val isLocal:Boolean, type:Type) : TastExpression(location,type)
class TastNeg(location: Location, val expr: TastExpression, type:Type) : TastExpression(location, type)
class TastNewArray(location: Location, val size:TastExpression, val isLocal:Boolean, type:Type) : TastExpression(location, type)
class TastIfExpression(location: Location, val cond: TastExpression, val thenExpr: TastExpression, val elseExpr: TastExpression, type:Type) : TastExpression(location,type)
class TastNot(location: Location, val expr: TastExpression, type:Type) : TastExpression(location, type)
class TastIs(location: Location, val expr: TastExpression, val compType:Type, val isNot:Boolean) : TastExpression(location, BoolType)

class TastMember(location: Location, val expr: TastExpression, val member: FieldSymbol, type:Type)
    : TastExpression(location, type)


class TastError(location: Location, message: String) : TastExpression(location, ErrorType) {
    init {
        Log.error(location, message)
    }
}

// Statements
sealed class TastStatement(location: Location) : Tast(location)
class TastDeclareVar(location: Location, val symbol:VarSymbol, val expr: TastExpression?) : TastStatement(location)
class TastDeclareGlobalVar(location: Location, val symbol: GlobalVarSymbol, val expr: TastExpression?) : TastStatement(location)
class TastAssign(location: Location, val lhs: TastExpression, val rhs: TastExpression) : TastStatement(location)
class TastCompoundAssign(location: Location, val op:TokenKind, val lhs: TastExpression, val rhs: TastExpression) : TastStatement(location)
class TastExpressionStatement(location: Location, val expr: TastExpression) : TastStatement(location)
class TastReturn(location: Location, val expr: TastExpression?) : TastStatement(location)
class TastDelete(location: Location, val expr: TastExpression) : TastStatement(location)
class TastIf(location: Location, val clauses:List<TastIfClause>) : TastStatement(location)
class TastBreak(location: Location) : TastStatement(location)
class TastContinue(location: Location) : TastStatement(location)
class TastWhen(location: Location, val expr: TastExpression, val clauses:List<TastWhenClause>) : TastStatement(location)
class TastWhenString(location: Location, val expr: TastExpression, val clauses:List<TastWhenClauseString>) : TastStatement(location)
class TastNullStatement(location: Location) : TastStatement(location)

class TastDeclareField(location: Location, val symbol: FieldSymbol, val expr: TastExpression?) : TastStatement(location)

// Blocks
sealed class TastBlock(location: Location, val symbolTable: SymbolTable) : TastStatement(location) {
    val statements = mutableListOf<TastStatement>()

    fun add(stmt: TastStatement) {
        statements.add(stmt)
    }
}

class TastTopLevel(location: Location, symbolTable: SymbolTable) : TastBlock(location, symbolTable) {
    val function = Function(Location.nullLocation, "<TopLevel>", emptyList(), false, UnitType, null)
        .also{allFunctions.add(it)}

    fun dump(): String {
        val sb = StringBuilder()
        dump(0, sb)
        return sb.toString()
    }
}

class TastWhile(location: Location, val expr: TastExpression, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastRepeat(location: Location, val expr: TastExpression, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastForRange(location: Location, val sym: VarSymbol, val from: TastExpression, val to: TastExpression, val comparator: TokenKind, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastForArray(location: Location, val sym: VarSymbol, val array: TastExpression, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastFunction(location: Location, symbolTable: SymbolTable, val function: Function)
    : TastBlock(location, symbolTable)

class TastIfClause(location: Location, val expr: TastExpression?, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable) {
        lateinit var label : Label
    }

class TastWhenClause(location: Location, val values: List<Int>, val isElse:Boolean, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastWhenClauseString(location: Location, val values: List<String>, val isElse:Boolean, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)


class TastClass(location: Location, symbolTable: SymbolTable, val klass: ClassType, val superclassConstructorArgs:List<TastExpression>)
    : TastBlock(location, symbolTable)
