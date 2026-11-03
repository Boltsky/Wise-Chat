package com.rizzwise.app.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rizzwise.app.R
import com.rizzwise.app.RizzWiseApp
import com.rizzwise.app.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RizzWiseTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val canDraw = Settings.canDrawOverlays(this)
        if (!canDraw) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        serviceScope.launch {
            val app = application as RizzWiseApp
            val currentlyEnabled = app.preferencesManager.overlayEnabled.first()
            val newEnabled = !currentlyEnabled
            app.preferencesManager.setOverlayEnabled(newEnabled)

            if (newEnabled) {
                OverlayService.start(this@RizzWiseTileService)
            } else {
                OverlayService.stop(this@RizzWiseTileService)
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val canDraw = Settings.canDrawOverlays(this)
        serviceScope.launch {
            val app = application as RizzWiseApp
            val isEnabled = app.preferencesManager.overlayEnabled.first() && canDraw

            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "RizzWise"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isEnabled) "Active" else "Off"
            }
            tile.icon = Icon.createWithResource(this@RizzWiseTileService, R.drawable.ic_notification)
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
