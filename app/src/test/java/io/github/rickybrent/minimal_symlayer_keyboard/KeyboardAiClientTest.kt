package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertPathValidatorException

class KeyboardAiClientTest {
	@Test
	fun describeErrorMapsMissingTrustAnchor() {
		val err = KeyboardAiClient.describeError(
			CertPathValidatorException("Trust anchor for certification path not found.")
		)
		assertEquals("TLS: certificate not trusted", err)
	}

	@Test
	fun describeErrorKeepsOrdinaryMessage() {
		val err = KeyboardAiClient.describeError(RuntimeException("no ollama model is loaded"))
		assertTrue(err.contains("no ollama model is loaded"))
	}
}
