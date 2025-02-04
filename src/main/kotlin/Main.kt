package falcon

fun main() {
    TODO("Command line arguments not yet implemented")
}

enum class StopAt {
    PARSE,
    TYPE_CHECK,
    IRGEN,
    EXECUTE_IR,
    COMPLETE
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

    if (stopAt == StopAt.EXECUTE_IR) {
        val main  = funcs.find { it.name == "main" }
        if (main == null) {
            Log.error("No main function found")
            return Log.getErrors()
        }
        Interpreter.output.clear()
        Interpreter(main,emptyList()).execute()
        return Interpreter.output.toString()
    }

    TODO("Code generation not yet implemented")
}