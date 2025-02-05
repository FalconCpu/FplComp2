package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class GenAssemblyTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.ASSEMBLY)
        assertEquals(expected, ret)
    }

    @Test
    fun simpleDeclaration() {
        val input = """
            fun add(a:Int, b:Int) -> Int
                return a+b+1
        """.trimIndent()

        val expected = """
            init:
            ret
            
            /add:
            add $1, $1, $2
            add $8, $1, 1
            ret
            

        """.trimIndent()
        runTest(input, expected)
    }
}
