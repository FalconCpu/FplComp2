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

    @Test
    fun printHex() {
        val input = """            
            
            fun main()
                printHex(0x1234ABCD)
        """.trimIndent()

        val expected = """
            1234ABCD
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun printfTest() {
        val input = """            
            fun main()
                printf("Hex: %x\n", 305441741)   # Expected output: "Hex: 1234ABCD"
                printf("String: %s\n", "Hello")  # Expected output: "String: Hello"
                printf("Char: %c\n", 'A')        # Expected output: "Char: A"
                printf("Integer: %d %d\n", 12345, -67890)  # Expected output: "Char: A"
        """.trimIndent()

        val expected = """
            Hex: 1234ABCD
            String: Hello
            Char: A
            Integer: 12345 -67890
            
        """.trimIndent()
        runTest(input, expected)
    }

}
