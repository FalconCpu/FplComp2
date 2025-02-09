package falcon

object Stdlib {
    val mallocObject = Function(Location.nullLocation, "mallocObject", emptyList(), false, IntType, null)
    val mallocArray = Function(Location.nullLocation, "mallocArray", emptyList(), false, IntType, null)
    val free = Function(Location.nullLocation, "free", emptyList(), false, UnitType, null)
    val strcmp = Function(Location.nullLocation, "strcmp", emptyList(), false, IntType, null)
    val strequals = Function(Location.nullLocation, "strequals", emptyList(), false, BoolType, null)
}