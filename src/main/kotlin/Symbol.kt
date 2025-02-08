package falcon

sealed class Symbol(val location: Location, val name:String, val type: Type) {
    override fun toString(): String = name
}

class ConstantSymbol(location: Location, name: String, type: Type, val value: Int) : Symbol(location, name, type)
class VarSymbol(location: Location, name: String, type: Type, val mutable:Boolean) : Symbol(location, name, type)
class GlobalVarSymbol(location: Location, name: String, type: Type, val mutable:Boolean, val offset:Int) : Symbol(location, name, type)
class FunctionSymbol(location: Location, name: String, type: FunctionType, val function: Function) : Symbol(location, name, type)
class TypeSymbol(location: Location, name: String, type: Type) : Symbol(location, name, type)
object UndefinedSymbol : Symbol(Location.nullLocation, "<undefined>", UndefinedType)


class FieldSymbol(location: Location, name: String, type: Type, val mutable: Boolean) : Symbol(location, name, type) {
    var offset = 0
}

val lengthSymbol = FieldSymbol(Location.nullLocation, "length", IntType, false).also { it.offset = -4 }


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
