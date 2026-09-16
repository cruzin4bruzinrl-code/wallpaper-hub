package com.robapps.wallpaperhub

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {

        private var player: ExoPlayer? = null
        private var hasFinishedPlaying = false
        private var isLockScreen = false

        private val prefsHome: SharedPreferences by lazy {
            getSharedPreferences("wallpaper_state_home", Context.MODE_PRIVATE)
        }
        private val prefsLock: SharedPreferences by lazy {
            getSharedPreferences("wallpaper_state_lock", Context.MODE_PRIVATE)
        }

        private fun currentPrefs(): SharedPreferences =
            if (isLockScreen) prefsLock else prefsHome

        private fun videoUri(): Uri =
            Uri.parse(if (isLockScreen) "asset:///Lock.mp4" else "asset:///Wallpaper.mp4")

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    playFromStart()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Officiell metod (Android 16 / API 36) - berättar exakt
            // vilken yta denna engine-instans faktiskt renderar för.
            // Ingen gissning längre.
            isLockScreen = try {
                (wallpaperFlags and WallpaperManager.FLAG_LOCK) != 0
            } catch (e: Exception) {
                false
            }
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            hasFinishedPlaying = currentPrefs().getBoolean("finished", false)

            if (hasFinishedPlaying) {
                showLastFrame(holder)
            } else {
                playFromStart()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                if (!hasFinishedPlaying && player == null) {
                    playFromStart()
                }
            } else {
                releasePlayer()
            }
        }

        private fun playFromStart() {
            releasePlayer()
            hasFinishedPlaying = false
            currentPrefs().edit().putBoolean("finished", false).apply()

            try {
                player = ExoPlayer.Builder(this@VideoWallpaperService).build().apply {
                    setVideoSurface(surfaceHolder.surface)
                    setMediaItem(MediaItem.fromUri(videoUri()))
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                hasFinishedPlaying = true
                                currentPrefs().edit().putBoolean("finished", true).apply()
                            }
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun showLastFrame(holder: SurfaceHolder) {
            releasePlayer()
            try {
                player = ExoPlayer.Builder(this@VideoWallpaperService).build().apply {
                    setVideoSurface(holder.surface)
                    setMediaItem(MediaItem.fromUri(videoUri()))
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                seekTo(duration - 1)
                                playWhenReady = false
                            }
                        }
                    })
                    prepare()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun releasePlayer() {
            player?.release()
            player = null
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
