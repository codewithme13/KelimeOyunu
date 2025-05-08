package com.example.kelimeoyunu.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kelimeoyunu.GameActivity
import com.example.kelimeoyunu.R
import android.widget.Button

// Aktif oyunları listeleyen ve yöneten RecyclerView adapter sınıfı
class ActiveGamesAdapter(
    private val context: Context,
    private val gamesList: List<Map<String, Any>>,
    private val userId: String,
    private val username: String
) : RecyclerView.Adapter<ActiveGamesAdapter.GameViewHolder>() {

    // Oyun öğelerinin görünüm elemanlarını tutan sınıf
    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val gameInfoTextView: TextView = view.findViewById(R.id.gameInfoTextView)
        val yourScoreTextView: TextView = view.findViewById(R.id.yourScoreTextView)
        val opponentScoreTextView: TextView = view.findViewById(R.id.opponentScoreTextView)
        val turnInfoTextView: TextView = view.findViewById(R.id.turnInfoTextView)
        val continueButton: Button = view.findViewById(R.id.continueButton)
    }

    // Görünüm öğelerini oluşturur
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.active_game_item, parent, false)
        return GameViewHolder(view)
    }

    // Veriyi görünüm öğelerine bağlar
    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = gamesList[position]

        val gameId = game["id"] as? String ?: ""
        val gameType = game["gameType"] as? String ?: "normal"
        val players = game["players"] as? Map<*, *> ?: mapOf<String, Any>()
        val currentTurn = game["currentTurn"] as? String ?: ""

        var opponentId: String? = null
        var opponentName: String? = null
        var yourScore = 0
        var opponentScore = 0

        for ((playerId, playerData) in players) {
            val data = playerData as? Map<*, *> ?: continue

            if (playerId.toString() != userId) {
                opponentId = playerId.toString()
                opponentName = data["username"] as? String ?: "Rakip"
                opponentScore = (data["score"] as? Number)?.toInt() ?: 0
            } else {
                yourScore = (data["score"] as? Number)?.toInt() ?: 0
            }
        }

        holder.gameInfoTextView.text = "Oyuncular: ${username} vs ${opponentName ?: "Rakip"}"
        holder.yourScoreTextView.text = "Puanınız: $yourScore"
        holder.opponentScoreTextView.text = "Rakip Puanı: $opponentScore"

        val turnText = if (currentTurn == userId) {
            "Sıra: Sizde"
        } else {
            "Sıra: Rakipte"
        }
        holder.turnInfoTextView.text = turnText

        holder.continueButton.setOnClickListener {
            val intent = Intent(context, GameActivity::class.java).apply {
                putExtra("gameId", gameId)
                putExtra("gameType", gameType)
                putExtra("userId", userId)
                putExtra("username", username)
                putExtra("opponentUsername", opponentName)
                putExtra("isNewGame", false)
            }
            context.startActivity(intent)
        }
    }

    // Listedeki toplam öğe sayısını döndürür
    override fun getItemCount() = gamesList.size
}