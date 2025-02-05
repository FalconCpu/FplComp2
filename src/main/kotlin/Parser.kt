package falcon
import falcon.TokenKind.*

class ParseError(location: Location, message: String) : Exception("$location: $message")

class Parser(private val lexer: Lexer) {
    private var lookahead = lexer.nextToken()

    private fun nextToken() : Token {
        val tok = lookahead
        lookahead = lexer.nextToken()
        return tok
    }

    private fun expect(kind:TokenKind) :Token {
        if (lookahead.kind == kind)
            return nextToken()
        throw ParseError(lookahead.location, "Got  $lookahead when expecting ${kind.text}")
    }

    private fun canTake(kind: TokenKind) : Boolean {
        if (lookahead.kind==kind) {
            nextToken()
            return true
        } else
            return false
    }

    private fun skipToEol() {
        while (lookahead.kind != EOL && lookahead.kind!=EOF)
            nextToken()
        nextToken()
    }

    private fun expectEol() {
        if (lookahead.kind != EOL)
            Log.error(lookahead.location, "Got  $lookahead when expecting EOL")
        skipToEol()
    }

    private fun parseIntLit() : AstIntLiteral {
        val tok = expect(INT_LITERAL)
        val value = try {
            if (tok.text.startsWith("0x", ignoreCase = true))
                tok.text.substring(2).toLong(16).toInt()
            else
                tok.text.toInt()
        } catch (_: NumberFormatException) {
            Log.error(tok.location, "Malformed Integer literal '$tok'")
            0
        }

        return AstIntLiteral(tok.location, value)
    }

    private fun parseRealLit() : AstRealLiteral {
        val tok = expect(REAL_LITERAL)
        val value = try {
            tok.text.toDouble()
        } catch (_: NumberFormatException) {
            Log.error(tok.location, "Malformed Real literal '$tok'")
            0.0
        }

        return AstRealLiteral(tok.location, value)
    }

    private fun parseCharLit() : AstCharLiteral {
        val tok = expect(CHAR_LITERAL)
        return AstCharLiteral(tok.location, tok.text)
    }

    private fun parseStringLit() : AstStringLiteral {
        val tok = expect(STRING_LITERAL)
        return AstStringLiteral(tok.location, tok.text)
    }

    private fun parseIdentifier() : AstIdentifier {
        val tok = expect(IDENTIFIER)
        return AstIdentifier(tok.location, tok.text)
    }

    private fun parseParenthesizedExpression() : AstExpression {
        expect(OPENB)
        val expr = parseExpression()
        if (canTake(COLON)) {
            val type = parseType()
            expect(CLOSEB)
            return AstCast(expr.location, expr, type)
        }
        expect(CLOSEB)
        return expr
    }

    private fun parsePrimary() : AstExpression {
        return when (lookahead.kind) {
            INT_LITERAL -> parseIntLit()
            REAL_LITERAL -> parseRealLit()
            STRING_LITERAL -> parseStringLit()
            CHAR_LITERAL -> parseCharLit()
            OPENB -> parseParenthesizedExpression()
            IDENTIFIER -> parseIdentifier()
            else -> throw ParseError(lookahead.location, "Got '$lookahead' when expecting primary expression")
        }
    }

    private fun parseIndexExpression(lhs: AstExpression) : AstExpression {
        val loc = expect(OPENSQ)
        val index = parseExpression()
        expect(CLOSESQ)
        return AstIndex(loc.location, lhs, index)
    }

    private fun parseFunctionCall(lhs: AstExpression) : AstExpression {
        val loc = expect(OPENB)
        val args = mutableListOf<AstExpression>()
        if (lookahead.kind!=CLOSEB)
            do {
                args.add(parseExpression())
            } while (canTake(COMMA))
        expect(CLOSEB)
        return AstFunctionCall(loc.location, lhs, args)
    }

    private fun parseMemberExpression(lhs: AstExpression) : AstExpression {
        val loc = expect(DOT)
        val id = expect(IDENTIFIER)
        return AstMember(loc.location, lhs, id.text)
    }

