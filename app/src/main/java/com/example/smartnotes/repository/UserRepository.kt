package com.example.smartnotes.repository

import com.example.smartnotes.models.ApiResponse
import com.example.smartnotes.models.LoginRequest
import com.example.smartnotes.models.User
import com.example.smartnotes.network.RetrofitClient
import retrofit2.Response

class UserRepository {
    suspend fun register(user: User): Response<ApiResponse> {
        return RetrofitClient.apiService.register(user)
    }

    suspend fun login(loginRequest: LoginRequest): Response<ApiResponse> {
        return RetrofitClient.apiService.login(loginRequest)
    }

    suspend fun checkServer(): Response<ApiResponse> {
        return RetrofitClient.apiService.checkServer()
    }

    suspend fun testDb(): Response<ApiResponse> {
        return RetrofitClient.apiService.testDb()
    }
}