package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ExecuteTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, ret)
    }

    @Test
    fun outputChar() {
        val input = """
            class HwRegs
                var sevenSeg : Int
                var led : Int
                var sw : Int
                var key : Int 
                var uartTx : Int
            
            fun main()
                val hwregs = (0xE0000000:HwRegs*)
                hwregs.uartTx = 65
        """.trimIndent()

        val expected = """
            A
        """.trimIndent()
        runTest(input, expected)
    }
}
