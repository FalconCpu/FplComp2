package falcon

sealed class Type(private val name:String) {
    override fun toString() = name

    // Check to see if 'other' is compatible with this type.  (Very simplistic for now - will be extended later.)
    fun isAssignableFrom(other: Type): Boolean {
        if (this==other || other == ErrorType || this==ErrorType)
            return true
        return false
    }

    fun checkType(expression: TastExpression) {
        if (!isAssignableFrom(expression.type))
            Log.error(expression.location, "Type mismatch. Expected ${this}, but found ${expression.type}")
    }

    fun getSize() = when(this) {
        BoolType -> 1
        CharType -> 1
        ErrorType -> 1
        IntType -> 4
        RealType -> 4
        UndefinedType -> 0
        StringType -> 4
        UnitType -> 0
        is ArrayType -> 4
        is FunctionType -> 4
        is PointerType -> 4
        is ClassType -> classSize
    }

}

object UnitType      : Type("Unit")
object BoolType      : Type("Bool")
object CharType      : Type("Char")
object IntType       : Type("Int")
object RealType      : Type("Real")
object StringType    : Type("String")
object ErrorType     : Type("<Error>")
object UndefinedType : Type("<Undefined>")

// ----------------------------------------------------------------------------
//                       Error type
// ----------------------------------------------------------------------------

fun makeErrorType(location: Location, message: String): Type {
    Log.error(location, message)
    return ErrorType
}


// ----------------------------------------------------------------------------
//                       Array types
// ----------------------------------------------------------------------------

class  ArrayType(val elementType: Type) : Type("Array<$elementType>") {
    companion object {
        private val allArrayTypes = mutableMapOf<Type, ArrayType>()
        fun make(elementType: Type): ArrayType {
            return allArrayTypes.getOrPut(elementType) { ArrayType(elementType) }
        }
    }
}

// ----------------------------------------------------------------------------
//                       Struct Types
// ----------------------------------------------------------------------------

class  ClassType(name:String) : Type(name) {
    val fields = mutableListOf<FieldSymbol>()
    var classSize = 0
    lateinit var constructor : Function

    fun add(field: FieldSymbol) {
        field.offset = classSize
        classSize += field.type.getSize()
        fields += field
    }

    companion object {
        private val allStructTypes = mutableListOf<ClassType>()
        fun make(name: String): ClassType {
            val new = ClassType(name)
            allStructTypes.add(new)
            return new
        }
    }
}

// ----------------------------------------------------------------------------
//                       Pointer Types
// ----------------------------------------------------------------------------

class  PointerType(val elementType: Type) : Type("$elementType*") {
    companion object {
        private val allPointerTypes = mutableMapOf<Type, PointerType>()
        fun make(elementType: Type): Type {
            if (elementType== ErrorType)
                return ErrorType
            return allPointerTypes.getOrPut(elementType) { PointerType(elementType) }
        }
    }
}

// ----------------------------------------------------------------------------
//                       Function Types
// ----------------------------------------------------------------------------

class FunctionType(val parameterTypes: List<Type>, val returnType: Type)
: Type("(${parameterTypes.joinToString(",")})->$returnType") {
    companion object {
        private val allFunctionTypes = mutableListOf<FunctionType>()
        fun make(parameterTypes: List<Type>, returnType: Type): FunctionType {
            return allFunctionTypes.find{it.parameterTypes==parameterTypes && it.returnType==returnType} ?:
                FunctionType(parameterTypes, returnType).also { allFunctionTypes.add(it) }
        }
    }
}
