package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
	private val dictionary = WordDictionary.load(
		sequenceOf(
			"the 100",
			"that 90",
			"this 80",
			"they 70",
			"then 60",
			"thank 50",
			"to 40",
			"you 30",
			"hello 20"
		),
		sequenceOf(
			"thank\tyou:10,the:2",
			"the\tfirst:5,same:4"
		)
	)

	@Test
	fun prefixCompletionsFollowFrequency() {
		val engine = SuggestionEngine(dictionary)
		assertEquals(listOf("the", "that", "this"), engine.suggest("th", "", 3))
	}

	@Test
	fun exactPrefixIsSkippedInFavorOfLongerWords() {
		val engine = SuggestionEngine(dictionary)
		assertEquals(listOf("they", "then"), engine.suggest("the", "", 3))
	}

	@Test
	fun unknownPrefixYieldsNothing() {
		val engine = SuggestionEngine(dictionary)
		assertTrue(engine.suggest("zzzz", "", 3).isEmpty())
	}

	@Test
	fun typoUsesClosestDictionaryWord() {
		val engine = SuggestionEngine(dictionary)
		assertEquals("the", engine.suggest("teh", "", 1).first())
	}

	@Test
	fun nextWordUsesBigramsThenFallsBack() {
		val engine = SuggestionEngine(dictionary)
		assertEquals("you", engine.suggest("", "thank", 1).first())
		assertEquals("you", engine.suggest("", "thank", 3).first())
		assertEquals("first", engine.suggest("", "the", 1).first())
	}

	@Test
	fun userBoostCanReorderCompletions() {
		val engine = SuggestionEngine(dictionary) { word -> if (word == "then") 1000 else 0 }
		assertEquals("then", engine.suggest("th", "", 1).first())
	}

	@Test
	fun editorWordBounds() {
		assertEquals("wo", SuggestionEngine.currentPrefix("hello wo"))
		assertEquals("hello", SuggestionEngine.previousWord("hello wo"))
		assertEquals("", SuggestionEngine.currentPrefix("hello "))
		assertEquals("hello", SuggestionEngine.previousWord("hello "))
		assertEquals("Hello", SuggestionEngine.currentPrefix("Hello"))
		assertEquals("", SuggestionEngine.previousWord("Hello"))
	}

	@Test
	fun applyCaseFollowsPrefix() {
		assertEquals("THE", SuggestionEngine.applyCase("the", "TH", false))
		assertEquals("The", SuggestionEngine.applyCase("the", "Th", false))
		assertEquals("the", SuggestionEngine.applyCase("the", "th", false))
		assertEquals("The", SuggestionEngine.applyCase("the", "", true))
		assertEquals("Am", SuggestionEngine.applyCase("am", "I", false))
	}

	@Test
	fun userLexiconPersistsLocallyAndBoosts() {
		val file = kotlin.io.path.createTempFile(prefix = "lexicon", suffix = ".txt").toFile()
		try {
			val lexicon = UserLexicon(file)
			lexicon.record("hello")
			assertTrue(lexicon.boost("hello") > 0)
			assertEquals(0, lexicon.boost("world"))
			val reloaded = UserLexicon(file)
			assertTrue(reloaded.boost("hello") > 0)
		} finally {
			file.delete()
		}
	}

	@Test
	fun bundledDictionaryPrefixAndNextWord() {
		val uniFile = java.io.File("src/main/res/raw/en_unigrams.txt")
		val biFile = java.io.File("src/main/res/raw/en_bigrams.txt")
		assertTrue(uniFile.exists())
		assertTrue(biFile.exists())
		val dictionary = WordDictionary.load(uniFile.readLines().asSequence(), biFile.readLines().asSequence())
		val engine = SuggestionEngine(dictionary)
		assertEquals(listOf("the", "that", "this"), engine.suggest("th", "", 3))
		assertEquals("you", engine.suggest("", "thank", 1).first())
	}
}
