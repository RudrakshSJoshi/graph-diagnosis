package com.example.medicaldocai.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
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

// --- Data Class ---

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String, // "user" or "assistant"
    val timestamp: Long,
    val isTyping: Boolean = false
)

// --- ViewModel ---

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // track the query number (1-based) as required by your API
    private var currentQueryNum = 1

    fun sendMessage(query: String) {
        if (query.isBlank() || _isLoading.value) return

        // 1. Add user message immediately
        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = query,
            sender = "user",
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMessage

        // 2. Set loading and add typing indicator
        _isLoading.value = true
        val typingMessage = ChatMessage(
            id = "typing_indicator",
            text = "...",
            sender = "assistant",
            timestamp = System.currentTimeMillis() + 1,
            isTyping = true
        )
        _messages.value = _messages.value + typingMessage

        // 3. Launch coroutine for ACTUAL API call
        viewModelScope.launch {
            try {
                // Prepare the request object
                val request = QueryRequest(
                    query = query,
                    queryNum = currentQueryNum
                )

                // Make the Network Call
                val response = RetrofitClient.api.sendQuery(request)

                // 4. Handle Response
                if (response.isSuccessful && response.body() != null) {
                    val apiData = response.body()!!

                    val responseMessage = ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = apiData.response, // The actual string from server
                        sender = "assistant",
                        timestamp = System.currentTimeMillis()
                    )

                    // Update the list: Remove typing indicator, add real response
                    _messages.value = _messages.value.filterNot { it.isTyping } + responseMessage

                    // Increment query number for the next turn
                    currentQueryNum++

                    // Optional: You can check apiData.shouldContinue here if you need logic for it

                } else {
                    // Handle Server Error (e.g., 404, 500)
                    val errorMessage = ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = "Error: Server returned code ${response.code()}",
                        sender = "assistant",
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value.filterNot { it.isTyping } + errorMessage
                }

            } catch (e: Exception) {
                // 5. Handle Network Failure (No internet, timeout, etc.)
                val failureMessage = ChatMessage(
                    id = System.currentTimeMillis().toString(),
                    text = "Failed to connect: ${e.localizedMessage}",
                    sender = "assistant",
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value.filterNot { it.isTyping } + failureMessage

            } finally {
                // Always turn off loading, even if it failed
                _isLoading.value = false
            }
        }
    }
}

// --- Main Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalChatScreen(
    onBackClick: () -> Unit = {},
    viewModel: ChatViewModel // Inject your ViewModel
) {
    // Collect state from the ViewModel
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Local UI state, not business logic
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
                        // --- 1. LEFT SIDE: The Logo ---
                        // Aligned to the start (left), with simple padding
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Bot Logo",
                            modifier = Modifier
                                .padding(start = 30.dp) // This replaces the left Spacer
                                .size(60.dp)
                                .align(Alignment.CenterStart), // Sticks to the left
                            contentScale = ContentScale.Fit
                        )

                        // --- 2. CENTER: The Text ---
                        // Aligned to the absolute center of the Box (and screen)
                        // It ignores the logo's existence entirely
                        Text(
                            text = "DOC AI",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                },
//                navigationIcon = {
//                    IconButton(onClick = onBackClick) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                            contentDescription = "Back",
//                            tint = MaterialTheme.colorScheme.onSurface
//                        )
//                    }
//                },
//                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.surface,
//                    scrolledContainerColor = MaterialTheme.colorScheme.surface
//                )
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
            // Medical Disclaimer Card (Animated)
            AnimatedVisibility(
                visible = isDisclaimerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically()
            ) {
                MedicalDisclaimerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            // Messages List
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
                        items = messages.reversed(), // Show latest message at the bottom
                        key = { it.id },
                        contentType = { it.sender }
                    ) { message ->
                        ChatMessageBubble(message)
                    }
                }
            }

            // Input Field
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

// --- UI Components ---

