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
            fun main()
                val a = 0x12
                var b = a*3+1
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START
            RET []

            Function main:
            START
            MOV #0, 18
            MOV a, #0
            MOV #1, 3
            MULI #2, a, #1
            MOV #3, 1
            ADDI #4, #2, #3
            MOV b, #4
            @0:
            RET []


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
            RET []

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
            RET []


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
            RET []

            Function printString:
            START
            MOV s, $1
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
            @4:
            ADDI i, i, 1
            @2:
            BLTI i, #3, @1
            @3:
            @0:
            RET []

            Function main:
            START
            LEA #0, "Hello, world!"
            MOV $1, #0
            CALL printString($1)
            MOV #1, 0
            @0:
            RET []


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
            RET []
            
            Function foo:
            START
            MOV a, $1
            MOV #0, 0
            BEQI a, #0, @3
            JMP @2
            @2:
            MOV #1, 1
            BEQI a, #1, @5
            JMP @4
            @4:
            JMP @7
            @6:
            JMP @1
            @3:
            LEA #2, "zero"
            MOV $8, #2
            JMP @0
            JMP @1
            @5:
            LEA #3, "one"
            MOV $8, #3
            JMP @0
            JMP @1
            @7:
            LEA #4, "other"
            MOV $8, #4
            JMP @0
            JMP @1
            @1:
            @0:
            RET [$8]
            
            
            """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun localArrayTest() {
        val input = """
            fun foo() 
                val array = local Array<Int>(10)
                for i in 0 to< array.length
                    array[i] = i
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START
            RET []
            
            Function foo:
            START
            MOV #0, 10
            ADDI #1, $31, 4
            ST4 #0, #1[length]
            MOV array, #1
            MOV #2, 0
            LD4 #3, array[length]
            MOV i, #2
            JMP @2
            @1:
            MULI #4, i, 4
            ADDI #5, array, #4
            ST4 i, #5[0]
            @4:
            ADDI i, i, 1
            @2:
            BLTI i, #3, @1
            @3:
            @0:
            RET []
            

        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun globalVarTest() {
        val input = """
            var count = 0
            var fred = 0
            fun foo() 
                while count<10
                    count = count + 1
                fred = count * 2
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START
            MOV #0, 0
            ST4 #0, GLOBAL[count]
            MOV #1, 0
            ST4 #1, GLOBAL[fred]
            RET []

            Function foo:
            START
            JMP @2
            @1:
            LD4 #0, GLOBAL[count]
            MOV #1, 1
            ADDI #2, #0, #1
            ST4 #2, GLOBAL[count]
            @2:
            LD4 #3, GLOBAL[count]
            MOV #4, 10
            BLTI #3, #4, @1
            JMP @3
            @3:
            LD4 #5, GLOBAL[count]
            MOV #6, 2
            MULI #7, #5, #6
            ST4 #7, GLOBAL[fred]
            @0:
            RET []


        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun methodCalls() {
        val input = """
            class Cat(val name:String, val age:Int)
                fun greet() -> String
                    return "meow"
            
            fun main()
                var c = new Cat("Fluffy", 3)
                c.greet()
        """.trimIndent()

        val expected = """
            Function <TopLevel>:
            START
            RET []
            
            Function Cat:
            START
            MOV this, $1
            MOV name, $2
            MOV age, $3
            ST4 name, this[name]
            ST4 age, this[age]
            @0:
            RET []
            
            Function Cat/greet:
            START
            MOV this, $1
            LEA #0, "meow"
            MOV $8, #0
            JMP @0
            @0:
            RET [$8]
            
            Function main:
            START
            LEA $1, CLASS(Cat)
            CALL mallocObject($1)
            MOV #0, $8
            MOV #1, $8
            LEA #2, "Fluffy"
            MOV #3, 3
            MOV $1, #1
            MOV $2, #2
            MOV $3, #3
            CALL Cat($1, $2)
            MOV #4, 0
            MOV c, #1
            MOV $1, c
            CALL Cat/greet()
            MOV #5, $8
            @0:
            RET []
            

        """.trimIndent()
        runTest(input, expected)
    }


}