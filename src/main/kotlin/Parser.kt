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

    private fun expect(kind1:TokenKind, kind2:TokenKind) :Token {
        if (lookahead.kind == kind1 || lookahead.kind == kind2)
            return nextToken()
        throw ParseError(lookahead.location, "Got  $lookahead when expecting ${kind1.text}")
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

    private fun parseExpressionList() : List<AstExpression> {
        val ret = mutableListOf<AstExpression>()
        expect(OPENB)
        if (lookahead.kind!=CLOSEB)
            do {
                ret.add(parseExpression())
            } while (canTake(COMMA))
        expect(CLOSEB)
        return ret
    }

    private fun parseFunctionCall(lhs: AstExpression) : AstExpression {
        val loc = lookahead.location
        val args = parseExpressionList()
        return AstFunctionCall(loc, lhs, args)
    }

    private fun parseMemberExpression(lhs: AstExpression) : AstExpression {
        val loc = expect(DOT)
        val id = expect(IDENTIFIER)
        return AstMember(loc.location, lhs, id.text)
    }

    private fun parseNullAssert(lhs: AstExpression) : AstExpression {
        val loc = expect(EMARKEMARK)
        return AstNullAssert(loc.location, lhs)
    }

    private fun parsePostfix() : AstExpression {
        var ret = parsePrimary()
        while(true)
            ret = when (lookahead.kind) {
                OPENSQ -> parseIndexExpression(ret)
                OPENB -> parseFunctionCall(ret)
                DOT -> parseMemberExpression(ret)
                EMARKEMARK -> parseNullAssert(ret)
                else -> return ret
            }
    }

    private fun parseNew() : AstExpression {
        val tok = nextToken()
        val isLocal = tok.kind==LOCAL
        if (canTake(ARRAY)) {
            expect(LT)
            val type = parseType()
            expect(GT)
            expect(OPENB)
            val size = parseExpression()
            expect(CLOSEB)
            return AstNewArray(tok.location, type, size, isLocal)
        } else {
            val type = parseTypeIdentifier()
            val args = parseExpressionList()
            return AstConstructor(tok.location, type, args, isLocal)
        }
    }

    private fun parseOptArrayType() : AstType? {
        if (canTake(LT)) {
            val ret = parseType()
            expect(GT)
            return ret
        }
        return null
    }

    private fun parseConstArray() : AstExpression {
        val loc = expect(ARRAY)
        val astType = parseOptArrayType()
        val values = parseExpressionList()
        return AstConstArray(loc.location, astType, values)
    }

    private fun parsePrefix() : AstExpression {
        if (canTake(MINUS) )
            return AstUnaryOp(lookahead.location, MINUS, parsePrefix())
        if (canTake(NOT))
            return AstUnaryOp(lookahead.location, NOT, parsePrefix())
        if (canTake(CONST))
            return parseConstArray()
        if (lookahead.kind==NEW || lookahead.kind==LOCAL)
            return parseNew()
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
               lookahead.kind == EQ || lookahead.kind == NE ||
               lookahead.kind == IS || lookahead.kind == ISNOT) {
            val op = nextToken()
            if (op.kind==IS || op.kind==ISNOT) {
                val right = parseType()
                left = AstIsExpression(op.location, left, right, op.kind==ISNOT)
            } else {
                val right = parseAdd()
                left = if (op.kind == EQ || op.kind == NE)
                    AstEqualsOp(op.location, op.kind, left, right)
                else
                    AstCompareOp(op.location, op.kind, left, right)
            }
        }
        return left
    }

    private fun parseAnd() : AstExpression {
        var left = parseComp()
        while (lookahead.kind == AND) {
            val op = nextToken()
            val right = parseComp()
            left = AstAndOp(op.location, left, right)
        }
        return left
    }

    private fun parseOr() : AstExpression {
        var left = parseAnd()
        while (lookahead.kind == OR) {
            val op = nextToken()
            val right = parseAnd()
            left = AstOrOp(op.location, left, right)
        }
        return left
    }

    internal fun parseExpression() : AstExpression {
        if (lookahead.kind == IF) {
            val loc = expect(IF)
            val cond = parseOr()
            expect(THEN)
            val thenExpr = parseExpression()
            expect(ELSE)
            val elseExpr = parseExpression()
            return AstIfExpression(loc.location, cond, thenExpr, elseExpr)
        } else
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

        if (canTake(QUESTION))
            ret = AstTypeNullable(lookahead.location, ret, true)
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

    private fun parseConstDecl(block:AstBlock) {
        val tok = nextToken()
        val id = parseIdentifier()
        val optType = parseOptType()
        expect(EQ)
        val optInit = parseExpression()
        expectEol()
        block.add(AstConstStatement(tok.location, id.name, optType, optInit))
    }


    private fun parseGlobalVarDecl(block:AstBlock) {
        val tok = nextToken()
        val id = parseIdentifier()
        val optType = parseOptType()
        val optInit = parseOptInitializer()
        expectEol()
        block.add(AstDeclareGlobalVar(tok.location, tok.kind, id, optType, optInit))
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

    private fun parseParameterList() : AstParameterList {
        val params = mutableListOf<AstParameter>()
        expect(OPENB)
        if (canTake(CLOSEB))
            return AstParameterList(params,false)
        do {
            params.add(parseParameter())
        } while (canTake(COMMA))
        val isVariadic = canTake(DOTDOTDOT)
        expect(CLOSEB)
        return AstParameterList(params,isVariadic)
    }

    private fun parseFunction(block: AstBlock, qualifiers: Set<TokenKind>) {
        expect(FUN)
        val id = expect(IDENTIFIER,DELETE)
        val params = parseParameterList()
        val retType = if (canTake(ARROW)) parseType() else null
        expectEol()
        val ret = AstFunction(id.location, id.text, params, retType, qualifiers, block)
        block.add(ret)

        if (lookahead.kind==INDENT) {
            if (qualifiers.contains(EXTERN))
                Log.error(lookahead.location, "Extern functions cannot have body")
            else
                parseIndentedBlock(ret)
        } else
            if (!qualifiers.contains(EXTERN))
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

    private fun parseDelete(block: AstBlock) {
        expect(DELETE)
        val loc = lookahead.location
        val expr = parseExpression()
        expectEol()
        block.add(AstDeleteStatement(loc, expr))
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

    private fun parseRepeat(block: AstBlock) {
        val tok = expect(REPEAT)
        expectEol()
        val ret = AstRepeat(tok.location, block)
        block.add(ret)
        if (lookahead.kind==INDENT)
            parseIndentedBlock(ret)
        else
            Log.error(lookahead.location, "Expected indented block after repeat")
        expect(UNTIL)
        ret.expr = parseExpression()
        expectEol()
    }


    private fun parseAssign(block:AstBlock) {
        val lhs = parsePostfix()
        if (canTake(EQ)) {
            val rhs = parseExpression()
            expectEol()
            block.add(AstAssign(lhs.location, lhs, rhs))
        } else if (lookahead.kind in listOf(PLUSEQ, MINUSEQ, STAREQ, SLASHEQ)) {
            val tok = nextToken()
            val rhs = parseExpression()
            expectEol()
            block.add(AstCompoundAssign(tok.location, tok.kind, lhs, rhs))
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
        val ret = if (canTake(TO)) {
            val comparator = if (lookahead.kind in listOf(LT, LE, GT, GE)) nextToken().kind else LE
            val to = parseExpression()
            expectEol()
            AstForRange(id.location, id, from, to, comparator, block)
        } else {
            expectEol()
            AstForArray(id.location, id, from, block)
        }
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

    private fun parseWhenClause(block:AstBlock) : AstWhenClause {
        val location = lookahead.location
        val exprs= mutableListOf<AstExpression>()
        val isElse = canTake(ELSE)
        if (!isElse)
            do
                exprs.add(parseExpression())
            while (canTake(COMMA))
        expect(ARROW)
        val ret = AstWhenClause(location, exprs, isElse, block)
        if (canTake(EOL))
            parseIndentedBlock(ret)
        else
            parseStatement(ret)
        return ret
    }

    private fun parseWhenStatement(block: AstBlock) {
        expect(WHEN)
        val expr = parseExpression()
        expectEol()
        val clauses = mutableListOf<AstWhenClause>()
        if (canTake(INDENT))
            do {
                clauses += parseWhenClause(block)
            } while (lookahead.kind!=DEDENT && lookahead.kind!=EOF)
        else
            Log.error(lookahead.location, "Expected indented block after when")
        expect(DEDENT)
        parseOptionalEnd(WHEN)
        val ret = AstWhenStatement(expr.location, expr, clauses)
        block.add(ret)
    }

    private fun parseSuperclass() : AstConstructor {
        val id = parseTypeIdentifier()
        val args = mutableListOf<AstExpression>()
        if (canTake(OPENB)) {
            if (lookahead.kind != CLOSEB)
                do {
                    args.add(parseExpression())
                } while (canTake(COMMA))
            expect(CLOSEB)
        }
        return AstConstructor(id.location, id, args, false)
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

    private fun parseClassParameterList() : AstParameterList {
        val ret = mutableListOf<AstParameter>()
        if (lookahead.kind!=OPENB)
            return AstParameterList(ret,false)
        expect(OPENB)
        if (lookahead.kind!=CLOSEB)
            do {
                ret.add(parseClassParameter())
            } while (canTake(COMMA))
        val isVariadic = canTake(DOTDOTDOT)
        expect(CLOSEB)
        return AstParameterList(ret, isVariadic)
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

    private fun resolveSuperclass(astSuperClass: AstConstructor?, block:AstBlock) : ClassType?? {
        if (astSuperClass==null)
            return null

        val sym = block.symbolTable.lookup(astSuperClass.astType.name)
        if (sym==null) {
            Log.error(astSuperClass.astType.location, "Undefined symbol ${astSuperClass.astType.name}")
            return null
        }

        if (sym !is TypeSymbol) {
            Log.error(astSuperClass.astType.location, "Symbol ${astSuperClass.astType.name} is a value not a class")
            return null
        }

        if (sym.type !is ClassType) {
            Log.error(astSuperClass.astType.location, "Type ${astSuperClass.astType.name} is not a class")
            return null
        }

        return sym.type
    }

    private fun parseClass(block: AstBlock) {
        expect(CLASS)
        val id = parseIdentifier()
        val params = parseClassParameterList()
        val astSuperclass = if (canTake(COLON)) parseSuperclass() else null
        expectEol()

        val type = ClassType.make(id.name, resolveSuperclass(astSuperclass, block))
        val ret = AstClass(id.location, id.name, params, type, astSuperclass, block)
        val sym = TypeSymbol(id.location, id.name, type)
        block.symbolTable.add(sym)
        block.add(ret)

        if (lookahead.kind == INDENT)
            parseClassBody(ret)
        parseOptionalEnd(CLASS)
    }

    private fun parseEnumBody() : List<AstIdentifier> {
        val ret = mutableListOf<AstIdentifier>()
        expect(INDENT)
        while (lookahead.kind != DEDENT && lookahead.kind != EOF) {
            val id = parseIdentifier()
            expectEol()
            ret.add(id)
        }
        expect(DEDENT)
        return ret
    }

    private fun parseEnum(block: AstBlock) {
        expect(ENUM)
        val id = parseIdentifier()
        expectEol()
        val body = parseEnumBody()
        parseOptionalEnd(ENUM)
        val type = EnumType.make(id.name, body)
        val ret = AstEnum(id.location, id.name, type, body, block)
        val typeSym = TypeSymbol(id.location, id.name, type)
        block.symbolTable.add(typeSym)
        block.add(ret)

    }


    private fun parseBreakStatement(block: AstBlock) {
        val loc = expect(BREAK)
        expectEol()
        val ret = AstBreakStatement(loc.location)
        block.add(ret)
    }

    private fun parseContinueStatement(block: AstBlock) {
        val loc = expect(CONTINUE)
        expectEol()
        val ret = AstContinueStatement(loc.location)
        block.add(ret)
    }

    private fun parseQualifiers(block: AstBlock) {
        val qualifiers = mutableSetOf<TokenKind>()
        while(lookahead.kind in listOf(VIRTUAL, OVERRIDE, EXTERN))
            qualifiers.add(nextToken().kind)
        if (qualifiers.contains(OVERRIDE) && qualifiers.contains(VIRTUAL))
            Log.error(lookahead.location, "Cannot use both virtual and override")

        when (lookahead.kind) {
            FUN -> parseFunction(block, qualifiers)
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }

    private fun parseStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseVarDecl(block)
            VAL -> parseVarDecl(block)
            RETURN -> parseReturn(block)
            WHILE -> parseWhile(block)
            REPEAT -> parseRepeat(block)
            IDENTIFIER, OPENB -> parseAssign(block)
            FOR -> parseFor(block)
            IF -> parseIf(block)
            WHEN -> parseWhenStatement(block)
            DELETE -> parseDelete(block)
            BREAK -> parseBreakStatement(block)
            CONTINUE -> parseContinueStatement(block)
            CONST -> parseConstDecl(block)
            CLASS -> throw ParseError(lookahead.location, "Class declarations are not allowed to nest")
            FUN -> throw ParseError(lookahead.location, "Function declarations are not allowed to nest")
            ENUM -> throw ParseError(lookahead.location, "Enum declarations are not allowed to nest")
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }

    private fun parseClassStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseFieldDecl(block)
            VAL -> parseFieldDecl(block)
            FUN -> parseFunction(block, emptySet())
            OVERRIDE, VIRTUAL, EXTERN -> parseQualifiers(block)
            CLASS -> throw ParseError(lookahead.location, "Class declarations are not allowed to nest")
            RETURN, WHILE, IDENTIFIER, OPENB, FOR, IF ->
                throw ParseError(lookahead.location, "Statements not allowed in class body")
            else -> throw ParseError(lookahead.location, "Got  $lookahead when expecting statement")
        }
    }

    private fun parseTopStatement(block:AstBlock) {
        when (lookahead.kind) {
            VAR -> parseGlobalVarDecl(block)
            VAL -> parseGlobalVarDecl(block)
            FUN -> parseFunction(block, emptySet())
            CLASS -> parseClass(block)
            CONST -> parseConstDecl(block)
            ENUM -> parseEnum(block)
            OVERRIDE, VIRTUAL, EXTERN -> parseQualifiers(block)
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