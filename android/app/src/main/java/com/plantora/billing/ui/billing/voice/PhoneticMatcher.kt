package com.plantora.billing.ui.billing.voice

import kotlin.math.max
import kotlin.math.min

/**
 * Indian-English phonetic matcher ported from the web app
 * (frontend/src/components/VoiceSearchButton.tsx). Used to map a noisy voice
 * transcript to the closest product name. Pure / unit-testable.
 */
object PhoneticMatcher {

    data class Match(val candidate: String, val score: Double)

    /**
     * Consonant-based phonetic key for a single word, handling common Indian
     * English mergers (v/w, s/sh, z/s, etc.) and dropping vowels after the first.
     */
    fun phoneticCode(word: String): String {
        var w = word.lowercase().trim()
        if (w.isEmpty()) return ""

        if (w.startsWith("x")) w = "z" + w.substring(1)

        w = w
            .replace("ph", "f")
            .replace("gh", "g")
            .replace("kh", "k")
            .replace("sh", "s")
            .replace("w", "v")
            .replace("c", "k")
            .replace("q", "k")
            .replace("z", "s")
            .replace("y", "i")

        // Drop consecutive duplicate letters.
        val dedup = StringBuilder()
        for (i in w.indices) {
            if (i == 0 || w[i] != w[i - 1]) dedup.append(w[i])
        }
        w = dedup.toString()

        if (w.length <= 1) return w
        val first = w[0]
        val rest = w.substring(1).replace(Regex("[aeiou]"), "")
        return first + rest
    }

    private fun phoneticMatchScore(a: String, b: String): Double {
        val w1 = a.lowercase().split(Regex("\\s+")).map { phoneticCode(it) }.filter { it.isNotEmpty() }.toSet()
        val w2 = b.lowercase().split(Regex("\\s+")).map { phoneticCode(it) }.filter { it.isNotEmpty() }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return 0.0
        val intersection = w1.count { it in w2 }
        val union = w1.size + w2.size - intersection
        return if (union > 0) intersection.toDouble() / union else 0.0
    }

    /** Best matching candidate above a confidence threshold, or null. */
    fun findBestMatch(transcript: String, candidates: List<String>): Match? {
        if (candidates.isEmpty()) return null
        val text = transcript.lowercase().trim()
        var best = ""
        var highest = -1.0

        for (candidate in candidates) {
            val cand = candidate.lowercase().trim()

            if (text == cand) return Match(candidate, 1.0)

            if (cand.contains(text) || text.contains(cand)) {
                val score = 0.8 + 0.15 * (min(text.length, cand.length).toDouble() / max(text.length, cand.length))
                if (score > highest) { highest = score; best = candidate }
                continue
            }

            val wordsText = text.split(Regex("\\s+")).toSet()
            val wordsCand = cand.split(Regex("\\s+")).toSet()
            val inter = wordsText.count { it in wordsCand }
            val union = wordsText.size + wordsCand.size - inter
            val wordScore = if (union > 0) (inter.toDouble() / union) * 0.9 else 0.0
            if (wordScore > highest && wordScore > THRESHOLD) { highest = wordScore; best = candidate; continue }

            val phon = phoneticMatchScore(text, cand) * 0.95
            if (phon > highest && phon > THRESHOLD) { highest = phon; best = candidate; continue }

            val dist = levenshtein(text, cand)
            val maxLen = max(text.length, cand.length)
            val lev = if (maxLen > 0) (1 - dist.toDouble() / maxLen) * 0.85 else 0.0
            if (lev > highest && lev > THRESHOLD) { highest = lev; best = candidate }
        }

        return if (highest > THRESHOLD) Match(best, highest) else null
    }

    /** Forgiving confidence floor — snap noisy speech to the nearest plant name. */
    private const val THRESHOLD = 0.12

    fun levenshtein(a: String, b: String): Int {
        val an = a.length; val bn = b.length
        if (an == 0) return bn
        if (bn == 0) return an
        val prev = IntArray(bn + 1) { it }
        val cur = IntArray(bn + 1)
        for (i in 1..an) {
            cur[0] = i
            for (j in 1..bn) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, bn + 1)
        }
        return prev[bn]
    }
}
