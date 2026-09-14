package com.robapps.wallpaperhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var hasFinishedPlaying = false

        private val prefs: SharedPreferences by lazy {
            getSharedPreferences("wallpaper_state", Context.MODE_PRIVATE)
        }

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    playFromStart()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            hasFinishedPlaying = prefs.getBoolean("finished", false)
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (hasFinishedPlaying) {
                showLastFrame(holder)
            } else {
                playFromStart()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                if (!hasFinishedPlaying && mediaPlayer == null) {
                    playFromStart()
                }
            } else {
                releasePlayer()
            }
        }

        private fun playFromStart() {
            releasePlayer()
            hasFinishedPlaying = false
            prefs.edit().putBoolean("finished", false).apply()

            try {
                val afd = assets.openFd("Wallpaper.mp4")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    setSurface(surfaceHolder.surface)
                    isLooping = false
                    setOnCompletionListener {
                        hasFinishedPlaying = true
                        prefs.edit().putBoolean("finished", true).apply()
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun showLastFrame(holder: SurfaceHolder) {
            releasePlayer()
            try {
                val afd = assets.openFd("Wallpaper.mp4")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    setSurface(holder.surface)
                    setOnPreparedListener { player ->
                        player.seekTo(player.duration - 1)
                    }
                    prepare()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun releasePlayer() {
            mediaPlayer?.let {
                try {
                    it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
            mediaPlayer = null
        }

        override fun onDestroy() {
            super.onDestroy()
            try {
                unregisterReceiver(unlockReceiver)
            } catch (_: Exception) {
            }
            releasePlayer()
        }
    }
}
