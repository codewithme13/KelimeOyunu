package com.example.kelimeoyunu

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var loadingOverlay: RelativeLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        usernameEditText = findViewById(R.id.usernameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        registerButton = findViewById(R.id.registerButton)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateEmail(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validatePassword(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        registerButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (validateInputs(username, email, password)) {
                registerUser(username, email, password)
            }
        }
    }

    private fun registerUser(username: String, email: String, password: String) {
        loadingOverlay.visibility = View.VISIBLE

        registerButton.isEnabled = false

        Log.d(TAG, "Kullanıcı adı benzersizliği kontrol ediliyor: $username")

        db.collection("usernames")
            .document(username)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Bu kullanıcı adı zaten kullanımda. Lütfen başka bir kullanıcı adı seçin.", Toast.LENGTH_LONG).show()
                    registerButton.isEnabled = true
                } else {
                    createFirebaseUser(username, email, password)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Kullanıcı adı kontrolü hatası", e)
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Kullanıcı adı kontrolü sırasında hata: ${e.message}", Toast.LENGTH_SHORT).show()
                registerButton.isEnabled = true
            }
    }

    private fun createFirebaseUser(username: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                Log.d(TAG, "Firebase Auth kaydı başarılı")

                val userId = authResult.user?.uid
                if (userId != null) {
                    val userData = hashMapOf(
                        "username" to username,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis(),
                        "statistics" to hashMapOf(
                            "gamesPlayed" to 0,
                            "gamesWon" to 0,
                            "totalScore" to 0,
                            "highestWordScore" to 0
                        )
                    )

                    val usernameData = hashMapOf(
                        "userId" to userId
                    )

                    val batch = db.batch()
                    batch.set(db.collection("users").document(userId), userData)
                    batch.set(db.collection("usernames").document(username), usernameData)

                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "Kullanıcı bilgileri ve kullanıcı adı kaydedildi")
                            loadingOverlay.visibility = View.GONE
                            Toast.makeText(this, "Kayıt başarılı! Giriş yapabilirsiniz.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore kayıt hatası", e)
                            loadingOverlay.visibility = View.GONE
                            Toast.makeText(this, "Kullanıcı bilgileri kaydedilemedi: ${e.message}", Toast.LENGTH_LONG).show()

                            authResult.user?.delete()

                            registerButton.isEnabled = true
                        }
                } else {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Kayıt hatası: Kullanıcı ID'si alınamadı", Toast.LENGTH_SHORT).show()
                    registerButton.isEnabled = true
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Firebase Auth kayıt hatası", exception)
                loadingOverlay.visibility = View.GONE

                val errorMessage = when {
                    exception.message?.contains("email address is already in use") == true ->
                        "Bu e-posta adresi zaten kullanımda."
                    else -> "Kayıt hatası: ${exception.message}"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                registerButton.isEnabled = true
            }
    }
    private fun validateInputs(username: String, email: String, password: String): Boolean {
        if (username.isEmpty()) {
            usernameEditText.error = "Kullanıcı adı boş olamaz"
            return false
        }

        if (!isValidEmail(email)) {
            emailEditText.error = "Geçerli bir e-posta adresi giriniz"
            return false
        }

        if (!isValidPassword(password)) {
            passwordEditText.error = "Şifre en az 8 karakter uzunluğunda olmalı, büyük/küçük harf ve rakam içermelidir"
            return false
        }

        return true
    }

    private fun validateEmail(email: String) {
        if (email.isNotEmpty() && !isValidEmail(email)) {
            emailEditText.error = "Geçerli bir e-posta adresi giriniz"
        } else {
            emailEditText.error = null
        }
    }

    private fun validatePassword(password: String) {
        if (!isValidPassword(password) && password.isNotEmpty()) {
            passwordEditText.error = "Şifre en az 8 karakter uzunluğunda olmalı, büyük/küçük harf ve rakam içermelidir"
        } else {
            passwordEditText.error = null
        }
    }

    private fun isValidEmail(email: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return false
        }

        val parts = email.split("@")
        if (parts.size != 2) return false

        val localPart = parts[0]
        val domainPart = parts[1]

        if (localPart.isEmpty()) return false
        if (domainPart.isEmpty()) return false

        val domainParts = domainPart.split(".")
        if (domainParts.size < 2) return false

        val extension = domainParts.last()
        if (extension.length < 2) return false

        val allowedLocalChars = Pattern.compile("^[A-Za-z0-9._%+-]+$")
        if (!allowedLocalChars.matcher(localPart).matches()) return false

        val validExtensions = listOf("com", "net", "org", "edu", "gov", "info", "biz", "tr")
        if (!validExtensions.contains(extension.toLowerCase())) {
        }

        return true
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = Pattern.compile(
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$"
        )
        return passwordPattern.matcher(password).matches()
    }
}