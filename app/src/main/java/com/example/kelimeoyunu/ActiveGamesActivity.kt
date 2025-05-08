package com.example.kelimeoyunu

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kelimeoyunu.adapters.ActiveGamesAdapter
import com.example.kelimeoyunu.utils.FirebaseHelper
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

// Kullanıcının aktif oyunlarını listeleyen ve yöneten aktivite
class ActiveGamesActivity : AppCompatActivity() {

    private lateinit var activeGamesRecyclerView: RecyclerView
    private lateinit var noGamesTextView: TextView
    private lateinit var deleteAllGamesButton: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var firebaseHelper: FirebaseHelper
    private lateinit var userId: String
    private lateinit var username: String

    // Aktivite başlatıldığında çağrılır, arayüz elemanlarını ayarlar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_games)

        activeGamesRecyclerView = findViewById(R.id.activeGamesRecyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        deleteAllGamesButton = findViewById(R.id.deleteAllGamesButton)

        noGamesTextView = TextView(this).apply {
            text = "Henüz aktif oyununuz bulunmuyor."
            textSize = 18f
            visibility = View.GONE
        }

        deleteAllGamesButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        swipeRefreshLayout.setOnRefreshListener {
            checkExpiredGamesAndLoad()
        }

        activeGamesRecyclerView.layoutManager = LinearLayoutManager(this)

        userId = intent.getStringExtra("USER_ID") ?: ""
        username = intent.getStringExtra("USERNAME") ?: ""

        firebaseHelper = FirebaseHelper()

        checkExpiredGamesAndLoad()
    }

    // Süresi dolan oyunları kontrol eder ve aktif oyunları yükler
    private fun checkExpiredGamesAndLoad() {
        swipeRefreshLayout.isRefreshing = true

        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("Oyunlar kontrol ediliyor...")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        firebaseHelper.checkAndFinishExpiredGames { success ->
            runOnUiThread {
                loadingDialog.dismiss()
                loadActiveGames()
            }
        }
    }

    // Tüm aktif oyunları silme onayı sorar
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tüm Aktif Oyunları Sil")
            .setMessage("Tüm aktif oyunlarınızı silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Evet, Sil") { _, _ ->
                deleteAllActiveGames()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // Kullanıcının tüm aktif oyunlarını siler
    private fun deleteAllActiveGames() {
        swipeRefreshLayout.isRefreshing = true

        firebaseHelper.deleteAllActiveGames(userId) { success ->
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false

                if (success) {
                    Toast.makeText(this, "Tüm aktif oyunlar başarıyla silindi", Toast.LENGTH_SHORT).show()
                    loadActiveGames()
                } else {
                    Toast.makeText(this, "Oyunlar silinirken bir hata oluştu", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Aktif oyunları Firebase'den çeker ve ekranda gösterir
    private fun loadActiveGames() {
        swipeRefreshLayout.isRefreshing = true

        firebaseHelper.getActiveGames(userId) { gamesList ->
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false

                if (gamesList.isEmpty()) {
                    activeGamesRecyclerView.visibility = View.GONE
                    noGamesTextView.visibility = View.VISIBLE

                    if (noGamesTextView.parent == null) {
                        val rootView = findViewById<View>(android.R.id.content)
                        (rootView as ViewGroup).addView(noGamesTextView)
                    }
                } else {
                    activeGamesRecyclerView.visibility = View.VISIBLE
                    noGamesTextView.visibility = View.GONE

                    val adapter = ActiveGamesAdapter(this, gamesList, userId, username)
                    activeGamesRecyclerView.adapter = adapter
                }
            }
        }
    }

    // Aktivite ön plana geldiğinde çağrılır, oyunları günceller
    override fun onResume() {
        super.onResume()
        checkExpiredGamesAndLoad()
    }
}