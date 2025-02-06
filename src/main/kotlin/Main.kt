package falcon

import java.io.FileWriter

fun main() {
    TODO("Command line arguments not yet implemented")
}

enum class StopAt {
    PARSE,
    TYPE_CHECK,
    IRGEN,
    OPTIMIZE,
    ASSEMBLY,
    EXECUTE
}

fun runAssembler(filename:String) {
    val process = ProcessBuilder("f32asm.exe", filename)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error(process.inputStream.bufferedReader().readText())
}

fun runProgram() : String {
    val process = ProcessBuilder("f32sim", "asm.hex")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0)
        Log.error("Execution failed with exit code $exitCode")
    return process.inputStream.bufferedReader().readText().replace("\r\n", "\n")
}

fun compile(files:List<Lexer>, stopAt:StopAt) : String {
    Log.clear()
    allFunctions.clear()
    DataSegment.clear()

    val ast = AstTopLevel()
    for (lexer in files) {
        Parser(lexer).parseTopLevel(ast)
    }

    if (Log.hasErrors())
        return Log.getErrors()
    if (stopAt == StopAt.PARSE)
        return ast.dump()

    val tast = ast.typeCheck()
    if (Log.hasErrors())
        return Log.getErrors()
    if (stopAt == StopAt.TYPE_CHECK)
        return tast.dump()

    val funcs = tast.codeGen()
    if (Log.hasErrors())
        return Log.getErrors()
    if (stopAt == StopAt.IRGEN)
        return funcs.dump()

    for(func in funcs)
        Backend(func).run()
    if (Log.hasErrors())
        return Log.getErrors()
    if (stopAt == StopAt.OPTIMIZE)
        return funcs.dump()

    val asm = StringBuilder()
    genAssemblyHeader(asm)
    for(func in funcs)
        func.genAssembly(asm)
    DataSegment.output(asm)
    if (Log.hasErrors())
        return Log.getErrors()
    if (stopAt == StopAt.ASSEMBLY)
        return asm.toString()

    val asmFile = FileWriter("asm.f32")
    asmFile.write(asm.toString())
    asmFile.close()
    runAssembler("asm.f32")
    if (Log.hasErrors())
        return Log.getErrors()

    val output = runProgram()
    if (Log.hasErrors())
        return Log.getErrors()
    return output
}