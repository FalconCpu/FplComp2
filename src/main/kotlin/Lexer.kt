package falcon

import java.io.Reader

class Lexer(private val fileName: String, private val fileHandle: Reader) {
    private var lineNumber = 1
    private var columnNumber = 1
    private var lookahead = readChar()
    private var atEof = false
    private var lineContinues = true
    private var atStartOfLine = true
    private var indentStack = mutableListOf<Int>(1)

    private fun readChar() : Char {
        val c = fileHandle.read()
        if (c == -1) {
            atEof = true
            return '\u0000'
        } else
            return c.toChar()
    }

    private fun nextChar() : Char {
        val c = lookahead
        lookahead = readChar()
        if (c == '\n') {
            lineNumber++
            columnNumber = 1
        } else
            columnNumber++
        return c
    }

    private fun readWord() : String {
        val sb = StringBuilder()
        sb.append(nextChar())
        while (lookahead.isJavaIdentifierPart() && !atEof)
            sb.append(nextChar())
        return sb.toString()
    }

    private fun nextEscapedChar() : Char {
        val c = nextChar()
        return if (c=='\\') {
            when (val e = nextChar()) {
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                '"' -> '"'
                '\\' -> '\\'
                '0' -> '\u0000'
                else -> e
            }
        } else
            c
    }

    private fun readStringLiteral() : String {
        val sb = StringBuilder()
        nextChar()  // skip opening quote
        while (lookahead!='"' && !atEof)
            sb.append(nextEscapedChar())

        if (lookahead == '"')
            nextChar()  // skip closing quote
        else
            Log.error(Location(fileName, lineNumber, columnNumber), "Unterminated string literal")
        return sb.toString()
    }

    private fun readCharLiteral() : String {
        val sb = StringBuilder()
        nextChar()  // skip opening quote
        sb.append(nextEscapedChar())

        if (lookahead == '\'')
            nextChar()  // skip closing quote
        else
            Log.error(Location(fileName, lineNumber, columnNumber), "Unterminated char literal")
        return sb.toString()
    }


    private fun skipWhitespaceAndComments() {
        while (!atEof && (lookahead==' ' || lookahead=='\t' || lookahead=='\r' || lookahead== '#' || (lookahead=='\n' && lineContinues))) {
            if (lookahead=='#')
                while (lookahead != '\n' && !atEof)
                    nextChar()
            else
                nextChar()
        }
    }

    private fun readPunctuation() : String {
        val c = nextChar()
        if (c=='.' && lookahead=='.') {
            nextChar()
            if (lookahead=='.') {
                nextChar()
                return "..."
            } else
                return ".."
        }
        if ((c=='<' && lookahead=='=') ||
            (c=='>' && lookahead=='=') ||
            (c=='!' && lookahead=='=') ||
            (c=='-' && lookahead=='>'))
            return c.toString() + nextChar()
        return c.toString()
    }

    fun nextToken() : Token {
        skipWhitespaceAndComments()
        val location = Location(fileName, lineNumber, columnNumber)

        val kind : TokenKind
        val text : String

        if (atEof) {
            if (!atStartOfLine) {
                kind = TokenKind.EOL
            } else if (indentStack.size>1) {
                kind = TokenKind.DEDENT
                indentStack.removeLast()
            } else {
                kind = TokenKind.EOF
            }
            text = kind.text

        } else if (lookahead=='\n') {
            nextChar()
            kind = TokenKind.EOL
            text = kind.text

        } else if (atStartOfLine && columnNumber > indentStack.last()) {
            indentStack.add(columnNumber)
            kind = TokenKind.INDENT
            text = kind.text

        } else if (atStartOfLine && columnNumber < indentStack.last()) {
            indentStack.removeLast()
            kind = TokenKind.DEDENT
            text = kind.text
            if (columnNumber>indentStack.last()) {
                Log.error(location, "Indentation error: Got column ${columnNumber}, when expected ${indentStack.last()}")
                indentStack.add(columnNumber)
            }

        } else if (lookahead.isLetter()) {
            text = readWord()
            kind = TokenKind.textToKind.getOrDefault(text, TokenKind.IDENTIFIER )

        } else if (lookahead.isDigit()) {
            val text1 = readWord()
            if (lookahead=='.') {
                nextChar()
                text = text1 + "." + readWord()
                kind = TokenKind.REAL_LITERAL
            } else {
                text = text1
                kind = TokenKind.INT_LITERAL

            }

        } else if (lookahead=='"') {
            text = readStringLiteral()
            kind = TokenKind.STRING_LITERAL

        } else if (lookahead=='\'') {
            text = readCharLiteral()
            kind = TokenKind.CHAR_LITERAL

        } else {
            text = readPunctuation()
            kind = TokenKind.textToKind.getOrDefault(text, TokenKind.ERROR )
        }

        if (kind==TokenKind.ERROR)
            Log.error(location, "Unexpected character: '$text'")

        lineContinues = kind.lineContinues
        atStartOfLine = (kind==TokenKind.EOL) || (kind==TokenKind.DEDENT)
        //println("$kind:$text")
        return Token(location, kind, text)
    }

}