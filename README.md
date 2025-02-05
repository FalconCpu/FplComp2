This project is my latest attempt to write a compiler for my fpl programming language, 
to target my F32 CPU.

# Language overview

The fpl language is a statically typed language with a Kotlin like syntax, but 
C like semantics. My goal is to use this language to write the operating system
for my Falcon computer.

So my goal with the fpl language is to try to create a modern 'C' - try to incorporate
modern features like type inference, generics, and other modern language features into 
a language that can run on bare metal (so no garbage collection etc).

I've also borrowed the indentation based syntax from Python. But with optional
`end` statements to close blocks - the idea being this can avoid the cliff edge effect
seen in Python code where many levels of indentation all end at once making it hard to 
see what is going on.

# Compiler overview

The compiler is written in Kotlin. It follows the fairly standard compiler structure

The `Lexer` class is responsible for converting the source code into `Token` objects.
Each `Token` has a `Location`, `kind` and `text` fields. 

The `Parser` class is responsible for converting the `Token` objects into a 
Abstract Syntax Tree (AST). The AST is described in the `ast.kt` file.

The `TypeChecker` class is responsible for checking the type of each expression in the AST, 
and converting the AST into a TypedAST - described in the "TAST.kt" file.

The `CodeGenerator` class then converts the TAST into  a list of `Function` objects, each of
which contains a list of `Instr` objects. This intermediate representation is close
to assembly language, but without restrictions on number of registers etc.

After the code generator has finished, the `Peephole` class performs some simple optimizations
such as constant propagation and dead code elimination.

Then the `RegisterAllocator` class assigns registers to variables and instructions.

Finally the `GenAssembly` class generates the assembly code for the program.






