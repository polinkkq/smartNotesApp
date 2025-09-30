package com.example.smartnotes.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartnotes.models.ApiResponse
import com.example.smartnotes.models.LoginRequest
import com.example.smartnotes.models.User
import com.example.smartnotes.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {
    private val repository = UserRepository()

    private val _registerState = MutableStateFlow<ApiState<ApiResponse>>(ApiState.Idle)
    val registerState: StateFlow<ApiState<ApiResponse>> = _registerState

    private val _loginState = MutableStateFlow<ApiState<ApiResponse>>(ApiState.Idle)
    val loginState: StateFlow<ApiState<ApiResponse>> = _loginState

    fun register(user: User) {
        _registerState.value = ApiState.Loading
        viewModelScope.launch {
            try {
                val response = repository.register(user)
                if (response.isSuccessful && response.body()?.status == "success") {
                    _registerState.value = ApiState.Success(response.body()!!)
                } else {
                    _registerState.value = ApiState.Error(
                        response.body()?.error ?: "Registration failed"
                    )
                }
            } catch (e: Exception) {
                _registerState.value = ApiState.Error("Network error: ${e.message}")
            }
        }
    }

    fun login(loginRequest: LoginRequest) {
        _loginState.value = ApiState.Loading
        viewModelScope.launch {
            try {
                val response = repository.login(loginRequest)
                if (response.isSuccessful && response.body()?.status == "success") {
                    _loginState.value = ApiState.Success(response.body()!!)
                } else {
                    _registerState.value = ApiState.Error(
                        response.body()?.error ?: "Login failed"
                    )
                }
            } catch (e: Exception) {
                _loginState.value = ApiState.Error("Network error: ${e.message}")
            }
        }
    }
}