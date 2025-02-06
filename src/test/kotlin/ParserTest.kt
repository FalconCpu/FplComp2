package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ParserTest {
     private fun runTest(input:String, expected:String) {
         val lexers = listOf(Lexer("input.fpl", StringReader(input)))
         val ret = compile(lexers, StopAt.PARSE)
         assertEquals(expected, ret)
     }

    @Test
    fun simpleDeclaration() {
        val input = """
            val a = 0x12
            var b = "hello world"
        """.trimIndent()

        val expected = """
            TopLevel
              Declare VAL
                Identifier a
                IntLit 18
              Declare VAR
                Identifier b
                StringLit hello world

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun declarationWithType() {
        val input = """
            val a : Int = 1
            var b : String = "hello world"
        """.trimIndent()

        val expected = """
            TopLevel
              Declare VAL
                Identifier a
                TypeIdentifier Int
                IntLit 1
              Declare VAR
                Identifier b
                TypeIdentifier String
                StringLit hello world

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun declarationWithNoInitializer() {
        val input = """
            val a : Int 
            var b : Real
        """.trimIndent()

        val expected = """
            TopLevel
              Declare VAL
                Identifier a
                TypeIdentifier Int
              Declare VAR
                Identifier b
                TypeIdentifier Real

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun malformedInitializer1() {
        val input = """
            val if = 4 
            var b = 1+
        """.trimIndent()

        val expected = """
            ERROR: input.fpl:1.5: Got  if when expecting <identifier>
            ERROR: input.fpl:2.11: Got '<end of line>' when expecting primary expression
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun simpleFunction() {
        val input = """
            fun foo(a:Int, b:String) -> Int
                val c = a
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                  Parameter
                    Identifier a
                    TypeIdentifier Int
                  Parameter
                    Identifier b
                    TypeIdentifier String
                TypeIdentifier Int
                Declare VAL
                  Identifier c
                  Identifier a

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun functionWithEnd() {
        val input = """
            fun foo(a:Int, b:String) -> Int
                val c = a
            end fun
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                  Parameter
                    Identifier a
                    TypeIdentifier Int
                  Parameter
                    Identifier b
                    TypeIdentifier String
                TypeIdentifier Int
                Declare VAL
                  Identifier c
                  Identifier a

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun functionWithNoParameters() {
        val input = """
            fun foo() -> Int
                var c = 23
            end fun
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                TypeIdentifier Int
                Declare VAR
                  Identifier c
                  IntLit 23

        """.trimIndent()

        runTest(input, expected)
    }


    @Test
    fun functionWithBadEnd() {
        val input = """
            fun foo(a:Int, b:String) -> Int
                val c = a
            end if
        """.trimIndent()

        val expected = """
            input.fpl:3.5: Got 'end if` when expecting 'end fun'
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun functionParameterMissingType() {
        val input = """
            fun foo(a, b:String) -> Int
                val c = a
        """.trimIndent()

        val expected = """
            input.fpl:1.10: Expected type after identifier
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun functionMissingBody() {
        val input = """
            fun foo(a:Int, b:String) -> Int
        """.trimIndent()

        val expected = """
            input.fpl:1.32: Expected indented block after function declaration
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badTopLevelStatement() {
        val input = """
            23
        """.trimIndent()

        val expected = """
            ERROR: input.fpl:1.1: Got  23 when expecting statement
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badStatementInFunction() {
        val input = """
            fun foo(a:Int, b:String) -> Int
                23
        """.trimIndent()

        val expected = """
            ERROR: input.fpl:2.5: Got  23 when expecting statement
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun malformedConstants() {
        val input = """
            val x = 1234A
            val y = 1234.XYZ
        """.trimIndent()

        val expected = """
            input.fpl:1.9: Malformed Integer literal '1234A'
            input.fpl:2.9: Malformed Real literal '1234.XYZ'
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun extraneousSymbols() {
        val input = """
            val x = 1234 5678
        """.trimIndent()

        val expected = """
            input.fpl:1.14: Got  5678 when expecting EOL
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun returnStatement() {
        val input = """
            fun foo(a:Int) -> Int
                return a+1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                  Parameter
                    Identifier a
                    TypeIdentifier Int
                TypeIdentifier Int
                Return
                  Binop PLUS
                    Identifier a
                    IntLit 1

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun whileStatement() {
        val input = """
            fun foo(a:Int) -> Int
                var x = 0
                while x < a
                    x = x + 1
                return x
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                  Parameter
                    Identifier a
                    TypeIdentifier Int
                TypeIdentifier Int
                Declare VAR
                  Identifier x
                  IntLit 0
                While
                  Compare LT
                    Identifier x
                    Identifier a
                  Assign
                    Identifier x
                    Binop PLUS
                      Identifier x
                      IntLit 1
                Return
                  Identifier x

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun assignment() {
        val input = """
            fun foo()
                var a = 0x12
                a=a+1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                ParameterList
                Declare VAR
                  Identifier a
                  IntLit 18
                Assign
                  Identifier a
                  Binop PLUS
                    Identifier a
                    IntLit 1

        """.trimIndent()
        runTest(input, expected)
    }

}