    private fun parsePostfix() : AstExpression {
        var ret = parsePrimary()
        while(true)
            ret = when (lookahead.kind) {
                OPENSQ -> parseIndexExpression(ret)
                OPENB -> parseFunctionCall(ret)
                DOT -> parseMemberExpression(ret)
                else -> return ret
            }
    }

    private fun parsePrefix() : AstExpression {
        if (canTake(MINUS) )
            return AstUnaryOp(lookahead.location, MINUS, parsePrefix())
        if (canTake(NOT))
            return AstUnaryOp(lookahead.location, MINUS, parsePrefix())
        if (canTake(NEW))
            return AstNew(lookahead.location, parsePrefix())
        return parsePostfix()
    }

    private fun parseMult() : AstExpression {
        var left = parsePrefix()
        while (lookahead.kind == STAR || lookahead.kind == SLASH ||
               lookahead.kind == PERCENT || lookahead.kind == AMPERSAND ||
               lookahead.kind == LSL || lookahead.kind == LSR) {
            val op = nextToken()
            val right = parsePrefix()
            left = AstBinaryOp(op.location, op.kind, left, right)
        }
        return left
    }

    private fun parseAdd() : AstExpression {
        var left = parseMult()
        while (lookahead.kind == PLUS || lookahead.kind == MINUS ||
               lookahead.kind == CARET || lookahead.kind == BAR) {
            val op = nextToken()
            val right = parseMult()
            left = AstBinaryOp(op.location, op.kind, left, right)
        }
        return left
    }

    private fun parseComp() : AstExpression {
        var left = parseAdd()
        while (lookahead.kind == LT || lookahead.kind == LE ||
               lookahead.kind == GT || lookahead.kind == GE ||
               lookahead.kind == EQ || lookahead.kind == NE) {
            val op = nextToken()
            val right = parseAdd()
            left = AstBinaryOp(op.location, op.kind, left, right)
        }
        return left
    }

    private fun parseAnd() : AstExpression {
        var left = parseComp()
        while (lookahead.kind == AND) {
            val op = nextToken()
            val right = parseComp()
            left = AstBinaryOp(op.location, op.kind, left, right)
        }
        return left
    }

    private fun parseOr() : AstExpression {
        var left = parseAnd()
        while (lookahead.kind == OR) {
            val op = nextToken()
            val right = parseAnd()
            left = AstBinaryOp(op.location, op.kind, left, right)
        }
        return left
    }


    internal fun parseExpression() : AstExpression {
        return parseOr()
    }

    private fun parseTypeIdentifier() : AstTypeIdentifier {
        val tok = expect(IDENTIFIER)
        return AstTypeIdentifier(tok.location, tok.text)
    }

    private fun parseArrayType() : AstArrayType {
        val loc = expect(ARRAY)
        expect(LT)
        val astType = parseType()
        expect(GT)
        return AstArrayType(loc.location, astType)
    }

    private fun parseType() : AstType {
        var ret = when(lookahead.kind) {
            ARRAY -> parseArrayType()
            IDENTIFIER -> parseTypeIdentifier()
            else -> throw ParseError(lookahead.location, "Got '${lookahead.kind}' when expecting type")
        }

        while(lookahead.kind==STAR || lookahead.kind==QUESTION) {
            val tok = nextToken()
            ret = AstTypePointer(tok.location, ret, tok.kind==QUESTION)
        }
        return ret
    }

    private fun parseOptType() : AstType? {
        if (canTake(COLON))
            return parseType()  // TODO: type
        return null
    }

    private fun parseOptInitializer() : AstExpression? {
        if (canTake(EQ))
            return parseExpression()
        return null
    }

    private fun parseOptionalEnd(kind: TokenKind) {
        if (canTake(END)) {
            if (lookahead.kind!=EOL && lookahead.kind!=EOF) {
                val tok = nextToken()
                if (tok.kind!=kind) {
                    Log.error(tok.location, "Got 'end $tok` when expecting 'end ${kind.text}'")
                }
            }
            expectEol()
        }
    }

    private fun parseVarDecl(block:AstBlock) {
        val tok = nextToken()
        val id = parseIdentifier()
        val optType = parseOptType()
        val optInit = parseOptInitializer()
        expectEol()
        block.add(AstDeclareVar(tok.location, tok.kind, id, optType, optInit))
    }

