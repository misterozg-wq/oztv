package com.iptvplayer.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var controls: View
    private lateinit var tvChannel: TextView
    private lateinit var listPanel: View
    private lateinit var list: RecyclerView

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private var resizeIdx = 0

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { controls.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        view = findViewById(R.id.playerView)
        controls = findViewById(R.id.controls)
        tvChannel = findViewById(R.id.tvChannel)
        listPanel = findViewById(R.id.listPanel)
        list = findViewById(R.id.channelList)

        if (Playlist.items.isEmpty()) { finish(); return }

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

        view.setOnClickListener { if (listPanel.visibility == View.VISIBLE) hideList() else showControls() }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ChannelListAdapter(Playlist.items) { pos ->
            Playlist.index = pos
            hideList()
            playCurrent()
        }

        playCurrent()
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
        Toast.makeText(this, "Image : $label", Toast.LENGTH_SHORT).show()
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

    /** Télécommande / clavier : haut = précédent, bas = suivant, OK = liste. */
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
