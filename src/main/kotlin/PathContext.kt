package falcon

class PathContext(
    val uninitialized: Set<Symbol> = emptySet(),
    val possiblyUninitialized: Set<Symbol> = emptySet(),
    val refinedTypes: Map<Symbol, Type> = emptyMap(),
    val unreachable: Boolean = false
) {
    fun initialize(symbol: Symbol) = PathContext(
            uninitialized = uninitialized - symbol,
            possiblyUninitialized = possiblyUninitialized - symbol,
            refinedTypes = refinedTypes,
            unreachable = unreachable
        )

    fun addUninitialized(symbol: Symbol) = PathContext (
            uninitialized = uninitialized + symbol,
            possiblyUninitialized = possiblyUninitialized + symbol,
            refinedTypes = refinedTypes,
            unreachable = unreachable
        )

    fun setUnreachable() = PathContext(
            uninitialized, possiblyUninitialized, refinedTypes, true
        )

    fun addRefinedType(expr: TastExpression, type:Type) : PathContext {
        val symbol = expr.getSymbol()
        return if (symbol!=null)
            PathContext (
                uninitialized, possiblyUninitialized,
                refinedTypes + (symbol to type),
                unreachable
            )
        else
            this
    }

    fun removeRefinedType(symbol: Symbol) = PathContext(
            uninitialized, possiblyUninitialized,
            refinedTypes - symbol,
            unreachable
        )

    fun removeRefinedType(expr: TastExpression) : PathContext {
        return if (expr is TastVariable)
            removeRefinedType(expr.symbol)
        else
            this
    }


    fun getType(symbol: Symbol): Type {
        return refinedTypes[symbol] ?: symbol.type
    }

    override fun toString() = "PathContext{$refinedTypes}"

}

fun List<PathContext>.merge(): PathContext {
    val reachable = filter {! it.unreachable}
    if (reachable.isEmpty()) return PathContext(unreachable = true)

    val allUninitialized = reachable.map { it.uninitialized }.reduce { acc, set -> acc intersect set }
    val anyPossiblyUninitialized = reachable.map { it.possiblyUninitialized }.reduce { acc, set -> acc union set }

    val commonRefinedTypes = reachable.first().refinedTypes.filter { (symbol, type) ->
        reachable.all { it.refinedTypes[symbol] == type }
    }

    return PathContext(
        uninitialized = allUninitialized,
        possiblyUninitialized = anyPossiblyUninitialized,
        refinedTypes = commonRefinedTypes,
        unreachable = false
    )
}
