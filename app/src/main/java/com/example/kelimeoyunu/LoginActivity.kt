package com.example.kelimeoyunu

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Lütfen kullanıcı adı ve şifre giriniz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUserWithUsername(username, password)
        }
    }

    private fun loginUserWithUsername(username: String, password: String) {
        loadingOverlay.visibility = View.VISIBLE

        loginButton.isEnabled = false

        Log.d(TAG, "Kullanıcı adı ile giriş işlemi başlatılıyor: $username")

        db.collection("usernames").document(username).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userId = document.getString("userId")
                    if (userId != null) {
                        db.collection("users").document(userId).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    val email = userDoc.getString("email")
                                    if (email != null) {
                                        signInWithEmailAndPassword(email, password, username)
                                    } else {
                                        loadingOverlay.visibility = View.GONE
                                        Toast.makeText(this, "Kullanıcı e-posta bilgisi bulunamadı!", Toast.LENGTH_SHORT).show()
                                        loginButton.isEnabled = true
                                    }
                                } else {
                                    loadingOverlay.visibility = View.GONE
                                    Toast.makeText(this, "Kullanıcı bilgileri bulunamadı!", Toast.LENGTH_SHORT).show()
                                    loginButton.isEnabled = true
                                }
                            }
                            .addOnFailureListener { e ->
                                loadingOverlay.visibility = View.GONE
                                Toast.makeText(this, "Kullanıcı bilgileri alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                loginButton.isEnabled = true
                            }
                    } else {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(this, "Kullanıcı ID bilgisi bulunamadı!", Toast.LENGTH_SHORT).show()
                        loginButton.isEnabled = true
                    }
                } else {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Kullanıcı adı bulunamadı!", Toast.LENGTH_SHORT).show()
                    loginButton.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Kullanıcı adı kontrolü sırasında hata: ${e.message}", Toast.LENGTH_SHORT).show()
                loginButton.isEnabled = true
            }
    }

    private fun signInWithEmailAndPassword(email: String, password: String, username: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                Log.d(TAG, "Firebase Auth girişi başarılı")

                val userId = authResult.user?.uid
                if (userId != null) {
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { document ->
                            loadingOverlay.visibility = View.GONE

                            if (document.exists()) {
                                Log.d(TAG, "Kullanıcı bilgileri alındı")

                                proceedToGameOptions(userId, username)
                            } else {
                                Log.e(TAG, "Firestore'da kullanıcı bilgisi bulunamadı")
                                Toast.makeText(this, "Kullanıcı bilgileri bulunamadı!", Toast.LENGTH_SHORT).show()
                                loginButton.isEnabled = true
                            }
                        }
                        .addOnFailureListener { e ->
                            loadingOverlay.visibility = View.GONE
                            Toast.makeText(this, "Kullanıcı bilgileri alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                            loginButton.isEnabled = true
                        }
                } else {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Kullanıcı ID'si alınamadı!", Toast.LENGTH_SHORT).show()
                    loginButton.isEnabled = true
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Firebase Auth giriş hatası", exception)
                loadingOverlay.visibility = View.GONE

                val errorMessage = when {
                    exception.message?.contains("password is invalid") == true ->
                        "Şifre hatalı!"
                    exception.message?.contains("no user record") == true ->
                        "Kullanıcı bulunamadı!"
                    exception.message?.contains("network") == true ->
                        "Ağ hatası. İnternet bağlantınızı kontrol edin."
                    exception.message?.contains("blocked") == true ->
                        "Çok fazla hatalı giriş denemesi. Lütfen daha sonra tekrar deneyin."
                    else -> "Giriş hatası: ${exception.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                loginButton.isEnabled = true
            }
    }

    private fun proceedToGameOptions(userId: String, username: String) {
        val gameIntent = Intent(this, GameDashboardActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("USERNAME", username)
        }
        startActivity(gameIntent)
        finish()
    }
}