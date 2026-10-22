package com.wisechat.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wisechat.app.WiseChatApp
import com.wisechat.app.data.local.entity.HistoryEntity
import com.wisechat.app.data.preferences.ThemeMode
import com.wisechat.app.domain.model.*
import com.wisechat.app.engine.ocr.MlKitOcrEngine
import com.wisechat.app.engine.provider.GenerateRepliesParams
import com.wisechat.app.engine.provider.RefineReplyParams
import com.wisechat.app.overlay.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WiseChatUiState(
    val conversationContext: ConversationContext? = null,
    val selectedStyle: ReplyStyle = ReplyStyle.CASUAL,
    val styleCustomization: StyleCustomization = StyleCustomization(),
    val suggestions: List<ReplySuggestion> = emptyList(),
    val isAnalyzingImage: Boolean = false,
    val isGeneratingReplies: Boolean = false,
    val activeProvider: AiProviderConfig? = null,
    val isOverlayEnabled: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedStarterCategory: StarterCategory = StarterCategory.SWEET_AWW,
    val startersList: List<ConversationStarter> = CuratedStartersLibrary.getByCategory(StarterCategory.SWEET_AWW),
    val isGeneratingStarters: Boolean = false,
    val starterTopicInput: String = ""
)

class WiseChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WiseChatApp
    private val ocrEngine = MlKitOcrEngine()

    private val _uiState = MutableStateFlow(WiseChatUiState())
    val uiState: StateFlow<WiseChatUiState> = _uiState.asStateFlow()

    private val _navigationChannel = Channel<String>(Channel.BUFFERED)
    val navigationEvents = _navigationChannel.receiveAsFlow()

    val providers: StateFlow<List<AiProviderConfig>> = app.providerRepository.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPresets: StateFlow<List<CustomPersonaPreset>> = app.styleRepository.customPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyEntries: StateFlow<List<HistoryEntity>> = app.historyRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = app.preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            app.preferencesManager.setThemeMode(mode)
        }
    }

    fun disconnectActiveProvider() {
        viewModelScope.launch {
            val current = _uiState.value.activeProvider
            if (current != null) {
                app.secureStorage.removeApiKey(current.id)
                val updated = current.copy(isEnabled = false)
                app.providerRepository.saveProvider(updated)
                observeActiveProvider()
                _uiState.update {
                    it.copy(successMessage = "Disconnected ${current.name} and removed API key.")
                }
            }
        }
    }

    init {
        checkOverlayStatus()
        observeActiveProvider()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            app.preferencesManager.setOnboardingCompleted(true)
        }
    }

    fun checkOverlayStatus() {
        val granted = Settings.canDrawOverlays(getApplication())
        _uiState.update { it.copy(isOverlayPermissionGranted = granted) }
        viewModelScope.launch {
            val enabled = app.preferencesManager.overlayEnabled.first()
            _uiState.update { it.copy(isOverlayEnabled = enabled && granted) }
            if (enabled && granted) {
                OverlayService.start(getApplication())
            }
        }
    }

    private fun observeActiveProvider() {
        viewModelScope.launch {
            val active = app.providerRepository.getActiveProvider()
            _uiState.update { it.copy(activeProvider = active) }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !Settings.canDrawOverlays(getApplication())) {
                _uiState.update {
                    it.copy(errorMessage = "Please grant the 'Display over other apps' permission first.")
                }
                return@launch
            }
            app.preferencesManager.setOverlayEnabled(enabled)
            _uiState.update { it.copy(isOverlayEnabled = enabled) }
            if (enabled) {
                OverlayService.start(getApplication())
            } else {
                OverlayService.stop(getApplication())
            }
        }
    }

    fun processImageUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingImage = true, errorMessage = null) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        if (uri.scheme == "file" || uri.path?.startsWith("/") == true) {
                            val path = uri.path ?: ""
                            BitmapFactory.decodeFile(path) ?: run {
                                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                                BitmapFactory.decodeStream(stream)
                            }
                        } else {
                            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        Log.e("WiseChatVM", "Failed to open image URI: $uri", e)
                        null
                    }
                }
                if (bitmap != null) {
                    val result = ocrEngine.processImage(bitmap)
                    result.onSuccess { context ->
                        _uiState.update {
                            it.copy(
                                conversationContext = context,
                                isAnalyzingImage = false,
                                suggestions = emptyList()
                            )
                        }
                        _navigationChannel.trySend(com.wisechat.app.ui.navigation.Routes.ANALYSIS)
                    }.onFailure { ex ->
                        _uiState.update {
                            it.copy(
                                isAnalyzingImage = false,
                                errorMessage = ex.localizedMessage ?: "Failed to read conversation text."
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(isAnalyzingImage = false, errorMessage = "Could not decode image.")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalyzingImage = false, errorMessage = e.localizedMessage ?: "Error loading image.")
                }
            }
        }
    }

    fun setTargetMessage(messageId: String) {
        _uiState.update { state ->
            val updatedContext = state.conversationContext?.withTarget(messageId)
            state.copy(conversationContext = updatedContext)
        }
    }

    fun updateMessageText(messageId: String, newText: String) {
        _uiState.update { state ->
            val updatedContext = state.conversationContext?.updateMessageText(messageId, newText)
            state.copy(conversationContext = updatedContext)
        }
    }

    fun selectStyle(style: ReplyStyle) {
        _uiState.update {
            it.copy(
                selectedStyle = style,
                styleCustomization = it.styleCustomization.copy(selectedStyle = style)
            )
        }
    }

    fun updateStyleCustomization(customization: StyleCustomization) {
        _uiState.update {
            it.copy(
                selectedStyle = customization.selectedStyle,
                styleCustomization = customization
            )
        }
    }

    fun generateReplies(customPreset: CustomPersonaPreset? = null) {
        val context = _uiState.value.conversationContext ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingReplies = true, errorMessage = null) }
            val params = GenerateRepliesParams(
                conversationContext = context,
                styleOptions = _uiState.value.styleCustomization,
                customPreset = customPreset
            )
            val result = app.providerCoordinator.generateReplies(params)
            result.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isGeneratingReplies = false,
                        suggestions = response.suggestions
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isGeneratingReplies = false,
                        errorMessage = ex.localizedMessage ?: "Failed to generate replies. Verify provider configuration."
                    )
                }
            }
        }
    }

    fun refineReply(originalText: String, type: RefinementType) {
        val context = _uiState.value.conversationContext ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingReplies = true, errorMessage = null) }
            val params = RefineReplyParams(
                originalReply = originalText,
                refinementType = type,
                conversationContext = context
            )
            val result = app.providerCoordinator.refineReply(params)
            result.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isGeneratingReplies = false,
                        suggestions = response.suggestions
                    )
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isGeneratingReplies = false,
                        errorMessage = ex.localizedMessage ?: "Failed to refine reply."
                    )
                }
            }
        }
    }

    fun onReplyCopied(suggestion: ReplySuggestion) {
        viewModelScope.launch {
            val target = _uiState.value.conversationContext?.resolvedTargetMessage?.text ?: "Unknown"
            val provider = _uiState.value.activeProvider?.name ?: "Default"
            app.historyRepository.saveHistory(
                targetMessage = target,
                selectedReply = suggestion.text,
                style = suggestion.style.title,
                providerName = provider
            )
        }
    }

    fun saveProvider(config: AiProviderConfig) {
        viewModelScope.launch {
            app.providerRepository.saveProvider(config)
            observeActiveProvider()
        }
    }

    fun setDefaultProvider(id: String) {
        viewModelScope.launch {
            app.providerRepository.setDefaultProvider(id)
            observeActiveProvider()
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            app.providerRepository.deleteProvider(id)
            observeActiveProvider()
        }
    }

    suspend fun testProviderConnection(config: AiProviderConfig): ProviderTestResult {
        val provider = app.providerCoordinator.createProvider(config)
        return provider.testConnection()
    }

    fun saveCustomPreset(preset: CustomPersonaPreset) {
        viewModelScope.launch {
            app.styleRepository.savePreset(preset)
        }
    }

    fun deleteCustomPreset(id: String) {
        viewModelScope.launch {
            app.styleRepository.deletePreset(id)
        }
    }

    fun deleteHistoryEntry(entry: HistoryEntity) {
        viewModelScope.launch {
            app.historyRepository.deleteHistory(entry)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            app.historyRepository.clearAllHistory()
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectStarterCategory(category: StarterCategory) {
        _uiState.update { state ->
            val starters = CuratedStartersLibrary.getByCategory(category)
            state.copy(selectedStarterCategory = category, startersList = starters)
        }
    }

    fun setStarterTopic(topic: String) {
        _uiState.update { it.copy(starterTopicInput = topic) }
    }

    fun generateStarters() {
        val category = _uiState.value.selectedStarterCategory
        val topic = _uiState.value.starterTopicInput
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingStarters = true, errorMessage = null) }
            val params = com.wisechat.app.engine.provider.GenerateStartersParams(
                category = category,
                profileOrTopic = topic.takeIf { it.isNotBlank() }
            )
            val result = app.providerCoordinator.generateConversationStarters(params)
            result.onSuccess { starters ->
                _uiState.update {
                    it.copy(
                        isGeneratingStarters = false,
                        startersList = starters
                    )
                }
            }.onFailure { ex ->
                val curated = CuratedStartersLibrary.getByCategory(category)
                _uiState.update {
                    it.copy(
                        isGeneratingStarters = false,
                        startersList = curated,
                        errorMessage = ex.localizedMessage ?: "Failed to generate AI openers, showing curated lines."
                    )
                }
            }
        }
    }

    fun onStarterCopied(starter: ConversationStarter) {
        viewModelScope.launch {
            val provider = if (starter.isAiGenerated) {
                _uiState.value.activeProvider?.name ?: "AI Assistant"
            } else {
                "Wise Chat Curated Vault"
            }
            app.historyRepository.saveHistory(
                targetMessage = "Opener [${starter.category.title}]",
                selectedReply = starter.text,
                style = starter.tag.ifBlank { starter.category.title },
                providerName = provider
            )
        }
    }
}
