package com.example.kelimeoyunu

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.example.kelimeoyunu.databinding.ActivityGameOptionsBinding
import com.example.kelimeoyunu.utils.FirebaseHelper

class GameOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOptionsBinding
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var userId: String
    private lateinit var username: String
    private var currentGameRequestId: String? = null
    private var loadingDialog: AlertDialog? = null
    private val TAG = "GameOptionsActivity"
    private val letterCountMap = mutableMapOf<Char, Int>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseHelper = FirebaseHelper()
        prepareLetterDistribution()

        userId = intent.getStringExtra("USER_ID") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        if (userId.isEmpty()) {
            Toast.makeText(this, "Kullanıcı bilgisi alınamadı. Lütfen tekrar giriş yapın.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.quickGame2MinButton.setOnClickListener {
            requestGame("fast")
        }
        binding.quickGame5MinButton.setOnClickListener {
            requestGame("medium")
        }
        binding.extendedGame12HButton.setOnClickListener {
            requestGame("slow")
        }
        binding.extendedGame24HButton.setOnClickListener {
            requestGame("day")
        }

    }

    private fun prepareLetterDistribution() {
        letterCountMap.apply {
            put('A', 12)
            put('B', 2)
            put('C', 2)
            put('Ç', 2)
            put('D', 2)
            put('E', 8)
            put('F', 1)
            put('G', 1)
            put('Ğ', 1)
            put('H', 1)
            put('I', 4)
            put('İ', 7)
            put('J', 1)
            put('K', 7)
            put('L', 7)
            put('M', 4)
            put('N', 5)
            put('O', 3)
            put('Ö', 1)
            put('P', 1)
            put('R', 6)
            put('S', 3)
            put('Ş', 2)
            put('T', 5)
            put('U', 3)
            put('Ü', 2)
            put('V', 1)
            put('Y', 2)
            put('Z', 2)
            put('*', 2)
        }
    }

    private fun updateLetterCount(letter: Char) {
        val currentCount = letterCountMap[letter] ?: 0
        if (currentCount > 0) {
            letterCountMap[letter] = currentCount - 1
        }
    }


    private fun requestGame(gameType: String) {
        try {
            setButtonsEnabled(false)

            showLoadingDialog()

            firebaseHelper.createGameRequest(userId, gameType, username) { success, result, requestId ->
                runOnUiThread {
                    if (success) {
                        currentGameRequestId = requestId

                        if (result?.startsWith("Rakip") == true) {
                            updateLoadingDialog(result)
                        } else {
                            dismissLoadingDialog()

                            if (result != null && !result.startsWith("Hata")) {
                                startGame(gameType, result)
                            } else {
                                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                                setButtonsEnabled(true)
                            }
                        }
                    } else {
                        dismissLoadingDialog()
                        Toast.makeText(this, "Hata: $result", Toast.LENGTH_SHORT).show()
                        setButtonsEnabled(true)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Oyun isteği oluşturma hatası", e)
            dismissLoadingDialog()
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            setButtonsEnabled(true)
        }
    }

    private fun showLoadingDialog() {
        loadingDialog = AlertDialog.Builder(this)
            .setTitle("Rakip Aranıyor")
            .setMessage("Rakip oyuncu bekleniyor...")
            .setCancelable(false)
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
                cancelGameRequest()
            }
            .create()

        loadingDialog?.show()
    }

    private fun updateLoadingDialog(message: String) {
        loadingDialog?.setMessage(message)
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun cancelGameRequest() {
        currentGameRequestId?.let { requestId ->
            firebaseHelper.cancelGameRequest(requestId) { success ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Oyun isteği iptal edildi", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "İstek iptal edilirken hata oluştu", Toast.LENGTH_SHORT).show()
                    }
                    setButtonsEnabled(true)
                }
            }
        } ?: run {
            setButtonsEnabled(true)
        }
    }


    private var gameStartingIds = HashSet<String>()

    private fun startGame(gameType: String, gameId: String) {

        if (gameStartingIds.contains(gameId)) {
            return
        }

        gameStartingIds.add(gameId)

        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("gameType", gameType)
            putExtra("gameId", gameId)
            putExtra("userId", userId)
            putExtra("username", username)
            putExtra("isLocalGame", true)
        }
        startActivity(intent)

        dismissLoadingDialog()

        finish()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.quickGame2MinButton.isEnabled = enabled
        binding.quickGame5MinButton.isEnabled = enabled
        binding.extendedGame12HButton.isEnabled = enabled
        binding.extendedGame24HButton.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissLoadingDialog()

        firebaseHelper.removeListeners()

        currentGameRequestId?.let { requestId ->
            firebaseHelper.cancelGameRequest(requestId) { }
        }
    }
}