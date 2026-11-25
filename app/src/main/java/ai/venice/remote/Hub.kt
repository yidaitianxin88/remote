package ai.venice.remote

object Hub {
    const val SERVER = "wss://brhky-183-253-233-246.free.pinggy.net/ws"
    @Volatile var control: ControlService? = null
    @Volatile var width = 1080
    @Volatile var height = 1920
}
