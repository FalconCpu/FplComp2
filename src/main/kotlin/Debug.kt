package falcon

object Debug {
    val enable = false

    fun log(msg: String) {
        if (enable) println(msg)
    }
}