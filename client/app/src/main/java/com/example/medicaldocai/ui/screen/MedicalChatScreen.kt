package com.example.medicaldocai.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable // Import clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox // Import PullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Import State
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaldocai.R
import com.example.medicaldocai.ui.components.*
import com.example.medicaldocai.ui.theme.MedicalAssistantTheme
import com.example.medicaldocai.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalChatScreen(
    onBackClick: () -> Unit = {},
    viewModel: ChatViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // State for pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var isDisclaimerVisible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Bot Logo - Click to Refresh",
                            modifier = Modifier
                                .padding(start = 30.dp)
                                .size(60.dp)
                                .align(Alignment.CenterStart)
                                .clip(MaterialTheme.shapes.small) // Optional: nice touch for clickable items
                                .clickable {
                                    // 1. CLICK TO REFRESH LOGIC
                                    viewModel.refresh()
                                },
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "DOC AI",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Disclaimer
            AnimatedVisibility(
                visible = isDisclaimerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically()
            ) {
                MedicalDisclaimerCard(modifier = Modifier.fillMaxWidth().padding(12.dp))
            }

            // 2. SCROLL DOWN TO RESET (PULL TO REFRESH)
            // We wrap the list content in PullToRefreshBox
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        viewModel.refresh() // Call the reset function
                        delay(500) // Small delay for visual feedback
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Content inside PullToRefresh
                if (messages.isEmpty()) {
                    // We must wrap EmptyState in a scrollable Box so the pull gesture works
                    // even when there are no items to scroll.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        EmptyStateUI(
                            onSuggestionClick = { suggestion ->
                                viewModel.sendMessage(suggestion)
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(
                            items = messages.reversed(),
                            key = { it.id },
                            contentType = { it.sender }
                        ) { message ->
                            ChatMessageBubble(message)
                        }
                    }
                }
            }

            // Input Area
            MessageInputField(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = { query ->
                    if (query.isNotBlank()) {
                        viewModel.sendMessage(query)
                        inputText = ""
                    }
                },
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
fun MedicalChatScreenPreview_Empty() {
    val previewViewModel = ChatViewModel() // Ensure this mock has a refresh() method
    MedicalAssistantTheme{
        MedicalChatScreen(
            onBackClick = {},
            viewModel = previewViewModel
        )
    }
}