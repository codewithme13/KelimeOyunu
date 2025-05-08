package com.example.kelimeoyunu

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kelimeoyunu.adapters.FinishedGamesAdapter
import com.example.kelimeoyunu.utils.FirebaseHelper

// Kullanıcının tamamlanmış oyunlarını listeleyen aktivite
class FinishedGamesActivity : AppCompatActivity() {

    private lateinit var finishedGamesRecyclerView: RecyclerView
    private lateinit var noGamesTextView: TextView
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var userId: String

    // Aktivite başlatıldığında çağrılır, arayüzü hazırlar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finished_games)

        finishedGamesRecyclerView = findViewById(R.id.finishedGamesRecyclerView)

        noGamesTextView = TextView(this).apply {
            text = "Henüz tamamlanmış oyununuz bulunmuyor."
            textSize = 18f
            visibility = View.GONE
        }

        finishedGamesRecyclerView.layoutManager = LinearLayoutManager(this)

        userId = intent.getStringExtra("USER_ID") ?: ""

        firebaseHelper = FirebaseHelper()

        loadFinishedGames()
    }

    // Tamamlanmış oyunları Firebase'den yükler ve gösterir
    private fun loadFinishedGames() {
        firebaseHelper.getFinishedGames(userId) { gamesList ->
            runOnUiThread {
                if (gamesList.isEmpty()) {
                    finishedGamesRecyclerView.visibility = View.GONE
                    noGamesTextView.visibility = View.VISIBLE

                    if (noGamesTextView.parent == null) {
                        val rootView = findViewById<View>(android.R.id.content)
                        (rootView as ViewGroup).addView(noGamesTextView)
                    }
                } else {
                    finishedGamesRecyclerView.visibility = View.VISIBLE
                    noGamesTextView.visibility = View.GONE

                    val adapter = FinishedGamesAdapter(gamesList, userId)
                    finishedGamesRecyclerView.adapter = adapter
                }
            }
        }
    }

    // Aktivite ön plana geldiğinde çağrılır, oyunları günceller
    override fun onResume() {
        super.onResume()
        loadFinishedGames()
    }
}