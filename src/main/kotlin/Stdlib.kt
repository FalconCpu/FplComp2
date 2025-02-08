package falcon

object Stdlib {
    val mallocObject = Function(Location.nullLocation, "mallocObject", emptyList(), false, IntType, null)
    val mallocArray = Function(Location.nullLocation, "mallocArray", emptyList(), false, IntType, null)
}