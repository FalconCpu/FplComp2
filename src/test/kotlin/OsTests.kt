package falcon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsTests   {
    val stdLibFiles = listOf("hwregs.fpl", "print.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }
    val osLibFiles = listOf("boot.fpl", "sysvars.fpl", "memory.fpl", "tasks.fpl", "graphics.fpl")
    val osLibLexers = osLibFiles.map { Lexer("falconOs/$it", FileReader("src/falconOs/$it")) }

    private fun runTest(input: String) {
        val lexers = stdLibLexers + osLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTABLE, listOf("src/falconOs/start.f32"))
        assertEquals("", Log.getErrors())
    }

    @Test
    fun basicOs() {
        val prog = """
            fun main()
                val task1 = newTask((test1:Int), "task1")    
                initializeRunQueue(task1)
                printf("Task1 = %x\n", task1)
                val task2 = newTask((test2:Int), "task2")
                addTaskToRunQueue(task2)
                printf("Task2 = %x\n", task2)
                val task3 = newTask((test3:Int), "task3")
                addTaskToRunQueue(task3)
                printf("Task3 = %x\n", task3)
                listTasks()
                Scheduler()
                
            fun test1()
                val hwregs = (0xE0000000:HwRegs)
                var i = 0
                hwregs.led = i

            fun test2()
                val hwregs = (0xE0000000:HwRegs)
                var i = 0
                while true
                    hwregs.sevenSeg = i
                    i += 1
                    yield()
                    
            fun test3()
                val gfx = new GraphicsContext()
                var color = 0
                while true
                    gfx.fillRect(20,20,200,200,color)
                    color += 1
                    yield()

        """.trimIndent()

        runTest(prog)
    }

    @Test
    fun messagesOs() {
        val prog = """
            fun main()
                val task1 = newTask((test1:Int), "task1")    
                addTaskToRunQueue(task1)
                printf("Task1 = %x\n", task1)
                val task2 = newTask((test2:Int), "task2")
                addTaskToRunQueue(task2)
                printf("Task2 = %x\n", task2)
                val task3 = newTask((test3:Int), "task3")
                addTaskToRunQueue(task3)
                printf("Task3 = %x\n", task3)
                listTasks()
                Scheduler()
                
            fun test1()
                while true
                    
                var i = 0
                hwregs.led = i

            fun test2()
                val hwregs = (0xE0000000:HwRegs)
                var i = 0
                while true
                    hwregs.sevenSeg = i
                    i += 1
                    yield()
                    
            fun test3()
                val gfx = new GraphicsContext()
                var color = 0
                while true
                    gfx.fillRect(20,20,200,200,color)
                    color += 1
                    yield()

        """.trimIndent()

        runTest(prog)
    }

}