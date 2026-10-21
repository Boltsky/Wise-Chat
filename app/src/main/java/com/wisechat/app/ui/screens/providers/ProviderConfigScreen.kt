package com.wisechat.app.ui.screens.providers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisechat.app.domain.model.AiProviderConfig
import com.wisechat.app.domain.model.AuthMode
import com.wisechat.app.domain.model.ProviderTestResult
import com.wisechat.app.domain.model.ProviderType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    providers: List<AiProviderConfig>,
    onSaveProvider: (AiProviderConfig) -> Unit,
    onSetDefaultProvider: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestConnection: suspend (AiProviderConfig) -> ProviderTestResult,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Fast Sign-In, 1: Manual BYOK

    var connectingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }
    var testResult by remember { mutableStateOf<ProviderTestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Accounts & BYOK") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingProvider = AiProviderConfig(
                            type = ProviderType.OPENAI,
                            name = "Custom Provider",
                            isDefault = false
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("⚡ Account Sign-In", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Custom BYOK", fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedTab == 0) {
                // Tab 0: Fast Account Sign-In (Gemini, ChatGPT, Claude)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "One-Tap Account Connection",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Sign in with your Google, ChatGPT, or Claude service account to start replying instantly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // Main 3 Cloud AI Services
                    val primaryTypes = listOf(ProviderType.GEMINI, ProviderType.OPENAI, ProviderType.CLAUDE)
                    val primaryProviders = primaryTypes.mapNotNull { type ->
                        providers.firstOrNull { it.type == type }
                    }

                    items(primaryProviders) { config ->
                        val isConnected = config.apiKey.isNotBlank()

                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (config.isDefault) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isConnected) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (config.type) {
                                                    ProviderType.GEMINI -> Icons.Default.AutoAwesome
                                                    ProviderType.OPENAI -> Icons.Default.ChatBubble
                                                    ProviderType.CLAUDE -> Icons.Default.Psychology
                                                    else -> Icons.Default.Computer
                                                },
                                                contentDescription = null,
                                                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = config.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (config.isDefault) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = if (isConnected) "Connected (${config.model})" else "Not connected",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    if (isConnected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Connected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { connectingProvider = config },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isConnected) Icons.Default.ManageAccounts else Icons.Default.Login,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isConnected) "Manage Account" else "Connect Account")
                                    }

                                    if (isConnected && !config.isDefault) {
                                        OutlinedButton(
                                            onClick = { onSetDefaultProvider(config.id) },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Set Active")
                                        }
                                    }

                                    if (isConnected) {
                                        IconButton(
                                            onClick = {
                                                isTesting = true
                                                testResult = null
                                                coroutineScope.launch {
                                                    testResult = onTestConnection(config)
                                                    isTesting = false
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Speed, contentDescription = "Test")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            } else {
                // Tab 1: Manual BYOK & Custom REST Server
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Custom BYOK allows entering arbitrary API keys, self-hosted Ollama/vLLM endpoints, Groq, or custom REST templates.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(providers) { config ->
                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (config.isDefault) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = config.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Model: ${config.model}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { editingProvider = config }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                                        }
                                        if (!config.isDefault) {
                                            IconButton(onClick = { onSetDefaultProvider(config.id) }) {
                                                Icon(Icons.Default.StarOutline, contentDescription = "Set Default")
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Key: ${config.maskedApiKey}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            isTesting = true
                                            testResult = null
                                            coroutineScope.launch {
                                                testResult = onTestConnection(config)
                                                isTesting = false
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test Connection")
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Connect Service Account Modal Dialog
    if (connectingProvider != null) {
        val target = connectingProvider!!
        var apiKeyOrToken by remember { mutableStateOf(target.apiKey) }
        var accountEmail by remember { mutableStateOf(target.accountEmail ?: "") }
        var isVerifying by remember { mutableStateOf(false) }
        var connectionError by remember { mutableStateOf<String?>(null) }

        val portalUrl = when (target.type) {
            ProviderType.GEMINI -> "https://aistudio.google.com/app/apikey"
            ProviderType.OPENAI -> "https://platform.openai.com/api-keys"
            ProviderType.CLAUDE -> "https://console.anthropic.com/settings/keys"
            else -> "https://platform.openai.com"
        }

        AlertDialog(
            onDismissRequest = { connectingProvider = null },
            title = { Text("Connect ${target.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sign in or authenticate with your ${target.name} account to enable intelligent reply generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1-tap web portal launcher
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open ${target.name} Account Portal")
                    }

                    OutlinedTextField(
                        value = apiKeyOrToken,
                        onValueChange = { apiKeyOrToken = it },
                        label = { Text("Account API Key / Service Token") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste account key or token") }
                    )

                    OutlinedTextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("Account Email / Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. user@gmail.com") }
                    )

                    if (connectionError != null) {
                        Text(
                            text = connectionError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isVerifying = true
                        connectionError = null
                        val targetModel = if (target.type == ProviderType.GEMINI && (target.model.contains("1.5") || target.model.contains("2.0"))) {
                            "gemini-flash-lite-latest"
                        } else target.model

                        val updated = target.copy(
                            apiKey = apiKeyOrToken.trim(),
                            accountEmail = accountEmail.trim().ifBlank { null },
                            model = targetModel,
                            authMode = AuthMode.SERVICE_ACCOUNT,
                            isConnected = apiKeyOrToken.isNotBlank(),
                            isDefault = true
                        )

                        coroutineScope.launch {
                            val test = onTestConnection(updated)
                            isVerifying = false
                            if (test.isSuccess || apiKeyOrToken.isNotBlank()) {
                                onSaveProvider(updated)
                                onSetDefaultProvider(updated.id)
                                connectingProvider = null
                            } else {
                                connectionError = test.message
                            }
                        }
                    },
                    enabled = apiKeyOrToken.isNotBlank() && !isVerifying
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...")
                    } else {
                        Text("Connect & Activate")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { connectingProvider = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Provider Edit / Add Modal Sheet for BYOK
    if (editingProvider != null) {
        val current = editingProvider!!
        var name by remember { mutableStateOf(current.name) }
        var apiKey by remember { mutableStateOf(current.apiKey) }
        var baseUrl by remember { mutableStateOf(current.baseUrl) }
        var model by remember { mutableStateOf(current.model) }
        var providerType by remember { mutableStateOf(current.type) }
        var showPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingProvider = null },
            title = { Text(if (current.apiKey.isBlank()) "Configure Custom Provider" else "Edit ${current.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key / Bearer Token") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model ID") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (providerType == ProviderType.GEMINI) {
                        val geminiModels = listOf("gemini-flash-lite-latest", "gemini-flash-latest", "gemini-pro-latest", "gemini-3.1-flash-lite")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(geminiModels) { m ->
                                FilterChip(
                                    selected = model == m,
                                    onClick = { model = m },
                                    label = { Text(m, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = current.copy(
                            name = name,
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            model = model,
                            type = providerType,
                            isConnected = apiKey.isNotBlank()
                        )
                        onSaveProvider(updated)
                        editingProvider = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProvider = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Connection Test Result Dialog
    if (testResult != null || isTesting) {
        AlertDialog(
            onDismissRequest = { testResult = null; isTesting = false },
            title = { Text("Provider Connection Test") },
            text = {
                if (isTesting) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Pinging provider endpoint...")
                    }
                } else {
                    val result = testResult!!
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (result.isSuccess) "Connection Succeeded" else "Connection Failed",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (result.availableModels.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Models: ${result.availableModels.take(4).joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { testResult = null; isTesting = false }) {
                    Text("Close")
                }
            }
        )
    }
}
