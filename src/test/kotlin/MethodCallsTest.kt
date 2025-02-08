package falcon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader


class MethodCallsTest {
    val stdLibFiles = listOf("memory.fpl", "print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String, expected: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, ret)
    }

    @Test
    fun methodCalls() {
        val input = """
            class Cat(val name:String, val age:Int)
                fun greet()
                    printf("%s says meow\n", name)
            
            fun main()
                var c = new Cat("Fluffy", 3)
                c.greet()
        """.trimIndent()

        val expected = """
            Fluffy says meow

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun multipleMethods() {
        val input = """
            class Cat(val name:String, var age:Int)
                fun greet()
                    printf("%s says meow\n", name)
    
                fun birthday()
                    age = age + 1
                    printf("%s is now %d years old\n", name, age)
            
            fun main()
                var c = new Cat("Whiskers", 2)
                c.greet()
                c.birthday()
                c.birthday()
        """.trimIndent()

        val expected = """
            Whiskers says meow
            Whiskers is now 3 years old
            Whiskers is now 4 years old

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun methodReturnValue() {
        val input = """
            class Cat(val name:String, val age:Int)
                fun getAge() -> Int
                    return age
                
            fun main()
                var c = new Cat("Mittens", 5)
                printf("%s is %d years old\n", c.name, c.getAge())

        """.trimIndent()

        val expected = """
            Mittens is 5 years old

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun methodCallsMethodTest() {
        val input = """
            class Dog(val name:String)
                fun speak()
                    printf("%s barks!\n", name)
                
                fun greet()
                    printf("%s wags tail.\n", name)
                    speak()

            fun main()
                var d = new Dog("Rex")
                d.greet()

        """.trimIndent()

        val expected = """
            Rex wags tail.
            Rex barks!

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun multipleObjectsTest() {
        val input = """
            class Animal(val name:String)
                fun speak()
                    printf("%s makes a sound\n", name)

            fun main()
                var a = new Animal("Tiger")
                var b = new Animal("Elephant")
                a.speak()
                b.speak()
        """.trimIndent()

        val expected = """
            Tiger makes a sound
            Elephant makes a sound

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun useThisExplititlyTest() {
        val input = """
            class Person(val name:String)
                fun introduce()
                    printf("Hi, I am %s\n", this.name)

            fun main()
                var p = new Person("Alice")
                p.introduce()

        """.trimIndent()

        val expected = """
            Hi, I am Alice

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun classReferences() {
        val input = """
            class Engine(val horsepower:Int)
                fun start()
                    printf("Engine with %d HP started.\n", horsepower)

            class Car(val model:String, val engine:Engine)
                fun start()
                    printf("%s is starting...\n", model)
                    engine.start()

            fun main()
                var e = new Engine(300)
                var car = new Car("SuperCar", e)
                car.start()

        """.trimIndent()

        val expected = """
            SuperCar is starting...
            Engine with 300 HP started.

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun methodWithParametersTest() {
        val input = """
            class Calculator()
                fun add(x: Int, y: Int) -> Int
                    return x + y

            fun main()
                var calc = new Calculator()
                printf("Sum: %d\n", calc.add(5, 7))

        """.trimIndent()

        val expected = """
            Sum: 12

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun recursiveMethodCallsTest() {
        val input = """
            class Counter(val max:Int)
                var count = 0

                fun increment()
                    if count < max
                        count = count + 1
                        printf("Count: %d\n", count)
                        increment()

            fun main()
                var c = new Counter(3)
                c.increment()

        """.trimIndent()

        val expected = """
            Count: 1
            Count: 2
            Count: 3

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun methodReturnsObjectTest() {
        val input = """
            class Number(val value:Int)
                fun double() -> Number
                    return new Number(value * 2)

            fun main()
                var n = new Number(5)
                var m = n.double().double()
                printf("Result: %d\n", m.value)

        """.trimIndent()

        val expected = """
            Result: 20

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun nullPointerTest() {
        val input = """
            class Printer()
                fun printMessage()
                    printf("Hello from printer\n")

            fun main()
                var p : Printer? = null
                p.printMessage()  # Should trigger an error

        """.trimIndent()

        val expected = """
            input.fpl:7.6: Value may be null
        """.trimIndent()
        runTest(input, expected)
    }


    // @Test
    fun emptyTest() {
        val input = """
        """.trimIndent()

        val expected = """
        """.trimIndent()
        runTest(input, expected)
    }


}