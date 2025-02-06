package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StdLibTest {
    val stdLibFiles = listOf("print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String, expected: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, ret)
    }

    @Test
    fun outputChar() {
        val input = """            
            
            fun main()
                printString("Hello, world!\n")
        """.trimIndent()

        val expected = """
            Hello, world!
            
        """.trimIndent()
        runTest(input, expected)
    }
}
