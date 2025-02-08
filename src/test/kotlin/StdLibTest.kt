package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class StdLibTest {
    val stdLibFiles = listOf("memory.fpl", "print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String, expected: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
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
            00001020       30 Array(10)
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
}
