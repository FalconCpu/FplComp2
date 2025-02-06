package falcon

object Log {
    val messages = mutableListOf<String>()

    fun clear() {
        messages.clear()
    }

    fun error(message: String) {
        val msg = "ERROR: $message"
        println(msg)
        messages.add(msg)
    }

    fun error(location: Location, message: String) {
        val msg = "$location: $message"
        println(msg)
        messages.add(msg)
    }

    fun hasErrors() = messages.isNotEmpty()

    fun getErrors() = messages.joinToString (separator = "\n")
}