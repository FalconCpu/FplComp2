package falcon

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class ParseExpressionTest {

     private fun runTest(input:String, expected:String) {
         Log.clear()
         val lexer = Lexer("input.fpl", StringReader(input))
         val parser = Parser(lexer)
         val ast = parser.parseExpression()
         val sb = StringBuilder()
         ast.dump(0, sb)
         assertEquals("", Log.getErrors())
         assertEquals(expected, sb.toString())
     }

    @Test
    fun expression1() {
        val expression = "123"
        val expected = """
            IntLit 123
            
        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun expression2() {
        val expression = "'a'-5"
        val expected = """
            Binop MINUS
              CharLit a
              IntLit 5

        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun expression3() {
        val expression = "a=b and c<d or c!=d"
        val expected = """
            Binop OR
              Binop AND
                Binop EQ
                  Identifier a
                  Identifier b
                Binop LT
                  Identifier c
                  Identifier d
              Identifier c
            
        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun expression4() {
        val expression = "5*(a+1.0)"
        val expected = """
            Binop STAR
              IntLit 5
              Binop PLUS
                Identifier a
                RealLit 1.0
            
        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun arrayIndex() {
        val expression = "a[3]-b[x*1]"
        val expected = """
            Binop MINUS
              Index
                Identifier a
                IntLit 3
              Index
                Identifier b
                Binop STAR
                  Identifier x
                  IntLit 1

        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun memberAccess() {
        val expression = "a[3].fred"
        val expected = """
            Member fred
              Index
                Identifier a
                IntLit 3

        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun unaryMinus() {
        val expression = "-b"
        val expected = """
            UnaryOp MINUS
              Identifier b

        """.trimIndent()
        runTest(expression, expected)
    }

    @Test
    fun unaryNot() {
        val expression = "not(a=b)"
        val expected = """
            UnaryOp MINUS
              Binop EQ
                Identifier a
                Identifier b

        """.trimIndent()
        runTest(expression, expected)
    }


}


