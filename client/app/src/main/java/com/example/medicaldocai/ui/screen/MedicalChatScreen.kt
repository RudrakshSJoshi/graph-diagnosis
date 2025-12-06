package com.example.medicaldocai.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicaldocai.R
import com.example.medicaldocai.backendconnect.RetrofitClient
import com.example.medicaldocai.dataModels.QueryRequest
import com.example.medicaldocai.ui.theme.MedicalAssistantTheme
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String,
    val timestamp: Long,
    val isTyping: Boolean = false
)
class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var currentQueryNum = 1

    // Constants for 429 error handling
    private val MAX_RETRIES = 10
    private val RETRY_DELAY_MS = 2000L // 2 seconds delay

    fun sendMessage(query: String) {
        if (query.isBlank() || _isLoading.value) return

        // 1. Add user message
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = query,
            sender = "user",
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMessage
        _isLoading.value = true

        // 2. Add typing indicator
        val typingMessage = ChatMessage(
            id = "typing_indicator",
            text = "...",
            sender = "assistant",
            timestamp = System.currentTimeMillis() + 1,
            isTyping = true
        )
        _messages.value = _messages.value + typingMessage

        viewModelScope.launch {
            // START OF TRY BLOCK
            try {
                var responseMessage: ChatMessage? = null
                var success = false
                var retryCount = 0

                while (!success && retryCount < MAX_RETRIES) {
                    try {
                        val request = QueryRequest(query = query, queryNum = currentQueryNum)
                        val response = RetrofitClient.api.sendQuery(request)

                        if (response.isSuccessful && response.body() != null) {
                            val apiData = response.body()!!
                            responseMessage = ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                text = apiData.response,
                                sender = "assistant",
                                timestamp = System.currentTimeMillis()
                            )
                            currentQueryNum++
                            success = true
                        } else if (response.code() == 429 && retryCount < MAX_RETRIES - 1) {
                            retryCount++
                            kotlinx.coroutines.delay(RETRY_DELAY_MS * retryCount)
                            continue
                        } else {
                            responseMessage = ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                text = "Error: Server returned code ${response.code()}",
                                sender = "assistant",
                                timestamp = System.currentTimeMillis()
                            )
                            success = true
                        }
                    } catch (e: Exception) {
                        if (retryCount >= MAX_RETRIES - 1) {
                            responseMessage = ChatMessage(
                                id = System.currentTimeMillis().toString(),
                                text = "Failed to connect: ${e.localizedMessage}",
                                sender = "assistant",
                                timestamp = System.currentTimeMillis()
                            )
                            success = true
                        } else {
                            retryCount++
                            kotlinx.coroutines.delay(RETRY_DELAY_MS * retryCount)
                        }
                    }
                }

                _messages.value = _messages.value.filterNot { it.isTyping } + (responseMessage ?: ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    text = "Unknown error occurred.",
                    sender = "assistant",
                    timestamp = System.currentTimeMillis()
                ))

            } finally {
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalChatScreen(
    onBackClick: () -> Unit = {},
    viewModel: ChatViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isDisclaimerVisible by remember { mutableStateOf(true) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Bot Logo",
                            modifier = Modifier
                                .padding(start = 30.dp)
                                .size(60.dp)
                                .align(Alignment.CenterStart),
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
//
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
            if (messages.isEmpty()) {
                EmptyStateUI(
                    onSuggestionClick = { suggestion ->
                        // Send the suggestion query via the ViewModel
                        viewModel.sendMessage(suggestion)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }
    }
}



@Composable
fun EmptyStateUI(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "👋",
                style = MaterialTheme.typography.displayMedium
            )
            Text(
                text = "Welcome to Your Medical Assistant",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Ask me about health precautions, symptoms, and medical suggestions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            QuickSuggestionButton(
                text = "How to prevent flu?",
                onClick = { onSuggestionClick("How to prevent flu?") }
            )
            QuickSuggestionButton(
                text = "Tips for better sleep",
                onClick = { onSuggestionClick("Tips for better sleep") }
            )
//            }
        }
    }
}

@Composable
fun QuickSuggestionButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }, // Make it clickable
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
@Composable
fun ChatMessageBubble(message: ChatMessage) {
    // 1. Handle Typing Indicator
    if (message.isTyping) {
        TypingIndicator()
        return
    }

    val isUserMessage = message.sender == "user"

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.7f

    val textColor = if (isUserMessage) Color.White else MaterialTheme.colorScheme.onSurface
    val bubbleColor = if (isUserMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
        ) {
            if(!isUserMessage){
                Spacer(Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Bot Logo",
                    modifier = Modifier
                        .size(36.dp) , // Keeps the click/layout bounds circular
                    contentScale = ContentScale.Fit // Ensures the transparent logo fits perfectly
                )
                Spacer(Modifier.width(8.dp))
            }

            Surface(

                modifier = Modifier.widthIn(max = maxBubbleWidth),

                shape = MaterialTheme.shapes.large,
                color = bubbleColor,
                shadowElevation = 2.dp
            ) {
                MarkdownText(
                    markdown = message.text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                )
            }
        }

        // Timestamp
        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Bot Logo",
            modifier = Modifier
                .size(36.dp) , // Keeps the click/layout bounds circular
            contentScale = ContentScale.Fit // Ensures the transparent logo fits perfectly
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 2.dp
        ) {
            Text(
                text = "Assistant is typing...",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
@Composable
fun MessageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp), // Reduced padding for better alignment
                placeholder = {
                    Text(
                        "Describe your health concern...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend(value) }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                minLines = 1,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = false
            )

            Button(
                onClick = { onSend(value) },
                enabled = value.isNotBlank() && !isLoading,
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.Bottom),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    if (isLoading) "..." else "Send",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
private fun formatTimestamp(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(calendar.time)
}
@Preview(name = "Empty State", showBackground = true)
@Composable
fun MedicalChatScreenPreview_Empty() {
    val previewViewModel = ChatViewModel()
    MedicalAssistantTheme{
        MedicalChatScreen(
            onBackClick = {},
            viewModel = previewViewModel
        )
    }
}
