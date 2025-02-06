package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader

class PathContextTest {
    private fun runTest(input: String, expected: String) {
        val lexers = listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.TYPE_CHECK)
        assertEquals(expected, ret)
    }

    @Test
    fun uninitializedVariable() {
        val input = """
            fun foo()
                val a : Int
                val b = a + 1       # error as b is uninitialized
        """.trimIndent()

        val expected = """
            input.fpl:3.13: 'a' is uninitialized
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun initializedVariable() {
        val input = """
            fun foo() 
                val a : Int
                a = 3           # OK to assign to uninitialized val
                val b = a + 1
        """.trimIndent()

        val expected = """
            TopLevel
              Function foo
                Declare a Int
                Assign
                  Variable a Int
                  IntLit 3 Int
                Declare b Int
                  Binop ADDI Int
                    Variable a Int
                    IntLit 1 Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun assignVal() {
        val input = """
            fun foo() 
                val a : Int
                a = 3           # OK to assign to uninitialized val
                a = a + 1       # error - reassigning val
        """.trimIndent()

        val expected = """
            input.fpl:4.5: Variable 'a' is not mutable
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun maybeInitialized() {
        val input = """
            fun foo(x:Int) 
                val a : Int
                if x=1
                    a = 2
                val b = a + 1   # error - 'a' may be uninitialized
                a = 3           # error - 'a' may already be initialized
        """.trimIndent()

        val expected = """
            input.fpl:5.13: 'a' is possibly uninitialized
            input.fpl:6.5: Variable 'a' is not mutable
        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun nullValues() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun foo(a:Cat?) -> Int
                if a=null
                    return 0        
                else
                    return a.age        # here 'a' will be of type Cat
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
                Assign
                  Member name (String)
                    Variable this Cat
                  Variable name String
                Assign
                  Member age (Int)
                    Variable this Cat
                  Variable age Int
              Function foo
                If
                  IfClause
                    Compare EQI Bool
                      Variable a Cat?
                      IntLit 0 Null
                    Return
                      IntLit 0 Int
                  IfClause
                    Return
                      Member age (Int)
                        Variable a Cat

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun nullValues2() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun foo(a:Cat?) -> Int
                if a=null
                    return 0        
                return a.age        # here 'a' will be of type Cat as unreachable if a=null
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
                Assign
                  Member name (String)
                    Variable this Cat
                  Variable name String
                Assign
                  Member age (Int)
                    Variable this Cat
                  Variable age Int
              Function foo
                If
                  IfClause
                    Compare EQI Bool
                      Variable a Cat?
                      IntLit 0 Null
                    Return
                      IntLit 0 Int
                Return
                  Member age (Int)
                    Variable a Cat

        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun nullValues3() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun foo(a:Cat?) -> Int
                if a=null
                    return 0        
                else
                    return a.age        # here 'a' will be of type Cat
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
                Assign
                  Member name (String)
                    Variable this Cat
                  Variable name String
                Assign
                  Member age (Int)
                    Variable this Cat
                  Variable age Int
              Function foo
                If
                  IfClause
                    Compare EQI Bool
                      Variable a Cat?
                      IntLit 0 Null
                    Return
                      IntLit 0 Int
                  IfClause
                    Return
                      Member age (Int)
                        Variable a Cat

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun andExpression() {
        val input = """
            class Cat(val name:String, val age:Int)
            
            fun foo(a:Cat?) -> Int
                if a!=null and a.age>4
                    return a.age
                return 0
        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
                Assign
                  Member name (String)
                    Variable this Cat
                  Variable name String
                Assign
                  Member age (Int)
                    Variable this Cat
                  Variable age Int
              Function foo
                If
                  IfClause
                    And Bool
                      Compare NEI Bool
                        Variable a Cat?
                        IntLit 0 Null
                      Binop GTI Bool
                        Member age (Int)
                          Variable a Cat
                        IntLit 4 Int
                    Return
                      Member age (Int)
                        Variable a Cat
                Return
                  IntLit 0 Int
            
        """.trimIndent()
        runTest(input, expected)
    }

}