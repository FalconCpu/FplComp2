package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class InterpreterTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE_IR)
        assertEquals(expected, ret)
    }

    @Test
    fun simpleOutput() {
        val input = """
            fun main()
                var hwregs = (0xE0000000 : Array<Int>)
                hwregs[0] = 0x48
        """.trimIndent()
        val expected = "H"
        runTest(input, expected)
    }

    @Test
    fun functionCall() {
        val input = """
            fun printChar(c: Int)
                var hwregs = (0xE0000000 : Array<Int>)
                hwregs[0] = c
                
            fun main()
                printChar(0x48)
        """.trimIndent()
        val expected = "H"
        runTest(input, expected)
    }

    @Test
    fun helloWorld() {
        val input = """
            fun printString(s: String)
                var hwregs = (0xE0000000 : Array<Int>)
                for i in 0 to< s.length
                    hwregs[0] = (s[i] : Int)
                
            fun main()
                printString("Hello, world!")
        """.trimIndent()
        val expected = "Hello, world!"
        runTest(input, expected)
    }

}