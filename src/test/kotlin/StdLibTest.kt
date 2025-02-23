package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StdLibTest {
    val stdLibFiles = listOf("hwregs.fpl", "memory.fpl", "string.fpl", "print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String, expected: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE,  listOf("src/stdlib/start.f32"))
        assertEquals(expected, ret)
    }

    @Test
    fun outputChar() {
        val input = """            
            
            fun main()
                printString("Hello, world!\n",0,0)
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
                printHex(0x1234ABCD,0,0)
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
                printf("Hex: %x\n", 305441741)
                printf("Hex: %08x\n", 0x1234)
                printf("Hex: %8x\n", 0x1234)
                printf("Hex: %-8x\n", 0x5678)
                printf("String: %s\n", "Hello")
                printf("Char: %c\n", 'A')
                printf("Integer: %d %d\n", 12345, -67890)
                printf("Padded zero: %05d\n", 123)
                printf("Padded left: %5d\n", 123)
                printf("Padded rght: %-5d\n", 123)
        """.trimIndent()

        val expected = """
            Hex: 1234ABCD
            Hex: 00001234
            Hex:     1234
            Hex: 5678    
            String: Hello
            Char: A
            Integer: 12345 -67890
            Padded zero: 00123
            Padded left:   123
            Padded rght: 123  

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun mallocTest() {
        // Test of calling malloc directly rather than using new/delete
        val prog="""
            fun main()
                # Initialize the heap.
                initializeMemory()

                # Dump the initial heap state.
                printf("Initial Heap:\n")
                dumpMemory()

                # Create a dummy ClassDescriptor for testing object allocation.
                # (Assume that we can allocate a ClassDescriptor on the stack for testing.)
                val  testClass = local ClassDescriptor()
                testClass.name = "TestObject"
                testClass.size = 16

                # Allocate an object using mallocObject.
                val objPtr = mallocObject(testClass)
                if objPtr = 0
                    printf("Failed to allocate TestObject\n")
                else
                    printf("Allocated TestObject at %x\n", objPtr)

                # Allocate an array of 10 integers.
                val arrayPtr = mallocArray(10, 4)   # Each int is 4 bytes.
                if arrayPtr = 0
                    printf("Failed to allocate array of ints\n")
                else
                    printf("Allocated int array at %x\n", arrayPtr)

                # Dump the heap after allocations.
                printf("Heap after allocations:\n")
                dumpMemory()

                # Free the allocated object and array.
                free(objPtr)
                free(arrayPtr)

                # Dump the heap after freeing to verify that the chunks are marked free.
                printf("Heap after frees:\n")
                dumpMemory()
        """.trimIndent()

        val expected = """
            Initial Heap:
            00001000  3F7F000 FREE

            Allocated TestObject at 1008
            Allocated int array at 1028
            Heap after allocations:
            00001000       20 TestObject
            00001020       30 Array(10,4)
            00001050  3F7EFB0 FREE

            Heap after frees:
            00001000       20 FREE
            00001020       30 FREE
            00001050  3F7EFB0 FREE


        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun newTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
                
            fun main()
                printf("Initial memory dump\n")
                dumpMemory()
                
                val c = new Cat("Fluffy",4)
                printf("c = %x\n", c)
                printf("Cat: %s %d\n", c.name, c.age)
                
                val d = new Array<Int>(40)
                printf("d = %x\n", d)
                
                printf("\nFinal memory dump\n")
                dumpMemory()
        """.trimIndent()

        val expected = """
            Initial memory dump
            00001000  3F7F000 FREE

            c = 1008
            Cat: Fluffy 4
            d = 1018

            Final memory dump
            00001000       10 Cat
            00001010       B0 Array(40,4)
            000010C0  3F7EF40 FREE

            
        """.trimIndent()

        runTest(prog,expected)
    }


    @Test
    fun forLoopArrayTest() {
        val prog = """
            fun main()
                # Allocate an array of 5 integers.
                var arr = new Array<Int>(5)
                
                # Initialize the array elements.
                arr[0] = 10
                arr[1] = 20
                arr[2] = 30
                arr[3] = 40
                arr[4] = 50

                # Use the for-each loop to iterate over the array.
                for x in arr
                    printf("%d ", x)
                
                # Print a newline after the loop.
                printf("\n")

        """.trimIndent()

        val expected = """
            10 20 30 40 50 

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun newStringTest() {
        val prog = """
            fun main()
                # Concatenate several strings together.
                var s = newString("Hello", ", ", "world", "!")
                
                # Print the resulting concatenated string.
                printf("Concatenated string: %s\n", s)

        """.trimIndent()

        val expected = """
            Concatenated string: Hello, world!

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun strcmpTest() {
        val prog = """
            fun main()
                val s1 = "apple"
                val s2 = "apple"
                val s3 = "apples"
                val s4 = "banana"
                val s5 = "applet"
                val s6 = "appld"
            
                printf("%d\n", strcmp(s1, s2))  # Expected: 0 (equal)
                printf("%d\n", strcmp(s1, s3))  # Expected: Negative (s1 < s3)
                printf("%d\n", strcmp(s3, s1))  # Expected: Positive (s3 > s1)
                printf("%d\n", strcmp(s1, s4))  # Expected: Negative (s1 < s4)
                printf("%d\n", strcmp(s4, s1))  # Expected: Positive (s4 > s1)
                printf("%d\n", strcmp(s1, s5))  # Expected: Negative (s1 < s5, 'e' < 't')
                printf("%d\n", strcmp(s1, s6))  # Expected: Positive (s1 > s6, 'e' > 'd')
        """.trimIndent()

        val expected = """
            0
            -1
            1
            -1
            1
            -1
            1

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun strequalsTest() {
        val prog = """
            fun main() 
                printf("%d\n", strequals("hello", "hello") )
                printf("%d\n", strequals("hello", "world") )
                printf("%d\n", strequals("hello", "hell") )
                printf("%d\n", strequals("abc", "abcd") )
                printf("%d\n", strequals("", "") )
                printf("%d\n", strequals("a", "a") )
                printf("%d\n", strequals("a", "b") )
                printf("%d\n", strequals("abcdef", "abcdef") )
                printf("%d\n", strequals("abcdef", "abcdeg") )
        """.trimIndent()

        val expected = """
            1
            0
            0
            0
            1
            1
            0
            1
            0
     
        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun stringComparisonsTest() {
        val prog = """
            fun main()
                val a = "apple"
                val b = "banana"
                val c = "apple"

                # Equality and inequality checks
                if (a = c)
                    printf("a == c\n")  # Should print
                if (a != b) 
                    printf("a != b\n")  # Should print
                if (a != c) 
                    printf("a != c\n")  # Should NOT print

                # Lexicographic comparisons
                if (a < b)
                    printf("a < b\n")  # Should print
                if (b > a)
                    printf("b > a\n")  # Should print
                if (a <= c) 
                    printf("a <= c\n")  # Should print
                if (b >= a) 
                    printf("b >= a\n")  # Should print
                if (a > b)
                    printf("a > b\n")  # Should NOT print
                if (b < a)
                    printf("b < a\n")  # Should NOT print
        """.trimIndent()

        val expected = """
            a == c
            a != b
            a < b
            b > a
            a <= c
            b >= a

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun whenStatementTest() {
        val prog = """
            fun main()
                for i in 0 to 5
                    when i
                        0 -> 
                            printf("zero\n")
                        1 -> 
                            printf("one\n")
                        2 -> 
                            printf("two\n")
                        else -> 
                            printf("lots\n")
        """.trimIndent()

        val expected = """
            zero
            one
            two
            lots
            lots
            lots

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun whenStatementStringTest() {
        val prog = """
            fun handleFruit(fruit: String)
                when fruit
                    "apple" -> printf("It's an apple!\n")
                    "banana" -> printf("It's a banana!\n")
                    "cherry" -> printf("It's a cherry!\n")
                    else -> printf("Unknown fruit\n")
            
            fun main()
                handleFruit("apple")
                handleFruit("banana")
                handleFruit("cherry")
                handleFruit("grape")
        """.trimIndent()

        val expected = """
            It's an apple!
            It's a banana!
            It's a cherry!
            Unknown fruit
            
        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun breakStatementTest() {
        val prog = """
            fun main()
                var i = 0
                
                while (i < 10)
                    i = i + 1

                    if (i = 3)
                        printf("Skipping 3\n")
                        continue  # Skips the rest of the loop body for i = 3

                    if (i = 7)
                        printf("Breaking at 7\n")
                        break  # Exits the loop when i = 7

                    printf("i: %d\n", i)

                printf("Loop finished. Final i: %d\n", i)
        """.trimIndent()

        val expected = """
            i: 1
            i: 2
            Skipping 3
            i: 4
            i: 5
            i: 6
            Breaking at 7
            Loop finished. Final i: 7

        """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun compoundAssignTest() {
        val prog = """
            var globalY = 0
            fun foo() -> Int
                globalY += 1
                return globalY

            fun main()
                var x = 10
                x += 5
                printf("x after += 5: %d\n", x)  # Should print 15
            
                x -= 3
                printf("x after -= 3: %d\n", x)  # Should print 12
            
                # Testing with an array
                val arr = new Array<Int>(3)
                arr[0] = 5
                arr[1] = 10
                arr[2] = 15
            
                arr[1] += 7
                printf("arr[1] after += 7: %d\n", arr[1])  # Should print 17
            
                # Testing with function calls (should evaluate only once)
                arr[foo()] += 1   # Should add 1 to arr[1]
                printf("arr[1] after foo() += 1: %d\n", arr[1])  # Should print 18
        """.trimIndent()

        val expected = """
            x after += 5: 15
            x after -= 3: 12
            arr[1] after += 7: 17
            arr[1] after foo() += 1: 18

            """.trimIndent()

        runTest(prog,expected)
    }

    @Test
    fun constTest() {
        val prog = """
            const MILLION = 1000000
            const GREETING = "Hello, world!"
            
            fun main()
                printf("MILLION: %d\n", MILLION) 
                printf("%s\n", GREETING)
            
                const X = 42
                const Y = X + 8
                printf("Y: %d\n", Y)  # Should print "Y: 50"
        """.trimIndent()

        val expected = """
            MILLION: 1000000
            Hello, world!
            Y: 50

        """.trimIndent()

        runTest(prog, expected)
    }

    @Test
    fun notTest() {
        val prog = """
            class Cat(val name:String, val age:Int)
            
            fun foo(x:Cat?)
                if not(x=null)
                    printf("Cat name: %s\n", x.name)
                else
                    printf("Cat is null\n")
            
            fun main()
                val c = local Cat("Whiskers", 3)
                foo(c)
                foo(null)
        """.trimIndent()

        val expected = """
            Cat name: Whiskers
            Cat is null
            
        """.trimIndent()

        runTest(prog,expected)
    }


    @Test
    fun externFuncTest() {
        val prog = """
            extern fun add(a:Int, b:Int) -> Int
            
            fun main()
                var result = add(5, 3)
                printf("Result: %d\n", result)
        """.trimIndent()

        val expected = """
            ERROR: Line 784: Undefined label '/add'

        """.trimIndent().replace("\n", "\r\n")

        runTest(prog,expected)
    }

}

