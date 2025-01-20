package falcon

class Location(private val fileName:String, private val lineNumber:Int, private val columnNumber:Int) {

    override fun toString(): String {
        return "$fileName:$lineNumber.$columnNumber"
    }

    companion object {
        val nullLocation = Location("", 0, 0)
    }
}