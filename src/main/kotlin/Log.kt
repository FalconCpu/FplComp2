package falcon

object Log {
    val messages = mutableListOf<String>()

    fun clear() {
        messages.clear()
    }

    fun error(message: String) {
        messages.add("ERROR: $message")
    }

    fun error(location: Location, message: String) {
        messages.add("$location: $message")
    }

    fun hasErrors() = messages.isNotEmpty()

    fun getErrors() = messages.joinToString (separator = "\n")
}