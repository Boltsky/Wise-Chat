package com.wisechat.app.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wisechat.app.R
import com.wisechat.app.WiseChatApp
import com.wisechat.app.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WiseChatTileService : TileService() {

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
            val app = application as WiseChatApp
            val currentlyEnabled = app.preferencesManager.overlayEnabled.first()
            val newEnabled = !currentlyEnabled
            app.preferencesManager.setOverlayEnabled(newEnabled)

            if (newEnabled) {
                OverlayService.start(this@WiseChatTileService)
            } else {
                OverlayService.stop(this@WiseChatTileService)
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val canDraw = Settings.canDrawOverlays(this)
        serviceScope.launch {
            val app = application as WiseChatApp
            val isEnabled = app.preferencesManager.overlayEnabled.first() && canDraw

            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Wise Chat"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isEnabled) "Active" else "Off"
            }
            tile.icon = Icon.createWithResource(this@WiseChatTileService, R.drawable.ic_notification)
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
