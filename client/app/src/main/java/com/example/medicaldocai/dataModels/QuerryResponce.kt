package com.example.medicaldocai.dataModels

import com.google.gson.annotations.SerializedName

// 1. Model for SENDING (Matches your Request Body)
data class QueryRequest(
    val query: String,

    // JSON uses "query_num", but Kotlin prefers camelCase.
    // This annotation maps them automatically.
    @SerializedName("query_num")
    val queryNum: Int
)

// 2. Model for RECEIVING (Matches your Return JSON)
data class QueryResponse(
    val response: String,
    @SerializedName("continue")
    val shouldContinue: Boolean
)