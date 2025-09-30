package com.example.smartnotes.network
import com.example.smartnotes.models.ApiResponse
import com.example.smartnotes.models.LoginRequest
import com.example.smartnotes.models.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("/")
    suspend fun checkServer(): Response<ApiResponse>

    @GET("/api/test-db")
    suspend fun testDb(): Response<ApiResponse>

    @POST("/api/register")
    suspend fun register(@Body user: User): Response<ApiResponse>

    @POST("/api/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<ApiResponse>

    companion object {
        const val BASE_URL = "https://polinkkq.pythonanywhere.com/"
    }
}