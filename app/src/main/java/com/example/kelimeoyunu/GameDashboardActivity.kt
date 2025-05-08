package com.example.kelimeoyunu

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.kelimeoyunu.utils.FirebaseHelper

// Oyun ana menüsünü ve navigasyonu yöneten aktivite
class GameDashboardActivity : AppCompatActivity() {

    private lateinit var newGameButton: Button
    private lateinit var activeGamesButton: Button
    private lateinit var finishedGamesButton: Button
    private lateinit var welcomeTextView: TextView
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var userId: String
    private lateinit var username: String

    // Aktivite başlatıldığında çağrılır, UI elemanlarını ve olay dinleyicilerini ayarlar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_dashboard)

        firebaseHelper = FirebaseHelper()

        newGameButton = findViewById(R.id.newGameButton)
        activeGamesButton = findViewById(R.id.activeGamesButton)
        finishedGamesButton = findViewById(R.id.finishedGamesButton)
        welcomeTextView = findViewById(R.id.welcomeTextView)

        userId = intent.getStringExtra("USER_ID") ?: ""
        username = intent.getStringExtra("USERNAME") ?: "Oyuncu"

        if (userId.isEmpty()) {
            goToLogin()
            return
        }

        welcomeTextView.text = "Hoş geldin, $username!"

        newGameButton.setOnClickListener {
            val gameOptionsIntent = Intent(this, GameOptionsActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("USERNAME", username)
            }
            startActivity(gameOptionsIntent)
        }

        activeGamesButton.setOnClickListener {
            val activeGamesIntent = Intent(this, ActiveGamesActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("USERNAME", username)
            }
            startActivity(activeGamesIntent)
        }

        finishedGamesButton.setOnClickListener {
            val finishedGamesIntent = Intent(this, FinishedGamesActivity::class.java).apply {
                putExtra("USER_ID", userId)
            }
            startActivity(finishedGamesIntent)
        }
    }

    // Periyodik oyun kontrol işlemini yöneten handler ve runnable
    private val handler = Handler(Looper.getMainLooper())
    private val gameCheckRunnable = object : Runnable {
        override fun run() {
            firebaseHelper.checkAndFinishExpiredGames { success ->
                handler.postDelayed(this, 60 * 1000)
            }
        }
    }

    // Aktivite ön plana geldiğinde çağrılır, periyodik kontrolleri başlatır
    override fun onResume() {
        super.onResume()
        handler.post(gameCheckRunnable)
    }

    // Aktivite arka plana geçtiğinde çağrılır, periyodik kontrolleri durdurur
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(gameCheckRunnable)
    }

    // Kullanıcı oturumu yoksa giriş ekranına yönlendirir
    private fun goToLogin() {
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }
}