package com.iptvplayer.app

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var controls: View
    private lateinit var tvChannel: TextView
    private lateinit var tvGesture: TextView
    private lateinit var listPanel: View
    private lateinit var list: RecyclerView

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private var resizeIdx = 0

    private lateinit var audio: AudioManager
    private var maxVol = 15
    private var volLevel = 0f
    private var brightness = 0.5f

    private var gestureMode = 0 // 0 aucun, 1 luminosité, 2 volume
    private lateinit var detector: GestureDetector

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { controls.visibility = View.GONE }
    private val hideGesture = Runnable { tvGesture.visibility = View.GONE }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        view = findViewById(R.id.playerView)
        controls = findViewById(R.id.controls)
        tvChannel = findViewById(R.id.tvChannel)
        tvGesture = findViewById(R.id.tvGesture)
        listPanel = findViewById(R.id.listPanel)
        list = findViewById(R.id.channelList)

        if (Playlist.items.isEmpty()) { finish(); return }

        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        brightness = window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        view.player = exo
        view.useController = false
        view.keepScreenOn = true

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity,
                    "Lecture impossible : ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { prev() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { next() }
        findViewById<ImageButton>(R.id.btnList).setOnClickListener { toggleList() }
        findViewById<ImageButton>(R.id.btnResize).setOnClickListener { cycleResize() }

        detector = GestureDetector(this, GestureListener())
        view.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) gestureMode = 0
            true
        }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ChannelListAdapter(Playlist.items) { pos ->
            Playlist.index = pos
            hideList()
            playCurrent()
        }

        playCurrent()
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { gestureMode = 0; return true }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (listPanel.visibility == View.VISIBLE) hideList() else showControls()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            if (e1 == null || listPanel.visibility == View.VISIBLE) return false
            val totX = e2.x - e1.x
            val totY = e2.y - e1.y
            if (gestureMode == 0) {
                if (abs(totY) > abs(totX) && abs(totY) > 40) {
                    gestureMode = if (e1.x < view.width / 2f) 1 else 2
                } else return false
            }
            val frac = dY / view.height * 1.5f  // dY>0 = doigt vers le haut
            when (gestureMode) {
                1 -> setBrightness(brightness + frac)
                2 -> setVolume(volLevel + frac * maxVol)
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            if (e1 == null || gestureMode != 0) return false
            if (abs(vX) > abs(vY) && abs(vX) > 800) {
                if (vX < 0) next() else prev()
                return true
            }
            return false
        }
    }

    private fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        showGesture("☀ ${(brightness * 100).toInt()} %")
    }

    private fun setVolume(v: Float) {
        volLevel = v.coerceIn(0f, maxVol.toFloat())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volLevel.toInt(), 0)
        showGesture("🔊 ${(volLevel / maxVol * 100).toInt()} %")
    }

    private fun showGesture(text: String) {
        tvGesture.text = text
        tvGesture.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideGesture)
        hideHandler.postDelayed(hideGesture, 800)
    }

    private fun playCurrent() {
        val item = Playlist.current() ?: return
        val url = item.streamUrl ?: return
        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
        tvChannel.text = item.name
        showControls()
    }

    private fun next() { Playlist.index = (Playlist.index + 1) % Playlist.items.size; playCurrent() }
    private fun prev() { Playlist.index = (Playlist.index - 1 + Playlist.items.size) % Playlist.items.size; playCurrent() }

    private fun cycleResize() {
        resizeIdx = (resizeIdx + 1) % resizeModes.size
        view.resizeMode = resizeModes[resizeIdx]
        val label = when (resizeIdx) { 0 -> "Ajusté"; 1 -> "Étiré"; else -> "Zoom" }
        showGesture("Image : $label")
        showControls()
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideControls)
        hideHandler.postDelayed(hideControls, 4000)
    }

    private fun toggleList() {
        if (listPanel.visibility == View.VISIBLE) hideList() else showList()
    }

    private fun showList() {
        listPanel.visibility = View.VISIBLE
        list.scrollToPosition(Playlist.index)
        list.requestFocus()
        hideHandler.removeCallbacks(hideControls)
    }

    private fun hideList() {
        listPanel.visibility = View.GONE
        showControls()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (listPanel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { hideList(); return true }
            return super.onKeyDown(keyCode, event)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { prev(); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> { next(); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> { showList(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
