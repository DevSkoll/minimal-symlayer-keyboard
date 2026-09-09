package io.github.rickybrent.minimal_symlayer_keyboard

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * One-shot continuation request to a user-configured HTTPS endpoint.
 * Does not run unless the user taps the AI chip and has set an endpoint.
 */
class KeyboardAiClient {
	private val executor = Executors.newSingleThreadExecutor()
	private val main = Handler(Looper.getMainLooper())

	fun complete(
		endpoint: String,
		text: String,
		prompt: String = "",
		mode: String = "generate",
		onResult: (String) -> Unit,
		onError: (String) -> Unit
	) {
		val bases = endpointBases(endpoint)
		if (bases.isEmpty()) {
			onError("AI endpoint is not set")
			return
		}
		executor.execute {
			var lastError = "AI request failed"
			for (base in bases) {
				try {
					val completion = postComplete(base, text, prompt, mode)
					if (completion.isEmpty()) {
						lastError = "Empty AI response"
						continue
					}
					main.post { onResult(completion) }
					return@execute
				} catch (e: Exception) {
					lastError = describeError(e)
					Log.w(TAG, "complete via $base failed", e)
				}
			}
			main.post { onError(lastError) }
		}
	}

	private fun postComplete(base: String, text: String, prompt: String, mode: String): String {
		val url = URL("$base/v1/complete")
		val conn = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = "POST"
			connectTimeout = 4000
			readTimeout = 180000
			useCaches = false
			doOutput = true
			setRequestProperty("Content-Type", "application/json; charset=utf-8")
			setRequestProperty("Accept", "application/json")
			setRequestProperty("Cache-Control", "no-cache")
		}
		val payload = JSONObject()
			.put("text", text.take(4000))
			.put("prompt", prompt.take(1000))
			.put("mode", mode)
			.put("nonce", java.util.UUID.randomUUID().toString())
			.put("max_tokens", 256)
			.toString()
		try {
			conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
			val code = conn.responseCode
			val stream = if (code in 200..299) conn.inputStream else conn.errorStream
			val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
			if (code !in 200..299) {
				val detail = try {
					JSONObject(body).optString("detail", "")
				} catch (_: Exception) {
					""
				}
				throw RuntimeException(detail.ifBlank { "AI HTTP $code" })
			}
			return JSONObject(body).optString("completion", "").trim()
		} finally {
			conn.disconnect()
		}
	}

	companion object {
		private const val TAG = "KeyboardAi"

		internal fun endpointBases(endpoint: String): List<String> {
			var base = endpoint.trim().trimEnd('/')
			if (base.isEmpty()) return emptyList()
			if (base.startsWith("http://")) {
				base = "https://" + base.removePrefix("http://")
			}
			return listOf(base)
		}

		internal fun describeError(e: Exception): String {
			val text = generateSequence(e as Throwable) { it.cause }
				.mapNotNull { it.message }
				.joinToString(" ")
			return when {
				text.contains("Trust anchor") || text.contains("CertPathValidator") ->
					"TLS: certificate not trusted"
				text.contains("Hostname") && text.contains("verif", ignoreCase = true) ->
					"TLS: hostname does not match certificate"
				else -> (e.message ?: e.javaClass.simpleName).take(160)
			}
		}
	}
}
