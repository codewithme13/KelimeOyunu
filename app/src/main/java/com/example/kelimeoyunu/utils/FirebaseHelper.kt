package com.example.kelimeoyunu.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore

// Firebase veritabanı işlemlerini yöneten yardımcı sınıf
class FirebaseHelper {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val realtimeDb = FirebaseDatabase.getInstance().reference

    private var gameRequestListener: ValueEventListener? = null
    private var gameRequestRef: DatabaseReference? = null

    companion object {
        private const val TAG = "FirebaseHelper"
        const val COLLECTION_GAME_REQUESTS = "gameRequests"
        const val COLLECTION_GAMES = "games"
        const val COLLECTION_USERS = "users"
    }

    // Sınıf başlatılırken Firebase bağlantısını test eder ve anonim giriş yapar
    init {
        FirebaseDatabase.getInstance().getReference(".info/connected")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d(TAG, "Firebase bağlantısı: $connected")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase bağlantı kontrolü iptal edildi", error.toException())
                }
            })

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d(TAG, "Anonim giriş başarılı!")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Anonim giriş başarısız", e)
                }
        }
    }

    // Firestore veritabanını döndürür
    fun getFirestoreDb(): FirebaseFirestore {
        return db
    }

    // Yeni oyun isteği oluşturur ve eşleşmeleri kontrol eder
    fun createGameRequest(
        userId: String,
        gameType: String,
        username: String,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        removeListeners()

        try {
            val gameRequestId = realtimeDb.child(COLLECTION_GAME_REQUESTS).push().key
            if (gameRequestId == null) {
                callback(false, "İstek ID oluşturulamadı", null)
                return
            }

            Log.d(TAG, "Oyun isteği oluşturuluyor: $gameRequestId")

            val gameRequest = hashMapOf(
                "userId" to userId,
                "gameType" to gameType,
                "username" to username,
                "timestamp" to ServerValue.TIMESTAMP,
                "status" to "waiting"
            )

            realtimeDb.child(COLLECTION_GAME_REQUESTS).child(gameRequestId).setValue(gameRequest)
                .addOnSuccessListener {
                    Log.d(TAG, "Oyun isteği başarıyla oluşturuldu")

                    callback(true, "Rakip oyuncu bekleniyor...", gameRequestId)

                    listenForMatching(gameRequestId, callback)

                    checkForExistingMatch(gameRequestId, userId, gameType, username, callback)

                    Handler(Looper.getMainLooper()).postDelayed({
                        realtimeDb.child(COLLECTION_GAME_REQUESTS).child(gameRequestId).get()
                            .addOnSuccessListener { snapshot ->
                                if (snapshot.exists()) {
                                    val status =
                                        snapshot.child("status").getValue(String::class.java)

                                    if (status == "waiting") {
                                        Log.d(
                                            TAG,
                                            "Oyun isteği 30 saniye doldu, iptal ediliyor: $gameRequestId"
                                        )
                                        cancelGameRequest(gameRequestId) { success ->
                                            if (success) {
                                                Log.d(
                                                    TAG,
                                                    "Oyun isteği zaman aşımı nedeniyle iptal edildi"
                                                )
                                                callback(
                                                    false,
                                                    "Oyun isteği zaman aşımına uğradı. Lütfen tekrar deneyin.",
                                                    null
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }, 30000)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Oyun isteği oluşturma hatası", e)
                    callback(false, "Oyun isteği oluşturulamadı: ${e.message}", null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Beklenmeyen hata", e)
            callback(false, "Beklenmeyen hata: ${e.message}", null)
        }
    }

    // Realtime Database referansını döndürür
    fun getDatabase(): DatabaseReference {
        return realtimeDb
    }

    // Eşleşme durumunu dinler ve eşleşme olduğunda bildirir
            private fun listenForMatching(
        requestId: String,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        try {
            gameRequestRef = realtimeDb.child(COLLECTION_GAME_REQUESTS).child(requestId)

            gameRequestListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val status = snapshot.child("status").getValue(String::class.java)
                        Log.d(TAG, "İstek durumu değişti: $status")

                        if (status == "matched") {
                            val gameId = snapshot.child("gameId").getValue(String::class.java)

                            if (gameId != null) {
                                Log.d(TAG, "Eşleşme bulundu! Game ID: $gameId")
                                removeListeners()
                                callback(true, gameId, requestId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "İstek durum değişikliği işlenirken hata", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "İstek dinleme hatası", error.toException())
                }
            }

            gameRequestRef?.addValueEventListener(gameRequestListener!!)
            Log.d(TAG, "İstek dinleyicisi eklendi")
        } catch (e: Exception) {
            Log.e(TAG, "İstek dinleme hatası", e)
        }
    }

    // Mevcut eşleşmeleri kontrol eder ve eşleşme bulunursa oyun oluşturur
    private fun checkForExistingMatch(
        requestId: String,
        userId: String,
        gameType: String,
        username: String,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        try {
            realtimeDb.child(COLLECTION_GAME_REQUESTS)
                .orderByChild("gameType")
                .equalTo(gameType)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            Log.d(
                                TAG,
                                "Mevcut eşleşmeler kontrol ediliyor. Sonuç sayısı: ${snapshot.childrenCount}"
                            )

                            for (requestSnapshot in snapshot.children) {
                                val otherRequestId = requestSnapshot.key ?: continue

                                if (otherRequestId == requestId) {
                                    Log.d(TAG, "Kendi isteğimiz atlanıyor: $otherRequestId")
                                    continue
                                }

                                val otherUserId =
                                    requestSnapshot.child("userId").getValue(String::class.java)
                                        ?: continue
                                val otherStatus =
                                    requestSnapshot.child("status").getValue(String::class.java)
                                        ?: continue
                                val otherUsername =
                                    requestSnapshot.child("username").getValue(String::class.java)
                                        ?: "Rakip"

                                Log.d(
                                    TAG,
                                    "Diğer istek kontrol ediliyor: $otherRequestId, status: $otherStatus"
                                )

                                if (otherUserId != userId && otherStatus == "waiting") {
                                    Log.d(TAG, "Eşleşme bulundu: $otherRequestId")

                                    createGame(
                                        requestId,
                                        userId,
                                        otherRequestId,
                                        otherUserId,
                                        gameType,
                                        username,
                                        otherUsername,
                                        callback
                                    )
                                    return
                                }
                            }

                            Log.d(TAG, "Mevcut eşleşme bulunamadı, bekleniyor...")
                        } catch (e: Exception) {
                            Log.e(TAG, "Eşleşme kontrolü sırasında hata", e)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Eşleşme kontrolü iptal edildi", error.toException())
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Eşleşme kontrolü sırasında beklenmeyen hata", e)
        }
    }

    // İki oyuncu arasında yeni bir oyun oluşturur
    private fun createGame(
        requestId1: String,
        userId1: String,
        requestId2: String,
        userId2: String,
        gameType: String,
        username1: String,
        username2: String,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        try {
            val gameId = realtimeDb.child(COLLECTION_GAMES).push().key
            if (gameId == null) {
                Log.e(TAG, "Oyun ID oluşturulamadı")
                return
            }

            val letterPool = createLetterPool()
            val player1Letters = mutableListOf<Char>()
            val player2Letters = mutableListOf<Char>()

            repeat(7) {
                if (letterPool.isNotEmpty()) {
                    val randomIndex = (0 until letterPool.size).random()
                    player1Letters.add(letterPool[randomIndex])
                    letterPool.removeAt(randomIndex)
                }
            }

            repeat(7) {
                if (letterPool.isNotEmpty()) {
                    val randomIndex = (0 until letterPool.size).random()
                    player2Letters.add(letterPool[randomIndex])
                    letterPool.removeAt(randomIndex)
                }
            }

            val gameData = hashMapOf(
                "gameType" to gameType,
                "createdAt" to ServerValue.TIMESTAMP,
                "status" to "active",
                "currentTurn" to userId1,
                "board" to createEmptyBoard(),
                "letterBag" to letterPool.joinToString(""),
                "lastMoveTime" to ServerValue.TIMESTAMP,
                "players" to hashMapOf(
                    userId1 to hashMapOf(
                        "score" to 0,
                        "username" to username1,
                        "remainingLetters" to player1Letters.joinToString("")
                    ),
                    userId2 to hashMapOf(
                        "score" to 0,
                        "username" to username2,
                        "remainingLetters" to player2Letters.joinToString("")
                    )
                )
            )

            realtimeDb.child(COLLECTION_GAMES).child(gameId).setValue(gameData)
                .addOnSuccessListener {
                    Log.d(TAG, "Oyun başarıyla oluşturuldu: $gameId")

                    val updates = hashMapOf<String, Any>(
                        "$COLLECTION_GAME_REQUESTS/$requestId1/status" to "matched",
                        "$COLLECTION_GAME_REQUESTS/$requestId1/gameId" to gameId,
                        "$COLLECTION_GAME_REQUESTS/$requestId2/status" to "matched",
                        "$COLLECTION_GAME_REQUESTS/$requestId2/gameId" to gameId
                    )

                    realtimeDb.updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "İstekler başarıyla güncellendi")
                            callback(true, gameId, requestId1)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "İstekler güncellenirken hata", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Oyun oluşturulurken hata", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Oyun oluşturma sırasında beklenmeyen hata", e)
        }
    }

    // Aktif dinleyicileri temizler
    fun removeListeners() {
        try {
            gameRequestListener?.let { listener ->
                gameRequestRef?.removeEventListener(listener)
                Log.d(TAG, "İstek dinleyicisi kaldırıldı")
            }
            gameRequestListener = null
            gameRequestRef = null
        } catch (e: Exception) {
            Log.e(TAG, "Dinleyicileri temizlerken hata", e)
        }
    }

    // Oyun isteğini iptal eder
    fun cancelGameRequest(requestId: String, callback: (Boolean) -> Unit) {
        try {
            if (requestId.isEmpty()) {
                callback(false)
                return
            }

            removeListeners()

            realtimeDb.child(COLLECTION_GAME_REQUESTS).child(requestId).removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "İstek başarıyla iptal edildi: $requestId")
                    callback(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "İstek iptal edilirken hata", e)
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "İstek iptal edilirken beklenmeyen hata", e)
            callback(false)
        }
    }

    // Boş oyun tahtası oluşturur
    private fun createEmptyBoard(): List<List<String>> {
        val board = mutableListOf<List<String>>()
        for (i in 0 until 15) {
            val row = MutableList(15) { "" }
            board.add(row)
        }
        return board
    }

    // Türkçe alfabeye göre harf havuzu oluşturur
    private fun createLetterPool(): MutableList<Char> {
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

        return pool.shuffled().toMutableList()
    }

    // Kullanıcının tüm aktif oyunlarını siler
    fun deleteAllActiveGames(userId: String, callback: (Boolean) -> Unit) {
        try {
            realtimeDb.child(COLLECTION_GAMES)
                .orderByChild("status")
                .equalTo("active")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val batch = mutableMapOf<String, Any>()
                            var foundGame = false

                            for (gameSnapshot in snapshot.children) {
                                val gameId = gameSnapshot.key ?: continue
                                val gameData = gameSnapshot.getValue(object :
                                    GenericTypeIndicator<Map<String, Any>>() {}) ?: continue
                                val players = gameData["players"] as? Map<*, *> ?: continue

                                if (players.containsKey(userId)) {
                                    foundGame = true
                                    batch["$COLLECTION_GAMES/$gameId/status"] = "cancelled"
                                }
                            }

                            if (foundGame) {
                                realtimeDb.updateChildren(batch)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Tüm aktif oyunlar başarıyla silindi")
                                        callback(true)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Aktif oyunlar silinirken hata", e)
                                        callback(false)
                                    }
                            } else {
                                Log.d(TAG, "Silinecek aktif oyun bulunamadı")
                                callback(true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Aktif oyunları silerken işleme hatası", e)
                            callback(false)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Aktif oyunları silerken sorgu hatası", error.toException())
                        callback(false)
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Aktif oyunları silerken beklenmeyen hata", e)
            callback(false)
        }
    }

    // Kullanıcının aktif oyunlarını getirir
    fun getActiveGames(userId: String, callback: (List<Map<String, Any>>) -> Unit) {
        try {
            realtimeDb.child(COLLECTION_GAMES)
                .orderByChild("status")
                .equalTo("active")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val gamesList = mutableListOf<Map<String, Any>>()

                            for (gameSnapshot in snapshot.children) {
                                val gameId = gameSnapshot.key ?: continue

                                val gameData = gameSnapshot.getValue(object :
                                    GenericTypeIndicator<Map<String, Any>>() {}) ?: continue

                                val players = gameData["players"] as? Map<*, *> ?: continue

                                if (players.containsKey(userId)) {
                                    val gameWithId = gameData.toMutableMap()
                                    gameWithId["id"] = gameId
                                    gamesList.add(gameWithId)
                                }
                            }

                            callback(gamesList)
                        } catch (e: Exception) {
                            Log.e(TAG, "Aktif oyunlar işlenirken hata", e)
                            callback(emptyList())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Aktif oyunlar alınırken hata", error.toException())
                        callback(emptyList())
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Aktif oyunlar alınırken beklenmeyen hata", e)
            callback(emptyList())
        }
    }

    // Oyuncu puanını günceller
    fun updatePlayerScore(gameId: String, userId: String, score: Int, callback: (Boolean) -> Unit) {
        try {
            val updates = hashMapOf<String, Any>(
                "players/$userId/score" to score
            )

            realtimeDb.child(COLLECTION_GAMES).child(gameId).updateChildren(updates)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Puan güncellenirken hata", e)
            callback(false)
        }
    }

    // Oyun hamlesini kaydeder ve sırayı rakibe geçirir
    fun makeMove(
        gameId: String,
        userId: String,
        word: String,
        score: Int,
        board: List<List<String>>,
        lastMoveData: HashMap<String, Any>,
        callback: (Boolean) -> Unit
    ) {
        try {
            realtimeDb.child(COLLECTION_GAMES).child(gameId).child("players").child(userId)
                .child("score").get()
                .addOnSuccessListener { snapshot ->
                    val currentScore = snapshot.getValue(Int::class.java) ?: 0
                    val newScore = currentScore + score

                    realtimeDb.child(COLLECTION_GAMES).child(gameId).child("players").get()
                        .addOnSuccessListener { playersSnapshot ->
                            var opponentId: String? = null

                            for (playerSnapshot in playersSnapshot.children) {
                                val playerId = playerSnapshot.key
                                if (playerId != userId) {
                                    opponentId = playerId
                                    break
                                }
                            }

                            if (opponentId != null) {
                                val updates = hashMapOf<String, Any>(
                                    "board" to board,
                                    "players/$userId/score" to newScore,
                                    "currentTurn" to opponentId,
                                    "lastMoveTime" to ServerValue.TIMESTAMP,
                                    "lastMove" to lastMoveData
                                )

                                realtimeDb.child(COLLECTION_GAMES).child(gameId)
                                    .updateChildren(updates)
                                    .addOnSuccessListener {
                                        callback(true)
                                    }
                                    .addOnFailureListener {
                                        callback(false)
                                    }
                            } else {
                                Log.e(TAG, "Rakip oyuncu bulunamadı")
                                callback(false)
                            }
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Oyuncular alınırken hata", it)
                            callback(false)
                        }
                }
                .addOnFailureListener {
                    Log.e(TAG, "Mevcut puan alınırken hata", it)
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Hamle yaparken hata", e)
            callback(false)
        }
    }

    // Harf torbası sayısını günceller
    fun updateLetterBagCount(gameId: String, count: Int, callback: (Boolean) -> Unit) {
        try {
            val updates = hashMapOf<String, Any>(
                "letterBag" to "A".repeat(count)
            )

            realtimeDb.child(COLLECTION_GAMES).child(gameId).updateChildren(updates)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Harf torbası güncellenirken hata", e)
            callback(false)
        }
    }

    // Oyun güncellemelerini dinler
    fun listenForGameUpdates(
        gameId: String,
        callback: (Map<String, Any>) -> Unit
    ): ValueEventListener {
        val gameRef = realtimeDb.child(COLLECTION_GAMES).child(gameId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val gameData =
                    snapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {})
                if (gameData != null) {
                    callback(gameData)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Oyun dinleme hatası", error.toException())
            }
        }

        gameRef.addValueEventListener(listener)
        return listener
    }

    // Oyun dinleyicisini kaldırır
    fun removeGameListener(gameId: String, listener: ValueEventListener) {
        val gameRef = realtimeDb.child(COLLECTION_GAMES).child(gameId)
        gameRef.removeEventListener(listener)
    }

    // Tamamlanmış oyunları getirir
    fun getFinishedGames(userId: String, callback: (List<Map<String, Any>>) -> Unit) {
        try {
            realtimeDb.child(COLLECTION_GAMES)
                .orderByChild("status")
                .equalTo("finished")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val gamesList = mutableListOf<Map<String, Any>>()

                            for (gameSnapshot in snapshot.children) {
                                val gameId = gameSnapshot.key ?: continue

                                val gameData = gameSnapshot.getValue(object :
                                    GenericTypeIndicator<Map<String, Any>>() {}) ?: continue

                                val players = gameData["players"] as? Map<*, *> ?: continue

                                if (players.containsKey(userId)) {
                                    val gameWithId = gameData.toMutableMap()
                                    gameWithId["id"] = gameId
                                    gamesList.add(gameWithId)
                                }
                            }

                            callback(gamesList)
                        } catch (e: Exception) {
                            Log.e(TAG, "Biten oyunlar işlenirken hata", e)
                            callback(emptyList())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Biten oyunlar alınırken hata", error.toException())
                        callback(emptyList())
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Biten oyunlar alınırken beklenmeyen hata", e)
            callback(emptyList())
        }
    }

    // Süresi dolan oyunları kontrol eder ve bitirir
    fun checkAndFinishExpiredGames(callback: (Boolean) -> Unit) {
        try {
            realtimeDb.child(COLLECTION_GAMES)
                .orderByChild("status")
                .equalTo("active")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val now = System.currentTimeMillis()
                        var expiredGamesFound = false
                        val batch = mutableMapOf<String, Any>()

                        for (gameSnapshot in snapshot.children) {
                            val gameId = gameSnapshot.key ?: continue
                            val gameData = gameSnapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {}) ?: continue

                            val gameType = gameData["gameType"] as? String ?: "normal"
                            val timeLimit = getTimeLimitForGameType(gameType)

                            val currentTurn = gameData["currentTurn"] as? String ?: ""
                            val lastMoveTime = (gameData["lastMoveTime"] as? Number)?.toLong() ?: (gameData["createdAt"] as? Number)?.toLong() ?: 0

                            val timeSinceLastMove = now - lastMoveTime

                            if (lastMoveTime > 0 && timeSinceLastMove > timeLimit) {
                                expiredGamesFound = true

                                val players = gameData["players"] as? Map<*, *> ?: continue
                                var winnerId = ""

                                for (playerId in players.keys) {
                                    if (playerId.toString() != currentTurn) {
                                        winnerId = playerId.toString()
                                        break
                                    }
                                }

                                if (winnerId.isNotEmpty()) {
                                    batch["$COLLECTION_GAMES/$gameId/status"] = "finished"
                                    batch["$COLLECTION_GAMES/$gameId/winner"] = winnerId
                                    batch["$COLLECTION_GAMES/$gameId/finishedAt"] = ServerValue.TIMESTAMP
                                    batch["$COLLECTION_GAMES/$gameId/finishReason"] = "timeout"
                                }
                            }
                        }

                        if (expiredGamesFound && batch.isNotEmpty()) {
                            realtimeDb.updateChildren(batch)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Süresi dolan oyunlar sonlandırıldı")
                                    callback(true)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Oyunlar sonlandırılırken hata", e)
                                    callback(false)
                                }
                        } else {
                            Log.d(TAG, "Süresi dolan oyun bulunamadı")
                            callback(true)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Süresi dolan oyunlar kontrol edilirken hata", error.toException())
                        callback(false)
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "checkAndFinishExpiredGames hatası", e)
            callback(false)
        }
    }

    // Oyun türüne göre süre limitini belirler
    private fun getTimeLimitForGameType(gameType: String): Long {
        return when (gameType) {
            "fast" -> 120000
            "medium" -> 300000
            "slow" -> 720000
            "day" -> 86400000
            else -> 120000
        }
    }
    // Oyun bittiğinde istatistikleri günceller
    private fun updateStatisticsAfterGame(gameId: String, winnerId: String) {
        try {
            realtimeDb.child(COLLECTION_GAMES).child(gameId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val gameData =
                            snapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {})
                                ?: return@addOnSuccessListener

                        val players =
                            gameData["players"] as? Map<*, *> ?: return@addOnSuccessListener

                        for ((playerId, playerData) in players) {
                            val userId = playerId.toString()
                            val playerInfoMap = playerData as? Map<*, *> ?: continue
                            val score = (playerInfoMap["score"] as? Number)?.toInt() ?: 0

                            val isWinner = userId == winnerId

                            val userRef = db.collection(COLLECTION_USERS).document(userId)

                            db.runTransaction { transaction ->
                                val userDoc = transaction.get(userRef)

                                val currentStats = userDoc.get("statistics") as? Map<*, *>
                                    ?: hashMapOf<String, Any>()

                                val gamesPlayed =
                                    (currentStats["gamesPlayed"] as? Number)?.toInt() ?: 0
                                val gamesWon = (currentStats["gamesWon"] as? Number)?.toInt() ?: 0
                                val totalScore =
                                    (currentStats["totalScore"] as? Number)?.toInt() ?: 0
                                val highestScore =
                                    (currentStats["highestWordScore"] as? Number)?.toInt() ?: 0

                                val newStats = hashMapOf(
                                    "gamesPlayed" to gamesPlayed + 1,
                                    "gamesWon" to if (isWinner) gamesWon + 1 else gamesWon,
                                    "totalScore" to totalScore + score,
                                    "highestWordScore" to maxOf(highestScore, score)
                                )

                                transaction.update(userRef, "statistics", newStats)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "İstatistikler güncellenirken hata", e)
        }
    }

    // Bölge yasağı jokeri uygular
    fun applyRegionBanJoker(gameId: String, userId: String, bannedSide: String, callback: (Boolean) -> Unit) {
        try {
            val jokerData = hashMapOf(
                "type" to "regionBan",
                "bannedSide" to bannedSide,
                "activatedBy" to userId,
                "activatedAt" to ServerValue.TIMESTAMP
            )

            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("activeEffects").setValue(jokerData)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Bölge yasağı jokeri uygulanırken hata", e)
            callback(false)
        }
    }

    // Harf yasağı jokeri uygular
    fun applyLetterBanJoker(gameId: String, userId: String, callback: (Boolean) -> Unit) {
        try {
            val jokerData = hashMapOf(
                "type" to "letterBan",
                "activatedBy" to userId,
                "activatedAt" to ServerValue.TIMESTAMP
            )

            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("activeEffects").setValue(jokerData)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Harf yasağı jokeri uygulanırken hata", e)
            callback(false)
        }
    }

    // Ekstra hamle jokeri uygular
    fun applyExtraMoveJoker(gameId: String, userId: String, callback: (Boolean) -> Unit) {
        try {
            val jokerData = hashMapOf(
                "type" to "extraMove",
                "activatedBy" to userId,
                "activatedAt" to ServerValue.TIMESTAMP
            )

            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("activeEffects").setValue(jokerData)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Ekstra hamle jokeri uygulanırken hata", e)
            callback(false)
        }
    }

    // Kullanıcının joker envanterine joker ekler
    fun addJokerToInventory(gameId: String, userId: String, jokerType: String, callback: (Boolean) -> Unit) {
        try {
            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("players").child(userId).child("jokers").child(jokerType)
                .setValue(true)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Joker envantere eklenirken hata", e)
            callback(false)
        }
    }

    // Kullanıcının joker envanterinden joker kaldırır
    fun removeJokerFromInventory(gameId: String, userId: String, jokerType: String, callback: (Boolean) -> Unit) {
        try {
            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("players").child(userId).child("jokers").child(jokerType)
                .removeValue()
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Joker envanterden kaldırılırken hata", e)
            callback(false)
        }
    }

    // Kullanıcının joker envanterini kontrol eder
    fun checkUserJokers(gameId: String, userId: String, callback: (Map<String, Boolean>) -> Unit) {
        try {
            getDatabase().child(COLLECTION_GAMES).child(gameId)
                .child("players").child(userId).child("jokers").get()
                .addOnSuccessListener { snapshot ->
                    val jokers = mutableMapOf<String, Boolean>()
                    if (snapshot.exists()) {
                        for (jokerSnapshot in snapshot.children) {
                            val jokerType = jokerSnapshot.key ?: continue
                            val jokerActive = jokerSnapshot.getValue(Boolean::class.java) ?: false
                            jokers[jokerType] = jokerActive
                        }
                    }
                    callback(jokers)
                }
                .addOnFailureListener {
                    callback(emptyMap())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Joker envanteri kontrol edilirken hata", e)
            callback(emptyMap())
        }
    }

    // Bir oyunu bitirir ve kazananı belirler
    fun finishGame(gameId: String, winnerId: String, reason: String, callback: (Boolean) -> Unit) {
        try {
            val updates = hashMapOf<String, Any>(
                "status" to "finished",
                "winner" to winnerId,
                "finishedAt" to ServerValue.TIMESTAMP,
                "finishReason" to reason
            )

            realtimeDb.child(COLLECTION_GAMES).child(gameId).updateChildren(updates)
                .addOnSuccessListener {
                    updateStatisticsAfterGame(gameId, winnerId)
                    callback(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Oyun sonlandırılamadı: $gameId", e)
                    callback(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "finishGame hatası", e)
            callback(false)
        }
    }
}