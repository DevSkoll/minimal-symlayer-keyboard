package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethod.SHOW_FORCED
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import java.io.File
import java.util.Locale
import android.inputmethodservice.InputMethodService as AndroidInputMethodService

/**
 * MP01 keycode sent instead of KeyEvent.KEYCODE_EMOJI_PICKER.
 */
const val MP01_KEYCODE_EMOJI_PICKER = 666;

/**
 * MP01 keycode sent instead of KeyEvent.KEYCODE_DICTATE.
 */
const val MP01_KEYCODE_DICTATE = 667;

// Only for modifier keys we want to force when using sym+keys to navigate.
val forceModifierPairs = listOf(
		KeyEvent.META_SHIFT_ON to KeyEvent.KEYCODE_SHIFT_LEFT,
		KeyEvent.META_META_ON to KeyEvent.KEYCODE_META_LEFT,
		KeyEvent.META_CTRL_ON to KeyEvent.KEYCODE_CTRL_LEFT,
	)

/**
 * @return true if it is suitable to provide suggestions or text transforms in the given editor.
 */
fun canUseSuggestions(editorInfo: EditorInfo): Boolean {
	if(editorInfo.inputType == InputType.TYPE_NULL) {
		return false
	}

	return when(editorInfo.inputType and InputType.TYPE_MASK_VARIATION) {
		InputType.TYPE_TEXT_VARIATION_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> false
		InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
		InputType.TYPE_TEXT_VARIATION_URI -> false
		else -> true
	}
}

/**
 * @return A KeyEvent made from the given one, but with the given key code.
 */
fun makeKeyEvent(original: KeyEvent, code: Int): KeyEvent {
	return makeKeyEvent(original, code, original.metaState, original.action, original.source, KeyCharacterMap.VIRTUAL_KEYBOARD)
}

/**
 * @return A KeyEvent made from the given one, but with the given key code, meta state, action, and source.
 */
fun makeKeyEvent(original: KeyEvent, code: Int, metaState: Int, action: Int, source: Int): KeyEvent {
	return makeKeyEvent(original, code, metaState, action, source, KeyCharacterMap.VIRTUAL_KEYBOARD)
}

/**
 * @return A KeyEvent made from the given one, but with the given key code, meta state, action, source, and deviceId.
 */
fun makeKeyEvent(original: KeyEvent, code: Int, metaState: Int, action: Int, source: Int, deviceId: Int): KeyEvent {
	return KeyEvent(original.downTime, original.eventTime, action, code, original.repeatCount, metaState, deviceId, code, 0, source)
}

