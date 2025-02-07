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
            
            fun printString(string:String)
                val hwregs = (0xE0000000:HwRegs)
                for i in 0 to< string.length
                    hwregs.uartTx = (string[i] : Int)
            
            fun main()
                printString("Hello, world!\n")
        """.trimIndent()

        val expected = """
            Hello, world!
            
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun variadicArgs() {
        val input = """
            class HwRegs
                var sevenSeg : Int
                var led : Int
                var sw : Int
                var key : Int 
                var uartTx : Int

            fun foo(args:Int...) -> Int
                var total = 0
                for index in 0 to< args.length
                    total = total + args[index]
                return total
                
            fun main()
                val hwregs = (0xE0000000:HwRegs)
                val tot = foo(1,2,3,4)
                hwregs.sevenSeg = tot
        """.trimIndent()

        val expected = """
            7-Segment = 00000a
            
        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun printInt() {
        val input = """
            class HwRegs
                var sevenSeg : Int
                var led : Int
                var sw : Int
                var key : Int 
                var uartTx : Int

            fun printChar(c:Char) 
                val hwregs = (0xE0000000:HwRegs)
                hwregs.uartTx = (c:Int)

            fun printInt(n:Int)
                var number = n
                if (n<0) 
                    printChar('-')
                    number = -number
                val buffer = local Array<Char>(10)
                var index = 0
                repeat
                    var digit = number % 10
                    number = number / 10
                    buffer[index] = (digit + '0' : Char)
                    index = index + 1
                until number = 0
                for i in index-1 to >= 0
                    printChar(buffer[i])
            
            fun main()
                printInt(12345)
                printChar('\n')
                printInt(-56789)
        """.trimIndent()

        val expected = """
            12345
            -56789
        """.trimIndent()
        runTest(input, expected)
    }

}
