package falcon

sealed class Type(private val name:String) {
    override fun toString() = name

    // Check to see if 'other' is compatible with this type.  (Very simplistic for now - will be extended later.)
    fun isAssignableFrom(other: Type): Boolean {
        if (this==other || other == ErrorType || this==ErrorType || this== AnyType)
            return true
        if (this is NullableType && other is NullType)
            return true
        if (this is NullableType && elementType.isAssignableFrom(other))
            return true
        if (this is ClassType && other is ClassType && other.superClass!=null)
            return isAssignableFrom(other.superClass)
        return false
    }

    fun isIntegerType() = this == IntType || this== CharType || this== ShortType || this== ErrorType

    fun checkType(expression: TastExpression) {
        if (!isAssignableFrom(expression.type))
            Log.error(expression.location, "Type mismatch. Expected ${this}, but found ${expression.type}")
    }

    fun getSize() = when(this) {
        BoolType -> 1
        CharType -> 1
        ErrorType -> 1
        IntType -> 4
        ShortType -> 2
        RealType -> 4
        UndefinedType -> 0
        StringType -> 4
        UnitType -> 0
        NullType -> 4
        AnyType -> 4
        is EnumType -> 4
        is ArrayType -> 4
        is FunctionType -> 4
        is NullableType -> 4
        is ClassType -> 4
    }
}

object AnyType       : Type("Any")
object UnitType      : Type("Unit")
object NullType      : Type("Null")
object BoolType      : Type("Bool")
object CharType      : Type("Char")
object ShortType     : Type("Short")
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
        fun make(elementType: Type): Type {
            if (elementType== ErrorType)
                return ErrorType
            return allArrayTypes.getOrPut(elementType) { ArrayType(elementType) }
        }
    }
}

// ----------------------------------------------------------------------------
//                       Struct Types
// ----------------------------------------------------------------------------

class  ClassType(name:String, val superClass: ClassType?) : Type(name) {
    val fields = mutableListOf<FieldSymbol>()
    val methods = mutableListOf<FunctionSymbol>()
    val virtualMethods = mutableListOf<FunctionSymbol>()
    var hasSubclasses = false

    var classSize = 0
    lateinit var constructor : Function

    fun add(field: FieldSymbol) {
        // add padding if necessary
        classSize = when (field.type.getSize()) {
            4 -> (classSize+3) and 0xFFFFFFC
            2 -> (classSize+1) and 0xFFFFFFE
            else -> classSize
        }

        if (field.offset != -1 && field.offset != classSize)
            Log.error(field.location, "Field ${field.name} has offset ${field.offset} but expected $classSize")

        field.offset = classSize
        classSize += field.type.getSize()
        fields += field
    }

    fun add(method: FunctionSymbol) {
        methods += method
    }

    fun addVirtualMethod(method: FunctionSymbol) {
        method.function.virtualFunctionNumber = virtualMethods.size
        virtualMethods += method
    }

    fun lookup(name: String): Symbol? {
        return fields.find { it.name == name } ?:
               methods.find { it.name == name }
    }

    fun isSubtypeOf(type: ClassType): Boolean {
        if (this==type)
            return true
        if (superClass==null)
            return false
        return superClass.isSubtypeOf(type)
    }


    companion object {
        fun make(name: String, superClass: ClassType?): ClassType {
            val new = ClassType(name, superClass)
            DataSegment.allClasses.add(new)
            return new
        }
    }
}

// ----------------------------------------------------------------------------
//                       Nullable Types
// ----------------------------------------------------------------------------

class  NullableType(val elementType: Type) : Type("$elementType?") {
    companion object {
        private val allPointerTypes = mutableMapOf<Type, NullableType>()
        fun make(elementType: Type): Type {
            if (elementType== ErrorType)
                return ErrorType
            return allPointerTypes.getOrPut(elementType) { NullableType(elementType) }
        }
    }
}

// ----------------------------------------------------------------------------
//                       Function Types
// ----------------------------------------------------------------------------

class FunctionType(val parameterTypes: List<Type>, val isVariadic:Boolean, val returnType: Type)
: Type("(${parameterTypes.joinToString(",")})->$returnType") { //TODO variadic function name
    companion object {
        private val allFunctionTypes = mutableListOf<FunctionType>()
        fun make(parameterTypes: List<Type>, isVariadic: Boolean, returnType: Type): FunctionType {
            return allFunctionTypes.find{it.parameterTypes==parameterTypes && it.returnType==returnType} ?:
                FunctionType(parameterTypes, isVariadic, returnType).also { allFunctionTypes.add(it) }
        }
    }
}

// ----------------------------------------------------------------------------
//                       Enum Types
// ----------------------------------------------------------------------------

class EnumType(name:String) : Type(name) {
    val fields = mutableListOf<ConstantSymbol>()

    fun lookup(name: String): Symbol? {
        return fields.find { it.name == name }
    }

    companion object {
        fun make(name: String, body:List<AstIdentifier>): EnumType {
            val ret = EnumType(name)
            for ((index,id) in body.withIndex())
                ret.fields += ConstantSymbol(id.location, id.name, ret, index)
            return ret
        }
    }
}

