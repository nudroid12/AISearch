package com.nudroid12.aisearch

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var keyStore: ApiKeyStore
    private lateinit var queryInput: EditText
    private lateinit var searchButton: Button
    private lateinit var voiceButton: Button
    private lateinit var apiKeyButton: Button
    private lateinit var resultText: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView

    private var hasResult = false
    private var isSearching = false

    companion object {
        private const val VOICE_REQUEST = 501
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        keyStore = ApiKeyStore(this)
        queryInput = findViewById(R.id.queryInput)
        searchButton = findViewById(R.id.searchButton)
        voiceButton = findViewById(R.id.voiceButton)
        apiKeyButton = findViewById(R.id.apiKeyButton)
        resultText = findViewById(R.id.resultText)
        resultScroll = findViewById(R.id.resultScroll)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)

        configureInput()
        configureButtons()
        configureResultScrolling()
        configureFocusAnimation()

        resultText.autoLinkMask = Linkify.WEB_URLS
        resultText.linksClickable = true

        queryInput.requestFocus()

        if (keyStore.load().isNullOrBlank()) {
            showApiKeyDialog(firstRun = true)
        }
    }

    private fun configureInput() {
        queryInput.setOnEditorActionListener { _, actionId, event ->
            val enterPressed =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

            if (
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                enterPressed
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        queryInput.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            ) {
                showKeyboard()
            }
            false
        }
    }

    private fun configureButtons() {
        searchButton.setOnClickListener { performSearch() }
        voiceButton.setOnClickListener { startVoiceSearch() }
        apiKeyButton.setOnClickListener { showApiKeyDialog() }
    }

    private fun configureResultScrolling() {
        resultScroll.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }

            val page =
                (resultScroll.height * 0.58f)
                    .toInt()
                    .coerceAtLeast(180)

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (resultScroll.canScrollVertically(1)) {
                        resultScroll.smoothScrollBy(0, page)
                    } else {
                        apiKeyButton.requestFocus()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (resultScroll.canScrollVertically(-1)) {
                        resultScroll.smoothScrollBy(0, -page)
                    } else {
                        queryInput.requestFocus()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    queryInput.requestFocus()
                    true
                }

                else -> false
            }
        }
    }

    private fun configureFocusAnimation() {
        listOf<View>(
            queryInput,
            voiceButton,
            searchButton,
            resultScroll,
            apiKeyButton
        ).forEach { view ->
            view.setOnFocusChangeListener { target, focused ->
                val scale = if (focused) 1.025f else 1.0f

                target.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120L)
                    .start()
            }
        }
    }

    private fun performSearch() {
        if (isSearching) return

        val query = queryInput.text.toString().trim()

        if (query.isBlank()) {
            statusText.text = "Enter a question first."
            queryInput.requestFocus()
            showKeyboard()
            return
        }

        val apiKey = keyStore.load()

        if (apiKey.isNullOrBlank()) {
            showApiKeyDialog(firstRun = true)
            return
        }

        hideKeyboard()
        setLoading(true)

        hasResult = false
        resultText.text = ""
        resultScroll.scrollTo(0, 0)
        statusText.text = "Searching current web sources..."

        executor.execute {
            try {
                val liveResults =
                    LiveSearchClient().search(query)

                runOnUiThread {
                    statusText.text =
                        "Reading ${liveResults.results.size} live sources..."
                }

                val reply =
                    GroqClient().answer(
                        apiKey = apiKey,
                        searchBundle = liveResults
                    )

                runOnUiThread {
                    hasResult = true

                    resultText.text = reply.text
                    Linkify.addLinks(
                        resultText,
                        Linkify.WEB_URLS
                    )

                    resultScroll.scrollTo(0, 0)
                    statusText.text =
                        "LIVE • ${reply.sourceCount} sources"

                    setLoading(false)
                    resultScroll.requestFocus()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    hasResult = false

                    resultText.text =
                        "Couldn't complete the live search.\n\n" +
                            (
                                error.message
                                    ?: "Please try again."
                            )

                    statusText.text = "Search failed"
                    setLoading(false)
                    searchButton.requestFocus()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        isSearching = loading

        progress.visibility =
            if (loading) View.VISIBLE else View.GONE

        searchButton.isEnabled = !loading
        voiceButton.isEnabled = !loading
        queryInput.isEnabled = !loading
        apiKeyButton.isEnabled = !loading
    }

    private fun startVoiceSearch() {
        if (isSearching) return

        hideKeyboard()

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "What do you want to search?"
                )
            }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                intent,
                VOICE_REQUEST
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Voice input is not available on this device.",
                Toast.LENGTH_LONG
            ).show()

            queryInput.requestFocus()
        }
    }

    @Deprecated("Kept for broad Android TV compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == VOICE_REQUEST &&
            resultCode == RESULT_OK
        ) {
            val results =
                data?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )

            val text =
                results?.firstOrNull()?.trim().orEmpty()

            if (text.isNotBlank()) {
                queryInput.setText(text)
                queryInput.setSelection(text.length)
                performSearch()
            } else {
                queryInput.requestFocus()
            }
        } else if (requestCode == VOICE_REQUEST) {
            queryInput.requestFocus()
        }
    }

    private fun showApiKeyDialog(
        firstRun: Boolean = false
    ) {
        hideKeyboard()

        val field = EditText(this).apply {
            hint = "gsk_..."
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setPadding(32, 12, 32, 12)
        }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Groq API Key")
                .setMessage(
                    "Your key is encrypted with Android Keystore " +
                        "and stored only on this device."
                )
                .setView(field)
                .setPositiveButton("Save", null)
                .setNegativeButton(
                    if (firstRun) "Later" else "Cancel",
                    null
                )
                .create()

        dialog.setOnShowListener {
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val key =
                        field.text.toString().trim()

                    if (key.isBlank()) {
                        field.error = "API key required"
                        return@setOnClickListener
                    }

                    keyStore.save(key)
                    dialog.dismiss()

                    Toast.makeText(
                        this,
                        "API key saved.",
                        Toast.LENGTH_SHORT
                    ).show()

                    statusText.text = "Ready for a live search"
                    queryInput.requestFocus()
                }
        }

        dialog.setOnDismissListener {
            queryInput.requestFocus()
        }

        dialog.show()
    }

    private fun showKeyboard() {
        queryInput.post {
            val inputMethod =
                getSystemService(INPUT_METHOD_SERVICE)
                    as InputMethodManager

            inputMethod.showSoftInput(
                queryInput,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun hideKeyboard() {
        val inputMethod =
            getSystemService(INPUT_METHOD_SERVICE)
                as InputMethodManager

        inputMethod.hideSoftInputFromWindow(
            queryInput.windowToken,
            0
        )
    }

    @Deprecated("TV back behaviour")
    override fun onBackPressed() {
        if (isSearching) return

        if (!queryInput.hasFocus()) {
            queryInput.requestFocus()

            if (hasResult) {
                queryInput.setSelection(
                    queryInput.text.length
                )
            }

            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
