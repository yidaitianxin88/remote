package ai.venice.remote

object Hub {
    const val SERVER = "wss://example.com/ws"
    @Volatile var control: ControlService? = null
    @Volatile var width = 1080
    @Volatile var height = 1920
}