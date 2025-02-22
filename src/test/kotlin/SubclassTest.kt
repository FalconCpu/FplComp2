package falcon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class SubclassTest   {
    val stdLibFiles = listOf("hwregs.fpl","memory.fpl", "string.fpl", "print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String, expected: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
        assertEquals(expected, ret)
    }

    @Test
    fun basicSubclass() {
        val input = """
            class Animal
                val species: String
                fun speak() -> String
                    return "Some sound"

            class Dog : Animal
                fun bark() -> String
                    return "Woof!"

                fun makeSound(a: Animal) -> String
                    return a.speak()

            fun main()
                val d = new Dog()
                printf("%s\n", d.speak())
                
                val a: Animal = d               # Should be allowed
                printf("%s\n", a.speak())       # Should print "Some sound"
                
                
        """.trimIndent()

        val expected = """
            Some sound
            Some sound
            
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSuperclassArgs() {
        val input = """
            class Animal
                val species: String
                fun speak() -> String
                    return "Some sound"

            class Dog : Animal("dog")
                fun bark() -> String
                    return "Woof!"

                fun makeSound(a: Animal) -> String
                    return a.speak()

            fun main()
                val d = new Dog()
                printf("%s\n", d.speak())
                
                val a: Animal = d               # Should be allowed
                printf("%s\n", a.speak())       # Should print "Some sound"
                
                
        """.trimIndent()

        val expected = """
            input.fpl:6.7: Expected 0 arguments, got 1
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun superclassArgs() {
        val input = """
            class Animal (val name: String)
                fun speak()
                    printf("I am %s\n", name)

            class Dog (name:String, val age:Int) : Animal(name)
                fun bark()
                    printf("%s says Woof!\n", name)

            fun main()
                val d = new Dog("Fido", 3)
                d.speak()
                d.bark()
                
        """.trimIndent()

        val expected = """
            I am Fido
            Fido says Woof!

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun multiplLevelSubclass() {
        val input = """
            class Animal(val name: String)
                fun speak()
                    printf("I am %s\n", name)
            
            class Dog(name: String, val age: Int) : Animal(name)
                fun bark()
                    printf("%s says Woof!\n", name)
            
            class GuideDog(name: String, age: Int, val trained: Bool) : Dog(name, age)
                fun guide()
                    if trained
                        printf("%s is guiding their owner.\n", name)
                    else
                        printf("%s is not trained yet.\n", name)
            
            fun main()
                val a = new Animal("Generic Animal")
                val d = new Dog("Fido", 5)
                val g = new GuideDog("Buddy", 3, true)
            
                a.speak()   # Expected: "I am Generic Animal"
                d.speak()   # Expected: "I am Fido"
                d.bark()    # Expected: "Fido says Woof!"
                
                g.speak()   # Expected: "I am Buddy"
                g.bark()    # Expected: "Buddy says Woof!"
                g.guide()   # Expected: "Buddy is guiding their owner."
        """.trimIndent()

        val expected = """
            I am Generic Animal
            I am Fido
            Fido says Woof!
            I am Buddy
            Buddy says Woof!
            Buddy is guiding their owner.

        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSubclass1() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)

            fun main()
                val a: Animal = new Dog("Fido", 5)
                printf("Age: %d\n", a.age)  # ERROR: 'age' is not in Animal
        """.trimIndent()

        val expected = """
            input.fpl:6.26: Class 'Animal has no field named 'age'
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badSubclass2() {
        val input = """
            class Animal(val name: String)
                fun speak()
                    printf("I am %s\n", name)

            class Dog(name: String) : Animal(name)
                fun bark()
                    printf("%s says Woof!\n", name)

            fun main()
                val a: Animal = new Dog("Fido")
                a.bark()  # ERROR: 'Animal' has no method 'bark'
        """.trimIndent()

        val expected = """
            input.fpl:11.6: Class 'Animal has no field named 'bark'
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSubclass3() {
        val input = """
            fun NotAClass()
                printf("I am not a class.\n")

            class Dog(name: String) : NotAClass  # ERROR: Cannot extend a function
        """.trimIndent()

        val expected = """
            input.fpl:4.27: Undefined symbol NotAClass
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSubclass4() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, age: Int) : Animal()  # ERROR: Missing argument for Animal constructor
        """.trimIndent()
        val expected = """
            input.fpl:2.7: Expected 1 arguments, got 0
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSubclass5() {
        val input = """
            class A : B  # ERROR: Cycle detected
            class B : A
        """.trimIndent()
        val expected = """
            input.fpl:1.11: Undefined symbol B
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun badSubclass6() {
        val input = """
            class Animal()
            class Dog : Animal()
            class Cat : Animal()

            fun main()
                val d: Dog = new Cat()  # ERROR: Cannot assign Cat to Dog
        """.trimIndent()

        val expected = """
            input.fpl:6.18: Type mismatch: expected Dog but got Cat
        """.trimIndent()
        runTest(input, expected)
    }

    @Test
    fun virtualMethod() {
        val input = """
            class Animal()
                virtual fun speak()
                    printf("I am an animal\n")
            
            class Dog : Animal()
                override fun speak()
                    printf("Woof!\n")
            
            fun main()
                val a = new Dog()
                a.speak()  # Should print "Woof!", not "I am an animal"
        """.trimIndent()

        val expected = """
            Woof!
            
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun virtualMethod2() {
        val input = """
            class A()
                virtual fun say() 
                    printf("A\n")

            class B : A()
                override fun say()
                    printf("B\n")

            class C : B()
                override fun say()
                    printf("C\n")

            fun main()
                val x: A = new C()
                x.say()  # Should print "C", not "A" or "B"

        """.trimIndent()

        val expected = """
            C
            
        """.trimIndent()

        runTest(input, expected)
    }


    @Test
    fun virtualMethod3() {
        val input = """
            # virtual dispatch on field
            class Animal()
                virtual fun speak()
                    printf("Animal sound\n")

            class Dog : Animal()
                override fun speak()
                    printf("Bark!\n")

            fun callSpeak(a: Animal)
                a.speak()

            fun main()
                val d = new Dog()
                callSpeak(d)  # Should print "Bark!"

        """.trimIndent()

        val expected = """
            Bark!
            
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun virtualMethod4() {
        val input = """
            # Ensuring Methods That Aren’t Overridden Still Work
            class A()
                virtual fun f()
                    printf("Function f\n")

            class B : A()
                virtual fun g()
                    printf("Function g\n")

            fun main()
                val b: A = new B()
                b.f()  # Should print "Function f"

        """.trimIndent()

        val expected = """
            Function f
            
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badVirtualMethod1() {
        val input = """
            # Overriding a Nonexistent Method (Should Fail)
            class A()
                fun f()
                    printf("A.f()\n")

            class B : A()
                fun g()  # B is defining a new method, not overriding
                    printf("B.g()\n")

            fun main()
                val x: A = new B()
                x.g()  # Error: g() is not in A

        """.trimIndent()

        val expected = """
            input.fpl:12.6: Class 'A has no field named 'g'
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badVirtualMethod2() {
        val input = """
            # overriding a method with a different signature (should fail)
            class A()
                virtual fun f(x: Int)
                    printf("A.f(%d)\n", x)

            class B : A()
                override fun f()  # Wrong: signature does not match A.f()
                    printf("B.f()\n")

            fun main()
                val x: A = new B()
                x.f(10)  # Error: B.f() does not match A.f()

        """.trimIndent()

        val expected = """
            input.fpl:7.18: Function 'f' has a 0 parameters but the override function has 1
        """.trimIndent()

        runTest(input, expected)
    }
    @Test
    fun badVirtualMethod3() {
        val input = """
            # overriding a non-virtual method (should fail)
            class A()
                fun x()  # Error: x is not a function in A
                    printf("B.x()\n")

            class B : A()
                override fun x()  # Error: x is not a function in A
                    printf("B.x()\n")

            fun main()
                val b = new B()
                b.x()

        """.trimIndent()

        val expected = """
            input.fpl:7.18: Function 'x' is not virtual
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun badVirtualMethod4() {
        val input = """
            # Overriding a val with a fun (Should Fail)
            class A()
                val x: Int = 10

            class B : A()
                fun x()->Int  # Error: x is a value in A, not a function
                    return 20

            fun main()
                val b = new B()
                printf("%d\n", b.x)

        """.trimIndent()

        val expected = """
            input.fpl:3.9: duplicate symbol name 'x'. First defined at input.fpl:6.9
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun manyFields() {
        val input = """
            class A(val a1: Int) 
                val a2: Int = 20  # Field defined in class body

            class B(a1: Int, val b1: Int) : A(a1) 
                val b2: Int = 30  # Field defined in class body

            fun main()
                val obj = new B(10, 15)
                printf("A.a1 = %d\n", obj.a1)  # Should print 10 (from A constructor)
                printf("A.a2 = %d\n", obj.a2)  # Should print 20 (from A body)
                printf("B.b1 = %d\n", obj.b1)  # Should print 15 (from B constructor)
                printf("B.b2 = %d\n", obj.b2)  # Should print 30 (from B body)

        """.trimIndent()

        val expected = """
            A.a1 = 10
            A.a2 = 20
            B.b1 = 15
            B.b2 = 30

        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun isTest() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)
            
            fun main()
                val d : Animal = new Dog("Fido", 3)
                if d is Dog
                    printf("%s is a dog\n", d.name)
            """.trimIndent()

        val expected = """
            Fido is a dog

            """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun isAlwaysTrue() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)
            class Car(val make: String, val model: String)

            fun main()
                val a = new Animal("Generic Animal")
                val d = new Dog("Fido", 3)

                if a is Animal                            # redundant test
                    printf("%s is an animal\n", a.name)

                if d is Animal                            # redundant test  
                    printf("%s is an animal\n", d.name)

                if d is Dog                                # redundant test
                    printf("%s is a dog\n", d.name)

                if a is Dog                                 # not a redundant test
                    printf("This should never print\n")

                if a is Car                                 # Error: Car is not a subclass of Animal
                    printf("This should never print\n")

        """.trimIndent()

        val expected = """
            input.fpl:9.10: is expression is always true
            input.fpl:12.10: is expression is always true
            input.fpl:15.10: is expression is always true
            input.fpl:21.10: is expression is always false
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun isTest2() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)
            class Cat(name: String, val age: Int) : Animal(name)

            fun main()
                val dog : Animal = new Dog("Rex", 4)
                val cat : Animal = new Cat("Whiskers", 2)
                
                if dog is Dog
                    printf("%s is a dog\n", dog.name)
                
                if cat is Dog
                    printf("This should never print\n")

        """.trimIndent()

        val expected = """
            Rex is a dog
            
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun isTest3() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)
            class Cat(name: String, val age: Int) : Animal(name)

            fun main()
                val dog : Animal = new Dog("Rex", 4)

                if dog isnot Cat
                    printf("%s is not a Cat\n", dog.name)

        """.trimIndent()

        val expected = """
            Rex is not a Cat
            
        """.trimIndent()

        runTest(input, expected)
    }

    @Test
    fun istest4() {
        val input = """
            class Animal(val name: String)
            class Dog(name: String, val age: Int) : Animal(name)

            fun main()
                val dog: Animal? = new Dog("Rex", 4)

                if dog is Dog
                    printf("%s is a dog\n", dog.name)

                if dog is Animal
                    printf("%s is an animal\n", dog.name)

                val nullAnimal: Animal? = null

                if nullAnimal is Animal
                    printf("This should never print %s\n", nullAnimal.name)
                else
                    printf("nullAnimal is not an animal\n")

        """.trimIndent()

        val expected = """
            Rex is a dog
            Rex is an animal
            nullAnimal is not an animal

        """.trimIndent()

        runTest(input, expected)
    }
}