val templates = hashMapOf(
	"fr" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('`', '^', 'æ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('^', 'œ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Y to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('ç', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"fr-ext" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('`', '^', '´', '¨', 'æ', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('^', '´', '¨', '`', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('^', '´', 'œ', '¨', '~', '`', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('`', '^', '´', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Y to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('ç', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"es" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"de" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('ß', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"pt" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('´', '^', '`', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '^', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', '^', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('ç', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"hu-de" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('´', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', '¨', 'ő', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', '¨', 'ű', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('ß', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"pl" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('ą', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('ę', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_L to arrayOf('ł', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_N to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Z to arrayOf('ż', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_X to arrayOf('ź', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"dk-no" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('å', 'æ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('ø', 'ö', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('ß', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"se-fi" to hashMapOf(
		KeyEvent.KEYCODE_Q to arrayOf('å', 'ä', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_A to arrayOf('ä', 'å', 'æ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('ö', 'ø', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('ß', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"rom" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('ă', 'â', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('î', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('ș', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_T to arrayOf('ț', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"lt" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('ą', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('č', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('ę', 'ė', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('į', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_S to arrayOf('š', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('ų', 'ū', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Z to arrayOf('ž', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"order1" to hashMapOf( // áàâäã
		KeyEvent.KEYCODE_A to arrayOf('´', '`', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '`', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', '`', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', '`', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', '`', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"order2" to hashMapOf( // àáâäã
		KeyEvent.KEYCODE_A to arrayOf('`', '´', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('`', '´', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('`', '´', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('`', '´', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('`', '´', '^', '¨', '~', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	)
)

class InputMethodService : AndroidInputMethodService() {
	private lateinit var vibrator: Vibrator
	private var pickerManager: PickerManager? = null
	private var mainInputView: View? = null
	private var suggestionBarView: View? = null
	private var inputViewStrip: View? = null
	private var stripStatusIcon: ImageView? = null

	val shift = Modifier()
	private val alt = Modifier()
	private val sym = SimpleModifier()
	private val dotCtrl = TripleModifier()
	private val emojiMeta = TripleModifier()
	private val caps = Modifier()
	private val cyrillicLayer = CyrillicLayerModifier()
	private val hangulComposer = HangulComposer()
	private val koreanInput = KoreanInputModifier()
	private var koreanInputToggleEnabled = false

	private var lastShift = false
	private var lastAlt = false
	private var lastSym = false
	private var lastDotCtrl = false
	private var lastEmojiMeta = false
	private var lastCaps = false
	private var lastCyrillicLayer = false

	private var cyrillicLayerToggleEnabled = false

	private var autoCapitalize = false
	private var showToolbar = false
	private var isInputViewActive = false
	private var imeWindowBusy = false
	private var wordSuggestionsEnabled = true
	private var suggestionsDismissed = false
	private var aiCompleteEnabled = false
	private var aiCompleteEndpoint = ""
	private var wordDictionary: WordDictionary? = null
	private var userLexicon: UserLexicon? = null
	private var suggestionEngine: SuggestionEngine? = null
	private val keyboardAiClient = KeyboardAiClient()
	private var suggestionButtons: Array<TextView>? = null
	private var aiButton: TextView? = null
	private var aiPromptPanel: View? = null
	private var aiPromptText: EditText? = null
	private var aiPromptStatus: TextView? = null
	private var suggestionRow: View? = null
	private var displayedSuggestions: List<String> = emptyList()
	private var displayedPrefix: String = ""
	private var aiBusy = false
	private var aiPromptOpen = false
	private var aiMode: String? = null
	private val aiPromptBuffer = StringBuilder()

	enum class DeviceType(val source: Int) {
		TITAN(InputDevice.SOURCE_KEYBOARD),
		MP01(InputDevice.SOURCE_KEYBOARD)
	}
	var lastDeviceId = -1
		private set
	var deviceType = DeviceType.TITAN
		private set

	private val multipress = MultipressController(arrayOf(
		templates["fr-ext"]!!,
		hashMapOf(
			KeyEvent.KEYCODE_Q to arrayOf(MPSUBST_TOGGLE_ALT, '°', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_W to arrayOf(MPSUBST_TOGGLE_ALT, '&', '↑', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_E to arrayOf(MPSUBST_TOGGLE_ALT, '€', '∃', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_R to arrayOf(MPSUBST_TOGGLE_ALT, '®', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_T to arrayOf(MPSUBST_TOGGLE_ALT, '[', '{', '<', '≤', '†', '™', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_Y to arrayOf(MPSUBST_TOGGLE_ALT, ']', '}', '>', '≥', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_U to arrayOf(MPSUBST_TOGGLE_ALT, '—', '–', '∪', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_I to arrayOf(MPSUBST_TOGGLE_ALT, '|', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_O to arrayOf(MPSUBST_TOGGLE_ALT, '\\', 'œ', 'º', '÷', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_P to arrayOf(MPSUBST_TOGGLE_ALT, ';', '¶', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_A to arrayOf(MPSUBST_TOGGLE_ALT, 'æ', 'ª', '←', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_S to arrayOf(MPSUBST_TOGGLE_ALT, 'ß', '§', '↓', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_D to arrayOf(MPSUBST_TOGGLE_ALT, '∂', '→', '⇒', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_F to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_CIRCUMFLEX, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_G to arrayOf(MPSUBST_TOGGLE_ALT, '•', '·', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_H to arrayOf(MPSUBST_TOGGLE_ALT, '²', '♯', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_J to arrayOf(MPSUBST_TOGGLE_ALT, '=', '≠', '≈', '±', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_K to arrayOf(MPSUBST_TOGGLE_ALT, '%', '‰', '‱', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_L to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BACKTICK, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_Z to arrayOf(MPSUBST_TOGGLE_ALT, '¡', '‽', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_X to arrayOf(MPSUBST_TOGGLE_ALT, '×', 'χ', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_C to arrayOf(MPSUBST_TOGGLE_ALT, 'ç', '©', '¢', '⊂', '⊄', '⊃', '⊅', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_V to arrayOf(MPSUBST_TOGGLE_ALT, '∀', '√', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_B to arrayOf(MPSUBST_TOGGLE_ALT, '…', 'ß', '∫', '♭', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_N to arrayOf(MPSUBST_TOGGLE_ALT, '~', '¬', '∩', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_M to arrayOf(MPSUBST_TOGGLE_ALT, '$', '€', '£', '¿', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			MP01_KEYCODE_EMOJI_PICKER to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BYPASS),
			MP01_KEYCODE_DICTATE to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_SPACE to arrayOf('\t', '⇥', MPSUBST_BYPASS)
		)
	))

	private val unlockReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
				updateFromPreferences()
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		val context = createDeviceProtectedStorageContext()
		pickerManager = PickerManager(this, this)

		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		preferences.registerOnSharedPreferenceChangeListener { _, _ ->
			updateFromPreferences()
		}
		updateFromPreferences()
		loadWordSuggestions(context)
		val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
		registerReceiver(unlockReceiver, filter)

		vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val mgr = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
			mgr.defaultVibrator
		} else {
			@Suppress("DEPRECATION")
			getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		pickerManager?.hide()
		unregisterReceiver(unlockReceiver)
	}

	override fun onCreateInputView(): View {
		mainInputView = layoutInflater.inflate(R.layout.input_view_container, null)

		val pickerContainer = mainInputView?.findViewById<FrameLayout>(R.id.picker_container_inline)
		pickerManager?.setInlineViewContainer(pickerContainer)

		val inputContainer = mainInputView?.findViewById<FrameLayout>(R.id.input_view_container)
		this.inputViewStrip = layoutInflater.inflate(R.layout.input_view_strip, null)
		stripStatusIcon = this.inputViewStrip?.findViewById(R.id.modifier_icon)
		inputContainer?.addView(this.inputViewStrip)
		this.inputViewStrip?.visibility = if (showToolbar) View.VISIBLE else View.GONE

		return mainInputView!!
	}

	private fun bindSuggestionBar(bar: View) {
		val buttons = arrayOf(
			bar.findViewById<TextView>(R.id.suggestion_0),
			bar.findViewById<TextView>(R.id.suggestion_1),
			bar.findViewById<TextView>(R.id.suggestion_2)
		)
		buttons.forEachIndexed { index, view ->
			view.setOnClickListener { applySuggestion(index) }
		}
		suggestionButtons = buttons
		aiButton = bar.findViewById(R.id.suggestion_ai)
		aiButton?.setOnClickListener { requestAiComplete() }
		aiPromptPanel = bar.findViewById(R.id.ai_prompt_panel)
		aiPromptText = bar.findViewById(R.id.ai_prompt_text)
		aiPromptStatus = bar.findViewById(R.id.ai_prompt_status)
		suggestionRow = bar.findViewById(R.id.suggestion_row)
		aiPromptText?.showSoftInputOnFocus = false
		aiPromptText?.setOnKeyListener { _, _, _ -> true }
		bar.findViewById<TextView>(R.id.ai_prompt_cancel).setOnClickListener { closeAiPrompt() }
		bar.findViewById<TextView>(R.id.ai_prompt_rewrite).setOnClickListener { armOrSubmitAi(rewrite = true) }
		bar.findViewById<TextView>(R.id.ai_prompt_go).setOnClickListener { armOrSubmitAi(rewrite = false) }
	}

	override fun onCreateCandidatesView(): View {
		val bar = layoutInflater.inflate(R.layout.suggestion_bar, null)
		bindSuggestionBar(bar)
		suggestionBarView = bar
		return bar
	}

	override fun onComputeInsets(outInsets: Insets) {
		super.onComputeInsets(outInsets)
		val screenH = resources.displayMetrics.heightPixels
		val chromeH = visibleImeChromeHeight()
		if (chromeH <= 0) {
			outInsets.contentTopInsets = screenH
			outInsets.visibleTopInsets = screenH
			return
		}
		val target = when {
			pickerManager?.isShowing() == true ->
				mainInputView?.findViewById(R.id.picker_container_inline) ?: mainInputView
			suggestionBarView?.visibility == View.VISIBLE -> suggestionBarView
			showToolbar -> inputViewStrip
			else -> mainInputView
		}
		val loc = IntArray(2)
		target?.getLocationInWindow(loc)
		val windowH = target?.rootView?.height?.takeIf { it > 0 } ?: screenH
		var top = loc[1]
		if (top <= 0 || windowH - top < chromeH / 2) {
			top = (windowH - chromeH).coerceAtLeast(0)
		}
		outInsets.contentTopInsets = top
		outInsets.visibleTopInsets = top
		outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
	}

	private fun visibleImeChromeHeight(): Int {
		var height = 0
		if (pickerManager?.isShowing() == true) {
			val picker = mainInputView?.findViewById<View>(R.id.picker_container_inline)
			height += picker?.height?.takeIf { it > 0 }
				?: (resources.displayMetrics.heightPixels / 2.25).toInt()
		}
		if (showToolbar) {
			height += inputViewStrip?.height?.takeIf { it > 0 }
				?: inputViewStrip?.minimumHeight?.takeIf { it > 0 }
				?: (48 * resources.displayMetrics.density).toInt()
		}
		if (suggestionBarView?.visibility == View.VISIBLE) {
			val bar = suggestionBarView!!
			height += bar.height.takeIf { it > 0 }
				?: bar.minimumHeight.takeIf { it > 0 }
				?: (49 * resources.displayMetrics.density).toInt()
		}
		return height
	}

	private fun setImeBarShown(shown: Boolean) {
		if (imeWindowBusy) return
		suggestionBarView?.visibility = if (shown) View.VISIBLE else View.GONE
		if (shown) {
			requestShowSelf(0)
		}
		setCandidatesViewShown(shown)
	}

	override fun onEvaluateFullscreenMode(): Boolean = false

	override fun onEvaluateInputViewShown(): Boolean {
		super.onEvaluateInputViewShown()
		if (pickerManager?.isShowing() == true || showToolbar) return true
		return false
	}

	override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
		if (pickerManager?.isShowing() == true || showToolbar) return true
		if (suggestionsEligible() || aiPromptOpen) return true
		return super.onShowInputRequested(flags, configChange)
	}

	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		super.onStartInputView(info, restarting)
		isInputViewActive = true
		updateStatusIconIfNeeded()
		refreshSuggestions()
	}

	private fun showEmojiPicker() {
		if (isInputViewActive.not()) requestShowSelf(SHOW_FORCED)
		pickerManager?.show()
	}

	private fun showSymbolPicker() {
		if (isInputViewActive.not()) requestShowSelf(SHOW_FORCED)
		pickerManager?.show(PickerManager.ViewType.SYMBOL)
	}

	private fun showClipboardHistory() {
		if (isInputViewActive.not()) requestShowSelf(SHOW_FORCED)
		pickerManager?.show(PickerManager.ViewType.CLIPBOARD)
	}

	override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
		super.onStartInput(attribute, restarting)

		updateFromPreferences()

		// Reset Hangul composer when starting input
		hangulComposer.reset(currentInputConnection)
		suggestionsDismissed = false

		if(!sym.get()) {
			updateAutoCapitalization()
		}
		refreshSuggestions()
	}

	/**
	 * Reset the shift/caps state when the InputView is closed and update the icons.
	 * Prevents auto-caps's icon from appearing when no text input is active.
	 */
	override fun onFinishInputView(finishingInput: Boolean) {
		imeWindowBusy = true
		try {
			super.onFinishInputView(finishingInput)
			isInputViewActive = false
			shift.reset()
			caps.reset()
			hangulComposer.reset(currentInputConnection)
			updateStatusIconIfNeeded()
			if (pickerManager?.isShowing() == true) {
				pickerManager?.hide()
			}
			closeAiPrompt()
		} finally {
			imeWindowBusy = false
		}
	}

	override fun onUpdateSelection(
		oldSelStart: Int,
		oldSelEnd: Int,
		newSelStart: Int,
		newSelEnd: Int,
		candidatesStart: Int,
		candidatesEnd: Int
	) {
		if(!sym.get()) {
			updateAutoCapitalization()
		}
		refreshSuggestions()

		super.onUpdateSelection(
			oldSelStart,
			oldSelEnd,
			newSelStart,
			newSelEnd,
			candidatesStart,
			candidatesEnd
		)
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (isInputViewActive && pickerManager?.isShowing() == true) {
			pickerManager!!.handleKeyEvent(event) // always eat
			return true
		} else if (event.keyCode == KeyEvent.KEYCODE_BACK) {
			if (aiPromptOpen) {
				closeAiPrompt()
				return true
			}
			if (!suggestionsDismissed && (wordSuggestionsEnabled || aiCompleteEnabled)) {
				suggestionsDismissed = true
				setImeBarShown(false)
				return true
			}
			sendDownUpKeyEvents(event.keyCode)
			return true
		}

		updateDeviceType(event)
		// Update modifier states
		if(!event.isLongPress && event.repeatCount == 0) {
			when(event.keyCode) {
				KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
					alt.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SHIFT_LEFT -> {
					if (caps.get()) {
						caps.reset()
					} else {
						shift.onKeyDown()
					}
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SHIFT_RIGHT -> {
					if (caps.get()) {
						caps.reset()
					} else {
						shift.onKeyDown()
					}
					if (cyrillicLayerToggleEnabled)
						cyrillicLayer.onRightShiftDown()
					if (koreanInputToggleEnabled)
						koreanInput.onRightShiftDown()
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SYM -> {
					sym.onKeyDown()
					onSymPossiblyChanged()
					updateStatusIconIfNeeded(true)
				}
				MP01_KEYCODE_DICTATE -> {
					dotCtrl.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
				MP01_KEYCODE_EMOJI_PICKER -> {
					emojiMeta.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
			}
		}

		if (aiPromptOpen) {
			return handleAiPromptKey(event)
		}

		if (suggestionsDismissed &&
			(event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE ||
				event.keyCode == KeyEvent.KEYCODE_DEL)
		) {
			suggestionsDismissed = false
			refreshSuggestions()
		}

		if(event.isCtrlPressed) {
			return super.onKeyDown(keyCode, event)
		}

		// Apply any special logic for triple modifiers that may modify key handling.
		if (tripleModifierOnKeyDown(keyCode, event)) {
			return true
		}

		if (deviceType == DeviceType.MP01 &&
			sym.get() && event.keyCode == KeyEvent.KEYCODE_SPACE &&
			!event.isLongPress && event.repeatCount == 0
		) {
			showSymbolPicker()
			sym.reset()
			updateStatusIconIfNeeded(true)
			return true
		}

		// Use special behavior when the SYM modifier is enabled
		if(sym.get()) {
			return onSymKey(event, true)
		}

		// Instant toggle for language layers when holding Right Shift and pressing Space
		if (!event.isLongPress && event.repeatCount == 0 && event.keyCode == KeyEvent.KEYCODE_SPACE) {
			var handled = false
			// Prefer Korean if both toggles are enabled and both are tracking right-shift
			if (koreanInputToggleEnabled && koreanInput.isRightShiftPressed()) {
				koreanInput.instantToggle()
				// Mutual exclusivity safeguard
				if (koreanInput.isActive() && cyrillicLayer.isActive()) {
					cyrillicLayer.deactivate()
				}
				// Reset composer whenever Korean mode changes
				hangulComposer.reset(currentInputConnection)
				Toast.makeText(this, if (koreanInput.isActive()) "한국" else "ENG", Toast.LENGTH_SHORT).show()
				vibrate()
				updateStatusIconIfNeeded(true)
				handled = true
			} else if (cyrillicLayerToggleEnabled && cyrillicLayer.isRightShiftPressed()) {
				cyrillicLayer.instantToggle()
				// If Cyrillic toggled on, ensure Korean is off
				if (cyrillicLayer.isActive() && koreanInput.isActive()) {
					koreanInput.deactivate()
					// Also reset composer when leaving Korean
					hangulComposer.reset(currentInputConnection)
				}
				Toast.makeText(this, if (cyrillicLayer.isActive()) "РУС" else "ENG", Toast.LENGTH_SHORT).show()
				vibrate()
				updateStatusIconIfNeeded(true)
				handled = true
			}
			if (handled) {
				// Prevent the right-shift key-up from arming a one-shot Shift (capitalizing next char)
				shift.suppressNextOnKeyUpOnce()
				// Do not treat this SPACE as input when used for toggling
				return true
			}
		}

		// Apply multipress substitution (disabled in Korean input mode)
		if(!koreanInput.isActive() && (event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE)) {
			val char = multipress.process(event, enhancedMetaState(event))
			if(char != MPSUBST_BYPASS) {
				if(char != MPSUBST_NOTHING) {
					currentInputConnection?.deleteSurroundingText(1, 0)
					updateAutoCapitalization()
					when(char) {
						MPSUBST_STR_DOTSPACE -> currentInputConnection?.commitText(". ", 2)
						else -> sendCharacter(char.toString())
					}

					consumeModifierNext()
					vibrate()
				}
				return true
			}
		}

		// Handle backspace/delete
		if(event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
			multipress.reset()
			if (koreanInput.isActive() && event.keyCode == KeyEvent.KEYCODE_DEL) {
				// Let Hangul composer handle backspace first; if it consumed, stop here
				if (hangulComposer.backspace(currentInputConnection)) {
					consumeModifierNext()
					return true
				}
			}
			consumeModifierNext()

			val handled = super.onKeyDown(keyCode, event)
			refreshSuggestions()
			return handled
		}

		// Ignore all long presses after this point
		if(event.isLongPress || event.repeatCount > 0) {
			return true
		}

		// Print something if it is a simple printing key press
		if((event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE || (event.keyCode == KeyEvent.KEYCODE_ENTER && shift.get()))) {
			if (koreanInput.isActive()) {
				// In Korean mode, honor Alt overrides before Hangul composition
				val isShifted = shift.get() || caps.get()
				if (alt.get() && multipress.overrideAltKeys) {
					val altChar = AltKeyMappings.getAltKeyChar(event.keyCode, isShifted)
					if (altChar != null) {
						hangulComposer.reset(currentInputConnection)
						currentInputConnection?.commitText(altChar.toString(), 1)
						consumeModifierNext()
						return true
					}
				}
				when (event.keyCode) {
					KeyEvent.KEYCODE_SPACE -> {
						hangulComposer.handleSpaceOrEnter(currentInputConnection, " ")
						consumeModifierNext()
						return true
					}
					KeyEvent.KEYCODE_ENTER -> {
						hangulComposer.handleSpaceOrEnter(currentInputConnection, "\n")
						consumeModifierNext()
						return true
					}
					else -> {
						val ch = event.getUnicodeChar(enhancedMetaState(event)).toChar()
						hangulComposer.inputLatinChar(ch, currentInputConnection)
						consumeModifierNext()
						return true
					}
				}
			}
			val isShifted = shift.get() || caps.get()
			val str = if (cyrillicLayer.isActive()) {
				if (alt.get() && CyrillicMappings.hasAltCyrillicMapping(event.keyCode)) {
					CyrillicMappings.getAltCyrillicChar(event.keyCode, isShifted)?.toString()
						?: event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
				} else if (CyrillicMappings.hasCyrillicMapping(event.keyCode)) {
					CyrillicMappings.getCyrillicChar(event.keyCode, isShifted)?.toString()
						?: event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
				} else {
					// No mapping: fall back to default Latin character
					event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
				}
			} else if (alt.get() && multipress.overrideAltKeys) {
				// temporary workaround with the latest software update.
				AltKeyMappings.getAltKeyChar(event.keyCode, isShifted)?.toString()
					?: event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
			} else {
				// Cyrillic layer not active: default Latin behavior
				event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
			}
			currentInputConnection?.commitText(str, 1)
			onCommittedText(str)

			consumeModifierNext()
			return true
		}

		if(event.keyCode == KeyEvent.KEYCODE_ENTER) {
			consumeModifierNext()
		}

		return super.onKeyDown(keyCode, event)
	}

	fun tripleModifierOnKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER || event.keyCode == MP01_KEYCODE_DICTATE) {
			val tripleMod =
				if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER) emojiMeta else dotCtrl;
			if (alt.get()) {
				tripleMod.activateSkipKeyUp()
				return false
			}
			if (!tripleMod.get()) {
				tripleMod.onKeyDown();
			}
			if (multipress.process(event, enhancedMetaState(event)) == MPSUBST_BYPASS) {
				return true;
			}
			if (!tripleMod.isLongPress()) {
				tripleMod.activateLongPress()
				vibrate()
			}
			return true
		} else if (KeyEvent.isModifierKey(event.keyCode)) {
			// pass
		} else if (dotCtrl.get() && dotCtrl.getModKey() == 0 && dotCtrl.modKeyCode != 0) {
			// mark that we've activated the mod key, then send ctrl.
			dotCtrl.activateModKey()
			sendKey(dotCtrl.getModKey(), event, true)
		} else if (emojiMeta.get() && emojiMeta.getModKey() == 0 && emojiMeta.modKeyCode != 0) {
			// mark that we've activated the mod key, then send meta.
			emojiMeta.activateModKey()
			sendKey(emojiMeta.getModKey(), event, true)
		}
		// Prioritizing ctrl/meta, so commenting this out:
		// if(sym.get()) { return onSymKey(event, true) }
		// Handle emojiMeta + key shortcuts.
		if (emojiMeta.get() && onEmojiMetaShotcut(event)) {
			emojiMeta.activateSkipKeyUp()
			return true
		}

		// If either modkey is active, send the key as a keypress.
		if (dotCtrl.getModKey() != 0 || emojiMeta.getModKey() != 0) {
			sendKey(keyCode, event, true)
			sendKey(keyCode, event, false)
			return true
		}
		return false
	}

	/**
	 * Overridden to ensure the input view is shown when our inline picker is active,
	 * even when a hardware keyboard is connected.
	 */
	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (isInputViewActive && pickerManager?.isShowing() == true) {
			pickerManager!!.handleKeyEvent(event) // always eat
			return true
		}
		if (aiPromptOpen &&
			event.keyCode != KeyEvent.KEYCODE_SHIFT_LEFT &&
			event.keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT &&
			event.keyCode != KeyEvent.KEYCODE_ALT_LEFT &&
			event.keyCode != KeyEvent.KEYCODE_ALT_RIGHT &&
			event.keyCode != KeyEvent.KEYCODE_SYM &&
			event.keyCode != MP01_KEYCODE_DICTATE &&
			event.keyCode != MP01_KEYCODE_EMOJI_PICKER
		) {
			return true
		}

		// Update modifier states
		when(event.keyCode) {
			KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
				alt.onKeyUp()
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SHIFT_LEFT -> {
				shift.onKeyUp()
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SHIFT_RIGHT -> {
				shift.onKeyUp()
				if (cyrillicLayerToggleEnabled)
					cyrillicLayer.onRightShiftUp()
				// Check if Cyrillic layer was toggled and provide haptic feedback
				if (cyrillicLayer.wasJustToggled()) {
					vibrate()
				}
				if (koreanInputToggleEnabled) {
					koreanInput.onRightShiftUp()
					if (koreanInput.wasJustToggled()) {
						hangulComposer.reset(currentInputConnection)
						Toast.makeText(this, if (koreanInput.isActive()) "한국" else "ENG", Toast.LENGTH_SHORT).show()
						vibrate()
					}
				}
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SYM -> {
				sym.onKeyUp()
				onSymPossiblyChanged()
				updateStatusIconIfNeeded(true)
			}
		}

		// Apply any special logic for triple modifiers that may modify key handling.
		if (tripleModifierOnKeyUp(keyCode, event)) {
			return true
		}
		// Use special behavior when the SYM modifier is enabled
		if(sym.get()) {
			return onSymKey(event, false)
		}

		return super.onKeyUp(keyCode, event)
	}

	/**
	 * Handle a triple modifier key up, which may send additional key presses or actions depending on if a key was pressed or how long it was held.
	 */
	private fun tripleModifierOnKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == MP01_KEYCODE_DICTATE || keyCode == MP01_KEYCODE_EMOJI_PICKER) {
			val modifier = if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER) emojiMeta else dotCtrl;
			val modKey = modifier.getModKey()
			var kbdKey = modifier.getKey()
			var metaState = event.metaState
			modifier.reset();
			updateStatusIconIfNeeded(true)
			if (alt.get() && multipress.overrideAltKeys) {
				// TODO: this may not even be correct.
				kbdKey = modifier.getAltKey()
				metaState = metaState and KeyEvent.META_ALT_ON.inv()
			}

			if (modKey != 0) {
				sendKey(modKey, event, false)
				return true
			} else if (kbdKey != 0) {
				// Simulate tapping the shortpress or longpress key.
				simulateKeyTap(kbdKey, event, metaState)
				consumeModifierNext()
				return true
			}
		}

		// Prioritizing ctrl/meta, so commenting this out:
		// if(sym.get()) { return onSymKey(event, false) }

		if (dotCtrl.getModKey() != 0 || emojiMeta.getModKey() != 0) {
			sendKey(keyCode, event, false)
			return true
		}

		return false
	}

	/**
	 * Update the active device type from a key event so we can reference it later.
	 */
	private fun updateDeviceType(event: KeyEvent) {
		if (event.deviceId == lastDeviceId)
			return
		lastDeviceId = event.deviceId
		val device = InputDevice.getDevice(event.deviceId)
		if (device?.isVirtual == true)
			return
		deviceType = if (device?.name == "aw9523b-key") DeviceType.MP01 else DeviceType.TITAN
	}

	/**
	 * Handle a key down event when the SYM modifier is enabled.
	 */
	fun onSymKey(event: KeyEvent, pressed: Boolean): Boolean {
		val mapping = SymKeyMappings.getMapping(event.keyCode, deviceType) ?: return if (!event.isPrintingKey) {
			if (pressed) super.onKeyDown(event.keyCode, event) else super.onKeyUp(event.keyCode, event)
		} else true

		if (pressed && event.repeatCount == 0 && !event.isLongPress) {
			when (val action = mapping.action) {
				is SendKey -> sendKey(action.keyCode, event, true)
				is SendChar -> {
					val char = if (shift.get() && action.shiftedCharacter != null) action.shiftedCharacter else action.character
					sendCharacter(char)
				}
				is ShiftPress -> {
					shift.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
			}
		} else if (!pressed) {
			when (val action = mapping.action) {
				is SendKey -> sendKey(action.keyCode, event, false)
				is SendChar -> { /* No action on key up for characters */ }
				is ShiftPress -> {
					shift.onKeyUp()
					updateStatusIconIfNeeded(true)
				}
			}
		}
		return true
	}

	// Event passed back to us from the Popup for sym key presses.
	fun forceSymKeyEvent(event: KeyEvent): Boolean {
		val pressed = event.action == KeyEvent.ACTION_DOWN
		if (event.keyCode == MP01_KEYCODE_DICTATE)
			return onSymKey(makeKeyEvent(event, KeyEvent.KEYCODE_PERIOD), pressed)
		if (onSymKey(event, pressed))
			return true

		return false
	}

	/**
	 * Handle keyboard shortcuts where emojiMeta is held.
	 */
	private fun onEmojiMetaShotcut(event: KeyEvent): Boolean {
		// skip the extra simulateKeyTap logic with sendDownUpKeyEvents.
		emojiMeta.activateSkipKeyUp()
		currentInputConnection?.sendKeyEvent(makeKeyEvent(event, emojiMeta.modKeyCode, 0, KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
		return when (event.keyCode) {
			KeyEvent.KEYCODE_V -> {
				showClipboardHistory()
				true
			}
			KeyEvent.KEYCODE_SPACE -> {
				showEmojiPicker()
				true
			}
			KeyEvent.KEYCODE_M -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_MENU)
				true
			}
			KeyEvent.KEYCODE_Q -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
				true
			}
			KeyEvent.KEYCODE_DEL -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE)
				true
			}
			MP01_KEYCODE_DICTATE -> {
				// TODO: latch control, even if disabled from dotCtrl.
				true
			}
			// Use intents in place of system-level key events.
			KeyEvent.KEYCODE_ENTER -> { // Home
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_HOME)
				true
			}
			KeyEvent.KEYCODE_E -> { // Email
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
				true
			}
			KeyEvent.KEYCODE_A -> { // Assistant (uses a different action)
				launchApp(Intent.ACTION_ASSIST)
				true
			}
			KeyEvent.KEYCODE_S -> { // Messaging
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING )
				true
			}
			KeyEvent.KEYCODE_C -> { // Contacts
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
				true
			}
			KeyEvent.KEYCODE_B -> { // Browser
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
				true
			}
			KeyEvent.KEYCODE_I -> { // Settings
				launchApp(Settings.ACTION_SETTINGS)
				true
			}
			KeyEvent.KEYCODE_P -> { // Music
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
				true
			}
			KeyEvent.KEYCODE_L -> { // Calendar
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
				true
			}
			// KeyEvent.KEYCODE_N -> // Notification shade. No standard intent for this.
			// We may be able to use an accessibility service, but it's not a priority for me.
			// Menu and Escape will only work for some apps when sent like this as well.
			else -> false
		}
	}

	/**
	 * Send a key press or release.
	 */
	private fun sendKey(code: Int, original: KeyEvent, pressed: Boolean) {
		val newState = enhancedMetaState(original)
		forceMatchMetaState(original, newState, pressed)
		currentInputConnection?.sendKeyEvent(makeKeyEvent(original, code, newState, if(pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
		forceMatchMetaState(original, newState, false)
	}

	/**
	 * Send a character, possibly uppercased depending on the Shift modifier.
	 */
	private fun sendCharacter(str: String, strict: Boolean = false) {
		var text = str
		if (!strict && (shift.get() || caps.get())) {
			text = text.uppercase(Locale.getDefault())
		}
		currentInputConnection?.commitText(text, 1)
		onCommittedText(text)
	}

	private fun simulateKeyTap(code: Int, original: KeyEvent, metaState: Int) {
		if (code == KeyEvent.KEYCODE_PICTSYMBOLS) {
			if (!emojiMeta.skipKeyUp()) {
				showEmojiPicker()
				emojiMeta.reset()
			}
			return
		} else if (code == KeyEvent.KEYCODE_VOICE_ASSIST) {
			startVoiceInput()
			dotCtrl.reset()
			return
		}
		if (koreanInput.isActive() && hangulComposer.isComposing() && code == KeyEvent.KEYCODE_PERIOD) {
			hangulComposer.commitComposingText(currentInputConnection)
			currentInputConnection?.commitText(".", 1)
			return
		}
		val event = makeKeyEvent(original, code, metaState, original.action, original.source, original.deviceId)
		if (sym.get()) {
			onSymKey(event, true)
			onSymKey(event, false)
		} else {
			val charInt = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).get(code, 0)
			if (multipress.overrideAltKeys && charInt != 0) {
				currentInputConnection?.commitText(charInt.toChar().toString(), 1)
				return
			}
			sendKey(code, event, true)
			sendKey(code, event, false)
		}
	}

	/**
	 * Forcefully match the metastate by pressing any missing modifier keys.
	 */
	private fun forceMatchMetaState(original: KeyEvent, enhanced: Int, pressed: Boolean) {
		val origMeta = original.metaState
		for ((metaOn, metaKey) in forceModifierPairs) {
			if (origMeta and metaOn == 0 && enhanced and metaOn != 0) {
				currentInputConnection?.sendKeyEvent(makeKeyEvent(original, metaKey, enhanced, if(pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
			}
		}
	}

	/**
	 * Make the device vibrate.
	 */
	private fun vibrate() {
		vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
	}

	fun updateModStateIcon() {
		updateStatusIconIfNeeded(true)
	}

	/**
	 * Update the icon in the status bar according to modifier states.
	 */
	private fun updateStatusIconIfNeeded(force: Boolean = false) {
		val shiftState = shift.get()
		val altState = alt.get()
		val symState = sym.get()
		val ctrlState = dotCtrl.get()
		val capsState = caps.get()
		val metaState = emojiMeta.get()
		val cyrillicState = cyrillicLayer.isActive()
		if(force || symState != lastSym || altState != lastAlt || shiftState != lastShift || capsState != lastCaps || ctrlState != lastDotCtrl || metaState != lastEmojiMeta || cyrillicState != lastCyrillicLayer) {
			if(sym.get()) {
				if (shift.get()) {
					showStatusIcon(R.drawable.symshift)
				} else {
					showStatusIcon(R.drawable.sym)
				}
			} else if(emojiMeta.get()) {
				showStatusIcon(R.drawable.meta)
			} else if (dotCtrl.get()) {
				showStatusIcon(if (dotCtrl.isLocked()) R.drawable.ctrllock else R.drawable.ctrl)
			} else if(cyrillicLayer.isActive()) {
				if(shift.get() || caps.get())
					showStatusIcon(if (alt.get()) R.drawable.cyrillicshiftalt else R.drawable.cyrillicshift)
				else
					showStatusIcon(if (alt.get()) R.drawable.cyrillicalt else R.drawable.cyrillic)
			} else if(alt.get()) {
				showStatusIcon(if (alt.isLocked()) R.drawable.altlock else R.drawable.alt)
			} else if(shift.get()) {
				showStatusIcon(if(shift.isLocked()) R.drawable.shiftlock else R.drawable.shift)
			} else if(caps.get()) {
				showStatusIcon(if(caps.isLocked()) R.drawable.capslock else R.drawable.caps)
			} else {
				hideStatusIcon()
			}
		}
		lastShift = shiftState
		lastAlt = altState
		lastSym = symState
		lastDotCtrl = ctrlState
		lastCaps = capsState
		lastEmojiMeta = metaState
		lastCyrillicLayer = cyrillicState
	}

	override fun showStatusIcon(iconResId: Int) {
		super.showStatusIcon(iconResId)

		stripStatusIcon?.setImageResource(iconResId)
		stripStatusIcon?.visibility = View.VISIBLE
	}

	override fun hideStatusIcon() {
		super.hideStatusIcon()
		stripStatusIcon?.visibility = View.GONE
	}

	/**
	 * Update the Shift modifier state for auto-capitalization.
	 */
	private fun updateAutoCapitalization() {
		if(!autoCapitalize) {
			return
		}
		if(currentInputEditorInfo == null || currentInputConnection == null) {
			return
		}

		if(currentInputConnection.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) > 0 && canUseSuggestions(currentInputEditorInfo)) {
			caps.activateForNext()
			updateStatusIconIfNeeded()
		}
	}

	/**
	 * Inform modifiers that the "next" key press has been consumed.
	 */
	private fun consumeModifierNext() {
		shift.nextDidConsume()
		alt.nextDidConsume()
		caps.nextDidConsume()
		dotCtrl.nextDidConsume()
		emojiMeta.nextDidConsume()
		updateStatusIconIfNeeded()
	}

	/**
	 * @return The metaState of the given event, enhanced with our own modifiers.
	 */
	private fun enhancedMetaState(original: KeyEvent): Int {
		var metaState = original.metaState
		if(shift.get()) {
			metaState = metaState or KeyEvent.META_SHIFT_ON
		}
		if(caps.get()) {
			metaState = metaState or KeyEvent.META_CAPS_LOCK_ON
		}
		if(alt.get()) {
			metaState = metaState or KeyEvent.META_ALT_ON
		}
		if (dotCtrl.getModKey() != 0) {
			metaState = metaState or KeyEvent.META_CTRL_ON
		}
		if (emojiMeta.getModKey() != 0) {
			metaState = metaState or KeyEvent.META_META_ON
		}
		// Strip the sym state if it is pressed.
		return metaState and KeyEvent.META_SYM_ON.inv()
	}

	/**
	 * Handle what happens when the SYM modifier has possibly changed.
	 */
	private fun onSymPossiblyChanged() {
		if(sym.get() && !lastSym) {
			if(shift.get() && !shift.isHeld()) {
				shift.reset()
			}
		} else if(!sym.get() && lastSym) {
			updateAutoCapitalization()
		}
	}

	/**
	 * Update values from the preferences.
	 */
	private fun updateFromPreferences() {
		val context = createDeviceProtectedStorageContext()
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)

		showToolbar = preferences.getBoolean("pref_show_toolbar", false)
		this.inputViewStrip?.visibility = if (showToolbar) View.VISIBLE else View.GONE

		autoCapitalize = preferences.getBoolean("AutoCapitalize", true)
		wordSuggestionsEnabled = preferences.getBoolean("WordSuggestions", true)
		aiCompleteEnabled = preferences.getBoolean("AiComplete", false)
		aiCompleteEndpoint = preferences.getString("AiCompleteEndpoint", "") ?: ""
		if (aiCompleteEndpoint.startsWith("http://")) {
			aiCompleteEndpoint = "https://" + aiCompleteEndpoint.removePrefix("http://")
		}

		val lockThreshold = preferences.getInt("ModifierLockThreshold", 250)
		shift.lockThreshold = lockThreshold
		alt.lockThreshold = lockThreshold
		sym.lockThreshold = lockThreshold

		val nextThreshold = preferences.getInt("ModifierNextThreshold", 350)
		shift.nextThreshold = nextThreshold
		alt.nextThreshold = nextThreshold

		multipress.multipressThreshold = preferences.getInt("MultipressThreshold", 750)
		multipress.ignoreFirstLevel = !preferences.getBoolean("UseFirstLevel", false)
		multipress.ignoreDotSpace = !preferences.getBoolean("DotSpace", true)
		multipress.ignoreConsonantsOnFirstLevel = preferences.getBoolean("FirstLevelOnlyVowels", false)
		multipress.ligaturesEnabled = preferences.getBoolean("pref_enable_ligatures", false)
		multipress.overrideAltKeys = preferences.getBoolean("override_alt_keys", true)

		cyrillicLayerToggleEnabled = preferences.getBoolean("pref_enable_cyrillic_layer", false)
		koreanInputToggleEnabled = preferences.getBoolean("pref_enable_korean_input", false)

		// Enforce mutual exclusivity at settings level: if both enabled, disable Cyrillic layer
		if (cyrillicLayerToggleEnabled && koreanInputToggleEnabled) {
			preferences.edit().putBoolean("pref_enable_cyrillic_layer", false).apply()
			cyrillicLayerToggleEnabled = false
		}

		// If Cyrillic feature disabled, also deactivate runtime layer
		if (!cyrillicLayerToggleEnabled && cyrillicLayer.isActive()) {
			cyrillicLayer.deactivate()
		}
		// If Korean feature disabled, also deactivate runtime mode and reset composer
		if (!koreanInputToggleEnabled && koreanInput.isActive()) {
			koreanInput.deactivate()
			hangulComposer.reset(currentInputConnection)
		}

		val templateId = preferences.getString("FirstLevelTemplate", "fr-ext")
		if(templates.containsKey(templateId)) {
			multipress.substitutions[0] = templates[templateId]!!
		}

		dotCtrl.shortPressKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_tap", "period"))
		dotCtrl.longPressKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_long_press", "voice"))
		dotCtrl.modKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_hold", "ctrl"))

		emojiMeta.shortPressKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_tap", "emoji"))
		emojiMeta.longPressKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_long_press", "0"))
		emojiMeta.modKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_hold", "meta"))


		// MP01 right-hand friendly Sym layer toggle (opt-in by default)
		val rightHandSym = preferences.getBoolean("pref_mp01_right_hand_sym", false)
		SymKeyMappings.enableRightHandMp01(rightHandSym)
		// Refresh picker symbols if currently shown to reflect new labels/actions
		pickerManager?.refreshSymbols()

		// TODO: Separate modifier and special-key logic and add better handling for sym and right shift.
	}

	private fun preferenceToKeyCode(preferenceValue: String?): Int {
		return when (preferenceValue) {
			"period" -> KeyEvent.KEYCODE_PERIOD
			"voice" -> KeyEvent.KEYCODE_VOICE_ASSIST
			"ctrl" -> KeyEvent.KEYCODE_CTRL_RIGHT
			"emoji" -> KeyEvent.KEYCODE_PICTSYMBOLS
			"0" -> KeyEvent.KEYCODE_0
			"meta" -> KeyEvent.KEYCODE_META_LEFT
			else -> 0 // "none" or any other value
		}
	}

	private fun startVoiceInput() {
		val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		val token = window.window?.attributes?.token ?: return

		val voiceImeId = findVoiceIme()
		if (voiceImeId != null) {
			imm.setInputMethod(token, voiceImeId)
		} else {
			Log.w(this.packageName,"No voice IME found.")
			Toast.makeText(this, "No voice IME found.", Toast.LENGTH_SHORT).show()
		}
	}

	private fun findVoiceIme(): String? {
		val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		for (imi in imm.enabledInputMethodList) {
			for (i in 0 until imi.subtypeCount) {
				val subtype = imi.getSubtypeAt(i)
				if (subtype.mode == "voice") {
					return imi.id
				}
			}
		}
		return null
	}

	/**
	 * Launches an application using an Intent.
	 */
	private fun launchApp(action: String, category: String? = null) {
		val intent = Intent(action)
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		if (category != null) {
			intent.addCategory(category)
		}
		try {
			startActivity(intent)
		} catch (e: Exception) {
			// Handle cases where the app isn't found or another error occurs
			e.printStackTrace()
		}
	}

	private fun loadWordSuggestions(context: android.content.Context) {
		if (wordDictionary != null) return
		try {
			wordDictionary = resources.openRawResource(R.raw.en_unigrams).bufferedReader().use { unigrams ->
				resources.openRawResource(R.raw.en_bigrams).bufferedReader().use { bigrams ->
					WordDictionary.load(unigrams.lineSequence(), bigrams.lineSequence())
				}
			}
			userLexicon = UserLexicon(File(context.filesDir, "user_lexicon.txt"))
			suggestionEngine = SuggestionEngine(wordDictionary!!) { word -> userLexicon?.boost(word) ?: 0 }
		} catch (e: Exception) {
			Log.w(packageName, "Word suggestions dictionary failed to load", e)
			wordDictionary = null
			suggestionEngine = null
		}
	}

	private fun suggestionsEligible(): Boolean {
		if (!wordSuggestionsEnabled && !aiCompleteEnabled) return false
		if (koreanInput.isActive() || cyrillicLayer.isActive()) return false
		if (pickerManager?.isShowing() == true) return false
		val info = currentInputEditorInfo ?: return false
		return canUseSuggestions(info)
	}

	private fun refreshSuggestions() {
		if (suggestionsDismissed && !aiPromptOpen) {
			setImeBarShown(false)
			return
		}
		if (!suggestionsEligible()) {
			displayedSuggestions = emptyList()
			displayedPrefix = ""
			if (!aiPromptOpen) setImeBarShown(false)
			return
		}
		if (aiPromptOpen) {
			aiPromptPanel?.visibility = View.VISIBLE
			suggestionRow?.visibility = View.GONE
			setImeBarShown(true)
			return
		}
		val before = currentInputConnection?.getTextBeforeCursor(64, 0)
		val prefix = SuggestionEngine.currentPrefix(before)
		val previous = SuggestionEngine.previousWord(before)
		val raw = if (wordSuggestionsEnabled) {
			suggestionEngine?.suggest(prefix, previous, 3) ?: emptyList()
		} else {
			emptyList()
		}
		val capsMode = currentInputConnection?.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) ?: 0
		val cased = raw.map { SuggestionEngine.applyCase(it, prefix, prefix.isEmpty() && capsMode > 0) }
		displayedPrefix = prefix
		displayedSuggestions = cased
		val buttons = suggestionButtons
		if (buttons != null) {
			for (i in buttons.indices) {
				val button = buttons[i]
				if (i < cased.size) {
					button.text = cased[i]
					button.visibility = View.VISIBLE
				} else {
					button.text = ""
					button.visibility = View.INVISIBLE
				}
			}
		}
		suggestionRow?.visibility = View.VISIBLE
		aiButton?.visibility =
			if (aiCompleteEnabled && aiCompleteEndpoint.isNotBlank()) View.VISIBLE else View.GONE
		aiButton?.text = "AI"
		setImeBarShown(true)
	}

	private fun requestAiComplete() {
		if (!aiCompleteEnabled || aiBusy) return
		if (!suggestionsEligible()) return
		if (!aiPromptOpen) {
			openAiPrompt()
			return
		}
		armOrSubmitAi(rewrite = false)
	}

	private fun openAiPrompt() {
		aiPromptOpen = true
		aiBusy = false
		aiMode = null
		suggestionsDismissed = false
		aiPromptBuffer.clear()
		aiPromptPanel?.visibility = View.VISIBLE
		suggestionRow?.visibility = View.GONE
		aiPromptStatus?.visibility = View.GONE
		aiPromptStatus?.text = ""
		aiPromptText?.hint = "Rewrite or Go, then type"
		aiPromptText?.setText("")
		aiPromptText?.isCursorVisible = true
		aiPromptText?.requestFocus()
		updateAiPromptDisplay()
		setImeBarShown(true)
	}

	private fun closeAiPrompt() {
		aiPromptOpen = false
		aiBusy = false
		aiMode = null
		aiPromptBuffer.clear()
		aiPromptPanel?.visibility = View.GONE
		suggestionRow?.visibility = View.VISIBLE
		aiPromptStatus?.visibility = View.GONE
		refreshSuggestions()
	}

	private fun setAiPromptStatus(message: String?) {
		if (message.isNullOrEmpty()) {
			aiPromptStatus?.text = ""
			aiPromptStatus?.visibility = View.GONE
		} else {
			aiPromptStatus?.text = message
			aiPromptStatus?.visibility = View.VISIBLE
		}
	}

	private fun updateAiPromptDisplay() {
		val edit = aiPromptText ?: return
		val typed = aiPromptBuffer.toString()
		if (edit.text.toString() != typed) {
			edit.setText(typed)
		}
		val pos = typed.length
		if (edit.selectionStart != pos || edit.selectionEnd != pos) {
			edit.setSelection(pos)
		}
		edit.isCursorVisible = true
		if (!edit.hasFocus()) edit.requestFocus()
	}

	private fun appendToPrompt(str: String) {
		if (str.isEmpty() || aiBusy) return
		aiPromptBuffer.append(str)
		updateAiPromptDisplay()
		consumeModifierNext()
	}

	private fun deleteFromPrompt() {
		if (aiPromptBuffer.isEmpty()) return
		val last = aiPromptBuffer.offsetByCodePoints(aiPromptBuffer.length, -1)
		aiPromptBuffer.delete(last, aiPromptBuffer.length)
		updateAiPromptDisplay()
	}

	private fun handleAiPromptKey(event: KeyEvent): Boolean {
		when (event.keyCode) {
			KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
			KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
			KeyEvent.KEYCODE_SYM -> return true
			KeyEvent.KEYCODE_ENTER -> {
				if (event.repeatCount == 0) {
					armOrSubmitAi(rewrite = aiMode == "rewrite")
				}
				return true
			}
			KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
				deleteFromPrompt()
				return true
			}
		}

		if (event.isLongPress || (event.repeatCount > 0 &&
				event.keyCode != KeyEvent.KEYCODE_DEL)
		) {
			return true
		}

		if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER) {
			if (alt.get()) {
				appendToPrompt("0")
			}
			return true
		}
		if (event.keyCode == MP01_KEYCODE_DICTATE) {
			appendToPrompt(".")
			return true
		}

		if (sym.get()) {
			val mapping = SymKeyMappings.getMapping(event.keyCode, deviceType)
			when (val action = mapping?.action) {
				is SendChar -> {
					val ch = if ((shift.get() || caps.get()) && action.shiftedCharacter != null) {
						action.shiftedCharacter
					} else {
						action.character
					}
					appendToPrompt(ch)
				}
				is SendKey -> if (action.keyCode == KeyEvent.KEYCODE_TAB) {
					appendToPrompt("\t")
				}
				else -> { }
			}
			return true
		}

		val isShifted = shift.get() || caps.get()
		if (alt.get() && (event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE ||
				event.keyCode == MP01_KEYCODE_EMOJI_PICKER)
		) {
			val altChar = AltKeyMappings.getAltKeyChar(event.keyCode, isShifted)
			if (altChar != null) {
				appendToPrompt(altChar.toString())
				return true
			}
		}

		if (event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE) {
			if (!koreanInput.isActive()) {
				val subst = multipress.process(event, enhancedMetaState(event))
				if (subst != MPSUBST_BYPASS) {
					if (subst != MPSUBST_NOTHING) {
						when (subst) {
							MPSUBST_STR_DOTSPACE -> {
								deleteFromPrompt()
								appendToPrompt(". ")
							}
							else -> {
								deleteFromPrompt()
								var text = subst.toString()
								if (shift.get() || caps.get()) {
									text = text.uppercase(Locale.getDefault())
								}
								appendToPrompt(text)
							}
						}
					}
					return true
				}
			}
			val ch = event.getUnicodeChar(enhancedMetaState(event))
			if (ch != 0 && ch != '\b'.code) {
				appendToPrompt(ch.toChar().toString())
			} else if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
				appendToPrompt(" ")
			}
			return true
		}
		return true
	}

	private fun armOrSubmitAi(rewrite: Boolean) {
		if (aiBusy) return
		aiMode = if (rewrite) "rewrite" else "generate"
		val prompt = aiPromptBuffer.toString().trim()
		if (prompt.isEmpty()) {
			aiPromptText?.hint = if (rewrite) {
				"How should we rewrite this?"
			} else {
				"What should we write?"
			}
			setAiPromptStatus(
				if (rewrite) "Rewrite — type how, then Rewrite or Enter"
				else "Go — type what you want, then Go or Enter"
			)
			aiPromptText?.requestFocus()
			return
		}
		submitAiPrompt(rewrite)
	}

	private fun submitAiPrompt(rewrite: Boolean) {
		if (aiBusy) return
		val prompt = aiPromptBuffer.toString().trim()
		if (prompt.isEmpty()) {
			setAiPromptStatus("Type a prompt first")
			updateAiPromptDisplay()
			return
		}
		val ic = currentInputConnection
		val before = ic?.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
		val after = ic?.getTextAfterCursor(4000, 0)?.toString().orEmpty()
		val field = before + after
		if (rewrite && field.isBlank()) {
			setAiPromptStatus("Nothing to rewrite")
			return
		}
		aiBusy = true
		setAiPromptStatus(if (rewrite) "Rewriting…" else "Working…")
		keyboardAiClient.complete(
			aiCompleteEndpoint,
			if (rewrite) field else before,
			prompt,
			if (rewrite) "rewrite" else "generate",
			onResult = { raw ->
				aiBusy = false
				if (rewrite) {
					val replacement = raw.trim()
					ic?.beginBatchEdit()
					ic?.deleteSurroundingText(before.length, after.length)
					ic?.commitText(replacement, 1)
					ic?.endBatchEdit()
				} else {
					ic?.commitText(formatAiInsertion(before, raw), 1)
				}
				closeAiPrompt()
			},
			onError = { message ->
				aiBusy = false
				setAiPromptStatus(message)
				updateAiPromptDisplay()
			}
		)
	}

	private fun formatAiInsertion(before: String, raw: String): String {
		var text = raw.trim()
		if (text.startsWith("\"") && text.endsWith("\"") && text.length > 1) {
			text = text.substring(1, text.length - 1).trim()
		}
		if (before.isNotEmpty() && before.last().isLetterOrDigit() &&
			text.isNotEmpty() && text.first().isLetterOrDigit()
		) {
			text = " $text"
		}
		return text
	}

	private fun applySuggestion(index: Int) {
		val word = displayedSuggestions.getOrNull(index) ?: return
		val ic = currentInputConnection ?: return
		val prefix = displayedPrefix
		ic.beginBatchEdit()
		if (prefix.isNotEmpty()) {
			ic.deleteSurroundingText(prefix.length, 0)
		}
		ic.commitText("$word ", 1)
		ic.endBatchEdit()
		userLexicon?.record(word)
		consumeModifierNext()
		refreshSuggestions()
	}

	private fun onCommittedText(text: String) {
		if (text.isNotEmpty() && !text[0].isLetter()) {
			val before = currentInputConnection?.getTextBeforeCursor(64, 0)
			val word = SuggestionEngine.previousWord(before)
			if (word.length >= 2) userLexicon?.record(word)
		}
		refreshSuggestions()
	}

	fun clearModifiers() {
		shift.reset()
		alt.reset()
		sym.reset()
		dotCtrl.reset()
		emojiMeta.reset()
		caps.reset()
		cyrillicLayer.reset()
		updateStatusIconIfNeeded(true)
	}

	fun onPickerVisibilityChanged(showing: Boolean) {
		if (imeWindowBusy) return
		if (showing) {
			setImeBarShown(false)
		} else {
			refreshSuggestions()
		}
	}

	/**
	 * Reset only the Emoji Meta modifier state and refresh the status icon.
	 *
	 * This is used by the picker when it gets dismissed via the emoji button
	 * (or close/back). Without this, if the picker was opened using an
	 * emoji meta shortcut (e.g., emoji + space), the modifier could remain
	 * latched, keeping the keyboard in the shortcut mode.
	 */

	fun resetEmojiMeta() {
		emojiMeta.reset()
		updateStatusIconIfNeeded(true)
	}
}