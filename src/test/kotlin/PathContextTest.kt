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
                      Compare GTI Bool
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

    @Test
    fun whileStatement() {
        val input = """
            class LinkedList(val next:LinkedList?, val data:Int)
            
            fun sum(list:LinkedList?) -> Int
                var total = 0
                var p = list
                while p!=null
                    total = total + p.data
                    p = p.next
                return total
            """.trimIndent()

        val expected = """
            TopLevel
              Class LinkedList
                Assign
                  Member next (LinkedList?)
                    Variable this LinkedList
                  Variable next LinkedList?
                Assign
                  Member data (Int)
                    Variable this LinkedList
                  Variable data Int
              Function sum
                Declare total Int
                  IntLit 0 Int
                Declare p LinkedList?
                  Variable list LinkedList?
                While
                  Compare NEI Bool
                    Variable p LinkedList?
                    IntLit 0 Null
                  Assign
                    Variable total Int
                    Binop ADDI Int
                      Variable total Int
                      Member data (Int)
                        Variable p LinkedList
                  Assign
                    Variable p LinkedList?
                    Member next (LinkedList?)
                      Variable p LinkedList
                Return
                  Variable total Int

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun unrachableCode1() {
        val input = """           
            fun sum() -> Int
                var total = 0
                return 3
                val a = 3
            """.trimIndent()

        val expected = """
            input.fpl:4.5: Unreachable code
        """.trimIndent()
        runTest(input, expected)
    }


    @Test
    fun notReturnOnAllPaths() {
        val input = """           
            fun sum(a:Int) -> Int
                if a>4
                    return 1
            """.trimIndent()

        val expected = """
            input.fpl:1.5: Function does not return a value along all paths
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun refinedFieldTypes() {
        val prog = """
            class Cat(val name:String?, val age:Int) 

            fun test(a:Cat) -> String
                if a.name!=null
                    return a.name       # allowed as name is proven to be not null
                else
                    return "unknown"

        """.trimIndent()

        val expected = """
            TopLevel
              Class Cat
                Assign
                  Member name (String?)
                    Variable this Cat
                  Variable name String?
                Assign
                  Member age (Int)
                    Variable this Cat
                  Variable age Int
              Function test
                If
                  IfClause
                    Compare NEI Bool
                      Member name (String?)
                        Variable a Cat
                      IntLit 0 Null
                    Return
                      Member name (String)
                        Variable a Cat
                  IfClause
                    Return
                      StringLit "unknown" String

        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun refinedFieldTypes2() {
        val prog = """
            class Cat(var name:String?, val age:Int) 

            fun test(a:Cat) -> String
                if a.name!=null      # Cannot smartcast to String here as name is mutable
                    return a.name
                else
                    return "unknown"

        """.trimIndent()

        val expected = """
            input.fpl:5.17: Type mismatch. Expected String, but found String?
        """.trimIndent()
        runTest(prog, expected)
    }

    @Test
    fun refinedFieldTypes3() {
        val prog = """
            class Address(val street: String?)
            class Person(val home: Address?)
            
            fun getStreet(p: Person) -> String
                if p.home != null and p.home.street != null
                    return p.home.street  # Should be allowed
                else
                    return "Unknown"
        """.trimIndent()

        val expected = """
            TopLevel
              Class Address
                Assign
                  Member street (String?)
                    Variable this Address
                  Variable street String?
              Class Person
                Assign
                  Member home (Address?)
                    Variable this Person
                  Variable home Address?
              Function getStreet
                If
                  IfClause
                    And Bool
                      Compare NEI Bool
                        Member home (Address?)
                          Variable p Person
                        IntLit 0 Null
                      Compare NEI Bool
                        Member street (String?)
                          Member home (Address)
                            Variable p Person
                        IntLit 0 Null
                    Return
                      Member street (String)
                        Member home (Address)
                          Variable p Person
                  IfClause
                    Return
                      StringLit "Unknown" String

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun refinedFieldTypes4() {
        val prog = """
            class Address(var street: String?)
            class Person(val home: Address?)
            
            fun getStreet(p: Person) -> String
                if p.home != null and p.home.street != null
                    return p.home.street  # Should be allowed
                else
                    return "Unknown"
        """.trimIndent()

        val expected = """
            input.fpl:6.22: Type mismatch. Expected String, but found String?
        """.trimIndent()
        runTest(prog, expected)
    }
}