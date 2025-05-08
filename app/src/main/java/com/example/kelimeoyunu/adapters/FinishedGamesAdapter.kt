package com.example.kelimeoyunu.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kelimeoyunu.R

// Tamamlanmış oyunları listeleyen ve görüntüleyen RecyclerView adapter sınıfı
class FinishedGamesAdapter(
    private val gamesList: List<Map<String, Any>>,
    private val userId: String
) : RecyclerView.Adapter<FinishedGamesAdapter.GameViewHolder>() {

    // Biten oyun öğelerinin görünüm elemanlarını tutan sınıf
    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val gameCard: CardView = view.findViewById(R.id.gameCard)
        val gameResultIconImageView: ImageView = view.findViewById(R.id.gameResultIconImageView)
        val opponentNameTextView: TextView = view.findViewById(R.id.opponentNameTextView)
        val yourScoreTextView: TextView = view.findViewById(R.id.yourScoreTextView)
        val opponentScoreTextView: TextView = view.findViewById(R.id.opponentScoreTextView)
        val gameResultTextView: TextView = view.findViewById(R.id.gameResultTextView)
        val gameTypeTextView: TextView = view.findViewById(R.id.gameTypeTextView)
        val gameTimeTextView: TextView = view.findViewById(R.id.gameTimeTextView)
    }

    // Görünüm öğelerini oluşturur
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.finished_game_item, parent, false)
        return GameViewHolder(view)
    }

    // Veriyi görünüm öğelerine bağlar ve oyun sonucuna göre formatlar
    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = gamesList[position]

        val gameType = game["gameType"] as? String ?: "normal"
        val finishedAt = (game["finishedAt"] as? Number)?.toLong() ?: 0
        val players = game["players"] as? Map<*, *> ?: mapOf<String, Any>()
        val winner = game["winner"] as? String ?: ""

        var opponentName: String? = null
        var yourScore = 0
        var opponentScore = 0

        for ((playerId, playerData) in players) {
            val data = playerData as? Map<*, *> ?: continue

            if (playerId.toString() != userId) {
                opponentName = data["username"] as? String ?: "Rakip"
                opponentScore = (data["score"] as? Number)?.toInt() ?: 0
            } else {
                yourScore = (data["score"] as? Number)?.toInt() ?: 0
            }
        }

        holder.opponentNameTextView.text = "Rakip: ${opponentName ?: "Bilinmiyor"}"
        holder.yourScoreTextView.text = "Puanınız: $yourScore"
        holder.opponentScoreTextView.text = "Rakip Puanı: $opponentScore"

        val gameTypeText = when (gameType) {
            "fast" -> "Hızlı Oyun (2 dk)"
            "medium" -> "Normal Oyun (5 dk)"
            "slow" -> "Uzun Oyun (12 dk)"
            "day" -> "Günlük Oyun (24 saat)"
            else -> "Normal Oyun"
        }
        holder.gameTypeTextView.text = "Tür: $gameTypeText"

        val gameDate = java.util.Date(finishedAt)
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        holder.gameTimeTextView.text = "Tarih: ${dateFormat.format(gameDate)}"

        val isWinner = userId == winner

        if (isWinner) {
            holder.gameCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.win_green))
            holder.gameResultTextView.text = "Sonuç: Kazandınız"
            holder.gameResultIconImageView.setImageResource(R.drawable.ic_trophy)
        } else {
            holder.gameCard.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.lose_red))
            holder.gameResultTextView.text = "Sonuç: Kaybettiniz"
            holder.gameResultIconImageView.setImageResource(R.drawable.ic_x_mark)
        }

        val textColor = if (isWinner) Color.WHITE else Color.WHITE
        holder.opponentNameTextView.setTextColor(textColor)
        holder.yourScoreTextView.setTextColor(textColor)
        holder.opponentScoreTextView.setTextColor(textColor)
        holder.gameResultTextView.setTextColor(textColor)
        holder.gameTypeTextView.setTextColor(textColor)
        holder.gameTimeTextView.setTextColor(textColor)
    }

    // Listedeki toplam öğe sayısını döndürür
    override fun getItemCount() = gamesList.size
}