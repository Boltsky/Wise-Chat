package com.wisechat.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisechat.app.domain.model.ChatMessage
import com.wisechat.app.domain.model.ConversationContext
import com.wisechat.app.domain.model.ConversationStarter
import com.wisechat.app.domain.model.ReplyStyle
import com.wisechat.app.domain.model.ReplySuggestion
import com.wisechat.app.domain.model.SenderRole
import com.wisechat.app.domain.model.StarterCategory

enum class OverlayTab(val title: String) {
    REPLIES("Chat Replies"),
    STARTERS("Pick-Up & Starters")
}

@Composable
fun OverlayPanelContent(
    isAnalyzing: Boolean,
    isGenerating: Boolean,
    conversationContext: ConversationContext?,
    suggestions: List<ReplySuggestion>,
    selectedStyle: ReplyStyle,
    errorMessage: String?,
    selectedTab: OverlayTab = OverlayTab.REPLIES,
    selectedStarterCategory: StarterCategory = StarterCategory.SWEET_AWW,
    startersList: List<ConversationStarter> = emptyList(),
    isGeneratingStarters: Boolean = false,
    starterTopicInput: String = "",
    onTabSelected: (OverlayTab) -> Unit = {},
    onStarterCategorySelected: (StarterCategory) -> Unit = {},
    onStarterTopicChanged: (String) -> Unit = {},
    onGenerateStartersClicked: () -> Unit = {},
    onCopyStarter: ((ConversationStarter) -> Unit)? = null,
    onAnalyzeScreenClicked: () -> Unit,
    onRescanClicked: () -> Unit,
    onStyleSelected: (ReplyStyle) -> Unit,
    onSelectTargetMessage: (String) -> Unit,
    onTargetMessageChanged: (String) -> Unit,
    onGenerateClicked: () -> Unit,
    onRegenerateClicked: () -> Unit,
    onOpenFullAppClicked: () -> Unit = {},
    onMinimizeClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onCopySuggestion: ((ReplySuggestion) -> Unit)? = null
) {
    val context = LocalContext.current
    var copiedSuggestionId by remember { mutableStateOf<String?>(null) }
    var copiedStarterId by remember { mutableStateOf<String?>(null) }
    var isMessagePickerExpanded by remember { mutableStateOf(false) }
    var isEditingTarget by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(22.dp))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                    keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP
                ) {
                    onMinimizeClicked()
                    true
                } else {
                    false
                }
            },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // --- HEADER BAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Wise Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Floating Reply Assistant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenFullAppClicked,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Wise Chat App",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (conversationContext != null && !isAnalyzing) {
                        IconButton(
                            onClick = onRescanClicked,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-scan Screen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onMinimizeClicked,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onCloseClicked,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- TAB SWITCHER (Replies vs Starters) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OverlayTab.values().forEach { tab ->
                    val isTabSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isTabSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (tab == OverlayTab.REPLIES) Icons.Default.ChatBubbleOutline else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isTabSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTabSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == OverlayTab.STARTERS) {
                // --- STARTERS & PICK-UP LINES TAB CONTENT ---
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(StarterCategory.values()) { cat ->
                        val isCatSelected = cat == selectedStarterCategory
                        FilterChip(
                            selected = isCatSelected,
                            onClick = { onStarterCategorySelected(cat) },
                            leadingIcon = { Text(cat.iconEmoji, fontSize = 12.sp) },
                            label = {
                                Text(
                                    cat.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional bio / topic personalization input
                OutlinedTextField(
                    value = starterTopicInput,
                    onValueChange = onStarterTopicChanged,
                    placeholder = {
                        Text("Bio hook or photo topic (optional)", fontSize = 12.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onGenerateStartersClicked,
                    enabled = !isGeneratingStarters,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    if (isGeneratingStarters) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crafting Unique Openers...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (starterTopicInput.isNotBlank()) "Generate Custom Openers"
                            else "Generate AI Openers",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedStarterCategory.title} (${startersList.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1-Tap Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    startersList.forEach { starter ->
                        val isCopied = copiedStarterId == starter.id
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCopied) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = starter.tag.ifBlank { starter.category.title },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Wise Chat Opener", starter.text)
                                            clipboard.setPrimaryClip(clip)
                                            copiedStarterId = starter.id
                                            onCopyStarter?.invoke(starter)
                                            Toast.makeText(context, "Opener copied! Ready to send.", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isCopied) "Copied" else "Copy", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = starter.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal
                                )

                                if (starter.whyItWorks.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "💡 ${starter.whyItWorks}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- REPLIES TAB CONTENT ---
                // --- STATE DISPLAY ---
                if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scanning screen for chat bubbles...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Distinguishing incoming and outgoing messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMinimizeClicked,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dismiss")
                    }
                    Button(
                        onClick = onAnalyzeScreenClicked,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try Again")
                    }
                }
            } else if (conversationContext == null) {
                // Initial State: Analyze Screen Button
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Open a conversation in WhatsApp, Telegram, or any chat app, then tap below to generate instant smart replies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAnalyzeScreenClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Current Chat", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenFullAppClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Full Wise Chat App", fontSize = 13.sp)
                    }
                }
            } else {
                // --- CONVERSATION RECOGNIZED ---
                val targetMsg = conversationContext.resolvedTargetMessage
                val messagesList = conversationContext.messages
                val receivedMessages = messagesList.filter { it.sender == SenderRole.INCOMING }
                val displayMessages = if (receivedMessages.isNotEmpty()) receivedMessages else messagesList

                // Target Message Display Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val roleLabel = when (targetMsg?.sender) {
                                    SenderRole.INCOMING -> "Them (Received)"
                                    SenderRole.OUTGOING -> "You (Sent)"
                                    else -> "Message"
                                }
                                val badgeColor = when (targetMsg?.sender) {
                                    SenderRole.INCOMING -> MaterialTheme.colorScheme.primary
                                    SenderRole.OUTGOING -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.outline
                                }

                                Text(
                                    text = "Replying to:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = badgeColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = roleLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Edit target message button
                                IconButton(
                                    onClick = {
                                        editedText = targetMsg?.text ?: ""
                                        isEditingTarget = !isEditingTarget
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit text",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Toggle message picker dropdown (only for received messages)
                                if (displayMessages.size > 1) {
                                    TextButton(
                                        onClick = { isMessagePickerExpanded = !isMessagePickerExpanded },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = if (isMessagePickerExpanded) "Hide" else "Pick (${displayMessages.size})",
                                            fontSize = 11.sp
                                        )
                                        Icon(
                                            imageVector = if (isMessagePickerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isEditingTarget) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editedText,
                                    onValueChange = { editedText = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = false,
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (editedText.isNotBlank()) {
                                            onTargetMessageChanged(editedText)
                                        }
                                        isEditingTarget = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Save edit",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = targetMsg?.text ?: "No message selected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // --- EXPANDABLE MESSAGE PICKER (RECEIVED ONLY) ---
                AnimatedVisibility(
                    visible = isMessagePickerExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Select a received message to reply to:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            displayMessages.forEach { msg ->
                                val isSelected = msg.id == targetMsg?.id

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onSelectTargetMessage(msg.id)
                                            isMessagePickerExpanded = false
                                        }
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (msg.hasMediaOrSticker) "Them 🖼️" else "Them",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- STYLE SELECTOR CHIPS ---
                val popularStyles = listOf(
                    ReplyStyle.CASUAL,
                    ReplyStyle.FUNNY,
                    ReplyStyle.FLIRTY,
                    ReplyStyle.INQUISITIVE,
                    ReplyStyle.PROFESSIONAL,
                    ReplyStyle.EMPATHETIC,
                    ReplyStyle.CONCISE
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(popularStyles) { style ->
                        FilterChip(
                            selected = style == selectedStyle,
                            onClick = {
                                onStyleSelected(style)
                            },
                            label = { Text(style.title, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- REPLIES SECTION ---
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Crafting personalized replies in ${selectedStyle.title} style...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Replies (${suggestions.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Top Regenerate button for quick access
                        TextButton(
                            onClick = onRegenerateClicked,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            val isCopied = copiedSuggestionId == suggestion.id

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCopied) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Normal
                                        )
                                        if (suggestion.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = suggestion.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    FilledTonalButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Wise Chat Reply", suggestion.text)
                                            clipboard.setPrimaryClip(clip)
                                            copiedSuggestionId = suggestion.id
                                            onCopySuggestion?.invoke(suggestion)
                                            Toast.makeText(context, "Reply copied! Ready to paste.", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isCopied) "Copied" else "Copy", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Action Bar: Regenerate & Re-scan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRegenerateClicked,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Replies", fontSize = 13.sp)
                        }

                        Button(
                            onClick = onRescanClicked,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-scan Screen", fontSize = 13.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onGenerateClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate ${selectedStyle.title} Replies")
                    }
                }
            }
        }
    }
}
}
