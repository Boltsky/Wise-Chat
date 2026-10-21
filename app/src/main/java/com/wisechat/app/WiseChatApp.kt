package com.wisechat.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.wisechat.app.data.local.AppDatabase
import com.wisechat.app.data.preferences.PreferencesManager
import com.wisechat.app.data.repository.HistoryRepository
import com.wisechat.app.data.repository.ProviderRepository
import com.wisechat.app.data.repository.StyleRepository
import com.wisechat.app.data.security.SecureCredentialStorage
import com.wisechat.app.engine.provider.ProviderCoordinator

class WiseChatApp : Application(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // Lifecycle components for Compose WindowManager overlay
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val appViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = appViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Core Dependencies
    val secureStorage by lazy { SecureCredentialStorage(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val database by lazy { AppDatabase.getDatabase(this) }

    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
    val providerRepository by lazy { ProviderRepository(preferencesManager, secureStorage) }
    val styleRepository by lazy { StyleRepository(preferencesManager) }

    val providerCoordinator by lazy { ProviderCoordinator(providerRepository) }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onTerminate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
        super.onTerminate()
    }
}
