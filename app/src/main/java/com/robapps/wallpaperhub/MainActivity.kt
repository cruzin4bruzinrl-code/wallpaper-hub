package com.robapps.wallpaperhub

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    data class WallpaperEntry(val label: String, val component: ComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnOwnWallpaper).setOnClickListener {
            applyWallpaper(ComponentName(this, VideoWallpaperService::class.java))
        }

        val entries = findInstalledLiveWallpapers()
        val listView = findViewById<ListView>(R.id.listWallpapers)
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            entries.map { it.label }
        )
        listView.setOnItemClickListener { _, _, position, _ ->
            applyWallpaper(entries[position].component)
        }
    }

    private fun findInstalledLiveWallpapers(): List<WallpaperEntry> {
        val pm = packageManager
        val intent = Intent("android.service.wallpaper.WallpaperService")
        val resolveInfos = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)

        return resolveInfos
            .filter { it.serviceInfo.packageName != packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                val component = ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
                WallpaperEntry(label, component)
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun applyWallpaper(component: ComponentName) {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        startActivity(intent)
    }
}
