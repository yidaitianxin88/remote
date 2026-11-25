package ai.venice.remote

import android.app.*
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenService : Service() {
    private var codec: MediaCodec? = null
    private var proj: MediaProjection? = null
    private var ws: WebSocket? = null
    private var running = true
    private val devId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }
    private val devName: String get() = Build.MANUFACTURER + " " + Build.MODEL

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent, f: Int, id: Int): Int {
        createChannel()
        val n = Notification.Builder(this, "s").setContentTitle("Remote")
            .setSmallIcon(android.R.drawable.stat_sys_upload).build()
        startForeground(1, n)
        val data = i.getParcelableExtra<Intent>("data")!!
        proj = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(android.app.Activity.RESULT_OK, data)
        connect()
        return START_STICKY
    }

    private fun createChannel() {
        val c = NotificationChannel("s", "Remote", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(c)
    }

    private fun connect() {
        val req = Request.Builder().url(Hub.SERVER).build()
        ws = OkHttpClient().newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) {
                w.send("""{"t":"hello","role":"phone","id":"$devId","name":"$devName"}""")
            }
            override fun onMessage(w: WebSocket, t: String) {
                val j = JSONObject(t)
                when (j.optString("t")) {
                    "tap" -> Hub.control?.tap(j.getDouble("x").toFloat(), j.getDouble("y").toFloat())
                    "swipe" -> Hub.control?.swipe(
                        j.getDouble("x1").toFloat(), j.getDouble("y1").toFloat(),
                        j.getDouble("x2").toFloat(), j.getDouble("y2").toFloat(), j.optLong("ms", 200))
                    "text" -> Hub.control?.text(j.getString("s"))
                }
            }
            override fun onFailure(w: WebSocket, e: Throwable, r: Response?) { if (running) connect() }
        })
        startEncode()
    }

    private fun startEncode() {
        val w = 720; val h = 1280
        Hub.width = w; Hub.height = h
        val fmt = MediaFormat.createVideoFormat("video/avc", w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val c = MediaCodec.createEncoderByType("video/avc")
        c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = c.createInputSurface()
        c.start()
        codec = c
        proj?.createVirtualDisplay("cap", w, h, 320, 0, surface, null, null)

        Thread {
            val info = MediaCodec.BufferInfo()
            while (running) {
                val idx = c.dequeueOutputBuffer(info, 10_000)
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    val sps = f.getByteBuffer("csd-0")!!.toBytes()
                    val pps = f.getByteBuffer("csd-1")!!.toBytes()
                    ws?.send("""{"t":"cfg","d":"${Base64.encodeToString(avcC(sps, pps), Base64.NO_WRAP)}"}""")
                    continue
                }
                if (idx < 0) continue
                val buf = c.getOutputBuffer(idx)!!
                buf.position(info.offset); buf.limit(info.offset + info.size)
                val bytes = ByteArray(info.size).also { buf.get(it) }
                c.releaseOutputBuffer(idx, false)
                if (bytes.isNotEmpty()) ws?.send(bytes.toByteString())
            }
        }.start()
    }

    private fun ByteBuffer.toBytes(): ByteArray { val b = ByteArray(remaining()); get(b); return b }

    private fun avcC(sps: ByteArray, pps: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(1); o.write(sps[1].toInt()); o.write(sps[2].toInt()); o.write(sps[3].toInt())
        o.write(0xFF); o.write(0xE1)
        o.write(sps.size shr 8); o.write(sps.size and 0xFF); o.write(sps)
        o.write(1); o.write(pps.size shr 8); o.write(pps.size and 0xFF); o.write(pps)
        return o.toByteArray()
    }

    override fun onDestroy() {
        running = false
        codec?.stop(); proj?.stop(); ws?.close(1000, null)
        super.onDestroy()
    }
}