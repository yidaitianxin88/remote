package ai.venice.remote

object Hub {
    const val SERVER = "ws://bore.pub:39317/ws"
    @Volatile var control: ControlService? = null
    @Volatile var width = 1080
    @Volatile var height = 1920
}