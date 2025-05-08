package com.example.kelimeoyunu

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.FrameLayout
import com.example.kelimeoyunu.utils.TurkishDictionary
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout
import com.example.kelimeoyunu.databinding.TileCellBinding
import android.app.AlertDialog
import android.view.View
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.PopupMenu
import android.content.Intent
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.widget.Toast
import android.view.MotionEvent
import com.example.kelimeoyunu.utils.FirebaseHelper
import com.google.firebase.database.ValueEventListener
import android.graphics.Color
import android.widget.TextView
import com.example.kelimeoyunu.databinding.ActivityGameBinding
import com.google.firebase.database.DatabaseError

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var turkishDictionary: TurkishDictionary
    private var gameTimeInMillis: Long = 120000
    private var currentPlayerTiles = mutableListOf<Char>()
    private var gameBoard = Array(15) { Array(15) { BoardCell() } }
    private var countDownTimer: CountDownTimer? = null
    private val visibleEffects = mutableMapOf<Pair<Int, Int>, SpecialEffect>()
    private val hiddenEffects = mutableMapOf<Pair<Int, Int>, SpecialEffect>()

    private lateinit var username: String

    private var letterBanPending = false
    private var regionBanPending = false
    private var bannedRegionSide: String? = null

    private var isMyTurn = true
    private lateinit var currentUserId: String
    private lateinit var gameId: String
    private lateinit var gameListener: ValueEventListener

    private var wordFrameView: View? = null
    private var wordScoreTextView: TextView? = null

    private var selectedLetterIndex: Int? = null
    private var selectedLetterView: View? = null
    private var selectedLetter: Char? = null

    private var isDragging = false
    private var draggedLetterIndex: Int? = null
    private var draggedLetterView: View? = null
    private var draggedLetter: Char? = null
    private var originalX = 0f
    private var originalY = 0f

    private var isValid: Boolean = true

    private var remainingLetters = 86

    private var hasRegionBanJoker = false
    private var hasLetterBanJoker = false
    private var hasExtraMoveJoker = false

    private var extraMoveUsed = false

    private var bannedRegion: String? = null

    private val frozenLetterIndices = mutableListOf<Int>()

    private var placementCounter = 0

    // Aktivite oluşturulduğunda başlatılır
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        turkishDictionary = TurkishDictionary(this)

        gameId = intent.getStringExtra("gameId") ?: ""
        currentUserId = intent.getStringExtra("userId") ?: ""
        username = intent.getStringExtra("username") ?: "Kullanıcı"
        val opponentUsername = intent.getStringExtra("opponentUsername") ?: "Rakip"
        val gameType = intent.getStringExtra("gameType") ?: "normal"
        val isNewGame = intent.getBooleanExtra("isNewGame", true)

        binding.userInfo.text = "Kullanıcı: $username | Puan: 0"
        binding.opponentInfo.text = "Rakip: $opponentUsername | Puan: 0"

        remainingLetters = 86
        updateRemainingLettersDisplay()

        listenForGameUpdates()

        setupGameBasedOnType(gameType)

        setupJokerButtons()

        listenForJokerEffects()

        createGameBoard()

        distributeLettersToPlayer()

        setupButtonListeners()

        startTimer()

        createWordFrame()

        updateTurnInfo()

        if (!isNewGame) {
            loadExistingGameState()
        } else {
            distributeLettersToPlayer()
        }
    }

    private fun loadExistingGameState() {
        try {
            val firebaseHelper = FirebaseHelper()

            val loadingDialog = AlertDialog.Builder(this)
                .setMessage("Oyun yükleniyor...")
                .setCancelable(false)
                .create()
            loadingDialog.show()

            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val boardData = snapshot.child("board").value
                        if (boardData != null) {
                            Log.d("GameActivity", "Board veri türü: ${boardData.javaClass}")

                            when (boardData) {
                                is List<*> -> {
                                    loadBoardState(boardData)
                                }
                                is Map<*, *> -> {
                                    val convertedBoard = mutableListOf<List<String>>()
                                    for (i in 0 until 15) {
                                        val rowKey = i.toString()
                                        val rowData = boardData[rowKey] as? Map<*, *> ?: continue

                                        val row = mutableListOf<String>()
                                        for (j in 0 until 15) {
                                            val colKey = j.toString()
                                            val cellValue = rowData[colKey] as? String ?: ""
                                            row.add(cellValue)
                                        }
                                        convertedBoard.add(row)
                                    }
                                    loadBoardState(convertedBoard)
                                }
                                else -> {
                                    Log.e("GameActivity", "Bilinmeyen tahta veri formatı")
                                    Toast.makeText(this, "Oyun tahtası yüklenemedi. Bilinmeyen veri formatı.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        val playersData = snapshot.child("players").value as? Map<*, *>
                        if (playersData != null) {
                            val playerData = playersData[currentUserId] as? Map<*, *>
                            if (playerData != null) {
                                val remainingLetters = playerData["remainingLetters"] as? String
                                if (remainingLetters != null) {
                                    loadPlayerLetters(remainingLetters)
                                }

                                // Donmuş harfleri kontrol et
                                val frozenLettersData = playerData["frozenLetters"]
                                if (frozenLettersData != null) {
                                    when (frozenLettersData) {
                                        is List<*> -> {
                                            frozenLetterIndices.clear()
                                            for (indexObj in frozenLettersData) {
                                                val index = (indexObj as? Long)?.toInt() ?: continue
                                                frozenLetterIndices.add(index)
                                            }
                                        }
                                        is Map<*, *> -> {
                                            frozenLetterIndices.clear()
                                            for (entry in frozenLettersData) {
                                                val index = entry.key.toString().toIntOrNull() ?: continue
                                                frozenLetterIndices.add(index)
                                            }
                                        }
                                    }

                                    // Harfleri dondur
                                    for (index in frozenLetterIndices) {
                                        if (index < binding.playerTiles.childCount) {
                                            val letterView = binding.playerTiles.getChildAt(index)
                                            letterView.setBackgroundColor(Color.BLACK)
                                            letterView.alpha = 0.5f
                                            letterView.isEnabled = false
                                            letterView.isClickable = false
                                            letterView.setOnTouchListener { _, _ -> true }
                                        }
                                    }

                                    if (frozenLetterIndices.isNotEmpty()) {
                                        Toast.makeText(this, "Bazı harfleriniz dondurulmuş durumda!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        updateScoresFromFirebase(snapshot)

                        val letterBag = snapshot.child("letterBag").value as? String
                        if (letterBag != null) {
                            remainingLetters = letterBag.length
                            updateRemainingLettersDisplay()
                        }

                        // Son hamleyi kontrol et ve görselleştir
                        val lastMoveData = snapshot.child("lastMove").value as? Map<*, *>
                        if (lastMoveData != null) {
                            val moveUserId = lastMoveData["userId"] as? String ?: ""
                            val word = lastMoveData["word"] as? String ?: ""
                            val isValid = lastMoveData["isValid"] as? Boolean ?: true

                            if (word.isNotEmpty()) {
                                val wordLetters = findWordOnBoard(word)

                                if (isValid) {
                                    for ((row, col) in wordLetters) {
                                        val cell = gameBoard[row][col]
                                        cell.view?.setBackgroundResource(R.drawable.valid_word_cell)
                                    }
                                    Log.d("GameActivity", "Son geçerli kelime görselleştirildi: $word")
                                } else {
                                    for ((row, col) in wordLetters) {
                                        val cell = gameBoard[row][col]
                                        cell.view?.setBackgroundResource(R.drawable.invalid_word_cell)
                                    }
                                    Log.d("GameActivity", "Son geçersiz kelime görselleştirildi: $word")
                                }
                            }
                        }

                        val currentTurn = snapshot.child("currentTurn").value as? String
                        isMyTurn = currentTurn == currentUserId
                        updateTurnInfo()

                        loadingDialog.dismiss()
                    } else {
                        loadingDialog.dismiss()
                        Toast.makeText(this, "Oyun bulunamadı!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    loadingDialog.dismiss()
                    Toast.makeText(this, "Oyun yüklenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Beklenmeyen hata: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    // Oyun tahtası durumunu yükler
    private fun loadBoardState(boardData: List<*>) {
        try {
            for (i in 0 until minOf(boardData.size, 15)) {
                val row = boardData[i]

                when (row) {
                    is List<*> -> {
                        for (j in 0 until minOf(row.size, 15)) {
                            val cellValue = row[j] as? String ?: ""

                            if (cellValue.isNotEmpty()) {
                                val letter = cellValue[0]
                                val cellView = gameBoard[i][j].view

                                if (cellView != null) {
                                    val cellBinding = TileCellBinding.bind(cellView)
                                    cellBinding.letterValue.text = letter.toString()
                                    cellBinding.letterPoint.text = getLetterPoint(letter).toString()
                                    gameBoard[i][j].isOccupied = true
                                    gameBoard[i][j].letter = letter

                                    cellBinding.specialEffect.visibility = View.GONE
                                }
                            }
                        }
                    }
                    is Map<*, *> -> {
                        for (j in 0 until 15) {
                            val colKey = j.toString()
                            val cellValue = row[colKey] as? String ?: ""

                            if (cellValue.isNotEmpty()) {
                                val letter = cellValue[0]
                                val cellView = gameBoard[i][j].view

                                if (cellView != null) {
                                    val cellBinding = TileCellBinding.bind(cellView)
                                    cellBinding.letterValue.text = letter.toString()
                                    cellBinding.letterPoint.text = getLetterPoint(letter).toString()
                                    gameBoard[i][j].isOccupied = true
                                    gameBoard[i][j].letter = letter

                                    cellBinding.specialEffect.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }

            Log.d("GameActivity", "Tahta başarıyla yüklendi")
        } catch (e: Exception) {
            Log.e("GameActivity", "Tahta yüklenirken hata", e)
            Toast.makeText(this, "Tahta yüklenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Oyuncunun elindeki harfleri yükler
    private fun loadPlayerLetters(letters: String) {
        try {
            binding.playerTiles.removeAllViews()
            currentPlayerTiles.clear()

            for (i in letters.indices) {
                val letter = letters[i]
                currentPlayerTiles.add(letter)

                val tileBinding = TileCellBinding.inflate(layoutInflater)
                val tileView = tileBinding.root

                tileBinding.letterValue.text = letter.toString()
                tileBinding.letterPoint.text = getLetterPoint(letter).toString()

                tileView.setOnClickListener {
                    selectLetter(i, tileView, letter)
                }

                val layoutParams = FrameLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.harf_size),
                    resources.getDimensionPixelSize(R.dimen.harf_size)
                )
                layoutParams.setMargins(5, 5, 5, 5)

                binding.playerTiles.addView(tileView, layoutParams)
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Oyuncu harfleri yüklenirken hata", e)
        }
    }

    // Firebase'den oyuncu puanlarını günceller
    private fun updateScoresFromFirebase(snapshot: DataSnapshot) {
        try {
            val playersData = snapshot.child("players").value as? Map<*, *> ?: return

            var yourScore = 0
            var opponentName = "Rakip"
            var opponentScore = 0

            for ((playerId, playerData) in playersData) {
                val data = playerData as? Map<*, *> ?: continue

                if (playerId.toString() == currentUserId) {
                    yourScore = (data["score"] as? Number)?.toInt() ?: 0
                } else {
                    opponentName = data["username"] as? String ?: "Rakip"
                    opponentScore = (data["score"] as? Number)?.toInt() ?: 0
                }
            }

            binding.userInfo.text = "Kullanıcı: $username | Puan: $yourScore"
            binding.opponentInfo.text = "Rakip: $opponentName | Puan: $opponentScore"
        } catch (e: Exception) {
            Log.e("GameActivity", "Puanlar güncellenirken hata", e)
        }
    }

    // Oyuncu puanını Firebase'de günceller
    private fun updateScoreOnFirebase(userId: String, newScore: Int) {
        val firebaseHelper = FirebaseHelper()
        firebaseHelper.updatePlayerScore(gameId, userId, newScore) { success ->
            if (!success) {
                Log.e("GameActivity", "Puan güncellenemedi!")
            }
        }
    }

    // Oyun durumunu dinler ve değişiklikleri işler
    private fun listenForGameUpdates() {
        val firebaseHelper = FirebaseHelper()
        gameListener = firebaseHelper.listenForGameUpdates(gameId) { gameData ->
            try {
                val gameStatus = gameData["status"] as? String ?: ""

                if (gameStatus == "finished") {
                    val winner = gameData["winner"] as? String ?: ""
                    val finishReason = gameData["finishReason"] as? String ?: "unknown"

                    firebaseHelper.removeGameListener(gameId, gameListener)

                    runOnUiThread {
                        var message = ""
                        if (winner == currentUserId) {
                            message = "Tebrikler! Oyunu kazandınız!"
                        } else {
                            message = when (finishReason) {
                                "surrender" -> "Rakip oyundan çekildi. Kazandınız!"
                                "timeout" -> "Rakibin süresi doldu. Kazandınız!"
                                else -> "Oyun sona erdi. Rakip kazandı."
                            }
                        }

                        AlertDialog.Builder(this)
                            .setTitle("Oyun Sona Erdi")
                            .setMessage(message)
                            .setCancelable(false)
                            .setPositiveButton("Tamam") { _, _ ->
                                navigateToMainScreen()
                            }
                            .show()
                    }

                    return@listenForGameUpdates
                }

                val currentTurn = gameData["currentTurn"] as? String ?: ""
                val oldIsMyTurn = isMyTurn
                isMyTurn = currentTurn == currentUserId

                if (!oldIsMyTurn && isMyTurn) {
                    startTimer()
                }

                updateTurnInfo()

                val players = gameData["players"] as? Map<*, *> ?: return@listenForGameUpdates

                for ((playerId, playerData) in players) {
                    val data = playerData as? Map<*, *> ?: continue
                    val score = (data["score"] as? Number)?.toInt() ?: 0
                    val username = data["username"] as? String ?: "Oyuncu"

                    if (playerId.toString() == currentUserId) {
                        binding.userInfo.text = "Kullanıcı: $username | Puan: $score"
                    } else {
                        binding.opponentInfo.text = "Rakip: $username | Puan: $score"
                    }
                }

                updateBoardState(gameData)

                val letterBag = gameData["letterBag"] as? String ?: ""
                remainingLetters = letterBag.length
                updateRemainingLettersDisplay()

                checkLastMove(gameData)

            } catch (e: Exception) {
                Log.e("GameActivity", "Oyun verisi işlenirken hata", e)
            }
        }
    }

    // Tahta durumunu günceller
    private fun updateBoardState(gameData: Map<String, Any>) {
        try {
            val boardData = gameData["board"] as? List<*> ?: return

            for (i in 0 until boardData.size) {
                val row = boardData[i] as? List<*> ?: continue

                for (j in 0 until row.size) {
                    val cellValue = row[j] as? String ?: ""

                    if (cellValue.isNotEmpty() && !gameBoard[i][j].isOccupied) {
                        val letter = cellValue[0]
                        val cellView = gameBoard[i][j].view

                        if (cellView != null) {
                            val cellBinding = TileCellBinding.bind(cellView)
                            cellBinding.letterValue.text = letter.toString()
                            cellBinding.letterPoint.text = getLetterPoint(letter).toString()
                            gameBoard[i][j].isOccupied = true
                            gameBoard[i][j].letter = letter
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Tahta güncellenirken hata", e)
        }
    }

    // Son hamleyi kontrol eder ve görsel vurgular ekler
    private fun checkLastMove(gameData: Map<String, Any>) {
        try {
            val lastMove = gameData["lastMove"] as? Map<*, *> ?: return
            val moveUserId = lastMove["userId"] as? String ?: ""
            val word = lastMove["word"] as? String ?: ""
            val score = (lastMove["score"] as? Number)?.toInt() ?: 0
            val isValid = lastMove["isValid"] as? Boolean ?: true

            if (moveUserId != currentUserId) {
                if (word.isNotEmpty()) {
                    val wordLetters = findWordOnBoard(word)

                    if (isValid) {
                        for ((row, col) in wordLetters) {
                            val cell = gameBoard[row][col]
                            cell.view?.setBackgroundResource(R.drawable.valid_word_cell)
                        }
                    } else {
                        for ((row, col) in wordLetters) {
                            val cell = gameBoard[row][col]
                            cell.view?.setBackgroundResource(R.drawable.invalid_word_cell)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Son hamle kontrolünde hata", e)
        }
    }

    // Belirli bir kelimeyi tahta üzerinde bulur
    private fun findWordOnBoard(word: String): List<Pair<Int, Int>> {
        val wordPositions = mutableListOf<Pair<Int, Int>>()

        val firstLetter = word[0]
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (gameBoard[i][j].letter == firstLetter) {

                    // Yatay kontrol
                    if (j + word.length <= 15) {
                        var match = true
                        for (k in 0 until word.length) {
                            if (gameBoard[i][j+k].letter != word[k]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            for (k in 0 until word.length) {
                                wordPositions.add(Pair(i, j+k))
                            }
                            return wordPositions
                        }
                    }

                    // Dikey kontrol
                    if (i + word.length <= 15) {
                        var match = true
                        for (k in 0 until word.length) {
                            if (gameBoard[i+k][j].letter != word[k]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            for (k in 0 until word.length) {
                                wordPositions.add(Pair(i+k, j))
                            }
                            return wordPositions
                        }
                    }

                    // Sağ çapraz kontrol
                    if (i + word.length <= 15 && j + word.length <= 15) {
                        var match = true
                        for (k in 0 until word.length) {
                            if (gameBoard[i+k][j+k].letter != word[k]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            for (k in 0 until word.length) {
                                wordPositions.add(Pair(i+k, j+k))
                            }
                            return wordPositions
                        }
                    }

                    // Sol çapraz kontrol
                    if (i + word.length <= 15 && j - word.length + 1 >= 0) {
                        var match = true
                        for (k in 0 until word.length) {
                            if (gameBoard[i+k][j-k].letter != word[k]) {
                                match = false
                                break
                            }
                        }
                        if (match) {
                            for (k in 0 until word.length) {
                                wordPositions.add(Pair(i+k, j-k))
                            }
                            return wordPositions
                        }
                    }
                }
            }
        }

        return wordPositions
    }

    // Kelime çerçevesini oluşturur
    private fun createWordFrame() {
        val frameLayout = FrameLayout(this)
        frameLayout.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        frameLayout.background = ContextCompat.getDrawable(this, R.drawable.word_frame_background)
        frameLayout.visibility = View.GONE

        val scoreTextView = TextView(this)
        val scoreParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        scoreParams.gravity = Gravity.BOTTOM or Gravity.END
        scoreParams.setMargins(0, 0, 8, 4)
        scoreTextView.layoutParams = scoreParams
        scoreTextView.setTextColor(Color.WHITE)
        scoreTextView.textSize = 12f
        scoreTextView.text = "+0"

        frameLayout.addView(scoreTextView)

        binding.gameBoard.addView(frameLayout)

        wordFrameView = frameLayout
        wordScoreTextView = scoreTextView
    }


    private fun updateTurnInfo() {
        if (isMyTurn) {
            binding.userInfo.setBackgroundResource(R.drawable.active_player_background)
            binding.opponentInfo.setBackgroundResource(R.drawable.inactive_player_background)
            binding.turnIndicator.text = "Sıra Sizde"
            binding.turnIndicator.setTextColor(ContextCompat.getColor(this, R.color.green))

            if (frozenLetterIndices.isNotEmpty()) {
                // Harflerin hala dondurulmuş olup olmadığını kontrol et
                val firebaseHelper = FirebaseHelper()
                firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                    .child("activeEffects").get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.exists() || snapshot.child("type").getValue(String::class.java) != "letterBan") {
                            // Letter ban kaldırıldıysa harfleri serbest bırak
                            for (index in frozenLetterIndices) {
                                if (index < binding.playerTiles.childCount) {
                                    val letterView = binding.playerTiles.getChildAt(index)
                                    letterView.setBackgroundResource(R.drawable.cell_background)
                                    letterView.alpha = 1.0f
                                    letterView.isEnabled = true
                                    letterView.isClickable = true

                                    val letter = if (letterView is FrameLayout) {
                                        val binding = TileCellBinding.bind(letterView)
                                        binding.letterValue.text.toString()[0]
                                    } else {
                                        ' '
                                    }

                                    letterView.setOnTouchListener(null)
                                    letterView.setOnClickListener {
                                        selectLetter(index, letterView, letter)
                                    }
                                }
                            }
                            frozenLetterIndices.clear()
                            Toast.makeText(this, "Harfleriniz artık serbest!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }

            val firebaseHelper = FirebaseHelper()
            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                .child("activeEffects").get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val effectType = snapshot.child("type").getValue(String::class.java) ?: ""

                        if (effectType == "regionBan") {
                            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                                .child("activeEffects").removeValue()
                                .addOnSuccessListener {
                                    Log.d("JokerEffects", "Bölge yasağı kaldırıldı - bir tur tamamlandı")

                                    for (i in 0 until 15) {
                                        for (j in 0 until 15) {
                                            if (gameBoard[i][j].isBanned) {
                                                gameBoard[i][j].isBanned = false
                                                gameBoard[i][j].view?.alpha = 1.0f
                                            }
                                        }
                                    }
                                    bannedRegion = null

                                    Toast.makeText(this@GameActivity,
                                        "Bölge yasağı kaldırıldı!",
                                        Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }

            startTimer()
        } else {
            binding.userInfo.setBackgroundResource(R.drawable.inactive_player_background)
            binding.opponentInfo.setBackgroundResource(R.drawable.active_player_background)
            binding.turnIndicator.text = "Sıra Rakipte"
            binding.turnIndicator.setTextColor(ContextCompat.getColor(this, R.color.red))
            countDownTimer?.cancel()

            applyPendingJokerEffects()
        }
    }

    private fun applyPendingJokerEffects() {
        if (letterBanPending) {
            letterBanPending = false

            val firebaseHelper = FirebaseHelper()
            val jokerData = hashMapOf(
                "type" to "letterBan",
                "activatedBy" to currentUserId,
                "activatedAt" to ServerValue.TIMESTAMP,
                "status" to "active"
            )

            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                .child("activeEffects").setValue(jokerData)
                .addOnSuccessListener {
                    Log.d("JokerEffects", "Harf yasağı jokeri aktifleştirildi.")
                }
                .addOnFailureListener { e ->
                    Log.e("JokerEffects", "Harf yasağı jokeri aktifleştirilemedi: ${e.message}")
                }
        }

        if (regionBanPending && bannedRegionSide != null) {
            regionBanPending = false

            val firebaseHelper = FirebaseHelper()
            val jokerData = hashMapOf(
                "type" to "regionBan",
                "bannedSide" to bannedRegionSide,
                "activatedBy" to currentUserId,
                "activatedAt" to ServerValue.TIMESTAMP,
                "status" to "active" // Beklemede değil, aktif
            )

            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                .child("activeEffects").setValue(jokerData)
                .addOnSuccessListener {
                    Log.d("JokerEffects", "Bölge yasağı jokeri aktifleştirildi.")
                }
                .addOnFailureListener { e ->
                    Log.e("JokerEffects", "Bölge yasağı jokeri aktifleştirilemedi: ${e.message}")
                }
        }
    }

    // Oyun türüne göre ayarları yapar
    private fun setupGameBasedOnType(gameType: String) {
        when (gameType) {
            "fast" -> gameTimeInMillis = 120000
            "medium" -> gameTimeInMillis = 300000
            "slow" -> gameTimeInMillis = 43200000
            "day" -> gameTimeInMillis = 86400000
            else -> gameTimeInMillis = 120000
        }

        setupSpecialEffects()
    }

    // Özel efektleri ayarlar
    private fun setupSpecialEffects() {
        visibleEffects[Pair(0, 5)] = SpecialEffect.H2
        visibleEffects[Pair(0, 9)] = SpecialEffect.H2
        visibleEffects[Pair(1, 6)] = SpecialEffect.H2
        visibleEffects[Pair(1, 8)] = SpecialEffect.H2
        visibleEffects[Pair(5, 0)] = SpecialEffect.H2
        visibleEffects[Pair(5, 5)] = SpecialEffect.H2
        visibleEffects[Pair(5, 9)] = SpecialEffect.H2
        visibleEffects[Pair(5, 14)] = SpecialEffect.H2
        visibleEffects[Pair(6, 1)] = SpecialEffect.H2
        visibleEffects[Pair(6, 6)] = SpecialEffect.H2
        visibleEffects[Pair(6, 8)] = SpecialEffect.H2
        visibleEffects[Pair(6, 13)] = SpecialEffect.H2
        visibleEffects[Pair(8, 1)] = SpecialEffect.H2
        visibleEffects[Pair(8, 6)] = SpecialEffect.H2
        visibleEffects[Pair(8, 8)] = SpecialEffect.H2
        visibleEffects[Pair(8, 13)] = SpecialEffect.H2
        visibleEffects[Pair(9, 0)] = SpecialEffect.H2
        visibleEffects[Pair(9, 5)] = SpecialEffect.H2
        visibleEffects[Pair(9, 9)] = SpecialEffect.H2
        visibleEffects[Pair(9, 14)] = SpecialEffect.H2
        visibleEffects[Pair(13, 6)] = SpecialEffect.H2
        visibleEffects[Pair(13, 8)] = SpecialEffect.H2
        visibleEffects[Pair(14, 5)] = SpecialEffect.H2
        visibleEffects[Pair(14, 9)] = SpecialEffect.H2

        visibleEffects[Pair(1, 1)] = SpecialEffect.H3
        visibleEffects[Pair(1, 13)] = SpecialEffect.H3
        visibleEffects[Pair(4, 4)] = SpecialEffect.H3
        visibleEffects[Pair(4, 10)] = SpecialEffect.H3
        visibleEffects[Pair(10, 4)] = SpecialEffect.H3
        visibleEffects[Pair(10, 10)] = SpecialEffect.H3
        visibleEffects[Pair(13, 1)] = SpecialEffect.H3
        visibleEffects[Pair(13, 13)] = SpecialEffect.H3

        visibleEffects[Pair(2, 7)] = SpecialEffect.K2
        visibleEffects[Pair(3, 3)] = SpecialEffect.K2
        visibleEffects[Pair(3, 11)] = SpecialEffect.K2
        visibleEffects[Pair(7, 2)] = SpecialEffect.K2
        visibleEffects[Pair(7, 12)] = SpecialEffect.K2
        visibleEffects[Pair(11, 3)] = SpecialEffect.K2
        visibleEffects[Pair(11, 11)] = SpecialEffect.K2
        visibleEffects[Pair(12, 7)] = SpecialEffect.K2

        visibleEffects[Pair(0, 2)] = SpecialEffect.K3
        visibleEffects[Pair(0, 12)] = SpecialEffect.K3
        visibleEffects[Pair(2, 0)] = SpecialEffect.K3
        visibleEffects[Pair(2, 14)] = SpecialEffect.K3
        visibleEffects[Pair(12, 0)] = SpecialEffect.K3
        visibleEffects[Pair(12, 14)] = SpecialEffect.K3
        visibleEffects[Pair(14, 2)] = SpecialEffect.K3
        visibleEffects[Pair(14, 12)] = SpecialEffect.K3

        hiddenEffects[Pair(4, 8)] = SpecialEffect.PUAN_BOLUNMESI
        hiddenEffects[Pair(7, 9)] = SpecialEffect.PUAN_BOLUNMESI
        hiddenEffects[Pair(8, 3)] = SpecialEffect.PUAN_BOLUNMESI
        hiddenEffects[Pair(10, 4)] = SpecialEffect.PUAN_BOLUNMESI
        hiddenEffects[Pair(13, 12)] = SpecialEffect.PUAN_BOLUNMESI

        hiddenEffects[Pair(0, 14)] = SpecialEffect.PUAN_TRANSFERI
        hiddenEffects[Pair(3, 1)] = SpecialEffect.PUAN_TRANSFERI
        hiddenEffects[Pair(3, 12)] = SpecialEffect.PUAN_TRANSFERI
        hiddenEffects[Pair(11, 7)] = SpecialEffect.PUAN_TRANSFERI

        hiddenEffects[Pair(2, 5)] = SpecialEffect.HARF_KAYBI
        hiddenEffects[Pair(6, 3)] = SpecialEffect.HARF_KAYBI
        hiddenEffects[Pair(10, 13)] = SpecialEffect.HARF_KAYBI

        hiddenEffects[Pair(5, 6)] = SpecialEffect.EKSTRA_HAMLE_ENGELI
        hiddenEffects[Pair(12, 10)] = SpecialEffect.EKSTRA_HAMLE_ENGELI

        hiddenEffects[Pair(1, 10)] = SpecialEffect.KELIME_IPTALI
        hiddenEffects[Pair(14, 2)] = SpecialEffect.KELIME_IPTALI

        hiddenEffects[Pair(7, 14)] = SpecialEffect.BOLGE_YASAGI
        hiddenEffects[Pair(9, 10)] = SpecialEffect.BOLGE_YASAGI
        hiddenEffects[Pair(0, 3)] = SpecialEffect.HARF_YASAGI
        hiddenEffects[Pair(9, 5)] = SpecialEffect.HARF_YASAGI
        hiddenEffects[Pair(13, 5)] = SpecialEffect.HARF_YASAGI
        hiddenEffects[Pair(5, 11)] = SpecialEffect.EKSTRA_HAMLE_JOKERI
        hiddenEffects[Pair(14, 7)] = SpecialEffect.EKSTRA_HAMLE_JOKERI
    }

    // Oyun tahtasını oluşturur
    private fun createGameBoard() {
        binding.gameBoard.rowCount = 15
        binding.gameBoard.columnCount = 15

        for (i in 0 until 15) {
            for (j in 0 until 15) {
                val cellBinding = TileCellBinding.inflate(layoutInflater)
                val cellView = cellBinding.root

                val visibleEffect = visibleEffects[Pair(i, j)]
                if (visibleEffect != null) {
                    val effectTextView = cellBinding.specialEffect
                    when (visibleEffect) {
                        SpecialEffect.H2 -> {
                            cellView.setBackgroundResource(R.drawable.h2_background)
                            effectTextView.visibility = View.VISIBLE
                            effectTextView.text = "H²"
                            effectTextView.setTextColor(ContextCompat.getColor(this, R.color.blue))
                        }
                        SpecialEffect.H3 -> {
                            cellView.setBackgroundResource(R.drawable.h3_background)
                            effectTextView.visibility = View.VISIBLE
                            effectTextView.text = "H³"
                            effectTextView.setTextColor(ContextCompat.getColor(this, R.color.red))
                        }
                        SpecialEffect.K2 -> {
                            cellView.setBackgroundResource(R.drawable.k2_background)
                            effectTextView.visibility = View.VISIBLE
                            effectTextView.text = "K²"
                            effectTextView.setTextColor(ContextCompat.getColor(this, R.color.green))
                        }
                        SpecialEffect.K3 -> {
                            cellView.setBackgroundResource(R.drawable.k3_background)
                            effectTextView.visibility = View.VISIBLE
                            effectTextView.text = "K³"
                            effectTextView.setTextColor(ContextCompat.getColor(this, R.color.brown))
                        }
                        else -> { }
                    }
                } else {
                    cellView.setBackgroundResource(R.drawable.cell_background)
                }

                val hiddenEffect = hiddenEffects[Pair(i, j)]

                cellView.setOnClickListener {
                    onCellClicked(i, j)
                }

                val cellParams = GridLayout.LayoutParams().apply {
                    width = resources.getDimensionPixelSize(R.dimen.tile_size)
                    height = resources.getDimensionPixelSize(R.dimen.tile_size)
                    rowSpec = GridLayout.spec(i)
                    columnSpec = GridLayout.spec(j)
                }

                binding.gameBoard.addView(cellView, cellParams)

                gameBoard[i][j] = BoardCell(
                    view = cellView,
                    specialEffect = visibleEffect,
                    hiddenEffect = hiddenEffect
                )
            }
        }
    }

    // Hücreye tıklandığında çağrılır
    private fun onCellClicked(row: Int, col: Int) {
        if (!isMyTurn) {
            Toast.makeText(this, "Sıra sizde değil!", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLetter == null || selectedLetterIndex == null) {
            Toast.makeText(this, "Önce bir harf seçin!", Toast.LENGTH_SHORT).show()
            return
        }

        if (gameBoard[row][col].isBanned) {
            Toast.makeText(this, "Bu bölge şu anda yasaklı!", Toast.LENGTH_SHORT).show()
            return
        }

        val cell = gameBoard[row][col]
        if (cell.isOccupied) {
            return
        }

        if (isFirstMove() && !isAnyLetterPlaced()) {
            if (row != 7 || col != 7) {
                Toast.makeText(this, "İlk harfi oyun tahtasının ortasına (7,7) yerleştirmelisiniz!", Toast.LENGTH_SHORT).show()
                return
            }
        } else if (!isFirstMove() && isAnyLetterPlaced() && !isAdjacentToExistingLetter(row, col)) {
            Toast.makeText(this, "Harfler mevcut kelimeye veya tahtadaki harflere bitişik olmalıdır!", Toast.LENGTH_SHORT).show()
            return
        }

        val currentIndex = selectedLetterIndex
        if (currentIndex != null && selectedLetter != null) {
            placeLetter(row, col, selectedLetter!!, currentIndex)
        } else {
            Toast.makeText(this, "Seçili harf indeksi bulunamadı!", Toast.LENGTH_SHORT).show()
            return
        }

        selectedLetterView?.setBackgroundResource(R.drawable.cell_background)
        selectedLetterIndex = null
        selectedLetterView = null
        selectedLetter = null
    }

    // Tahtada yeni yerleştirilmiş bir harf olup olmadığını kontrol eder
    private fun isAnyLetterPlaced(): Boolean {
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (gameBoard[i][j].isOccupied && gameBoard[i][j].isNewlyPlaced) {
                    return true
                }
            }
        }
        return false
    }

    // Yerleştirilecek harfin mevcut bir harfe bitişik olup olmadığını kontrol eder
    private fun isAdjacentToExistingLetter(row: Int, col: Int): Boolean {
        if (row > 0 && gameBoard[row-1][col].isOccupied) {
            return true
        }
        if (row < 14 && gameBoard[row+1][col].isOccupied) {
            return true
        }
        if (col > 0 && gameBoard[row][col-1].isOccupied) {
            return true
        }
        if (col < 14 && gameBoard[row][col+1].isOccupied) {
            return true
        }
        if (row > 0 && col > 0 && gameBoard[row-1][col-1].isOccupied) {
            return true
        }
        if (row > 0 && col < 14 && gameBoard[row-1][col+1].isOccupied) {
            return true
        }
        if (row < 14 && col > 0 && gameBoard[row+1][col-1].isOccupied) {
            return true
        }
        if (row < 14 && col < 14 && gameBoard[row+1][col+1].isOccupied) {
            return true
        }
        return false
    }

    // Harfi seçer
    private fun selectLetter(index: Int, letterView: View, letter: Char) {
        if (!isMyTurn) {
            Toast.makeText(this, "Sıra sizde değil!", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLetterIndex == index && selectedLetterView == letterView) {
            selectedLetterView?.setBackgroundResource(R.drawable.cell_background)
            selectedLetterIndex = null
            selectedLetterView = null
            selectedLetter = null
            return
        }

        selectedLetterView?.setBackgroundResource(R.drawable.cell_background)

        val currentIndex = findLetterIndex(letter)
        if (currentIndex != -1) {
            selectedLetterIndex = currentIndex
            selectedLetterView = letterView
            selectedLetter = letter

            letterView.setBackgroundResource(R.drawable.selected_letter_background)

            setupDragListeners(letterView, letter, currentIndex)
        } else {
            Toast.makeText(this, "Seçilen harf artık mevcut değil!", Toast.LENGTH_SHORT).show()
        }
    }

    // Sürükleme için dinleyicileri ayarlar
    private fun setupDragListeners(view: View, letter: Char, index: Int) {
        view.setOnTouchListener { v, event ->
            if (!isMyTurn) {
                Toast.makeText(this, "Sıra sizde değil!", Toast.LENGTH_SHORT).show()
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    draggedLetterIndex = index
                    draggedLetterView = v
                    draggedLetter = letter
                    originalX = v.x
                    originalY = v.y

                    v.elevation = 10f

                    v.tag = Pair(event.x, event.y)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val offset = v.tag as Pair<Float, Float>
                        v.x = event.rawX - offset.first
                        v.y = event.rawY - offset.second
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        v.elevation = 0f

                        val dropPosition = findDropPosition(event.rawX, event.rawY)
                        if (dropPosition != null) {
                            val (row, col) = dropPosition

                            if (isFirstMove() && !isAnyLetterPlaced()) {
                                if (row != 7 || col != 7) {
                                    Toast.makeText(
                                        this,
                                        "İlk harfi oyun tahtasının ortasına (7,7) yerleştirmelisiniz!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    v.animate().x(originalX).y(originalY).setDuration(300).start()
                                    return@setOnTouchListener true
                                }
                            } else if (!isFirstMove() && isAnyLetterPlaced() && !isAdjacentToExistingLetter(
                                    row,
                                    col
                                )
                            ) {
                                Toast.makeText(
                                    this,
                                    "Harfler mevcut kelimeye veya tahtadaki harflere bitişik olmalıdır!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                v.animate().x(originalX).y(originalY).setDuration(300).start()
                                return@setOnTouchListener true
                            }

                            val currentIndex = findLetterIndex(letter)
                            if (currentIndex != -1) {
                                placeLetter(row, col, letter, currentIndex)
                            } else {
                                Toast.makeText(this, "Harf bulunamadı!", Toast.LENGTH_SHORT).show()
                            }

                            v.x = originalX
                            v.y = originalY
                        } else {
                            v.animate().x(originalX).y(originalY).setDuration(300).start()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    // İlk hamle olup olmadığını kontrol eder
    private fun isFirstMove(): Boolean {
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (gameBoard[i][j].isOccupied && !gameBoard[i][j].isNewlyPlaced) {
                    return false
                }
            }
        }
        return true
    }

    // Harfin bırakıldığı konumu bulur
    private fun findDropPosition(rawX: Float, rawY: Float): Pair<Int, Int>? {
        val gameBoardLocation = IntArray(2)
        binding.gameBoard.getLocationOnScreen(gameBoardLocation)

        val boardX = rawX - gameBoardLocation[0]
        val boardY = rawY - gameBoardLocation[1]

        if (boardX < 0 || boardY < 0 || boardX > binding.gameBoard.width || boardY > binding.gameBoard.height) {
            return null
        }

        val tileSize = resources.getDimensionPixelSize(R.dimen.tile_size)

        val row = (boardY / tileSize).toInt()
        val col = (boardX / tileSize).toInt()

        if (row >= 0 && row < 15 && col >= 0 && col < 15) {
            if (!gameBoard[row][col].isOccupied) {
                return Pair(row, col)
            }
        }

        return null
    }

    // Harfi tahtaya yerleştirir
    private fun placeLetter(row: Int, col: Int, letter: Char, letterIndex: Int) {
        try {
            if (row < 0 || row >= 15 || col < 0 || col >= 15) {
                Toast.makeText(this, "Geçersiz hücre konumu!", Toast.LENGTH_SHORT).show()
                return
            }

            val cell = gameBoard[row][col]
            val cellView = cell.view

            if (cellView == null) {
                Toast.makeText(this, "Hücre görünümü bulunamadı!", Toast.LENGTH_SHORT).show()
                return
            }

            if (cell.isOccupied) {
                Toast.makeText(this, "Bu hücre zaten dolu!", Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val cellBinding = TileCellBinding.bind(cellView)
                cellBinding.letterValue.text = letter.toString()
                cellBinding.letterPoint.text = getLetterPoint(letter).toString()
            } catch (e: Exception) {
                Toast.makeText(this, "Hücre güncellenirken hata: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }

            cell.isOccupied = true
            cell.letter = letter
            cell.isNewlyPlaced = true
            cell.placementIndex = placementCounter++

            try {
                if (letterIndex >= 0 && letterIndex < binding.playerTiles.childCount) {
                    removeLetterFromPlayerTiles(letterIndex)
                } else {
                    Toast.makeText(this, "Geçersiz harf indeksi: $letterIndex", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Harf kaldırılırken hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            cell.specialEffect?.let {
                try {
                    handleSpecialEffect(it, row, col)
                } catch (e: Exception) {
                    Toast.makeText(this, "Özel efekt uygulanırken hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            cell.hiddenEffect?.let {
                try {
                    handleSpecialEffect(it, row, col)
                } catch (e: Exception) {
                    Toast.makeText(this, "Gizli efekt uygulanırken hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "placeLetter hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Harfin indeksini bulur
    private fun findLetterIndex(letter: Char): Int {
        for (i in currentPlayerTiles.indices) {
            if (currentPlayerTiles[i] == letter) {
                if (i < binding.playerTiles.childCount) {
                    val view = binding.playerTiles.getChildAt(i)
                    val binding = TileCellBinding.bind(view)
                    if (binding.letterValue.text.toString() == letter.toString()) {
                        return i
                    }
                }
            }
        }

        for (i in 0 until binding.playerTiles.childCount) {
            val view = binding.playerTiles.getChildAt(i)
            val binding = TileCellBinding.bind(view)
            if (binding.letterValue.text.toString() == letter.toString()) {
                return i
            }
        }

        return -1
    }

    // Oluşturulan kelimeyi kontrol eder ve vurgular
    private fun checkAndHighlightWord() {
        val placedLetters = mutableListOf<Triple<Int, Int, Char>>()

        for (i in 0 until 15) {
            for (j in 0 until 15) {
                val cell = gameBoard[i][j]
                if (cell.isOccupied && cell.isNewlyPlaced) {
                    cell.letter?.let { letter ->
                        placedLetters.add(Triple(i, j, letter))
                    }
                }
            }
        }

        if (placedLetters.isEmpty()) return

        if (!checkLetterAlignment(placedLetters)) return

        val mainWord = buildWord(placedLetters)
        if (mainWord.length <= 1) return

        val isValid = turkishDictionary.isValidWord(mainWord)

        val wordScore = if (isValid) calculateWordScore(placedLetters) else 0

        highlightWord(placedLetters, isValid, wordScore)
    }

    // Kelimeyi çerçeve içine alır
    private fun highlightWord(letters: List<Triple<Int, Int, Char>>, isValid: Boolean, score: Int) {
        val frameView = wordFrameView ?: return
        val scoreText = wordScoreTextView ?: return

        var minRow = 15
        var maxRow = 0
        var minCol = 15
        var maxCol = 0

        for (letter in letters) {
            val row = letter.first
            val col = letter.second

            minRow = minOf(minRow, row)
            maxRow = maxOf(maxRow, row)
            minCol = minOf(minCol, col)
            maxCol = maxOf(maxCol, col)
        }

        val tileSize = resources.getDimensionPixelSize(R.dimen.tile_size)
        val padding = 4

        val startX = minCol * tileSize - padding
        val startY = minRow * tileSize - padding
        val width = (maxCol - minCol + 1) * tileSize + padding * 2
        val height = (maxRow - minRow + 1) * tileSize + padding * 2

        val params = frameView.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = startX
        params.topMargin = startY
        params.width = width
        params.height = height
        frameView.layoutParams = params

        if (isValid) {
            frameView.background = ContextCompat.getDrawable(this, R.drawable.valid_word_frame)
            scoreText.text = "+$score"
        } else {
            frameView.background = ContextCompat.getDrawable(this, R.drawable.invalid_word_frame)
            scoreText.text = ""
        }

        frameView.visibility = View.VISIBLE
    }

    // Özel efektleri uygular
    private fun handleSpecialEffect(effect: SpecialEffect, row: Int, col: Int) {
        Log.d("JokerTest", "handleSpecialEffect çağrıldı: $effect, konum: ($row, $col)")

        when (effect) {
            SpecialEffect.H2, SpecialEffect.H3, SpecialEffect.K2, SpecialEffect.K3 -> {
            }

            SpecialEffect.PUAN_BOLUNMESI -> {
                showMineEffect(row, col, "Puan Bölünmesi!")
            }
            SpecialEffect.PUAN_TRANSFERI -> {
                showMineEffect(row, col, "Puan Transferi!")
            }
            SpecialEffect.HARF_KAYBI -> {
                showMineEffect(row, col, "Harf Kaybı!")
            }
            SpecialEffect.EKSTRA_HAMLE_ENGELI -> {
                showMineEffect(row, col, "Ekstra Hamle Engeli!")
            }
            SpecialEffect.KELIME_IPTALI -> {
                showMineEffect(row, col, "Kelime İptali!")
            }
            SpecialEffect.BOLGE_YASAGI -> {
                Log.d("JokerTest", "BÖLGE YASAĞI jokeri aktif ediliyor!")
                showBonusEffect(row, col, "Bölge Yasağı Kazanıldı!")
                addBonusToInventory(SpecialEffect.BOLGE_YASAGI)
            }
            SpecialEffect.HARF_YASAGI -> {
                Log.d("JokerTest", "HARF YASAĞI jokeri aktif ediliyor!")
                showMineEffect(row, col, "Harf Kaybı!")
                addBonusToInventory(SpecialEffect.HARF_YASAGI)
            }
            SpecialEffect.EKSTRA_HAMLE_JOKERI -> {
                Log.d("JokerTest", "EKSTRA HAMLE jokeri aktif ediliyor!")
                showBonusEffect(row, col, "Ekstra Hamle Jokeri Kazanıldı!")
                addBonusToInventory(SpecialEffect.EKSTRA_HAMLE_JOKERI)
            }
        }
    }

    // Mayın efektini gösterir
    private fun showMineEffect(row: Int, col: Int, message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()

        val cell = gameBoard[row][col]
        cell.view?.let { view ->
            val originalBackground = view.background
            view.setBackgroundResource(R.drawable.mine_effect_background)

            Handler(Looper.getMainLooper()).postDelayed({
                view.background = originalBackground
            }, 1000)
        }

        Log.d("SpecialEffects", "Mayın efekti gösterildi: $message")
    }

    // Bonus efektini gösterir
    private fun showBonusEffect(row: Int, col: Int, message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()

        val cell = gameBoard[row][col]
        cell.view?.let { view ->
            val originalBackground = view.background
            view.setBackgroundResource(R.drawable.bonus_cell_highlight)

            Handler(Looper.getMainLooper()).postDelayed({
                view.background = originalBackground
            }, 1000)
        }
    }


    private fun updateBonusInventoryUI() {
    }

    // Harf kaybı efektini uygular
    private fun applyLetterLossEffect() {
        binding.playerTiles.removeAllViews()
        currentPlayerTiles.clear()

        val unluHarfler = listOf('A', 'E', 'I', 'İ', 'O', 'Ö', 'U', 'Ü')
        val unsuHarfler = listOf('B', 'C', 'Ç', 'D', 'F', 'G', 'Ğ', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R', 'S', 'Ş', 'T', 'V', 'Y', 'Z')

        var unluSayisi = 0
        var unsuSayisi = 0

        for (i in 0 until 7) {
            if (remainingLetters > 0) {
                val eklenecekTur = if (unluSayisi < 3 && unsuSayisi >= 4) {
                    "unlu"
                } else if (unsuSayisi < 4 && unluSayisi >= 3) {
                    "unsu"
                } else {
                    if (kotlin.random.Random.nextBoolean()) "unlu" else "unsu"
                }

                val letter = if (eklenecekTur == "unlu") {
                    unluHarfler.random()
                } else {
                    unsuHarfler.random()
                }

                addNewLetterToPlayerTiles(letter)

                if (eklenecekTur == "unlu") unluSayisi++ else unsuSayisi++

                remainingLetters--
            }
        }

        updateRemainingLettersDisplay()

        updateLetterBagOnFirebase()

        Toast.makeText(this, "Harf Kaybı: Tüm harfleriniz yenilendi!", Toast.LENGTH_SHORT).show()
    }

    private fun validateWord() {
        try {
            if (!isMyTurn) {
                Toast.makeText(this, "Sıra sizde değil!", Toast.LENGTH_SHORT).show()
                return
            }

            val placedLetters = mutableListOf<Triple<Int, Int, Char>>()
            for (i in 0 until 15) {
                for (j in 0 until 15) {
                    val cell = gameBoard[i][j]
                    if (cell.isOccupied && cell.isNewlyPlaced) {
                        cell.letter?.let { letter ->
                            placedLetters.add(Triple(i, j, letter))
                        }
                    }
                }
            }

            if (placedLetters.isEmpty()) {
                Toast.makeText(this, "Önce harfleri yerleştirin!", Toast.LENGTH_SHORT).show()
                return
            }

            var harfKaybiVarMi = false
            for (letterPos in placedLetters) {
                val (row, col, _) = letterPos
                val cell = gameBoard[row][col]
                val effect = cell.specialEffect ?: cell.hiddenEffect

                if (effect == SpecialEffect.HARF_KAYBI) {
                    harfKaybiVarMi = true
                    break
                }
            }

            val sortedPlacedLetters = placedLetters.sortedBy { (row, col, _) ->
                gameBoard[row][col].placementIndex
            }

            val wordBuilder = StringBuilder()
            for (letterPos in sortedPlacedLetters) {
                wordBuilder.append(letterPos.third)
            }

            val word = wordBuilder.toString()

            if (word.length <= 1) {
                Toast.makeText(this, "En az 2 harfli bir kelime oluşturun!", Toast.LENGTH_SHORT).show()
                return
            }

            val isValid = turkishDictionary.isValidWord(word)

            for (letterPos in placedLetters) {
                val (row, col, _) = letterPos
                val cell = gameBoard[row][col]
                val cellView = cell.view
                if (cellView != null) {
                    if (isValid) {
                        cellView.setBackgroundResource(R.drawable.valid_word_cell)
                    } else {
                        cellView.setBackgroundResource(R.drawable.invalid_word_cell)
                    }
                }
            }

            if (isValid) {
                var totalScore = calculateWordScore(placedLetters)
                totalScore = applySpecialEffects(placedLetters, totalScore)

                this.isValid = true

                updateBoardAndMakeMove(word, totalScore)

                updatePlayerScore(totalScore)

                val currentText = binding.userInfo.text.toString()
                val pattern = ".*\\| Puan: (\\d+)".toRegex()
                val matchResult = pattern.find(currentText)
                val updatedScore = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
                updateScoreOnFirebase(currentUserId, updatedScore)

                val successMessage = "\"$word\" kelimesi kabul edildi. +$totalScore puan!"
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "\"$word\" geçerli bir Türkçe kelime değil!", Toast.LENGTH_SHORT).show()
                this.isValid = false

                updateBoardAndMakeMove(word, 0)
            }
            markLettersAsPlaced(placedLetters)

            if (harfKaybiVarMi) {
                applyLetterLossEffect()
            } else {
                replenishPlayerTiles(placedLetters.size)
            }

            resetPlacementCounter()

            if (extraMoveUsed) {
                // Ekstra hamle jokerini kullandık, sıra yine bizde kalacak
                val firebaseHelper = FirebaseHelper()
                firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                    .child("currentTurn").setValue(currentUserId)
                    .addOnSuccessListener {
                        Log.d("JokerEffects", "Ekstra hamle jokeri uygulandı, sıra hala sizde")

                        // Jokeri kaldır
                        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                            .child("activeEffects").removeValue()

                        // Diğer sıfırlamaları yap
                        extraMoveUsed = false

                        // Sıranın bizde olduğunu güncelle
                        isMyTurn = true
                        updateTurnInfo()

                        Toast.makeText(this, "Ekstra hamle jokeriniz sayesinde sıra hala sizde!", Toast.LENGTH_SHORT).show()

                        // Kelime çerçevesini gizle
                        wordFrameView?.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        Log.e("GameActivity", "Ekstra hamle jokeri uygulanırken hata: ${e.message}")

                        // Hata durumunda da hamle hakkını kaybetmeyelim
                        extraMoveUsed = false
                        isMyTurn = true
                        updateTurnInfo()

                        Toast.makeText(this, "Ekstra hamle hatası! Ancak sıra hala sizde.", Toast.LENGTH_SHORT).show()

                        // Kelime çerçevesini gizle
                        wordFrameView?.visibility = View.GONE
                    }
            } else {
                isMyTurn = false

                if (bannedRegion != null) {
                    for (i in 0 until 15) {
                        for (j in 0 until 15) {
                            if (gameBoard[i][j].isBanned) {
                                gameBoard[i][j].isBanned = false
                                gameBoard[i][j].view?.alpha = 1.0f
                            }
                        }
                    }

                    bannedRegion = null

                    val firebaseHelper = FirebaseHelper()
                    firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                        .child("activeEffects").removeValue()

                    Log.d("JokerEffects", "Bölge yasağı kaldırıldı - tur tamamlandı")
                    Toast.makeText(this, "Bölge yasağı kaldırıldı!", Toast.LENGTH_SHORT).show()
                }

                val firebaseHelper = FirebaseHelper()
                val opponentId = getOpponentId()
                if (opponentId.isNotEmpty()) {
                    firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                        .child("currentTurn").setValue(opponentId)
                        .addOnSuccessListener {
                            updateTurnInfo()
                        }
                        .addOnFailureListener {
                            updateTurnInfo()
                        }
                } else {
                    updateTurnInfo()
                }
            }

            wordFrameView?.visibility = View.GONE

        } catch (e: Exception) {
            Toast.makeText(this, "Kelime doğrulanırken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("GameActivity", "validateWord hatası", e)
        }
    }
    // Yerleştirme sayacını sıfırlar
    private fun resetPlacementCounter() {
        placementCounter = 0
    }

    // Oyuncuya yeni harfler ekler
    private fun replenishPlayerTiles(usedLetterCount: Int) {
        val tilesToAdd = usedLetterCount

        if (tilesToAdd <= 0) return

        val unluHarfler = listOf('A', 'E', 'I', 'İ', 'O', 'Ö', 'U', 'Ü')
        val unsuHarfler = listOf('B', 'C', 'Ç', 'D', 'F', 'G', 'Ğ', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R', 'S', 'Ş', 'T', 'V', 'Y', 'Z')

        val unluSayisi = currentPlayerTiles.count { it in unluHarfler }
        val unsuSayisi = currentPlayerTiles.count { it in unsuHarfler && it != '*' }

        val idealUnluCount = 3
        val idealUnsuCount = 4

        val eklenecekUnluCount = maxOf(0, minOf(tilesToAdd, idealUnluCount - unluSayisi))
        val eklenecekUnsuCount = maxOf(0, minOf(tilesToAdd - eklenecekUnluCount, idealUnsuCount - unsuSayisi))

        val eklenecekExtra = tilesToAdd - eklenecekUnluCount - eklenecekUnsuCount

        for (index in frozenLetterIndices) {
            if (index < binding.playerTiles.childCount) {
                val letterView = binding.playerTiles.getChildAt(index)
                letterView.setBackgroundColor(Color.BLACK)
                letterView.alpha = 0.5f
                letterView.isEnabled = false
                letterView.isClickable = false
                letterView.setOnTouchListener { _, _ -> true }
            }
        }

        for (i in 0 until eklenecekUnluCount) {
            if (remainingLetters <= 0) break

            val letter = unluHarfler.random()
            addNewLetterToPlayerTiles(letter)
            remainingLetters--
        }

        for (i in 0 until eklenecekUnsuCount) {
            if (remainingLetters <= 0) break

            val letter = unsuHarfler.random()
            addNewLetterToPlayerTiles(letter)
            remainingLetters--
        }

        for (i in 0 until eklenecekExtra) {
            if (remainingLetters <= 0) break

            val currentUnluRatio = (unluSayisi + eklenecekUnluCount).toFloat() /
                    (unluSayisi + unsuSayisi + eklenecekUnluCount + eklenecekUnsuCount)

            val letter = if (currentUnluRatio < 0.4f)
                unluHarfler.random() else unsuHarfler.random()

            addNewLetterToPlayerTiles(letter)
            remainingLetters--
        }

        for (i in 0 until binding.playerTiles.childCount) {
            val letterView = binding.playerTiles.getChildAt(i)
            val letterBinding = TileCellBinding.bind(letterView)
            val letter = letterBinding.letterValue.text.toString()[0]

            letterView.setOnClickListener {
                selectLetter(i, letterView, letter)
            }
        }

        updateRemainingLettersDisplay()

        updateLetterBagOnFirebase()
    }

    // Firebase'de harf torbasını günceller
    private fun updateLetterBagOnFirebase() {
        val firebaseHelper = FirebaseHelper()
        val letterBagUpdate = HashMap<String, Any>()
        val letterBagString = "A".repeat(remainingLetters)
        letterBagUpdate["letterBag"] = letterBagString

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .updateChildren(letterBagUpdate)
            .addOnSuccessListener {
                Log.d("GameActivity", "Harf torbası güncellendi: $remainingLetters harf kaldı")
            }
            .addOnFailureListener { e ->
                Log.e("GameActivity", "Harf torbası güncellenemedi: ${e.message}")
            }
    }

    // Tüm etkileşimleri devre dışı bırakır
    private fun disableAllInteractions() {
        for (i in 0 until binding.playerTiles.childCount) {
            val letterView = binding.playerTiles.getChildAt(i)
            letterView.isEnabled = false
            letterView.alpha = 0.5f
        }

        for (i in 0 until 15) {
            for (j in 0 until 15) {
                gameBoard[i][j].view?.isEnabled = false
            }
        }

        binding.confirmButton.isEnabled = false
        binding.confirmButton.alpha = 0.5f
        binding.regionBanJoker.isEnabled = false
        binding.letterBanJoker.isEnabled = false
        binding.extraMoveJoker.isEnabled = false
    }

    // Harflerin yerleştirilme yönü
    enum class PlacementDirection {
        HORIZONTAL, VERTICAL, DIAGONAL_RIGHT, DIAGONAL_LEFT, UNKNOWN
    }

    // Oluşturulan tüm kelimeleri bulur
    private fun findAllFormedWords(placedLetters: List<Triple<Int, Int, Char>>): List<Pair<String, List<Triple<Int, Int, Char>>>> {
        val potentialWords = mutableListOf<Pair<String, List<Triple<Int, Int, Char>>>>()

        if (placedLetters.size > 1) {
            val direction = detectPlacementDirection(placedLetters)

            if (direction != PlacementDirection.UNKNOWN) {
                val mainWord = findMainWord(placedLetters, direction)
                if (mainWord.first.length > 1) {
                    potentialWords.add(mainWord)
                }
            } else {
                for (letter in placedLetters) {
                    val row = letter.first
                    val col = letter.second

                    val horizontalWord = findHorizontalWord(row, col)
                    if (horizontalWord.first.length > 1) {
                        potentialWords.add(horizontalWord)
                    }

                    val verticalWord = findVerticalWord(row, col)
                    if (verticalWord.first.length > 1) {
                        potentialWords.add(verticalWord)
                    }
                    val rightDiagonalWord = findRightDiagonalWord(row, col)
                    if (rightDiagonalWord.first.length > 1) {
                        potentialWords.add(rightDiagonalWord)
                    }

                    val leftDiagonalWord = findLeftDiagonalWord(row, col)
                    if (leftDiagonalWord.first.length > 1) {
                        potentialWords.add(leftDiagonalWord)
                    }
                }
            }
        } else if (placedLetters.size == 1) {
            val letter = placedLetters[0]
            val row = letter.first
            val col = letter.second

            val horizontalWord = findHorizontalWord(row, col)
            if (horizontalWord.first.length > 1) {
                potentialWords.add(horizontalWord)
            }

            val verticalWord = findVerticalWord(row, col)
            if (verticalWord.first.length > 1) {
                potentialWords.add(verticalWord)
            }
        }

        for (letter in placedLetters) {
            val row = letter.first
            val col = letter.second

            val direction = detectPlacementDirection(placedLetters)

            when (direction) {
                PlacementDirection.HORIZONTAL -> {
                    val verticalWord = findVerticalWord(row, col)
                    if (verticalWord.first.length > 1) {
                        potentialWords.add(verticalWord)
                    }
                }
                PlacementDirection.VERTICAL -> {
                    val horizontalWord = findHorizontalWord(row, col)
                    if (horizontalWord.first.length > 1) {
                        potentialWords.add(horizontalWord)
                    }
                }
                else -> {
                    val horizontalWord = findHorizontalWord(row, col)
                    if (horizontalWord.first.length > 1) {
                        potentialWords.add(horizontalWord)
                    }

                    val verticalWord = findVerticalWord(row, col)
                    if (verticalWord.first.length > 1) {
                        potentialWords.add(verticalWord)
                    }
                }
            }
        }

        return potentialWords.distinctBy { it.first }
    }

    // Harflerin yerleştirilme yönünü tespit eder
    private fun detectPlacementDirection(placedLetters: List<Triple<Int, Int, Char>>): PlacementDirection {
        if (placedLetters.size <= 1) {
            return PlacementDirection.UNKNOWN
        }

        val allSameRow = placedLetters.all { it.first == placedLetters[0].first }

        val allSameColumn = placedLetters.all { it.second == placedLetters[0].second }

        if (allSameRow) {
            val columns = placedLetters.map { it.second }.sorted()

            for (i in 0 until columns.size - 1) {
                if (columns[i + 1] - columns[i] > 1) {
                    val row = placedLetters[0].first
                    for (col in columns[i] + 1 until columns[i + 1]) {
                        if (!gameBoard[row][col].isOccupied) {
                            return PlacementDirection.UNKNOWN
                        }
                    }
                }
            }
            return PlacementDirection.HORIZONTAL
        }

        if (allSameColumn) {
            val rows = placedLetters.map { it.first }.sorted()

            for (i in 0 until rows.size - 1) {
                if (rows[i + 1] - rows[i] > 1) {
                    val col = placedLetters[0].second
                    for (row in rows[i] + 1 until rows[i + 1]) {
                        if (!gameBoard[row][col].isOccupied) {
                            return PlacementDirection.UNKNOWN
                        }
                    }
                }
            }
            return PlacementDirection.VERTICAL
        }

        val sortedLetters = placedLetters.sortedWith(compareBy({ it.first }, { it.second }))

        var isRightDiagonal = true
        for (i in 1 until sortedLetters.size) {
            val rowDiff = sortedLetters[i].first - sortedLetters[i-1].first
            val colDiff = sortedLetters[i].second - sortedLetters[i-1].second

            if (rowDiff != 1 || colDiff != 1) {
                if (rowDiff > 1 && colDiff > 1 && rowDiff == colDiff) {
                    var allFilled = true
                    for (j in 1 until rowDiff) {
                        val row = sortedLetters[i-1].first + j
                        val col = sortedLetters[i-1].second + j
                        if (!gameBoard[row][col].isOccupied) {
                            allFilled = false
                            break
                        }
                    }
                    if (!allFilled) {
                        isRightDiagonal = false
                        break
                    }
                } else {
                    isRightDiagonal = false
                    break
                }
            }
        }
        if (isRightDiagonal) {
            return PlacementDirection.DIAGONAL_RIGHT
        }

        val sortedForLeftDiagonal = placedLetters.sortedWith(compareBy({ it.first }, { -it.second }))

        var isLeftDiagonal = true
        for (i in 1 until sortedForLeftDiagonal.size) {
            val rowDiff = sortedForLeftDiagonal[i].first - sortedForLeftDiagonal[i-1].first
            val colDiff = sortedForLeftDiagonal[i-1].second - sortedForLeftDiagonal[i].second

            if (rowDiff != 1 || colDiff != 1) {
                if (rowDiff > 1 && colDiff > 1 && rowDiff == colDiff) {
                    var allFilled = true
                    for (j in 1 until rowDiff) {
                        val row = sortedForLeftDiagonal[i-1].first + j
                        val col = sortedForLeftDiagonal[i-1].second - j
                        if (!gameBoard[row][col].isOccupied) {
                            allFilled = false
                            break
                        }
                    }
                    if (!allFilled) {
                        isLeftDiagonal = false
                        break
                    }
                } else {
                    isLeftDiagonal = false
                    break
                }
            }
        }
        if (isLeftDiagonal) {
            return PlacementDirection.DIAGONAL_LEFT
        }

        return PlacementDirection.UNKNOWN
    }

    // Ana kelimeyi bulur
    private fun findMainWord(placedLetters: List<Triple<Int, Int, Char>>, direction: PlacementDirection): Pair<String, List<Triple<Int, Int, Char>>> {

        if (placedLetters.isEmpty()) {
            return Pair("", emptyList())
        }

        if (placedLetters.size == 1) {
            val letter = placedLetters[0]
            val row = letter.first
            val col = letter.second

            val horizontalWord = findHorizontalWord(row, col)
            val verticalWord = findVerticalWord(row, col)

            return if (horizontalWord.first.length >= verticalWord.first.length) {
                horizontalWord
            } else {
                verticalWord
            }
        }

        val allSameRow = placedLetters.all { it.first == placedLetters[0].first }
        val allSameCol = placedLetters.all { it.second == placedLetters[0].second }

        if (allSameRow) {
            val row = placedLetters[0].first
            val cols = placedLetters.map { it.second }.sorted()

            val startCol = cols.first()
            val endCol = cols.last()

            var realStartCol = startCol
            while (realStartCol > 0 && gameBoard[row][realStartCol - 1].isOccupied) {
                realStartCol--
            }

            var realEndCol = endCol
            while (realEndCol < 14 && gameBoard[row][realEndCol + 1].isOccupied) {
                realEndCol++
            }

            val wordBuilder = StringBuilder()
            val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

            for (col in realStartCol..realEndCol) {
                val letter = gameBoard[row][col].letter
                if (letter != null) {
                    wordBuilder.append(letter)
                    wordTiles.add(Triple(row, col, letter))
                }
            }

            return Pair(wordBuilder.toString(), wordTiles)
        }

        if (allSameCol) {
            val col = placedLetters[0].second
            val rows = placedLetters.map { it.first }.sorted()

            val startRow = rows.first()
            val endRow = rows.last()

            var realStartRow = startRow
            while (realStartRow > 0 && gameBoard[realStartRow - 1][col].isOccupied) {
                realStartRow--
            }

            var realEndRow = endRow
            while (realEndRow < 14 && gameBoard[realEndRow + 1][col].isOccupied) {
                realEndRow++
            }

            val wordBuilder = StringBuilder()
            val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

            for (row in realStartRow..realEndRow) {
                val letter = gameBoard[row][col].letter
                if (letter != null) {
                    wordBuilder.append(letter)
                    wordTiles.add(Triple(row, col, letter))
                }
            }

            return Pair(wordBuilder.toString(), wordTiles)
        }

        val sortedLetters = placedLetters.sortedWith(compareBy({ it.first }, { it.second }))

        val isRightDiagonal = (1 until sortedLetters.size).all {
            (sortedLetters[it].first - sortedLetters[it-1].first == sortedLetters[it].second - sortedLetters[it-1].second) &&
                    (sortedLetters[it].first - sortedLetters[it-1].first > 0)
        }

        if (isRightDiagonal) {
            val startRow = sortedLetters.first().first
            val startCol = sortedLetters.first().second
            val endRow = sortedLetters.last().first
            val endCol = sortedLetters.last().second

            var realStartRow = startRow
            var realStartCol = startCol
            while (realStartRow > 0 && realStartCol > 0 && gameBoard[realStartRow - 1][realStartCol - 1].isOccupied) {
                realStartRow--
                realStartCol--
            }

            var realEndRow = endRow
            var realEndCol = endCol
            while (realEndRow < 14 && realEndCol < 14 && gameBoard[realEndRow + 1][realEndCol + 1].isOccupied) {
                realEndRow++
                realEndCol++
            }

            val isBottomToTop = placedLetters.size >= 2 &&
                    placedLetters.first().first > placedLetters.last().first

            val wordBuilder = StringBuilder()
            val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

            if (isBottomToTop) {
                var row = realEndRow
                var col = realEndCol
                while (row >= realStartRow && col >= realStartCol) {
                    val letter = gameBoard[row][col].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(row, col, letter))
                    }
                    row--
                    col--
                }
            } else {
                var row = realStartRow
                var col = realStartCol
                while (row <= realEndRow && col <= realEndCol) {
                    val letter = gameBoard[row][col].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(row, col, letter))
                    }
                    row++
                    col++
                }
            }

            return Pair(wordBuilder.toString(), wordTiles)
        }

        val sortedForLeftDiagonal = placedLetters.sortedWith(compareBy({ it.first }, { -it.second }))

        val isLeftDiagonal = (1 until sortedForLeftDiagonal.size).all {
            (sortedForLeftDiagonal[it].first - sortedForLeftDiagonal[it-1].first == sortedForLeftDiagonal[it-1].second - sortedForLeftDiagonal[it].second) &&
                    (sortedForLeftDiagonal[it].first - sortedForLeftDiagonal[it-1].first > 0)
        }

        if (isLeftDiagonal) {
            val startRow = sortedForLeftDiagonal.first().first
            val startCol = sortedForLeftDiagonal.first().second
            val endRow = sortedForLeftDiagonal.last().first
            val endCol = sortedForLeftDiagonal.last().second

            var realStartRow = startRow
            var realStartCol = startCol
            while (realStartRow > 0 && realStartCol < 14 && gameBoard[realStartRow - 1][realStartCol + 1].isOccupied) {
                realStartRow--
                realStartCol++
            }

            var realEndRow = endRow
            var realEndCol = endCol
            while (realEndRow < 14 && realEndCol > 0 && gameBoard[realEndRow + 1][realEndCol - 1].isOccupied) {
                realEndRow++
                realEndCol--
            }

            val isBottomToTop = placedLetters.size >= 2 &&
                    placedLetters.first().first > placedLetters.last().first

            val wordBuilder = StringBuilder()
            val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

            if (isBottomToTop) {
                var row = realEndRow
                var col = realEndCol
                while (row >= realStartRow && col <= realStartCol) {
                    val letter = gameBoard[row][col].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(row, col, letter))
                    }
                    row--
                    col++
                }
            } else {
                var row = realStartRow
                var col = realStartCol
                while (row <= realEndRow && col >= realEndCol) {
                    val letter = gameBoard[row][col].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(row, col, letter))
                    }
                    row++
                    col--
                }
            }

            return Pair(wordBuilder.toString(), wordTiles)
        }

        val allPossibleWords = mutableListOf<Pair<String, List<Triple<Int, Int, Char>>>>()

        for (letter in placedLetters) {
            val row = letter.first
            val col = letter.second

            val horizontalWord = findHorizontalWord(row, col)
            val verticalWord = findVerticalWord(row, col)

            if (horizontalWord.first.length > 1) {
                allPossibleWords.add(horizontalWord)
            }

            if (verticalWord.first.length > 1) {
                allPossibleWords.add(verticalWord)
            }
        }

        return allPossibleWords.maxByOrNull { it.first.length } ?: Pair("", emptyList())
    }

    // Yan etki kelimelerini bulur
    private fun findSideWords(placedLetters: List<Triple<Int, Int, Char>>, mainWord: Pair<String, List<Triple<Int, Int, Char>>>): List<Pair<String, List<Triple<Int, Int, Char>>>> {
        val sideWords = mutableListOf<Pair<String, List<Triple<Int, Int, Char>>>>()

        val mainWordPositions = mainWord.second.map { Pair(it.first, it.second) }.toSet()

        for (letter in placedLetters) {
            val row = letter.first
            val col = letter.second
            val position = Pair(row, col)

            if (position in mainWordPositions) {
                val isHorizontal = mainWord.second.size > 1 && mainWord.second.all { it.first == mainWord.second[0].first }
                val isVertical = mainWord.second.size > 1 && mainWord.second.all { it.second == mainWord.second[0].second }

                if (isHorizontal) {
                    val verticalWord = findVerticalWord(row, col)
                    if (verticalWord.first.length > 1 && verticalWord.first != mainWord.first) {
                        sideWords.add(verticalWord)
                    }
                } else if (isVertical) {
                    val horizontalWord = findHorizontalWord(row, col)
                    if (horizontalWord.first.length > 1 && horizontalWord.first != mainWord.first) {
                        sideWords.add(horizontalWord)
                    }
                } else {
                    val horizontalWord = findHorizontalWord(row, col)
                    if (horizontalWord.first.length > 1 && horizontalWord.first != mainWord.first) {
                        sideWords.add(horizontalWord)
                    }

                    val verticalWord = findVerticalWord(row, col)
                    if (verticalWord.first.length > 1 && verticalWord.first != mainWord.first) {
                        sideWords.add(verticalWord)
                    }
                }
            }
        }

        return sideWords.distinctBy { it.first }
    }

    // Yatay kelimeyi bulur
    private fun findHorizontalWord(row: Int, col: Int): Pair<String, List<Triple<Int, Int, Char>>> {
        if (!gameBoard[row][col].isOccupied) return Pair("", emptyList())

        var startCol = col
        var endCol = col

        while (startCol > 0 && gameBoard[row][startCol - 1].isOccupied) {
            startCol--
        }

        while (endCol < 14 && gameBoard[row][endCol + 1].isOccupied) {
            endCol++
        }

        if (endCol - startCol < 1) return Pair("", emptyList())

        val wordBuilder = StringBuilder()
        val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

        for (c in startCol..endCol) {
            val letter = gameBoard[row][c].letter
            if (letter != null) {
                wordBuilder.append(letter)
                wordTiles.add(Triple(row, c, letter))
            }
        }

        return Pair(wordBuilder.toString(), wordTiles)
    }

    // Dikey kelimeyi bulur
    private fun findVerticalWord(row: Int, col: Int): Pair<String, List<Triple<Int, Int, Char>>> {
        if (!gameBoard[row][col].isOccupied) return Pair("", emptyList())

        var startRow = row
        var endRow = row

        while (startRow > 0 && gameBoard[startRow - 1][col].isOccupied) {
            startRow--
        }

        while (endRow < 14 && gameBoard[endRow + 1][col].isOccupied) {
            endRow++
        }

        if (endRow - startRow < 1) return Pair("", emptyList())

        val wordBuilder = StringBuilder()
        val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

        for (r in startRow..endRow) {
            val letter = gameBoard[r][col].letter
            if (letter != null) {
                wordBuilder.append(letter)
                wordTiles.add(Triple(r, col, letter))
            }
        }

        return Pair(wordBuilder.toString(), wordTiles)
    }

    // Sol çapraz kelimeyi bulur
    private fun findLeftDiagonalWord(row: Int, col: Int): Pair<String, List<Triple<Int, Int, Char>>> {
        if (!gameBoard[row][col].isOccupied) return Pair("", emptyList())

        var startRow = row
        var startCol = col
        var endRow = row
        var endCol = col

        while (startRow > 0 && startCol < 14 && gameBoard[startRow - 1][startCol + 1].isOccupied) {
            startRow--
            startCol++
        }

        while (endRow < 14 && endCol > 0 && gameBoard[endRow + 1][endCol - 1].isOccupied) {
            endRow++
            endCol--
        }

        if (endRow - startRow < 1 || startCol - endCol < 1) return Pair("", emptyList())

        val wordBuilder = StringBuilder()
        val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

        val placedLetterPositions = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (gameBoard[i][j].isNewlyPlaced) {
                    placedLetterPositions.add(Pair(i, j))
                }
            }
        }

        if (placedLetterPositions.isNotEmpty()) {
            val isBottomToTop = placedLetterPositions.size >= 2 &&
                    placedLetterPositions.first().first > placedLetterPositions.last().first

            if (isBottomToTop) {
                var r = endRow
                var c = endCol
                while (r >= startRow && c <= startCol) {
                    val letter = gameBoard[r][c].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(r, c, letter))
                    }
                    r--
                    c++
                }
            } else {
                var r = startRow
                var c = startCol
                while (r <= endRow && c >= endCol) {
                    val letter = gameBoard[r][c].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(r, c, letter))
                    }
                    r++
                    c--
                }
            }
        } else {
            var r = startRow
            var c = startCol
            while (r <= endRow && c >= endCol) {
                val letter = gameBoard[r][c].letter
                if (letter != null) {
                    wordBuilder.append(letter)
                    wordTiles.add(Triple(r, c, letter))
                }
                r++
                c--
            }
        }

        return Pair(wordBuilder.toString(), wordTiles)
    }

    // Sağ çapraz kelimeyi bulur
    private fun findRightDiagonalWord(row: Int, col: Int): Pair<String, List<Triple<Int, Int, Char>>> {
        if (!gameBoard[row][col].isOccupied) return Pair("", emptyList())

        var startRow = row
        var startCol = col
        var endRow = row
        var endCol = col

        while (startRow > 0 && startCol > 0 && gameBoard[startRow - 1][startCol - 1].isOccupied) {
            startRow--
            startCol--
        }

        while (endRow < 14 && endCol < 14 && gameBoard[endRow + 1][endCol + 1].isOccupied) {
            endRow++
            endCol++
        }

        if (endRow - startRow < 1 || endCol - startCol < 1) return Pair("", emptyList())

        val wordBuilder = StringBuilder()
        val wordTiles = mutableListOf<Triple<Int, Int, Char>>()

        val placedLetterPositions = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (gameBoard[i][j].isNewlyPlaced) {
                    placedLetterPositions.add(Pair(i, j))
                }
            }
        }

        if (placedLetterPositions.isNotEmpty()) {
            val isBottomToTop = placedLetterPositions.size >= 2 &&
                    placedLetterPositions.first().first > placedLetterPositions.last().first

            if (isBottomToTop) {
                var r = endRow
                var c = endCol
                while (r >= startRow && c >= startCol) {
                    val letter = gameBoard[r][c].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(r, c, letter))
                    }
                    r--
                    c--
                }
            } else {
                var r = startRow
                var c = startCol
                while (r <= endRow && c <= endCol) {
                    val letter = gameBoard[r][c].letter
                    if (letter != null) {
                        wordBuilder.append(letter)
                        wordTiles.add(Triple(r, c, letter))
                    }
                    r++
                    c++
                }
            }
        } else {
            var r = startRow
            var c = startCol
            while (r <= endRow && c <= endCol) {
                val letter = gameBoard[r][c].letter
                if (letter != null) {
                    wordBuilder.append(letter)
                    wordTiles.add(Triple(r, c, letter))
                }
                r++
                c++
            }
        }

        return Pair(wordBuilder.toString(), wordTiles)
    }

    data class BoardColorInfo(
        val row: Int,
        val col: Int,
        val isValid: Boolean
    )

    // Tahta ve hamle bilgilerini Firebase'e gönderir
    private fun updateBoardAndMakeMove(word: String, wordScore: Int) {
        try {
            val boardData = mutableListOf<List<String>>()

            for (i in 0 until 15) {
                val row = mutableListOf<String>()
                for (j in 0 until 15) {
                    val cell = gameBoard[i][j]
                    row.add(cell.letter?.toString() ?: "")
                }
                boardData.add(row)
            }

            val colorInfoList = mutableListOf<Map<String, Any>>()

            for (i in 0 until 15) {
                for (j in 0 until 15) {
                    val cell = gameBoard[i][j]
                    if (cell.isNewlyPlaced) {
                        val colorInfo = hashMapOf<String, Any>(
                            "row" to i,
                            "col" to j,
                            "isValid" to isValid
                        )
                        colorInfoList.add(colorInfo)
                    }
                }
            }
            val firebaseHelper = FirebaseHelper()

            val lastMoveData = hashMapOf(
                "userId" to currentUserId,
                "word" to word,
                "score" to wordScore,
                "isValid" to isValid,
                "timestamp" to ServerValue.TIMESTAMP,
                "colorInfo" to colorInfoList
            )

            firebaseHelper.makeMove(gameId, currentUserId, word, wordScore, boardData, lastMoveData)
            {
                    success -> if (!success)
            {
                Log.e("GameActivity", "Hamle Firebase'e gönderilemedi!")
            }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Tahta ve hamle güncellenirken hata", e)
        }
    }

    // Harflerin düzgün yerleştirilip yerleştirilmediğini kontrol eder
    private fun checkLetterAlignment(placedLetters: List<Triple<Int, Int, Char>>): Boolean {
        if (placedLetters.size == 1) return true

        val allSameRow = placedLetters.all { it.first == placedLetters[0].first }

        val allSameColumn = placedLetters.all { it.second == placedLetters[0].second }

        val isDiagonal = checkDiagonalAlignment(placedLetters)

        if (allSameRow) {
            val columns = placedLetters.map { it.second }.sorted()

            for (i in 0 until columns.size - 1) {
                if (columns[i + 1] - columns[i] > 1) {
                    val row = placedLetters[0].first
                    for (col in columns[i] + 1 until columns[i + 1]) {
                        if (!gameBoard[row][col].isOccupied) {
                            return false
                        }
                    }
                }
            }
            return true
        } else if (allSameColumn) {
            val rows = placedLetters.map { it.first }.sorted()

            for (i in 0 until rows.size - 1) {
                if (rows[i + 1] - rows[i] > 1) {
                    val col = placedLetters[0].second
                    for (row in rows[i] + 1 until rows[i + 1]) {
                        if (!gameBoard[row][col].isOccupied) {
                            return false
                        }
                    }
                }
            }
            return true
        } else if (isDiagonal) {
            return true
        } else {
            return false
        }
    }

    // Çapraz yerleştirmeyi kontrol eder
    private fun checkDiagonalAlignment(placedLetters: List<Triple<Int, Int, Char>>): Boolean {
        if (placedLetters.size <= 1) return true

        val sortedLetters = placedLetters.sortedWith(compareBy({ it.first }, { it.second }))

        val isRightDiagonal = (1 until sortedLetters.size).all {
            (sortedLetters[it].first - sortedLetters[it-1].first == 1) &&
                    (sortedLetters[it].second - sortedLetters[it-1].second == 1)
        }

        val isLeftDiagonal = (1 until sortedLetters.size).all {
            (sortedLetters[it].first - sortedLetters[it-1].first == 1) &&
                    (sortedLetters[it].second - sortedLetters[it-1].second == -1)
        }

        if (isRightDiagonal) {
            for (i in 0 until sortedLetters.size - 1) {
                val currentRow = sortedLetters[i].first
                val currentCol = sortedLetters[i].second
                val nextRow = sortedLetters[i+1].first
                val nextCol = sortedLetters[i+1].second

                if (nextRow - currentRow > 1 && nextCol - currentCol > 1) {
                    for (j in 1 until nextRow - currentRow) {
                        val row = currentRow + j
                        val col = currentCol + j
                        if (!gameBoard[row][col].isOccupied) {
                            return false
                        }
                    }
                }
            }
            return true
        } else if (isLeftDiagonal) {
            for (i in 0 until sortedLetters.size - 1) {
                val currentRow = sortedLetters[i].first
                val currentCol = sortedLetters[i].second
                val nextRow = sortedLetters[i+1].first
                val nextCol = sortedLetters[i+1].second

                if (nextRow - currentRow > 1 && currentCol - nextCol > 1) {
                    for (j in 1 until nextRow - currentRow) {
                        val row = currentRow + j
                        val col = currentCol - j
                        if (!gameBoard[row][col].isOccupied) {
                            return false
                        }
                    }
                }
            }
            return true
        }

        return false
    }

    // Harflerin mevcut tahta ile bağlantılı olup olmadığını kontrol eder
    private fun checkLetterConnection(placedLetters: List<Triple<Int, Int, Char>>): Boolean {
        var hasConnection = false
        for (letter in placedLetters) {
            val row = letter.first
            val col = letter.second

            if (row > 0 && gameBoard[row-1][col].isOccupied && !isNewlyPlaced(row-1, col, placedLetters)) {
                hasConnection = true
                break
            }
            if (row < 14 && gameBoard[row+1][col].isOccupied && !isNewlyPlaced(row+1, col, placedLetters)) {
                hasConnection = true
                break
            }
            if (col > 0 && gameBoard[row][col-1].isOccupied && !isNewlyPlaced(row, col-1, placedLetters)) {
                hasConnection = true
                break
            }
            if (col < 14 && gameBoard[row][col+1].isOccupied && !isNewlyPlaced(row, col+1, placedLetters)) {
                hasConnection = true
                break
            }
        }

        return hasConnection
    }

    // Hücrenin yeni yerleştirilmiş olup olmadığını kontrol eder
    private fun isNewlyPlaced(row: Int, col: Int, placedLetters: List<Triple<Int, Int, Char>>): Boolean {
        return placedLetters.any { it.first == row && it.second == col }
    }

    // Yerleştirilen harflerden kelimeyi oluşturur
    private fun buildWord(placedLetters: List<Triple<Int, Int, Char>>): String {
        if (placedLetters.isEmpty()) return ""

        val allSameRow = placedLetters.all { it.first == placedLetters[0].first }

        val allSameColumn = placedLetters.all { it.second == placedLetters[0].second }

        val sortedLetters = placedLetters.sortedWith(compareBy({ it.first }, { it.second }))
        val isRightDiagonal = (1 until sortedLetters.size).all {
            (sortedLetters[it].first - sortedLetters[it-1].first == 1) &&
                    (sortedLetters[it].second - sortedLetters[it-1].second == 1)
        }

        val isLeftDiagonal = (1 until sortedLetters.size).all {
            (sortedLetters[it].first - sortedLetters[it-1].first == 1) &&
                    (sortedLetters[it].second - sortedLetters[it-1].second == -1)
        }

        if (allSameRow) {
            val sortedByCol = placedLetters.sortedBy { it.second }
            val row = sortedByCol[0].first
            val startCol = sortedByCol[0].second
            val endCol = sortedByCol.last().second

            val wordBuilder = StringBuilder()
            for (col in startCol..endCol) {
                val cell = gameBoard[row][col]
                if (cell.isOccupied) {
                    cell.letter?.let { wordBuilder.append(it) }
                }
            }

            return wordBuilder.toString()
        } else if (allSameColumn) {
            val sortedByRow = placedLetters.sortedBy { it.first }
            val col = sortedByRow[0].second
            val startRow = sortedByRow[0].first
            val endRow = sortedByRow.last().first

            val wordBuilder = StringBuilder()
            for (row in startRow..endRow) {
                val cell = gameBoard[row][col]
                if (cell.isOccupied) {
                    cell.letter?.let { wordBuilder.append(it) }
                }
            }

            return wordBuilder.toString()
        } else if (isRightDiagonal) {
            val startRow = sortedLetters[0].first
            val startCol = sortedLetters[0].second
            val endRow = sortedLetters.last().first
            val endCol = sortedLetters.last().second

            val wordBuilder = StringBuilder()
            var row = startRow
            var col = startCol
            while (row <= endRow && col <= endCol) {
                val cell = gameBoard[row][col]
                if (cell.isOccupied) {
                    cell.letter?.let { wordBuilder.append(it) }
                }
                row++
                col++
            }

            return wordBuilder.toString()
        } else if (isLeftDiagonal) {
            val startRow = sortedLetters[0].first
            val startCol = sortedLetters[0].second
            val endRow = sortedLetters.last().first
            val endCol = sortedLetters.last().second

            val wordBuilder = StringBuilder()
            var row = startRow
            var col = startCol
            while (row <= endRow && col >= endCol) {
                val cell = gameBoard[row][col]
                if (cell.isOccupied) {
                    cell.letter?.let { wordBuilder.append(it) }
                }
                row++
                col--
            }

            return wordBuilder.toString()
        } else {
            if (placedLetters.size == 1) {
                return placedLetters[0].third.toString()
            }

            return ""
        }
    }

    // Yeni yerleştirilen harfleri kalıcı olarak işaretler
    private fun markLettersAsPlaced(placedLetters: List<Triple<Int, Int, Char>>) {
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                val cell = gameBoard[i][j]
                if (cell.isNewlyPlaced) {
                    cell.isNewlyPlaced = false
                }
            }
        }
    }


    private fun applySpecialEffects(placedLetters: List<Triple<Int, Int, Char>>, initialScore: Int): Int {
        var score = initialScore
        var wordMultiplier = 1
        var transferPoints = false
        var reducePoints = false
        var disableMultipliers = false
        var cancelWord = false // Kelime iptali için

        Log.d("SpecialEffects", "Başlangıç puanı: $score")

        // Önce kelime iptali olup olmadığını kontrol et
        for (letterPos in placedLetters) {
            val (row, col, _) = letterPos
            val cell = gameBoard[row][col]
            val effect = cell.specialEffect ?: cell.hiddenEffect

            if (effect == SpecialEffect.KELIME_IPTALI) {
                cancelWord = true
                Log.d("SpecialEffects", "Kelime iptali etkisi tespit edildi")
                Toast.makeText(this, "Kelime İptali: Bu kelimeden puan alamazsınız!", Toast.LENGTH_SHORT).show()
                return 0; // Hemen 0 döndür, hiç puan hesaplaması yapma
            }
        }

        // Diğer efektleri kontrol et
        for (letterPos in placedLetters) {
            val (row, col, _) = letterPos
            val cell = gameBoard[row][col]
            val effect = cell.specialEffect ?: cell.hiddenEffect

            if (effect == SpecialEffect.PUAN_TRANSFERI) {
                transferPoints = true
                Log.d("SpecialEffects", "Puan transferi etkisi tespit edildi")
            } else if (effect == SpecialEffect.EKSTRA_HAMLE_ENGELI) {
                disableMultipliers = true
                Log.d("SpecialEffects", "Ekstra hamle engeli etkisi uygulandı")
                Toast.makeText(this, "Ekstra Hamle Engeli: Çarpanlar iptal edildi!", Toast.LENGTH_SHORT).show()
            } else if (effect == SpecialEffect.PUAN_BOLUNMESI) {
                reducePoints = true
                Log.d("SpecialEffects", "Puan bölünmesi etkisi uygulandı")
                Toast.makeText(this, "Puan Bölünmesi: Puan %30'a düşürüldü!", Toast.LENGTH_SHORT).show()
            }
        }

        // Puan hesapla
        if (disableMultipliers) {
            // Ekstra hamle engeli varsa, sadece harf puanlarını topla, çarpanları kullanma
            score = 0
            for (letterPos in placedLetters) {
                val (_, _, letter) = letterPos
                score += getLetterPoint(letter)
            }
        } else {
            // Normal hesaplama (çarpanları uygula)
            score *= wordMultiplier
        }

        // Puan bölünmesi etkisi
        if (reducePoints) {
            score = (score * 0.3).toInt()
            Log.d("SpecialEffects", "Puan %30'a düşürüldü: $score")
        }

        // Puan transferi etkisi
        if (transferPoints) {
            // Puanı doğrudan rakibin skoruna ekle
            updateOpponentScore(score)

            // Oyuncuya bilgi ver
            Toast.makeText(this@GameActivity,
                "Puan Transferi: $score puan rakibe transfer edildi!",
                Toast.LENGTH_SHORT).show()

            // Oyuncuya 0 puan dön (kendisi hiç puan alamayacak)
            return 0
        }

        Log.d("SpecialEffects", "Son puan: $score")
        return score
    }

    // Çarpansız puan hesaplama
    private fun calculateWordScoreWithoutMultipliers(placedLetters: List<Triple<Int, Int, Char>>): Int {
        var score = 0

        for (letterPos in placedLetters) {
            val (_, _, letter) = letterPos
            score += getLetterPoint(letter)
        }

        return score
    }

    // Kelimenin puanını hesaplar
    private fun calculateWordScore(placedLetters: List<Triple<Int, Int, Char>>): Int {
        var score = 0
        var wordMultiplier = 1

        for (letterPos in placedLetters) {
            val (row, col, letter) = letterPos
            val letterScore = getLetterPoint(letter)
            val cell = gameBoard[row][col]

            var finalLetterScore = letterScore
            when (cell.specialEffect) {
                SpecialEffect.H2 -> finalLetterScore *= 2
                SpecialEffect.H3 -> finalLetterScore *= 3
                else -> { }
            }

            score += finalLetterScore

            when (cell.specialEffect) {
                SpecialEffect.K2 -> wordMultiplier *= 2
                SpecialEffect.K3 -> wordMultiplier *= 3
                else -> { }
            }
        }

        return score * wordMultiplier
    }

    // Oyuncu puanını günceller
    private fun updatePlayerScore(score: Int) {
        val currentText = binding.userInfo.text.toString()

        val pattern = "Puan: (\\d+)".toRegex()
        val matchResult = pattern.find(currentText)
        val currentScore = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val newScore = currentScore + score

        val newText = currentText.replace("Puan: $currentScore", "Puan: $newScore")
        binding.userInfo.text = newText
    }

    // Rakip puanını günceller
    private fun updateOpponentScore(score: Int) {
        val opponentId = getOpponentId()
        if (opponentId.isEmpty()) return

        val firebaseHelper = FirebaseHelper()

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("players").child(opponentId).child("score").get()
            .addOnSuccessListener { snapshot ->
                val currentScore = snapshot.getValue(Int::class.java) ?: 0
                val newScore = currentScore + score

                firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                    .child("players").child(opponentId).child("score").setValue(newScore)
                    .addOnSuccessListener {
                        val currentText = binding.opponentInfo.text.toString()
                        val pattern = ".*\\| Puan: (\\d+)".toRegex()
                        val matchResult = pattern.find(currentText)
                        val oldScore = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0

                        val newText = currentText.replace("Puan: $oldScore", "Puan: $newScore")
                        binding.opponentInfo.text = newText

                        Toast.makeText(this, "Rakibe $score puan transfer edildi!", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    // Oyuncunun elinden harf kaldırır
    private fun removeLetterFromPlayerTiles(index: Int) {
        try {
            if (index < 0 || index >= binding.playerTiles.childCount) {
                Toast.makeText(this, "Geçersiz harf indeksi!", Toast.LENGTH_SHORT).show()
                return
            }

            binding.playerTiles.removeViewAt(index)

            if (index < currentPlayerTiles.size) {
                currentPlayerTiles.removeAt(index)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Harf kaldırılırken hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Oyuncuya yeni harf ekler
    private fun addNewLetterToPlayerTiles(letter: Char) {
        currentPlayerTiles.add(letter)

        val tileBinding = TileCellBinding.inflate(layoutInflater)
        val tileView = tileBinding.root

        tileBinding.letterValue.text = letter.toString()
        tileBinding.letterPoint.text = getLetterPoint(letter).toString()

        val newIndex = currentPlayerTiles.size - 1
        tileView.setOnClickListener {
            selectLetter(newIndex, tileView, letter)
        }

        val layoutParams = FrameLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.harf_size),
            resources.getDimensionPixelSize(R.dimen.harf_size)
        )
        layoutParams.setMargins(5, 5, 5, 5)

        binding.playerTiles.addView(tileView, layoutParams)
    }

    // Oyuncuya başlangıç harflerini dağıtır
    private fun distributeLettersToPlayer() {
        binding.playerTiles.removeAllViews()
        currentPlayerTiles.clear()

        val unluHarfler = listOf('A', 'E', 'I', 'İ', 'O', 'Ö', 'U', 'Ü')
        val unsuHarfler = listOf('B', 'C', 'Ç', 'D', 'F', 'G', 'Ğ', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'R', 'S', 'Ş', 'T', 'V', 'Y', 'Z')

        val shuffledUnluHarfler = unluHarfler.shuffled().toMutableList()
        val shuffledUnsuHarfler = unsuHarfler.shuffled().toMutableList()

        val unluCount = 3
        val unsuCount = 4

        for (i in 0 until unluCount) {
            if (shuffledUnluHarfler.isNotEmpty()) {
                val letter = shuffledUnluHarfler.removeAt(0)
                currentPlayerTiles.add(letter)

                val tileBinding = TileCellBinding.inflate(layoutInflater)
                val tileView = tileBinding.root

                tileBinding.letterValue.text = letter.toString()
                tileBinding.letterPoint.text = getLetterPoint(letter).toString()

                tileView.setOnClickListener {
                    selectLetter(i, tileView, letter)
                }

                val layoutParams = FrameLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.harf_size),
                    resources.getDimensionPixelSize(R.dimen.harf_size)
                )
                layoutParams.setMargins(5, 5, 5, 5)

                binding.playerTiles.addView(tileView, layoutParams)
            }
        }

        for (i in 0 until unsuCount) {
            if (shuffledUnsuHarfler.isNotEmpty()) {
                val letter = shuffledUnsuHarfler.removeAt(0)
                currentPlayerTiles.add(letter)

                val tileBinding = TileCellBinding.inflate(layoutInflater)
                val tileView = tileBinding.root

                tileBinding.letterValue.text = letter.toString()
                tileBinding.letterPoint.text = getLetterPoint(letter).toString()

                tileView.setOnClickListener {
                    selectLetter(unluCount + i, tileView, letter)
                }

                val layoutParams = FrameLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.harf_size),
                    resources.getDimensionPixelSize(R.dimen.harf_size)
                )
                layoutParams.setMargins(5, 5, 5, 5)

                binding.playerTiles.addView(tileView, layoutParams)
            }
        }

        remainingLetters -= 7
        updateRemainingLettersDisplay()
    }

    // Kalan harf sayısını günceller
    private fun updateRemainingLettersDisplay() {
        binding.remainingLettersCount.text = remainingLetters.toString()
    }

    // Harf havuzu oluşturur
    private fun createLetterPool(): List<Char> {
        val pool = mutableListOf<Char>()

        val letterDistribution = mapOf(
            'A' to 12, 'B' to 2, 'C' to 2, 'Ç' to 2, 'D' to 2, 'E' to 8,
            'F' to 1, 'G' to 1, 'Ğ' to 1, 'H' to 1, 'I' to 4, 'İ' to 7,
            'J' to 1, 'K' to 7, 'L' to 7, 'M' to 4, 'N' to 5, 'O' to 3,
            'Ö' to 1, 'P' to 1, 'R' to 6, 'S' to 3, 'Ş' to 2, 'T' to 5,
            'U' to 3, 'Ü' to 2, 'V' to 1, 'Y' to 2, 'Z' to 2
        )

        repeat(2) {
            pool.add('*')
        }

        letterDistribution.forEach { (letter, count) ->
            repeat(count) {
                pool.add(letter)
            }
        }

        return pool.shuffled()
    }

    // Harfin puanını döndürür
    private fun getLetterPoint(letter: Char): Int {
        return when (letter) {
            'A' -> 1
            'B' -> 3
            'C' -> 4
            'Ç' -> 4
            'D' -> 3
            'E' -> 1
            'F' -> 7
            'G' -> 5
            'Ğ' -> 8
            'H' -> 5
            'I' -> 2
            'İ' -> 1
            'J' -> 10
            'K' -> 1
            'L' -> 1
            'M' -> 2
            'N' -> 1
            'O' -> 2
            'Ö' -> 7
            'P' -> 5
            'R' -> 1
            'S' -> 2
            'Ş' -> 4
            'T' -> 1
            'U' -> 2
            'Ü' -> 3
            'V' -> 7
            'Y' -> 3
            'Z' -> 4
            '*' -> 0
            else -> 0
        }
    }

    // Buton dinleyicilerini ayarlar
    private fun setupButtonListeners() {
        binding.confirmButton.setOnClickListener {
            if (isMyTurn) {
                validateWord()
            } else {
                Toast.makeText(this, "Sıra sizde değil!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    // Popup menüyü gösterir
    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.game_actions_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_pass -> {
                    passTurn()
                    true
                }
                R.id.action_surrender -> {
                    surrenderGame()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    // Sırayı rakibe geçirir
    private fun passTurn() {
        try {
            val firebaseHelper = FirebaseHelper()

            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val playersData = snapshot.child("players").value as? Map<*, *>
                        if (playersData != null) {
                            var opponentId: String? = null
                            for (playerId in playersData.keys) {
                                if (playerId.toString() != currentUserId) {
                                    opponentId = playerId.toString()
                                    break
                                }
                            }

                            if (opponentId != null) {
                                val updates = hashMapOf<String, Any>(
                                    "currentTurn" to opponentId
                                )

                                firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                                    .updateChildren(updates)
                                    .addOnSuccessListener {
                                        isMyTurn = false
                                        updateTurnInfo()
                                        Toast.makeText(this, "Sıra rakibe geçti", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Sıra geçilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                Toast.makeText(this, "Rakip bulunamadı! Yeniden deneyin.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Oyuncu bilgileri alınamadı!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Oyun bulunamadı!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Oyun bilgisi alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Oyundan çekilir ve rakibi kazanan ilan eder
    private fun surrenderGame() {
        AlertDialog.Builder(this)
            .setTitle("Teslim Ol")
            .setMessage("Oyunu sonlandırmak istediğinize emin misiniz? Rakip kazanan olarak ilan edilecek.")
            .setPositiveButton("Evet") { _, _ ->
                try {
                    val firebaseHelper = FirebaseHelper()

                    firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                val playersData = snapshot.child("players").value as? Map<*, *>
                                if (playersData != null) {
                                    var opponentId: String? = null
                                    for (playerId in playersData.keys) {
                                        if (playerId.toString() != currentUserId) {
                                            opponentId = playerId.toString()
                                            break
                                        }
                                    }

                                    if (opponentId != null) {
                                        val updates = hashMapOf<String, Any>(
                                            "status" to "finished",
                                            "winner" to opponentId,
                                            "finishedAt" to ServerValue.TIMESTAMP,
                                            "finishReason" to "surrender"
                                        )

                                        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                                            .updateChildren(updates)
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Oyun sonlandırıldı. Rakip kazanan!", Toast.LENGTH_SHORT).show()

                                                firebaseHelper.removeGameListener(gameId, gameListener)

                                                val intent = Intent(this, GameOptionsActivity::class.java)
                                                intent.putExtra("USER_ID", currentUserId)
                                                intent.putExtra("USERNAME", username)
                                                intent.putExtra("MESSAGE", "Oyundan çekildiniz. Rakip kazanan olarak belirlendi.")
                                                startActivity(intent)
                                                finish()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(this, "Oyun sonlandırılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        Toast.makeText(this, "Rakip bulunamadı! Yeniden deneyin.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this, "Oyuncu bilgileri alınamadı!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "Oyun bulunamadı!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Oyun bilgisi alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } catch (e: Exception) {
                    Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hayır", null)
            .show()
    }

    // Rakip ID'sini döndürür
    private fun getOpponentId(): String {
        try {
            var gameData: Map<String, Any>? = null
            val latch = CountDownLatch(1)

            val firebaseHelper = FirebaseHelper()
            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        gameData = snapshot.value as? Map<String, Any>
                    }
                    latch.countDown()
                }
                .addOnFailureListener {
                    latch.countDown()
                }

            latch.await(3, TimeUnit.SECONDS)

            if (gameData != null) {
                val players = gameData?.get("players") as? Map<*, *> ?: return ""

                for (playerId in players.keys) {
                    if (playerId.toString() != currentUserId) {
                        return playerId.toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Rakip ID'si alınırken hata", e)
        }
        return ""
    }

    // Geri sayım sayacını başlatır
    private fun startTimer() {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(gameTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                binding.timeRemaining.text = "Kalan Süre: %02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                binding.timeRemaining.text = "Süre Bitti!"

                disableAllInteractions()

                if (isMyTurn) {
                    Toast.makeText(this@GameActivity, "Süreniz doldu! Oyun sonlandırılıyor...", Toast.LENGTH_LONG).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        surrenderDueToTimeout()
                    }, 1000)
                }
            }
        }.start()
    }

    // Bonus'u envantere ekler
    private fun addBonusToInventory(bonus: SpecialEffect) {
        Log.d("JokerTest", "addBonusToInventory çağrıldı: $bonus")

        when (bonus) {
            SpecialEffect.BOLGE_YASAGI -> {
                Log.d("JokerTest", "Bölge yasağı jokeri envantere ekleniyor")

                hasRegionBanJoker = true
                binding.regionBanJoker.visibility = View.VISIBLE
                binding.jokerPanel.visibility = View.VISIBLE

                Log.d("JokerTest", "Bölge yasağı butonu görünürlük: ${binding.regionBanJoker.visibility}")
                Log.d("JokerTest", "Joker panel görünürlük: ${binding.jokerPanel.visibility}")

            }
            SpecialEffect.HARF_YASAGI -> {
                Log.d("JokerTest", "harf yasağı jokeri envantere ekleniyor")

                hasLetterBanJoker = true
                binding.letterBanJoker.visibility = View.VISIBLE
                binding.jokerPanel.visibility = View.VISIBLE
                Log.d("JokerTest", "harf yasağı butonu görünürlük: ${binding.regionBanJoker.visibility}")
                Log.d("JokerTest", "Joker panel görünürlük: ${binding.jokerPanel.visibility}")
            }
            SpecialEffect.EKSTRA_HAMLE_JOKERI -> {
                Log.d("JokerTest", "extra hamle  jokeri envantere ekleniyor")

                hasExtraMoveJoker = true
                binding.extraMoveJoker.visibility = View.VISIBLE
                binding.jokerPanel.visibility = View.VISIBLE
                Log.d("JokerTest", "extra hamle  butonu görünürlük: ${binding.regionBanJoker.visibility}")
                Log.d("JokerTest", "Joker panel görünürlük: ${binding.jokerPanel.visibility}")
            }
            SpecialEffect.H2,
            SpecialEffect.H3,
            SpecialEffect.K2,
            SpecialEffect.K3,
            SpecialEffect.PUAN_BOLUNMESI,
            SpecialEffect.PUAN_TRANSFERI,
            SpecialEffect.HARF_KAYBI,
            SpecialEffect.EKSTRA_HAMLE_ENGELI,
            SpecialEffect.KELIME_IPTALI -> {
                return
            }
        }

        updateJokerPanelVisibility()

        val firebaseHelper = FirebaseHelper()

        val jokerType = when (bonus) {
            SpecialEffect.BOLGE_YASAGI -> "regionBan"
            SpecialEffect.HARF_YASAGI -> "letterBan"
            SpecialEffect.EKSTRA_HAMLE_JOKERI -> "extraMove"
            else -> ""
        }

        if (jokerType.isNotEmpty()) {
            firebaseHelper.addJokerToInventory(gameId, currentUserId, jokerType) { success ->
                if (!success) {
                    runOnUiThread {
                        Toast.makeText(this, "Joker envantere eklenemedi, sunucu hatası!",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Joker butonlarını ayarlar
    private fun setupJokerButtons() {
        binding.regionBanJoker.visibility = View.GONE
        binding.letterBanJoker.visibility = View.GONE
        binding.extraMoveJoker.visibility = View.GONE

        updateJokerPanelVisibility()

        binding.regionBanJoker.setOnClickListener {
            if (!isMyTurn) {
                Toast.makeText(this, "Jokerleri sadece kendi sıranızda kullanabilirsiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showRegionBanDialog()
        }

        binding.letterBanJoker.setOnClickListener {
            if (!isMyTurn) {
                Toast.makeText(this, "Jokerleri sadece kendi sıranızda kullanabilirsiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            activateLetterBanJoker()
        }

        binding.extraMoveJoker.setOnClickListener {
            if (!isMyTurn) {
                Toast.makeText(this, "Jokerleri sadece kendi sıranızda kullanabilirsiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            activateExtraMoveJoker()
        }

        checkJokerInventory()
    }

    // Joker panelinin görünürlüğünü günceller
    private fun updateJokerPanelVisibility() {
        if (hasRegionBanJoker || hasLetterBanJoker || hasExtraMoveJoker) {
            binding.jokerPanel.visibility = View.VISIBLE
        } else {
            binding.jokerPanel.visibility = View.GONE
        }
    }

    // Envanterdeki jokerleri kontrol eder
    private fun checkJokerInventory() {
        try {
            val firebaseHelper = FirebaseHelper()
            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                .child("players").child(currentUserId).child("jokers").get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        for (jokerSnapshot in snapshot.children) {
                            val jokerType = jokerSnapshot.key ?: continue

                            when (jokerType) {
                                "regionBan" -> {
                                    hasRegionBanJoker = true
                                    binding.regionBanJoker.visibility = View.VISIBLE
                                }

                                "letterBan" -> {
                                    hasLetterBanJoker = true
                                    binding.letterBanJoker.visibility = View.VISIBLE
                                }

                                "extraMove" -> {
                                    hasExtraMoveJoker = true
                                    binding.extraMoveJoker.visibility = View.VISIBLE
                                }
                            }
                        }

                        updateJokerPanelVisibility()
                    }
                }
        } catch (e: Exception) {
            Log.e("GameActivity", "Joker envanteri kontrol edilirken hata", e)
        }
    }

    // Bölge yasağı diyaloğunu gösterir
    private fun showRegionBanDialog() {
        val options = arrayOf("Sol Bölgeyi Yasakla", "Sağ Bölgeyi Yasakla")

        AlertDialog.Builder(this)
            .setTitle("Bölge Yasağı")
            .setItems(options) { _, which ->
                val side = if (which == 0) "left" else "right"
                activateRegionBanJoker(side)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    // Bölge yasağı jokerini aktifleştirir
    private fun activateRegionBanJoker(side: String) {
        hasRegionBanJoker = false
        binding.regionBanJoker.visibility = View.GONE
        updateJokerPanelVisibility()

        regionBanPending = true
        bannedRegionSide = side

        val firebaseHelper = FirebaseHelper()
        val jokerData = hashMapOf(
            "type" to "regionBan",
            "bannedSide" to side,
            "activatedBy" to currentUserId,
            "activatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
            "status" to "pending"
        )

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("activeEffects").setValue(jokerData)
            .addOnFailureListener { e ->
                Log.e("GameActivity", "Bölge yasağı jokeri uygulanırken hata", e)
                Toast.makeText(this, "Joker uygulanamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("players").child(currentUserId).child("jokers").child("regionBan")
            .removeValue()

        Toast.makeText(
            this,
            if (side == "left") "Sol bölge yasağı aktifleştirildi! Sıra rakibe geçtiğinde etki başlayacak."
            else "Sağ bölge yasağı aktifleştirildi! Sıra rakibe geçtiğinde etki başlayacak.",
            Toast.LENGTH_SHORT
        ).show()
    }
    private fun activateLetterBanJoker() {
        hasLetterBanJoker = false
        binding.letterBanJoker.visibility = View.GONE
        updateJokerPanelVisibility()

        letterBanPending = true

        val firebaseHelper = FirebaseHelper()
        val jokerData = hashMapOf(
            "type" to "letterBan",
            "activatedBy" to currentUserId,
            "activatedAt" to ServerValue.TIMESTAMP,
            "status" to "pending"
        )

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("activeEffects").setValue(jokerData)
            .addOnSuccessListener {
                Log.d("JokerEffects", "Harf yasağı jokeri kaydedildi, sıra rakibe geçtiğinde etkin olacak")
                Toast.makeText(this, "Harf yasağı jokeri aktifleştirildi! Sıra rakibe geçtiğinde 2 harfi dondurulacak.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("GameActivity", "Harf yasağı jokeri uygulanırken hata", e)
                Toast.makeText(this, "Joker uygulanamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("players").child(currentUserId).child("jokers").child("letterBan")
            .removeValue()
    }

    private fun activateExtraMoveJoker() {
        hasExtraMoveJoker = false
        binding.extraMoveJoker.visibility = View.GONE
        updateJokerPanelVisibility()

        extraMoveUsed = true

        val firebaseHelper = FirebaseHelper()
        val jokerData = hashMapOf(
            "type" to "extraMove",
            "activatedBy" to currentUserId,
            "activatedAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
            "status" to "pending"
        )

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("activeEffects").setValue(jokerData)
            .addOnFailureListener { e ->
                Log.e("GameActivity", "Ekstra hamle jokeri uygulanırken hata", e)
                Toast.makeText(this, "Joker uygulanamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("players").child(currentUserId).child("jokers").child("extraMove")
            .removeValue()

        Toast.makeText(this, "Ekstra hamle jokeri aktifleştirildi! Kelimeyi onayladığınızda bir hamle daha yapabileceksiniz.", Toast.LENGTH_SHORT).show()
    }

    // Bölge yasağını uygular
    private fun applyRegionBan(side: String) {
        for (i in 0 until 15) {
            for (j in 0 until 15) {
                if (side == "left" && j < 7) {
                    gameBoard[i][j].view?.alpha = 0.3f
                    gameBoard[i][j].isBanned = true
                }
                else if (side == "right" && j > 7) {
                    gameBoard[i][j].view?.alpha = 0.3f
                    gameBoard[i][j].isBanned = true
                }
            }
        }
        bannedRegion = side

    }

    // Joker panelini gizlemek için kontrol yapar
    private fun checkAndHideJokerPanel() {
        if (!hasRegionBanJoker && !hasLetterBanJoker && !hasExtraMoveJoker) {
            binding.jokerPanel.visibility = View.GONE
        }
    }

    private fun listenForJokerEffects() {
        val firebaseHelper = FirebaseHelper()
        firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
            .child("activeEffects").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        return@onDataChange
                    }

                    val effectType = snapshot.child("type").getValue(String::class.java) ?: return@onDataChange
                    val activatedBy = snapshot.child("activatedBy").getValue(String::class.java) ?: return@onDataChange
                    val status = snapshot.child("status").getValue(String::class.java) ?: "active"

                    if (activatedBy != currentUserId && status == "active") {
                        runOnUiThread {
                            when (effectType) {
                                "regionBan" -> {
                                    val bannedSide = snapshot.child("bannedSide").getValue(String::class.java) ?: return@runOnUiThread
                                    applyRegionBan(bannedSide)
                                    bannedRegion = bannedSide
                                    Toast.makeText(
                                        this@GameActivity,
                                        "Rakibiniz bölge yasağı jokeri kullandı! ${if (bannedSide == "left") "Sol" else "Sağ"} bölgede oynayamazsınız.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                "letterBan" -> {
                                    if (isMyTurn) {
                                        Log.d("JokerEffects", "Harf yasağı jokeri tespit edildi, harfler dondurulacak")
                                        freezeRandomLetters()
                                    }
                                }
                                "extraMove" -> {
                                    Toast.makeText(
                                        this@GameActivity,
                                        "Rakibiniz ekstra hamle jokeri kullandı! Bu hamleden sonra sıra rakibinizde kalacak.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("GameActivity", "Joker efektleri dinlenemedi", error.toException())
                }
            })
    }


    private fun freezeRandomLetters() {
        try {
            val letterCount = binding.playerTiles.childCount
            Log.d("JokerEffects", "freezeRandomLetters() çağrıldı, mevcut harf sayısı: $letterCount")

            if (letterCount < 2) {
                Log.d("JokerEffects", "Yetersiz harf, dondurma işlemi yapılamıyor")
                return
            }

            frozenLetterIndices.clear()
            val indices = (0 until letterCount).shuffled().take(2)
            Log.d("JokerEffects", "Dondurulacak harfler seçildi: $indices")

            for (index in indices) {
                if (index < binding.playerTiles.childCount) {
                    val letterView = binding.playerTiles.getChildAt(index)

                    // Arka plan rengini siyah yap
                    letterView.setBackgroundColor(Color.BLACK)
                    letterView.alpha = 0.5f

                    letterView.isEnabled = false
                    letterView.isClickable = false

                    letterView.setOnTouchListener { _, _ -> true }

                    frozenLetterIndices.add(index)
                    Log.d("JokerEffects", "$index indeksindeki harf donduruldu")
                } else {
                    Log.e("JokerEffects", "Geçersiz harf indeksi: $index, toplam harf sayısı: ${binding.playerTiles.childCount}")
                }
            }

            // Donmuş harfleri Firebase'e kaydet
            val firebaseHelper = FirebaseHelper()
            val frozenLettersData = hashMapOf<String, Any>(
                "frozenLetters" to frozenLetterIndices
            )

            firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                .child("players").child(currentUserId).updateChildren(frozenLettersData)
                .addOnSuccessListener {
                    Log.d("JokerEffects", "Donmuş harf indeksleri Firebase'e kaydedildi: $frozenLetterIndices")
                }
                .addOnFailureListener { e ->
                    Log.e("JokerEffects", "Donmuş harf indeksleri kaydedilemedi: ${e.message}")
                }

            Toast.makeText(this, "Rakibiniz harf yasağı jokeri kullandı! 2 harfiniz bir tur kullanılamayacak.",
                Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("JokerEffects", "Harfler dondurulurken hata oluştu", e)
            Toast.makeText(this, "Harf dondurma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Süre dolduğunda oyunu bitirir
    private fun surrenderDueToTimeout() {
        try {
            val firebaseHelper = FirebaseHelper()
            val opponentId = getOpponentId()

            if (opponentId.isNotEmpty()) {
                val updates = hashMapOf<String, Any>(
                    "status" to "finished",
                    "winner" to opponentId,
                    "finishedAt" to ServerValue.TIMESTAMP,
                    "finishReason" to "timeout"
                )

                firebaseHelper.getDatabase().child(FirebaseHelper.COLLECTION_GAMES).child(gameId)
                    .updateChildren(updates)
                    .addOnSuccessListener {
                        firebaseHelper.removeGameListener(gameId, gameListener)

                        runOnUiThread {
                            val alertDialog = AlertDialog.Builder(this)
                                .setTitle("Süre Doldu!")
                                .setMessage("Hamle yapma süreniz doldu. Rakip oyuncu kazandı.")
                                .setCancelable(false)
                                .setPositiveButton("Tamam") { dialog, _ ->
                                    dialog.dismiss()
                                    navigateToMainScreen()
                                }
                                .create()

                            alertDialog.show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("GameActivity", "Süre doldu hatası: ${e.message}")
                        Handler(Looper.getMainLooper()).postDelayed({
                            surrenderDueToTimeout()
                        }, 3000)
                    }
            } else {
                Log.e("GameActivity", "Rakip bulunamadı, oyun sonlandırılıyor")
                navigateToMainScreen()
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Süre doldu hatası: ${e.message}")
            navigateToMainScreen()
        }
    }

    // Ana ekrana döner
    private fun navigateToMainScreen() {
        val intent = Intent(this, GameDashboardActivity::class.java)
        intent.putExtra("USER_ID", currentUserId)
        intent.putExtra("USERNAME", username)
        intent.putExtra("MESSAGE", "Süre doldu! Rakip kazandı.")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    // Aktivite yok edildiğinde timer'ı durdurur
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    // Tahta hücresini temsil eden iç sınıf
    inner class BoardCell(
        val view: View? = null,
        var letter: Char? = null,
        var isOccupied: Boolean = false,
        var specialEffect: SpecialEffect? = null,
        var hiddenEffect: SpecialEffect? = null,
        var isNewlyPlaced: Boolean = false,
        var isBanned: Boolean = false,
        var placementIndex: Int = -1
    )

    // Özel efekt türleri
    enum class SpecialEffect {
        H2, H3, K2, K3,
        PUAN_BOLUNMESI,
        PUAN_TRANSFERI,
        HARF_KAYBI,
        EKSTRA_HAMLE_ENGELI,
        KELIME_IPTALI,
        BOLGE_YASAGI,
        HARF_YASAGI,
        EKSTRA_HAMLE_JOKERI
    }
}