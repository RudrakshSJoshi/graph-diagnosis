package com.example.medicaldocai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicaldocai.backendconnect.RetrofitClient
import com.example.medicaldocai.model.ChatMessage
import com.example.medicaldocai.model.QueryRequest
import com.example.medicaldocai.model.QueryResponse // Assuming this is your response class
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentQueryNum = 1

    // Track the current API job so we can cancel it if refresh is clicked
    private var currentJob: Job? = null

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

        // 3. API Call with Job tracking
        currentJob = viewModelScope.launch {
            try {
                // Call the retry helper function
                val apiData = sendQueryWithRetry(query, currentQueryNum)

                if (apiData != null) {
                    val responseMessage = ChatMessage(
                        id = System.currentTimeMillis().toString(),
                        text = apiData.response,
                        sender = "assistant",
                        timestamp = System.currentTimeMillis()
                    )
                    // Remove typing indicator and add response
                    _messages.value = _messages.value.filterNot { it.isTyping } + responseMessage
                    currentQueryNum++
                } else {
                    // This block runs if retries fail or other non-429 errors occur
                    handleError("Unable to get a response from server.")
                }

            } catch (e: Exception) {
                // If the job was cancelled (e.g., by refresh), don't show an error
                if (e is kotlinx.coroutines.CancellationException) throw e
                handleError("Failed to connect: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
                currentJob = null
            }
        }
    }

    /**
     * Resets the entire ViewModel state.
     * Cancels any ongoing API requests immediately.
     */
    fun refresh() {
        // 1. Cancel ongoing API call if any
        currentJob?.cancel()
        currentJob = null

        // 2. Reset local UI state immediately (Optimistic update)
        _messages.value = emptyList()
        _isLoading.value = false
        currentQueryNum = 1

        // 3. Call Backend to reset memory
        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.resetMemory()

                if (!response.isSuccessful) {
                    // Optional: Handle server error (e.g., Log it or show a Toast)
                    // We typically don't revert the UI here because the user wanted a clear screen anyway.
                    System.err.println("Backend failed to reset memory: ${response.code()}")
                }
            } catch (e: Exception) {
                // Optional: Handle network error
                System.err.println("Network error during refresh: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Recursive-style or loop-based retry logic for 429 errors.
     * Uses exponential backoff (wait time increases: 2s -> 4s -> 8s).
     */
    private suspend fun sendQueryWithRetry(query: String, queryNum: Int): QueryResponse? {
        val maxRetries = 3
        var currentAttempt = 0
        var delayTime = 2000L // Start with 2 seconds

        while (currentAttempt <= maxRetries) {
            try {
                val request = QueryRequest(query = query, queryNum = queryNum)
                val response = RetrofitClient.api.sendQuery(request)

                if (response.isSuccessful && response.body() != null) {
                    return response.body()
                } else if (response.code() == 429) {
                    // HIT 429: Rate Limit Exceeded
                    if (currentAttempt < maxRetries) {
                        currentAttempt++
                        // Optional: update UI to show "Retrying..." if desired,
                        // or just keep the typing indicator.
                        delay(delayTime)
                        delayTime *= 2 // Exponential backoff
                        continue // Retry loop
                    } else {
                        handleError("Server is busy (Too many requests). Please try again later.")
                        return null
                    }
                } else {
                    // Other standard errors (404, 500, etc)
                    handleError("Error: Server returned code ${response.code()}")
                    return null
                }
            } catch (e: Exception) {
                // Network errors usually throw exceptions immediately
                throw e
            }
        }
        return null
    }

    private fun handleError(message: String) {
        val errorMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            text = message,
            sender = "assistant",
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value.filterNot { it.isTyping } + errorMessage
    }
}