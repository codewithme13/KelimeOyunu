package com.example.kelimeoyunu.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class TurkishDictionary(private val context: Context) {
    private val TAG = "TurkishDictionary"
    private val wordSet = HashSet<String>()
    private val turkishAlphabet = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ"

    init {
        loadDictionary()
    }

    private fun loadDictionary() {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("turkce_kelime_listesi.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.trim()?.uppercase(Locale.getDefault())?.let {
                    if (it.isNotEmpty()) {
                        wordSet.add(it)
                    }
                }
            }

            reader.close()
            inputStream.close()

            Log.d(TAG, "Sözlük yüklendi. Kelime sayısı: ${wordSet.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Sözlük yüklenirken hata oluştu", e)
        }
    }

    fun isValidWord(word: String): Boolean {
        val upperCaseWord = word.trim().uppercase(Locale.getDefault())

        // Joker içermeyen kelimeler için normal kontrol
        if (!upperCaseWord.contains('*')) {
            return wordSet.contains(upperCaseWord)
        }

        return checkWithJoker(upperCaseWord)
    }

    private fun checkWithJoker(word: String): Boolean {
        val jokerIndex = word.indexOf('*')

        if (jokerIndex == -1) {
            return wordSet.contains(word)
        }

        for (letter in turkishAlphabet) {
            val newWord = word.substring(0, jokerIndex) + letter + word.substring(jokerIndex + 1)

            if (newWord.contains('*')) {
                if (checkWithJoker(newWord)) {
                    return true
                }
            } else {
                if (wordSet.contains(newWord)) {
                    return true
                }
            }
        }

        return false
    }

    fun getSuggestedWords(prefix: String, maxResults: Int = 5): List<String> {
        val results = mutableListOf<String>()
        val upperCasePrefix = prefix.trim().uppercase(Locale.getDefault())

        if (upperCasePrefix.isEmpty()) return results

        for (word in wordSet) {
            if (word.startsWith(upperCasePrefix)) {
                results.add(word)
                if (results.size >= maxResults) break
            }
        }

        return results
    }
}