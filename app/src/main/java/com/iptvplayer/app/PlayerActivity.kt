package com.iptvplayer.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val view = findViewById<PlayerView>(R.id.playerView)

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        view.player = exo
        view.keepScreenOn = true

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity,
                    "Lecture impossible : ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })

        exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
