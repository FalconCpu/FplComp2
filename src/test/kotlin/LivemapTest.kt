package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class LiveMapTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.OPTIMIZE)
        assertEquals(expected, ret)
    }

    @Test
    fun simpleDeclaration() {
        val input = """
            fun add(a:Int, b:Int) -> Int
                return a+b+1
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START
            RET []
            
            Function add:
            START
            ADDI $1, $1, $2
            ADDI $8, $1, 1
            RET [$8]
            
            
        """.trimIndent()
        runTest(input, expected)
    }
}
