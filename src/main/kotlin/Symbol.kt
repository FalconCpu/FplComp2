package falcon

sealed class Symbol(val location: Location, val name:String, val type: Type, val mutable:Boolean) {
    override fun toString(): String = name
}

class ConstantSymbol(location: Location, name: String, type: Type, val value: Any)
    : Symbol(location, name, type, false)
class VarSymbol(location: Location, name: String, type: Type, mutable:Boolean)
    : Symbol(location, name, type, mutable)
class GlobalVarSymbol(location: Location, name: String, type: Type, mutable:Boolean, val offset:Int)
    : Symbol(location, name, type, mutable)
class FunctionSymbol(location: Location, name: String, type: FunctionType, val function: Function)
    : Symbol(location, name, type, false)
class TypeSymbol(location: Location, name: String, type: Type)
    : Symbol(location, name, type, false)
object UndefinedSymbol
    : Symbol(Location.nullLocation, "<undefined>", UndefinedType, false)


class FieldSymbol(location: Location, name: String, type: Type, mutable: Boolean)
    : Symbol(location, name, type, mutable) {
        var offset: Int = -1 // offset from the start of the object. -1 means not yet assigned
}

val lengthSymbol = FieldSymbol(Location.nullLocation, "length", IntType, false).also { it.offset = -4 }

// dummy symbol to represent a chain of field accesses for the purpose of type checking
class FieldAccessSymbol(location: Location, val lhs:Symbol, val rhs: FieldSymbol, type: Type, mutable: Boolean)
    : Symbol(location, "$lhs.$rhs", type, mutable)

// ----------------------------------------------------------------------------
//                       Symbol Tables
// ----------------------------------------------------------------------------

class SymbolTable(private val parent: SymbolTable? = null) {
    private val symbols = mutableMapOf<String, Symbol>()

    fun add(symbol: Symbol) {
        val duplicate = symbols[symbol.name]
        if (duplicate != null)
            Log.error(symbol.location, "duplicate symbol name '$symbol'. First defined at ${duplicate.location}")
        symbols[symbol.name] = symbol
    }

    fun lookup(name: String): Symbol? {
        return symbols[name] ?: parent?.lookup(name)
    }
}

// ----------------------------------------------------------------------------
//                       Predefined Symbols
// ----------------------------------------------------------------------------
// These are symbols that are predefined in the language.

val predefinedSymbols = definePredefinedSymbols()

private fun definePredefinedSymbols(): SymbolTable {
    val symbolTable = SymbolTable()
    symbolTable.add(TypeSymbol(Location.nullLocation, "Any", AnyType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "Int", IntType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "Real", RealType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "String", StringType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "Char", CharType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "Bool", BoolType))
    symbolTable.add(TypeSymbol(Location.nullLocation, "Unit", UnitType))
    symbolTable.add(ConstantSymbol(Location.nullLocation, "true", BoolType, 1))
    symbolTable.add(ConstantSymbol(Location.nullLocation, "false", BoolType, 0))
    symbolTable.add(ConstantSymbol(Location.nullLocation, "null", NullType, 0))
    return symbolTable
}
