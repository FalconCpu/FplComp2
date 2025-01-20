package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class IRGenTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.IRGEN)
        assertEquals(expected, ret)
    }

    @Test
    fun simpleDeclaration() {
        val input = """
            val a = 0x12
            var b = a*3+1
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START 
            MOV #0, 18
            MOV a, #0
            MOV #1, 3
            MULI #2, a, #1
            MOV #3, 1
            ADDI #4, #2, #3
            MOV b, #4
            RET null


        """.trimIndent()
        runTest(input, expected)
    }

@Test
    fun assignment() {
        val input = """
            var a = 0x12
            a=a+1
        """.trimIndent()

        val expected = """
            ERROR: input.fpl:2.1: Assignment statements are not allowed at top level
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun whileLoop() {
        val input = """
            fun foo()
                var a = 0
                while a<20
                    a=a+1
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START 
            RET null

            Function foo:
            START 
            MOV #0, 0
            MOV a, #0
            JMP @2
            @1:
            MOV #1, 1
            ADDI #2, a, #1
            MOV a, #2
            @2:
            MOV #3, 20
            BLTI a, #3, @1
            JMP @3
            @3:
            @0:
            RET null


        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun helloWorld() {
        val input = """
            fun printString(s: String)
                var hwregs = (0xE0000000 : Array<Int>)
                for i in 0 to <12
                    hwregs[0] = (s[i] : Int)
                
            fun main()
                printString("Hello, world!")
        """.trimIndent()
        val expected = """
            Function <TopLevel>:
            START 
            RET null

            Function printString:
            START s
            MOV #0, -536870912
            MOV #1, #0
            MOV hwregs, #1
            MOV #2, 0
            MOV #3, 12
            MOV i, #2
            JMP @2
            @1:
            MULI #4, i, 1
            ADDI #5, s, #4
            LD1 #6, #5[0]
            MOV #7, #6
            MOV #8, 0
            MULI #9, #8, 4
            ADDI #10, hwregs, #9
            ST4 #7, #10[0]
            ADDI i, i, 1
            @2:
            BLTI i, #3, @1
            @3:
            @0:
            RET null

            Function main:
            START 
            LEA #0, "Hello, world!"
            CALL printString(#0)
            @0:
            RET null


        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun ifStatement() {
        val input = """
            fun foo(a:Int) -> String
                if a=0
                    return "zero"
                elsif a=1
                    return "one"
                else
                    return "other"
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START 
            RET null

            Function foo:
            START a
            MOV #1, 0
            BEQI a, #1, @3
            JMP @2
            @2:
            JMP @1
            MOV #2, 1
            BEQI a, #2, @5
            JMP @4
            @4:
            JMP @1
            JMP @7
            @6:
            JMP @1
            @3:
            LEA #3, "zero"
            MOV #0, #3
            JMP @0
            JMP @1
            @5:
            LEA #4, "one"
            MOV #0, #4
            JMP @0
            JMP @1
            @7:
            LEA #5, "other"
            MOV #0, #5
            JMP @0
            JMP @1
            @1:
            @0:
            RET #0


            """.trimIndent()
        runTest(input, expected)
    }


}