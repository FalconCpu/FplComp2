package falcon

class Token(val location: Location, val kind: TokenKind, val text: String) {

    override fun toString(): String {
        return text
    }
}