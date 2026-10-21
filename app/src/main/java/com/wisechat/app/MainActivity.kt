package com.wisechat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.wisechat.app.ui.WiseChatViewModel
import com.wisechat.app.ui.navigation.Routes
import com.wisechat.app.ui.navigation.WiseChatNavHost
import com.wisechat.app.ui.theme.WiseChatTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: WiseChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIncomingShareIntent(intent)

        lifecycleScope.launch {
            val app = application as WiseChatApp
            val onboardingCompleted = app.preferencesManager.onboardingCompleted.first()
            val startDestination = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING

            setContent {
                val darkThemePref by app.preferencesManager.isDarkTheme.collectAsState(initial = null)
                val useDarkTheme = darkThemePref ?: isSystemInDarkTheme()

                WiseChatTheme(darkTheme = useDarkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WiseChatNavHost(
                            viewModel = viewModel,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.checkOverlayStatus()
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (imageUri != null) {
                viewModel.processImageUri(imageUri)
            }
        }
    }

    companion object {
        var isResumed: Boolean = false
            private set
        var instance: MainActivity? = null
            private set
    }
}
