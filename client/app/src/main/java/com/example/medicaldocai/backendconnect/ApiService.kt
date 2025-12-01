package com.example.medicaldocai.backendconnect

import com.example.medicaldocai.dataModels.QueryRequest
import com.example.medicaldocai.dataModels.QueryResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("query") // The endpoint path
    suspend fun sendQuery(@Body request: QueryRequest): Response<QueryResponse>
}