    private fun parseFieldDecl(block:AstBlock) {
        val tok = nextToken()
        val id = parseIdentifier()
        val optType = parseOptType()
        val optInit = parseOptInitializer()
        expectEol()
        block.add(AstDeclareField(tok.location, tok.kind, id, optType, optInit))
    }


    private fun parseParameter() : AstParameter {
        val id = parseIdentifier()
        val type : AstType
        if (canTake(COLON)) {
            type = parseType()
        } else {
            Log.error(lookahead.location, "Expected type after identifier")
            type = AstTypeIdentifier(id.location, "Int")
        }
        return AstParameter(id.location, EOL, id, type)
    }

    private fun parseParameterList() : List<AstParameter> {
        val params = mutableListOf<AstParameter>()
        expect(OPENB)
        if (canTake(CLOSEB))
            return params
        do {
            params.add(parseParameter())
        } while (canTake(COMMA))
        expect(CLOSEB)
        return params
    }

    private fun parseFunction(block: AstBlock) {
        expect(FUN)
        val id = parseIdentifier()
        val params = parseParameterList()
        val retType = if (canTake(ARROW)) parseType() else null
        expectEol()
        val ret = AstFunction(id.location, id.name, params, retType, block)
        block.add(ret)

        if (lookahead.kind==INDENT) {
            parseIndentedBlock(ret)
        } else
            Log.error(lookahead.location, "Expected indented block after function declaration")
        parseOptionalEnd(FUN)
    }

    private fun parseIndentedBlock(block:AstBlock) {
        expect(INDENT)
        while (lookahead.kind != DEDENT && lookahead.kind != EOF) {
            try {
                parseStatement(block)
            } catch (e: ParseError) {
                Log.error(e.message!!)
                skipToEol()
            }
        }
        expect(DEDENT)
    }

    private fun parseReturn(block:AstBlock) {
        expect(RETURN)
        val loc = lookahead.location
        val expr = if (lookahead.kind!=EOL) parseExpression() else null
        expectEol()
        block.add(AstReturn(loc, expr))
    }

    private fun parseWhile(block: AstBlock) {
        expect(WHILE)
        val cond = parseExpression()
        expectEol()
        val ret = AstWhile(cond.location, cond, block)
        block.add(ret)
        if (lookahead.kind==INDENT)
            parseIndentedBlock(ret)
        else
            Log.error(lookahead.location, "Expected indented block after while")
        parseOptionalEnd(WHILE)
    }

    private fun parseAssign(block:AstBlock) {
        val lhs = parsePostfix()
        if (canTake(EQ)) {
            val rhs = parseExpression()
            expectEol()
            block.add(AstAssign(lhs.location, lhs, rhs))
        } else {
            expectEol()
            if (lhs is AstFunctionCall)
                block.add(AstExpressionStatement(lhs.location, lhs))
            else
                Log.error(lhs.location, "Statement has no effect")
        }
    }

    private fun parseFor(block: AstBlock) {
        expect(FOR)
        val id = parseIdentifier()
        expect(IN)
        val from = parseExpression()
        expect(TO)
        val comparator = if (lookahead.kind in listOf(LT,LE,GT,GE)) nextToken().kind else LE
        val to = parseExpression()
        expectEol()
        val ret = AstForRange(id.location, id, from, to, comparator, block)
        block.add(ret)
        if (lookahead.kind==INDENT)
            parseIndentedBlock(ret)
        else
            Log.error(lookahead.location, "Expected indented block after for")
        parseOptionalEnd(FOR)
    }

    private fun parseIfClause(block: AstBlock) : AstIfClause {
        val loc = nextToken()
        val cond = parseExpression()
        expectEol()
        val ret = AstIfClause(loc.location, cond, block)
        if (lookahead.kind==INDENT)
            parseIndentedBlock(ret)
        else
            Log.error(lookahead.location, "Expected indented block after if")
        return ret
    }

