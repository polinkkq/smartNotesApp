package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.example.smartnotes.R
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var logoutButton: ImageButton
    private lateinit var userName: TextView

    private val authRepository = AuthRepository()
    private val firebaseRepository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        menuButton = findViewById(R.id.menuButton)
        logoutButton = findViewById(R.id.logoutButton)
        userName = findViewById(R.id.userName)
    }

    private fun setupClickListeners() {

        menuButton.setOnClickListener {
            drawerLayout.open()
        }


        logoutButton.setOnClickListener {
            logout()
        }

    }

    private fun loadUserData() {
        val currentUser = authRepository.getCurrentUser()
        currentUser?.let { user ->
            CoroutineScope(Dispatchers.IO).launch {
                val userData = firebaseRepository.getUser(user.uid)
                runOnUiThread {
                    userData.getOrNull()?.let {
                        userName.text = "${it.lastName} ${it.firstName}"
                    }
                }
            }
        }
    }

    private fun openChat(chatName: String) {
        Toast.makeText(this, "Открыт чат: $chatName", Toast.LENGTH_SHORT).show()
        // Здесь будет переход к конкретному чату
    }

    private fun logout() {
        authRepository.logoutUser()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}