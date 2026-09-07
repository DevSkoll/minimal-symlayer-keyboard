package io.github.rickybrent.minimal_symlayer_keyboard

import java.io.File

/**
 * Local frequency boosts for words this device has typed or accepted.
 * Stored only in app-private storage; never uploaded.
 */
class UserLexicon(private val file: File, private val maxWords: Int = 1500) {
	private val counts = LinkedHashMap<String, Int>()

	init {
		load()
	}

	fun boost(word: String): Int {
		val n = counts[word.lowercase()] ?: return 0
		return n * 40
	}

	fun record(word: String) {
		val w = word.lowercase()
		if (w.length < 2 || !w.all { it.isLetter() }) return
		val next = (counts.remove(w) ?: 0) + 1
		counts[w] = next
		if (counts.size > maxWords) {
			val oldest = counts.entries.take(counts.size - maxWords).map { it.key }
			oldest.forEach { counts.remove(it) }
		}
		persist()
	}

	private fun load() {
		if (!file.exists()) return
		file.forEachLine { raw ->
			val line = raw.trim()
			if (line.isEmpty()) return@forEachLine
			val sp = line.indexOf(' ')
			if (sp <= 0) return@forEachLine
			val word = line.substring(0, sp).lowercase()
			val n = line.substring(sp + 1).toIntOrNull() ?: return@forEachLine
			if (word.all { it.isLetter() }) counts[word] = n
		}
	}

	private fun persist() {
		val parent = file.parentFile
		if (parent != null && !parent.exists()) parent.mkdirs()
		file.writeText(counts.entries.joinToString("\n") { "${it.key} ${it.value}" } + "\n")
	}
}
