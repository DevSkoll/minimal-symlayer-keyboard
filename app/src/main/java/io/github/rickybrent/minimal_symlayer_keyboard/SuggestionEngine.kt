package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Prefix completions while a word is being typed, and next-word predictions after a separator.
 * Never rewrites text on its own (no autocorrect).
 */
class SuggestionEngine(
	private val dictionary: WordDictionary,
	private val extraScore: (String) -> Int = { 0 }
) {
	fun suggest(prefix: String, previousWord: String, limit: Int = 3): List<String> {
		if (limit <= 0) return emptyList()
		return if (prefix.isNotEmpty()) {
			dictionary.complete(prefix, limit, extraScore).map { it.word }
		} else {
			dictionary.nextWords(previousWord, limit, extraScore).map { it.word }
		}
	}

	companion object {
		fun currentPrefix(beforeCursor: CharSequence?): String {
			if (beforeCursor.isNullOrEmpty()) return ""
			var i = beforeCursor.length
			while (i > 0 && beforeCursor[i - 1].isLetter()) i--
			return beforeCursor.subSequence(i, beforeCursor.length).toString()
		}

		fun previousWord(beforeCursor: CharSequence?): String {
			if (beforeCursor.isNullOrEmpty()) return ""
			var i = beforeCursor.length
			while (i > 0 && beforeCursor[i - 1].isLetter()) i--
			while (i > 0 && !beforeCursor[i - 1].isLetter()) i--
			val end = i
			while (i > 0 && beforeCursor[i - 1].isLetter()) i--
			return if (end > i) beforeCursor.subSequence(i, end).toString() else ""
		}

		fun applyCase(suggestion: String, prefix: String, capitalize: Boolean): String {
			if (prefix.length > 1 && prefix.all { it.isUpperCase() }) {
				return suggestion.uppercase()
			}
			if (prefix.isNotEmpty() && prefix.first().isUpperCase()) {
				return suggestion.replaceFirstChar { it.uppercase() }
			}
			if (prefix.isEmpty() && capitalize) {
				return suggestion.replaceFirstChar { it.uppercase() }
			}
			return suggestion
		}
	}
}