@Composable
fun MedicalDisclaimerCard(modifier: Modifier = Modifier) {
//    Surface(
//        modifier = modifier,
//        shape = MaterialTheme.shapes.medium,
//        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
//        shadowElevation = 2.dp
//    ) {
//        Column(
//            modifier = Modifier.padding(12.dp),
//            verticalArrangement = Arrangement.spacedBy(6.dp)
//        ) {
//            Text(
//                text = "⚕️ Important",
//                style = MaterialTheme.typography.labelLarge,
//                fontWeight = FontWeight.Bold,
//                color = MaterialTheme.colorScheme.onSecondaryContainer
//            )
//            Text(
//                text = "This is an AI-powered assistant and NOT a substitute for professional medical advice. Always consult a qualified doctor for serious health concerns or emergencies.",
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSecondaryContainer,
//                lineHeight = 16.sp
//            )
//        }
//    }
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

//            // Quick suggestions
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(top = 16.dp),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                QuickSuggestionButton(
//                    text = "What are cold symptoms?",
//                    onClick = { onSuggestionClick("What are cold symptoms?") }
//                )
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

// --- Utility Function ---

private fun formatTimestamp(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(calendar.time)
}

/**
 * Preview for the MedicalChatScreen in its initial empty state.
 */
@Preview(name = "Empty State", showBackground = true)
@Composable
fun MedicalChatScreenPreview_Empty() {
    // 1. Create a dummy ViewModel for the preview.
    val previewViewModel = ChatViewModel()

    // 2. Wrap the screen in your app's theme for correct styling.
    MedicalAssistantTheme{
        MedicalChatScreen(
            onBackClick = {},
            viewModel = previewViewModel
        )
    }
}

/**
 * Preview for the MedicalChatScreen with some messages already populated.
 */
//@Preview(name = "With Content", showBackground = true)
//@Composable
//fun MedicalChatScreenPreview_WithContent() {
//    val previewViewModel = ChatViewModel()
//
//    // Manually add messages to the ViewModel for this preview scenario.
//    // We can do this by launching a coroutine that updates the ViewModel's state.
//    // In a real preview, you might create a FakeChatViewModel for easier state setup.
//    remember {
//        previewViewModel.viewModelScope.launch {
//            // Directly update the internal state for preview purposes
//            (previewViewModel.messages as MutableStateFlow).value = listOf(
//                ChatMessage(
//                    "1",
//                    "Hello, I have a headache.",
//                    "user",
//                    System.currentTimeMillis() - 20000
//                ),
//                ChatMessage(
//                    "2",
//                    "I understand your concern. Based on your query 'Hello, I have a headache.', here are some medical precautions... Remember, this is not a real medical advice.",
//                    "assistant",
//                    System.currentTimeMillis() - 10000
//                ),
//                ChatMessage(
//                    "3",
//                    "What are the symptoms of a cold?",
//                    "user",
//                    System.currentTimeMillis()
//                )
//            )
//        }
//    }
//
//    MedicalAssistantTheme {
//        MedicalChatScreen(
//            onBackClick = {},
//            viewModel = previewViewModel
//        )
//    }
//}
//
///**
// * Preview showing the loading state after a user sends a message.
// */
//@Preview(name = "Loading State", showBackground = true)
//@Composable
//fun MedicalChatScreenPreview_Loading() {
//    val previewViewModel = ChatViewModel()
//
//    remember {
//        previewViewModel.viewModelScope.launch {
//            (previewViewModel.messages as MutableStateFlow).value = listOf(
//                ChatMessage("1", "How to prevent flu?", "user", System.currentTimeMillis() - 1000),
//                ChatMessage(
//                    "typing",
//                    "...",
//                    "assistant",
//                    System.currentTimeMillis(),
//                    isTyping = true
//                )
//            );
//            (previewViewModel.isLoading as MutableStateFlow).value = true
//        }
//    }
//
//    MedicalAssistantTheme {
//        MedicalChatScreen(
//            onBackClick = {},
//            viewModel = previewViewModel
//        )
//    }
//}