package falcon

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class LexerTest {

    @Test
    fun lexerTest() {
        val prog = """
            fun a ( b:c)
            "hello world" if   # comment
               a <= 23
            end
        """.trimIndent()

        val expected = """
            input.txt:1.1 FUN fun
            input.txt:1.5 IDENTIFIER a
            input.txt:1.7 OPENB (
            input.txt:1.9 IDENTIFIER b
            input.txt:1.10 COLON :
            input.txt:1.11 IDENTIFIER c
            input.txt:1.12 CLOSEB )
            input.txt:1.13 EOL <end of line>
            input.txt:2.1 STRING_LITERAL hello world
            input.txt:2.15 IF if
            input.txt:2.29 EOL <end of line>
            input.txt:3.4 INDENT <indent>
            input.txt:3.4 IDENTIFIER a
            input.txt:3.6 LE <=
            input.txt:3.9 INT_LITERAL 23
            input.txt:3.11 EOL <end of line>
            input.txt:4.1 DEDENT <dedent>
            input.txt:4.1 END end
            input.txt:4.4 EOL <end of line>
            input.txt:4.4 EOF <end of file>

        """.trimIndent()

        Log.clear()
        val lexer = Lexer("input.txt", StringReader(prog))
        val sb = StringBuilder()
        do {
            val tok = lexer.nextToken()
            sb.append("${tok.location} ${tok.kind} ${tok.text}\n")

        } while (tok.kind!=TokenKind.EOF)
        assertEquals("", Log.getErrors())
        assertEquals(expected, sb.toString())

    }

}