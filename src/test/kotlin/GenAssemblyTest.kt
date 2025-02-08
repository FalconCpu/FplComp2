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
            # Generated by Falcon Compiler
            sub $31, 4
            stw $30, $31[0]
            jsr init
            jsr /main
            ldw $30, $31[0]
            add $31, 4
            ret
            
            init:
            ret
            
            /add:
            # a = $1
            # b = $2
            add $1, $1, $2
            add $8, $1, 1
            ret
            

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
            # Generated by Falcon Compiler
            sub $31, 4
            stw $30, $31[0]
            jsr init
            jsr /main
            ldw $30, $31[0]
            add $31, 4
            ret
            
            init:
            stw 0,$29[0]
            stw 0,$29[4]
            ret
            
            /foo:
            jmp @2
            @1:
            ldw $1,$29[0]
            add $1, $1, 1
            stw $1,$29[0]
            @2:
            ldw $1,$29[0]
            ld $2,10
            blt $1, $2, @1
            ldw $1,$29[0]
            lsl $1, $1, 1
            stw $1,$29[4]
            ret
            

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun newTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
                
            fun main()
                val c = new Cat("Fluffy",4)
        """.trimIndent()

        val expected = """
            # Generated by Falcon Compiler
            sub $31, 4
            stw $30, $31[0]
            jsr init
            jsr /main
            ldw $30, $31[0]
            add $31, 4
            ret
            
            init:
            ret
            
            /Cat:
            # this = $1
            # name = $2
            # age = $3
            stw $2,$1[0]
            stw $3,$1[4]
            ret
            
            /main:
            sub $31, $31, 4
            stw $30, $31[0]
            ld $1, Class/Cat
            jsr /mallocObject
            ld $2, string_0
            ld $1, $8
            ld $3,4
            jsr /Cat
            ldw $30, $31[0]
            add $31, $31, 4
            ret
            
            Class/Cat:
            dcw string_1
            dcw 8
            
            dcw 6
            string_0: # Fluffy
            dcw 1718971462
            dcw 31078
            dcw 3
            string_1: # Cat
            dcw 7627075
        """.trimIndent()

        runTest(prog,expected)
    }

}