    private fun parseElseClause(block: AstBlock) : AstIfClause {
        val loc = expect(ELSE)
        expectEol()
        val ret = AstIfClause(loc.location, null, block)
        if (lookahead.kind==INDENT)
            parseIndentedBlock(ret)
        else
            Log.error(lookahead.location, "Expected indented block after else")
        return ret
    }

    private fun parseIf(block: AstBlock) {
        val location = lookahead.location
        val clauses = mutableListOf<AstIfClause>()
        clauses.add(parseIfClause(block))
        while (lookahead.kind==ELSIF)
            clauses.add(parseIfClause(block))
        if (lookahead.kind==ELSE)
            clauses.add(parseElseClause(block))
        parseOptionalEnd(IF)
        val ret = AstIfStatement(location, clauses)
        block.add(ret)
    }

    private fun parseClassParameter() : AstParameter {
        val kind = if (lookahead.kind==VAR || lookahead.kind==VAL) nextToken().kind else EOL
        val id = parseIdentifier()
        val type : AstType
        if (canTake(COLON)) {
            type = parseType()
        } else {
            Log.error(lookahead.location, "Expected type after identifier")
            type = AstTypeIdentifier(id.location, "Int")
        }
        return AstParameter(id.location, kind, id, type)
    }

    private fun parseClassParameterList() : List<AstParameter> {
        val ret = mutableListOf<AstParameter>()
        if (lookahead.kind!=OPENB)
            return ret
        expect(OPENB)
        if (lookahead.kind!=CLOSEB)
            do {
                ret.add(parseClassParameter())
            } while (canTake(COMMA))
        expect(CLOSEB)
        return ret
    }

    private fun parseClassBody(block:AstBlock) {
        expect(INDENT)
        while (lookahead.kind != DEDENT && lookahead.kind != EOF) {
            try {
                parseClassStatement(block)
            } catch (e: ParseError) {
                Log.error(e.message!!)
                skipToEol()
            }
        }
        expect(DEDENT)
    }

    private fun parseClass(block: AstBlock) {
        expect(CLASS)
        val id = parseIdentifier()
        val params = parseClassParameterList()
        expectEol()
        val type = ClassType.make(id.name)
        val ret = AstClass(id.location, id.name, params, type, block)
        val sym = TypeSymbol(id.location, id.name, type)
        block.symbolTable.add(sym)
        block.add(ret)

        if (lookahead.kind == INDENT)
            parseClassBody(ret)
        parseOptionalEnd(CLASS)
    }


    private fun parseStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseVarDecl(block)
            VAL -> parseVarDecl(block)
            RETURN -> parseReturn(block)
            WHILE -> parseWhile(block)
            IDENTIFIER, OPENB -> parseAssign(block)
            FOR -> parseFor(block)
            IF -> parseIf(block)
            CLASS -> throw ParseError(lookahead.location, "Class declarations are not allowed to nest")
            FUN -> throw ParseError(lookahead.location, "Function declarations are not allowed to nest")
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }

    private fun parseClassStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseFieldDecl(block)
            VAL -> parseFieldDecl(block)
            FUN -> parseFunction(block)
            CLASS -> throw ParseError(lookahead.location, "Class declarations are not allowed to nest")
            RETURN, WHILE, IDENTIFIER, OPENB, FOR, IF ->
                throw ParseError(lookahead.location, "Statements not allowed in class body")
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }


    private fun parseTopStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseVarDecl(block)
            VAL -> parseVarDecl(block)
            FUN -> parseFunction(block)
            CLASS -> parseClass(block)
            WHILE -> throw ParseError(lookahead.location, "While statements are not allowed at top level")
            RETURN -> throw ParseError(lookahead.location, "Return statements are not allowed at top level")
            FOR -> throw ParseError(lookahead.location, "For statements are not allowed at top level")
            IF -> throw ParseError(lookahead.location, "If statements are not allowed at top level")
            IDENTIFIER, OPENB -> throw ParseError(lookahead.location, "Assignment statements are not allowed at top level")
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }

    fun parseTopLevel(top:AstTopLevel) {
        while (lookahead.kind != EOF) {
            try {
                parseTopStatement(top)
            } catch (e: ParseError) {
                Log.error(e.message!!)
                skipToEol()
            }
        }
    }
}