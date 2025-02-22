package falcon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileReader
import java.io.StringReader

class OsTests   {
    val stdLibFiles = listOf("hwregs.fpl","memory.fpl", "string.fpl", "print.fpl", "graphics.fpl")
    val stdLibLexers = stdLibFiles.map { Lexer("stdlib/$it", FileReader("src/stdlib/$it")) }

    private fun runTest(input: String) {
        val lexers = stdLibLexers + listOf(Lexer("input.fpl", StringReader(input)))
        val ret = compile(lexers, StopAt.EXECUTE)
        assertEquals("", Log.getErrors())
    }

    @Test
    fun basicGraphics() {
        val prog = """
            fun main()
                val gfx = new GraphicsContext()
                gfx.fillRect(0, 0, 640, 480, 2)     # DARK GREEN

                for x in 0 to gfx.screenWidth/10
                    gfx.drawLine(x*10, 0, x*10, (gfx.screenHeight:Int), 0xff) # WHITE

                for y in 0 to gfx.screenHeight/10
                    gfx.drawLine(0, y*10, (gfx.screenWidth:Int), y*10, 0xFF)
                
                # Step 3: Draw text
                gfx.drawString(10, 10, "Hello, FPGA!", 0x09)    # BLUE
                
                # Step 4: Draw a filled rectangle (yellow)
                gfx.fillRect(100, 100, 200, 150, 0x0B)
                
                # Step 5: Draw some individual pixels (cyan)
                gfx.drawPixel(51, 51, 0x0B)
                gfx.drawPixel(51, 50, 0x0B)
                gfx.drawPixel(50, 50, 0x0B)
                gfx.drawPixel(50, 51, 0x0B)
                
                # Step 6: Scroll up and down
                for i in 1 to 10
                    gfx.scrollVertical(1)
                for i in 1 to 10
                    gfx.scrollVertical(-1)
                
                # Step 7: Set transparency color (to white)
                gfx.setTransparencyColor(0xFFFF)
                
        """.trimIndent()

        runTest(prog)

    }
}