package falcon

// ----------------------------------------------------------------------------
//                       Type-Aware Syntax Trees
// ----------------------------------------------------------------------------
// This is the AST with type information.

sealed class Tast(val location: Location) {
    fun dump(indent: Int, sb: StringBuilder) {
        sb.append("  ".repeat(indent))
        when (this) {
            is TastIntLiteral -> sb.append("IntLit $value $type\n")
            is TastCharLiteral -> sb.append("CharLit $value $type\n")
            is TastRealLiteral -> sb.append("RealLit $value $type\n")
            is TastStringLiteral -> sb.append("StringLit \"$value\" $type\n")
            is TastVariable -> sb.append("Variable $symbol $type\n")
            is TastDeclareVar -> {
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

            is TastAssign -> {
                sb.append("Assign\n")
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
        }
    }
}


// Expressions
sealed class TastExpression(location: Location, val type: Type) : Tast(location) {
    fun checkType(expectedType: Type) {
        if (!expectedType.isAssignableFrom(this.type))
            Log.error(location, "Type mismatch: expected $expectedType but got $type")
    }

    fun checkIsLValue() {
        when(this) {
            is TastVariable ->
                if (!symbol.mutable)
                    Log.error(location, "Variable '$symbol' is not mutable")

            is  TastIndex -> {}

            else -> Log.error(location, "Expression is not an lvalue")
        }
    }
}

class TastIntLiteral(location: Location, val value: Int) : TastExpression(location, IntType)
class TastRealLiteral(location: Location, val value: Double) : TastExpression(location, RealType)
class TastStringLiteral(location: Location, val value: String) : TastExpression(location, StringType)
class TastCharLiteral(location: Location, val value: Char) : TastExpression(location, CharType)
class TastFunctionLiteral(location: Location, val function: Function, type:Type) : TastExpression(location, type)
class TastVariable(location: Location, val symbol: VarSymbol, type: Type) : TastExpression(location, type)
class TastBinaryOp(location: Location, val op: AluOp, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastCompareOp(location: Location, val op: AluOp, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastAndOp(location: Location, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastOrOp(location: Location, val left: TastExpression, val right: TastExpression, type: Type) : TastExpression(location, type)
class TastCast(location: Location, val expr: TastExpression, type: Type) : TastExpression(location, type)
class TastIndex(location: Location, val expr: TastExpression, val index: TastExpression, type:Type) : TastExpression(location, type)
class TastFunctionCall(location: Location, val func: TastExpression, val args: List<TastExpression>, type:Type) : TastExpression(location, type)
class TastMember(location: Location, val expr: TastExpression, val member: FieldSymbol, type:Type) : TastExpression(location, type)

class TastError(location: Location, message: String) : TastExpression(location, ErrorType) {
    init {
        Log.error(location, message)
    }
}

// Statements
sealed class TastStatement(location: Location) : Tast(location)
class TastDeclareVar(location: Location, val symbol:VarSymbol, val expr: TastExpression?) : TastStatement(location)
class TastAssign(location: Location, val lhs: TastExpression, val rhs: TastExpression) : TastStatement(location)
class TastExpressionStatement(location: Location, val expr: TastExpression) : TastStatement(location)
class TastReturn(location: Location, val expr: TastExpression?) : TastStatement(location)
class TastIf(location: Location, val clauses:List<TastIfClause>) : TastStatement(location)

// Blocks
sealed class TastBlock(location: Location, val symbolTable: SymbolTable) : TastStatement(location) {
    val statements = mutableListOf<TastStatement>()

    fun add(stmt: TastStatement) {
        statements.add(stmt)
    }
}

class TastTopLevel(location: Location, symbolTable: SymbolTable) : TastBlock(location, symbolTable) {
    val function = Function(Location.nullLocation, "<TopLevel>", emptyList(), UnitType)
    fun dump(): String {
        val sb = StringBuilder()
        dump(0, sb)
        return sb.toString()
    }
}

class TastWhile(location: Location, val expr: TastExpression, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastForRange(location: Location, val sym: VarSymbol, val from: TastExpression, val to: TastExpression, val comparator: TokenKind, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable)

class TastFunction(location: Location, symbolTable: SymbolTable, val function: Function)
    : TastBlock(location, symbolTable)

class TastIfClause(location: Location, val expr: TastExpression?, symbolTable: SymbolTable)
    : TastBlock(location, symbolTable) {
        lateinit var label : Label
    }
