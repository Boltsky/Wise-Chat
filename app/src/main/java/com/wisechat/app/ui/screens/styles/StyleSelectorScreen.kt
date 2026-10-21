package com.wisechat.app.ui.screens.styles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisechat.app.domain.model.*
import com.wisechat.app.ui.WiseChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSelectorScreen(
    uiState: WiseChatUiState,
    onStyleCustomizationChanged: (StyleCustomization) -> Unit,
    onGenerateClicked: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var customization by remember { mutableStateOf(uiState.styleCustomization) }
    var selectedCategory by remember { mutableStateOf(ReplyStyle.StyleCategory.EVERYDAY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Reply Personality") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            onStyleCustomizationChanged(customization)
                            onGenerateClicked()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate ${customization.selectedStyle.title} Replies", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Category Tabs
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ReplyStyle.StyleCategory.values()) { cat ->
                        FilterChip(
                            selected = cat == selectedCategory,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // Styles Grid in selected category
            item {
                val stylesInCategory = ReplyStyle.values().filter { it.category == selectedCategory }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stylesInCategory.forEach { style ->
                        val isSelected = style == customization.selectedStyle
                        ElevatedCard(
                            onClick = {
                                customization = customization.copy(selectedStyle = style)
                                onStyleCustomizationChanged(customization)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = style.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        customization = customization.copy(selectedStyle = style)
                                        onStyleCustomizationChanged(customization)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Custom Controls Divider
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Tone & Intensity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ToneIntensity.values()) { intensity ->
                        FilterChip(
                            selected = intensity == customization.intensity,
                            onClick = {
                                customization = customization.copy(intensity = intensity)
                                onStyleCustomizationChanged(customization)
                            },
                            label = { Text(intensity.level, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // Length Selector
            item {
                Text(
                    text = "Reply Length",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ReplyLength.values()) { len ->
                        FilterChip(
                            selected = len == customization.length,
                            onClick = {
                                customization = customization.copy(length = len)
                                onStyleCustomizationChanged(customization)
                            },
                            label = { Text(len.label, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // Emoji Usage Selector
            item {
                Text(
                    text = "Emoji Usage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EmojiUsage.values()) { usage ->
                        FilterChip(
                            selected = usage == customization.emojiUsage,
                            onClick = {
                                customization = customization.copy(emojiUsage = usage)
                                onStyleCustomizationChanged(customization)
                            },
                            label = { Text(usage.label, fontSize = 13.sp) }
                        )
                    }
                }
            }

            // Conversational Objective
            item {
                Text(
                    text = "Conversational Objective (Optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ConversationalObjective.values()) { obj ->
                        val isSelected = obj == customization.objective
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                customization = customization.copy(
                                    objective = if (isSelected) null else obj
                                )
                                onStyleCustomizationChanged(customization)
                            },
                            label = { Text(obj.title, fontSize = 12.sp) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
