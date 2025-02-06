package falcon

sealed class AstNode (val location:Location) {
    fun dump(indent: Int, sb: StringBuilder) {
        sb.append("  ".repeat(indent))
        when(this) {
            is AstIntLiteral ->
                sb.append("IntLit $value\n")

            is AstRealLiteral ->
                sb.append("RealLit $value\n")

            is AstStringLiteral ->
                sb.append("StringLit $value\n")

            is AstCharLiteral ->
                sb.append("CharLit $value\n")

            is AstIdentifier ->
                sb.append("Identifier $name\n")

            is AstBinaryOp -> {
                sb.append("Binop $op\n")
                left.dump(indent+1, sb)
                right.dump(indent+1, sb)
            }

            is AstCompareOp -> {
                sb.append("Compare $op\n")
                left.dump(indent+1, sb)
                right.dump(indent+1, sb)
            }

            is AstEqualsOp -> {
                sb.append("Equals $op\n")
                left.dump(indent+1, sb)
                right.dump(indent+1, sb)
            }

            is AstAndOp -> {
                sb.append("And\n")
                left.dump(indent+1, sb)
                right.dump(indent+1, sb)
            }

            is AstOrOp -> {
                sb.append("Or\n")
                left.dump(indent+1, sb)
                right.dump(indent+1, sb)
            }

            is AstDeclareVar -> {
                sb.append("Declare $decl\n")
                id.dump(indent+1, sb)
                type?.dump(indent+1, sb)
                expr?.dump(indent+1, sb)
            }

            is AstDeclareField-> {
                sb.append("DeclareField $decl\n")
                id.dump(indent+1, sb)
                type?.dump(indent+1, sb)
                expr?.dump(indent+1, sb)
            }

            is AstTopLevel -> {
                sb.append("TopLevel\n")
                for (stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstParameter -> {
                sb.append("Parameter\n")
                id.dump(indent+1, sb)
                type.dump(indent+1, sb)
            }

            is AstFunction -> {
                sb.append("Function $name\n")
                params.dump(indent+1, sb)
                retType?.dump(indent+1, sb)
                for(stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstTypeIdentifier -> {
                sb.append("TypeIdentifier $name\n")
            }

            is AstIndex -> {
                sb.append("Index\n")
                expr.dump(indent+1, sb)
                index.dump(indent+1, sb)
            }

            is AstMember -> {
                sb.append("Member $name\n")
                expr.dump(indent+1, sb)
            }

            is AstUnaryOp -> {
                sb.append("UnaryOp $op\n")
                expr.dump(indent+1, sb)
            }

            is AstReturn -> {
                sb.append("Return\n")
                expr?.dump(indent+1, sb)
            }

            is AstWhile -> {
                sb.append("While\n")
                expr.dump(indent+1, sb)
                for(stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstAssign -> {
                sb.append("Assign\n")
                lhs.dump(indent+1, sb)
                rhs.dump(indent+1, sb)
            }

            is AstCast -> {
                sb.append("Cast\n")
                expr.dump(indent+1, sb)
                type.dump(indent+1, sb)
            }

            is AstArrayType -> {
                sb.append("ArrayType\n")
                astType.dump(indent+1, sb)
            }

            is AstFunctionCall -> {
                sb.append("FunctionCall\n")
                expr.dump(indent+1, sb)
                for(arg in args)
                    arg.dump(indent+1, sb)
            }

            is AstExpressionStatement -> {
                sb.append("ExpressionStatement\n")
                expr.dump(indent+1, sb)
            }

            is AstForRange -> {
                sb.append("ForRange $comparator\n")
                id.dump(indent+1, sb)
                from.dump(indent+1, sb)
                to.dump(indent+1, sb)
                for(stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstIfClause -> {
                sb.append("IfClause\n")
                expr?.dump(indent+1, sb)
                for(stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstIfStatement -> {
                sb.append("IfStatement\n")
                for(clause in clauses)
                    clause.dump(indent+1, sb)
            }

            is AstClass -> {
                sb.append("Class $name\n")
                params.dump(indent+1, sb)
                for(stmt in statements)
                    stmt.dump(indent+1, sb)
            }

            is AstTypeNullable -> {
                sb.append("TypePointer\n")
                expr.dump(indent+1, sb)
            }

            is AstParameterList -> {
                sb.append("ParameterList\n")
                for(param in parameters)
                    param.dump(indent+1, sb)
            }

            is AstConstructor -> {
                sb.append("Constructor\n")
                astType.dump(indent+1, sb)
                args.forEach { it.dump(indent+1, sb) }
            }

            is AstNewArray -> {
                sb.append("New Array\n")
                elType.dump(indent+1, sb)
                size.dump(indent+1, sb)
            }
        }
    }
}

sealed class AstExpression(location: Location) : AstNode(location)
sealed class AstStatement(location: Location) : AstNode(location)
sealed class AstType(location: Location) : AstNode(location)

sealed class AstBlock(location: Location, val parent:AstBlock?) : AstStatement(location) {
    val statements = mutableListOf<AstStatement>()
    val symbolTable : SymbolTable = SymbolTable(parent?.symbolTable)

    fun add(stmt:AstStatement) {
        statements.add(stmt)
    }
}

// Expression classes
class AstIntLiteral(location: Location, val value: Int) : AstExpression(location)
class AstRealLiteral(location: Location, val value: Double) : AstExpression(location)
class AstStringLiteral(location: Location, val value: String) : AstExpression(location)
class AstCharLiteral(location: Location, val value: String) : AstExpression(location)
class AstIdentifier(location: Location, val name: String) : AstExpression(location)
class AstBinaryOp(location: Location, val op: TokenKind, val left: AstExpression, val right: AstExpression) : AstExpression(location)
class AstEqualsOp(location: Location, val op: TokenKind, val left: AstExpression, val right: AstExpression) : AstExpression(location)
class AstCompareOp(location: Location, val op: TokenKind, val left: AstExpression, val right: AstExpression) : AstExpression(location)
class AstAndOp(location: Location, val left: AstExpression, val right: AstExpression) : AstExpression(location)
class AstOrOp(location: Location, val left: AstExpression, val right: AstExpression) : AstExpression(location)
class AstIndex(location: Location, val expr: AstExpression, val index: AstExpression) : AstExpression(location)
class AstMember(location: Location, val expr: AstExpression, val name: String) : AstExpression(location)
class AstUnaryOp(location: Location, val op: TokenKind, val expr: AstExpression) : AstExpression(location)
class AstCast(location: Location, val expr: AstExpression, val type: AstType) : AstExpression(location)
class AstFunctionCall(location: Location, val expr: AstExpression, val args: List<AstExpression>) : AstExpression(location)
class AstNewArray(location: Location, val elType: AstType, val size: AstExpression) : AstExpression(location)
class AstConstructor(location: Location, val astType: AstTypeIdentifier, val args: List<AstExpression>) : AstExpression(location)


// Type description classes
class AstTypeIdentifier(location: Location, val name: String) : AstType(location)
class AstArrayType(location: Location, val astType: AstType) : AstType(location)
class AstTypeNullable(location: Location, val expr:AstType, val nullable: Boolean) : AstType(location)

// Statement classes
class AstDeclareVar(location: Location, val decl:TokenKind, val id: AstIdentifier, val type: AstType?, val expr: AstExpression?)
    : AstStatement(location)

class AstDeclareField(location: Location, val decl:TokenKind, val id: AstIdentifier, val type: AstType?, val expr: AstExpression?)
    : AstStatement(location) {
        var tcExpr : TastExpression? = null
        lateinit var symbol : FieldSymbol
    }

class AstReturn(location: Location, val expr: AstExpression?) : AstStatement(location)
class AstAssign(location: Location, val lhs: AstExpression, val rhs: AstExpression) : AstStatement(location)
class AstExpressionStatement(location: Location, val expr: AstExpression) : AstStatement(location)
class AstIfStatement(location: Location, val clauses: List<AstIfClause>) : AstStatement(location)

// Block classes
class AstTopLevel() : AstBlock(Location.nullLocation, null) {
    fun dump() : String {
        val sb = StringBuilder()
        dump(0, sb)
        return sb.toString()
    }
}

class AstFunction(location: Location, val name:String, val params: AstParameterList, val retType:AstType?, parent:AstBlock)
    : AstBlock(location, parent) {
        lateinit var function: Function
}

class AstClass(location: Location, val name:String, val params: AstParameterList, val klass: ClassType, parent:AstBlock)
    : AstBlock(location, parent) {
        lateinit var constructor: Function
}

class AstWhile(location: Location, val expr: AstExpression, parent:AstBlock) : AstBlock(location, parent)
class AstForRange(location: Location, val id: AstIdentifier, val from: AstExpression, val to: AstExpression, val comparator: TokenKind, parent:AstBlock)
    : AstBlock(location, parent)
class AstIfClause(location: Location, val expr: AstExpression?,  parent:AstBlock) : AstBlock(location, parent) {
    lateinit var pathContextOut : PathContext
}

// Miscellaneous classes
class AstParameter(location: Location, val kind:TokenKind, val id: AstIdentifier, val type: AstType) : AstNode(location)

class AstParameterList(val parameters:List<AstParameter>, val isVariadic: Boolean) : AstNode(Location.nullLocation)