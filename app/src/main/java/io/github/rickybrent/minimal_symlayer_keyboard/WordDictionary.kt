package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * On-device unigram + bigram tables. Loaded from bundled raw resources; never fetched.
 */
class WordDictionary(
	private val unigrams: List<Unigram>,
	private val scoreByWord: Map<String, Int>,
	private val bigrams: Map<String, List<Unigram>>
) {
	data class Unigram(val word: String, val score: Int)

	fun prefixMatches(prefix: String, limit: Int, extraScore: (String) -> Int = { 0 }): List<Unigram> {
		if (prefix.isEmpty() || limit <= 0) return emptyList()
		val p = prefix.lowercase()
		val hits = ArrayList<Unigram>(limit * 4)
		for (entry in unigrams) {
			if (!entry.word.startsWith(p)) continue
			if (entry.word == p) continue
			hits.add(scored(entry.word, entry.score, extraScore))
			if (hits.size >= 40) break
		}
		if (hits.isEmpty()) return emptyList()
		hits.sortByDescending { it.score }
		return hits.take(limit)
	}

	fun complete(prefix: String, limit: Int, extraScore: (String) -> Int = { 0 }): List<Unigram> {
		if (prefix.isEmpty() || limit <= 0) return emptyList()
		val prefixHits = prefixMatches(prefix, limit, extraScore)
		if (prefixHits.size >= limit || prefix.length < 2) return prefixHits
		val p = prefix.lowercase()
		val seen = HashSet<String>(prefixHits.size)
		prefixHits.forEach { seen.add(it.word) }
		val fuzzy = ArrayList<Unigram>()
		for (entry in unigrams) {
			if (entry.word in seen) continue
			if (!editDistanceAtMost1(p, entry.word)) continue
			fuzzy.add(scored(entry.word, entry.score / 2, extraScore))
		}
		fuzzy.sortByDescending { it.score }
		val out = ArrayList<Unigram>(limit)
		out.addAll(prefixHits)
		for (hit in fuzzy) {
			if (out.size >= limit) break
			out.add(hit)
		}
		return out
	}

	fun nextWords(previous: String, limit: Int, extraScore: (String) -> Int = { 0 }): List<Unigram> {
		if (limit <= 0) return emptyList()
		val prev = previous.lowercase()
		val seen = HashSet<String>()
		val fromBigrams = ArrayList<Unigram>()
		bigrams[prev]?.forEach {
			fromBigrams.add(scored(it.word, it.score, extraScore))
			seen.add(it.word)
		}
		fromBigrams.sortByDescending { it.score }
		if (fromBigrams.size >= limit) return fromBigrams.take(limit)
		val result = ArrayList<Unigram>(fromBigrams)
		for (entry in unigrams) {
			if (entry.word == prev || entry.word in seen) continue
			result.add(scored(entry.word, entry.score, extraScore))
			if (result.size >= limit) break
		}
		return result
	}

	fun contains(word: String): Boolean = scoreByWord.containsKey(word.lowercase())

	private fun scored(word: String, base: Int, extraScore: (String) -> Int): Unigram {
		return Unigram(word, base + extraScore(word))
	}

	companion object {
		internal fun editDistanceAtMost1(a: String, b: String): Boolean {
			val n = a.length
			val m = b.length
			if (kotlin.math.abs(n - m) > 1) return false
			if (n == m) {
				var diffs = 0
				var first = -1
				for (i in 0 until n) {
					if (a[i] != b[i]) {
						if (first < 0) first = i
						if (++diffs > 2) return false
					}
				}
				if (diffs == 1) return true
				return diffs == 2 &&
					first + 1 < n &&
					a[first] == b[first + 1] &&
					a[first + 1] == b[first]
			}
			val shorter = if (n < m) a else b
			val longer = if (n < m) b else a
			var i = 0
			var j = 0
			var skipped = false
			while (i < shorter.length && j < longer.length) {
				if (shorter[i] == longer[j]) {
					i++; j++
				} else if (!skipped) {
					skipped = true
					j++
				} else {
					return false
				}
			}
			return true
		}

		fun load(unigramLines: Sequence<String>, bigramLines: Sequence<String>): WordDictionary {
			val unigrams = ArrayList<Unigram>(1024)
			val scoreByWord = HashMap<String, Int>(1024)
			for (raw in unigramLines) {
				val line = raw.trim()
				if (line.isEmpty() || line.startsWith("#")) continue
				val sp = line.indexOf(' ')
				if (sp <= 0) continue
				val word = line.substring(0, sp).lowercase()
				val score = line.substring(sp + 1).toIntOrNull() ?: continue
				if (word.isEmpty() || !word.all { it.isLetter() }) continue
				if (scoreByWord.containsKey(word)) continue
				scoreByWord[word] = score
				unigrams.add(Unigram(word, score))
			}
			unigrams.sortByDescending { it.score }

			val bigrams = HashMap<String, List<Unigram>>()
			for (raw in bigramLines) {
				val line = raw.trim()
				if (line.isEmpty() || line.startsWith("#")) continue
				val tab = line.indexOf('\t')
				if (tab <= 0) continue
				val prev = line.substring(0, tab).lowercase()
				val parts = line.substring(tab + 1).split(',')
				val next = ArrayList<Unigram>(parts.size)
				for (part in parts) {
					val colon = part.indexOf(':')
					if (colon <= 0) continue
					val word = part.substring(0, colon).lowercase()
					val score = part.substring(colon + 1).toIntOrNull() ?: continue
					next.add(Unigram(word, score))
				}
				if (next.isNotEmpty()) bigrams[prev] = next
			}
			return WordDictionary(unigrams, scoreByWord, bigrams)
		}
	}
}
