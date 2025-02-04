package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class TypeCheckingTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.TYPE_CHECK)
        assertEquals(expected, ret)
    }

    @Test
    fun simpleDeclaration() {
        val input = """
            val a = 0x12
            val b = "hello world"
        """.trimIndent()

        val expected = """
            TopLevel
              Declare a Int
                IntLit 18 Int
              Declare b String
                StringLit "hello world" String

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun duplicateSymbol() {
        val input = """
            val a = 0x12
            val a = "hello world"
        """.trimIndent()

        val expected = """
            input.fpl:2.5: duplicate symbol name 'a'. First defined at input.fpl:1.5
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun useVariable() {
        val input = """
            val a = 0x12
            val b = a
        """.trimIndent()

        val expected = """
            TopLevel
              Declare a Int
                IntLit 18 Int
              Declare b Int
                Variable a Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun simpleBinop() {
        val input = """
            val a = 0x12
            val b = a + 1
            val c = a < b
            val d = 1.3 * 2.6 - 1.2
        """.trimIndent()

        val expected = """
            TopLevel
              Declare a Int
                IntLit 18 Int
              Declare b Int
                Binop ADDI Int
                  Variable a Int
                  IntLit 1 Int
              Declare c Bool
                Compare LTI Bool
                  Variable a Int
                  Variable b Int
              Declare d Real
                Binop SUBR Real
                  Binop MULR Real
                    RealLit 1.3 Real
                    RealLit 2.6 Real
                  RealLit 1.2 Real

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun mismatchedType() {
        val input = """
            val a : String = 0x12
        """.trimIndent()

        val expected = """
            input.fpl:1.18: Type mismatch: expected String but got Int
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun functionDefinition() {
        val input = """
            fun foo(a:Int)
                val b = a+1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                Declare b Int
                  Binop ADDI Int
                    Variable a Int
                    IntLit 1 Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun functionDuplicateParameter() {
        val input = """
            fun foo(a:Int, a:String)
                val b = a+1
        """.trimIndent()

        val expected = """
            input.fpl:1.16: duplicate symbol name 'a'. First defined at input.fpl:1.9
            input.fpl:2.14: No operation '+' for types String and Int
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun functionDuplicateParameter2() {
        val input = """
            fun foo(a:Int)
                val a = 4
        """.trimIndent()

        val expected = """
            input.fpl:2.9: duplicate symbol name 'a'. First defined at input.fpl:1.9
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun assignment() {
        val input = """
            fun foo()
                var a = 4
                a = a + 1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                Declare a Int
                  IntLit 4 Int
                Assign
                  Variable a Int
                  Binop ADDI Int
                    Variable a Int
                    IntLit 1 Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun assignmentNonMutable() {
        val input = """
            fun foo()
                val a = 4
                a = a + 1
        """.trimIndent()

        val expected = """
            input.fpl:3.5: Variable 'a' is not mutable
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun assignmentNonLValue() {
        val input = """
            fun foo()
                val a = 4
                (a+1) = 3
        """.trimIndent()

        val expected = """
            input.fpl:3.7: Expression is not an lvalue
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun whileLoop() {
        val input = """
            fun foo()
                var a = 0
                while a < 10
                    a = a + 1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                Declare a Int
                  IntLit 0 Int
                While
                  Compare LTI Bool
                    Variable a Int
                    IntLit 10 Int
                  Assign
                    Variable a Int
                    Binop ADDI Int
                      Variable a Int
                      IntLit 1 Int

        """.trimIndent()
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

        val expected = """
            TopLevel
              Function printChar
                Declare hwregs Array<Int>
                  Cast (Array<Int>)
                    IntLit -536870912 Int
                Assign
                  Index (Int)
                    Variable hwregs Array<Int>
                    IntLit 0 Int
                  Variable c Int
              Function main
                ExpressionStatement
                  FunctionCall (Unit)
                    FunctionLiteral printChar
                    IntLit 72 Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun functionCall1() {
        val input = """
            fun double(a:Int)->Int
                return a * 2
                
            fun main()
                val b = double(4)
        """.trimIndent()

        val expected = """
            TopLevel
              Function double
                Return
                  Binop MULI Int
                    Variable a Int
                    IntLit 2 Int
              Function main
                Declare b Int
                  FunctionCall (Int)
                    FunctionLiteral double
                    IntLit 4 Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun functionCallBadArgs1() {
        val input = """
            fun double(a:Int)->Int
                return a * 2
                
            fun main()
                val a = double("three")
                val b = double()
                val c = double(4, 5)
        """.trimIndent()

        val expected = """
            input.fpl:5.20: Type mismatch. Expected Int, but found String
            input.fpl:6.19: Expected 1 arguments, got 0
            input.fpl:7.19: Expected 1 arguments, got 2
        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun functionCallBadReturn() {
        val input = """
            fun double(a:Int)->Int
                return "three"
                
            fun main()
                val b = double(5)
        """.trimIndent()

        val expected = """
            input.fpl:2.12: Type mismatch. Expected Int, but found String
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
            TopLevel
              Function foo
                If
                  IfClause
                    Compare EQI Bool
                      Variable a Int
                      IntLit 0 Int
                    Return
                      StringLit "zero" String
                  IfClause
                    Compare EQI Bool
                      Variable a Int
                      IntLit 1 Int
                    Return
                      StringLit "one" String
                  IfClause
                    Return
                      StringLit "other" String

            """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun ifNotBool() {
        val input = """
            fun foo(a:Int) -> String
                if a
                    return "zero"
                else
                    return "other"
        """.trimIndent()

        val expected = """
            input.fpl:2.8: Type mismatch: expected Bool but got Int
            """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun classTest() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun main()
                val cat = Cat("Fluffy", 3)
                val a = cat.age
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
              Function main
                Declare cat Cat
                  Constructor (Cat)
                    StringLit "Fluffy" String
                    IntLit 3 Int
                Declare a Int
                  Member age (Int)
                    Variable cat Cat
            
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun notClassTest() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun main()
                val cat = 23
                val a = cat.age
        """.trimIndent()

        val expected = """
            input.fpl:5.16: Not a class
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun callNonFunc() {
        val input = """
            fun main()
                val f = 23
                val b = f(2)
        """.trimIndent()

        val expected = """
            input.fpl:3.14: Call on non-function type Int
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun notTypeExpression() {
        val input = """
            fun main()
                val f = 23
                val b : f = 4
        """.trimIndent()

        val expected = """
            input.fpl:3.13: Type 'f' is a variable not a type
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun newClassTest() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun main()
                val cat = new Cat("fluffy",5)
                val a = cat.age
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
              Function main
                Declare cat Cat*
                  New (Cat*)
                    Constructor (Cat)
                      StringLit "fluffy" String
                      IntLit 5 Int
                Declare a Int
                  Member age (Int)
                    Variable cat Cat*

        """.trimIndent()
        runTest(input, expected)
    }
}
