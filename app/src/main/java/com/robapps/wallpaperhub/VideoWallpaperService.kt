package com.robapps.wallpaperhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowManager

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var hasFinishedPlaying = false
        private var resolvedFileName: String? = null

        private val prefsHome: SharedPreferences by lazy {
            getSharedPreferences("wallpaper_state_home", Context.MODE_PRIVATE)
        }
        private val prefsLock: SharedPreferences by lazy {
            getSharedPreferences("wallpaper_state_lock", Context.MODE_PRIVATE)
        }

        private fun currentPrefs(): SharedPreferences =
            if (resolvedFileName == "Lock.mp4") prefsLock else prefsHome

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    playFromStart()
                }
            }
        }

        private fun guessIsLockScreen(): Boolean {
            return try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val screenHeight = wm.currentWindowMetrics.bounds.height()
                val desired = desiredMinimumHeight
                desired in 1 until (screenHeight - 50)
            } catch (e: Exception) {
                false
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            resolvedFileName = if (guessIsLockScreen()) "Lock.mp4" else "Wallpaper.mp4"
            hasFinishedPlaying = currentPrefs().getBoolean("finished", false)

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
            val fileName = resolvedFileName ?: "Wallpaper.mp4"
            releasePlayer()
            hasFinishedPlaying = false
            currentPrefs().edit().putBoolean("finished", false).apply()

            try {
                val afd = assets.openFd(fileName)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    setSurface(surfaceHolder.surface)
                    isLooping = false
                    setOnCompletionListener {
                        hasFinishedPlaying = true
                        currentPrefs().edit().putBoolean("finished", true).apply()
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun showLastFrame(holder: SurfaceHolder) {
            val fileName = resolvedFileName ?: "Wallpaper.mp4"
            releasePlayer()
            try {
                val afd = assets.openFd(fileName)
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
