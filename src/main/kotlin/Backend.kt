package falcon

class Backend(val func:Function) {

    fun run() {
        Peephole(func).run()
        val liveMap = Livemap(func)
        RegisterAllocator(func, liveMap).run()
        Peephole(func).run()
    }

}