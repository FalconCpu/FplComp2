package falcon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsTests   {
    val stdLibFiles = listOf("hwregs.fpl", "string.fpl", "print.fpl")
    val osLibFiles = listOf("boot.fpl", "sysvars.fpl", "memory.fpl", "tasks.fpl", "graphics.fpl", "keyboard.fpl", "window.fpl")

    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }
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
                initializeRunQueue(task1)
                printf("Task1 = %x\n", task1)
                val task2 = newTask((test2:Int), "task2")
                addTaskToRunQueue(task2)
                printf("Task2 = %x\n", task2)
                Scheduler()
                
            fun test1()
                val task2 = findTask("task2")
                if task2 = null
                    printf("Task2 not found\n")
                    abort(99)
                    return
                printf("Task2 = %x\n", task2)
                for i in 0 to 10
                    if i%3 = 0
                        val message = new Message(i)
                        sendMessage(task2, message)
                    yield()
                while true
                    val dummy = 1

            fun test2()
                while true
                    val message = getMessage()
                    printf("Received message: %d\n", message.data)

        """.trimIndent()

        runTest(prog)
    }

    @Test
    fun keyboard() {
        val prog = """
            fun main()
                val keyboard = new Keyboard()
                repeat
                    val key = keyboard.getKey()
                    if key != 0
                        printf("%c",key)
                until false

        """.trimIndent()

        runTest(prog)
    }

    @Test
    fun windows() {
        val prog = """
            fun main()
                val mngr = new WindowManager()
                val win1 = new Window(100, 100, 200, 200, "Window 1")
                val win2 = new Window(300, 300, 200, 200, "Window 2")
                mngr.addWindow(win1)
                mngr.addWindow(win2)
                mngr.draw()

                while true
                    val dummy = 1
        """.trimIndent()

        runTest(prog)
    }




}