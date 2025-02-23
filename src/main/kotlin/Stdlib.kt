package falcon

object Stdlib {
    val mallocObject = Function(Location.nullLocation, "mallocObject", emptyList(), false, IntType, null, false)
    val mallocArray = Function(Location.nullLocation, "mallocArray", emptyList(), false, IntType, null, false)
    val free = Function(Location.nullLocation, "free", emptyList(), false, UnitType, null, false)
    val strcmp = Function(Location.nullLocation, "strcmp", emptyList(), false, IntType, null, false)
    val strequals = Function(Location.nullLocation, "strequals", emptyList(), false, BoolType, null, false)
}