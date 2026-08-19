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

    companion object {
        private const val VOICE_REQUEST = 501
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        keyStore = ApiKeyStore(this)
        queryInput = findViewById(R.id.queryInput)
        searchButton = findViewById(R.id.searchButton)
        voiceButton = findViewById(R.id.voiceButton)
        apiKeyButton = findViewById(R.id.apiKeyButton)
        resultText = findViewById(R.id.resultText)
        resultScroll = findViewById(R.id.resultScroll)
        progress = findViewById(R.id.progress)

        queryInput.setOnEditorActionListener { _, actionId, event ->
            val enter =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

            if (
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                enter
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener {
            performSearch()
        }

        voiceButton.setOnClickListener {
            startVoiceSearch()
        }

        apiKeyButton.setOnClickListener {
            showApiKeyDialog()
        }

        resultText.autoLinkMask = Linkify.WEB_URLS
        resultText.linksClickable = true

        queryInput.requestFocus()

        if (keyStore.load().isNullOrBlank()) {
            showApiKeyDialog(firstRun = true)
        }
    }

    private fun performSearch() {
        val query = queryInput.text.toString().trim()

        if (query.isBlank()) {
            queryInput.requestFocus()
            return
        }

        val apiKey = keyStore.load()

        if (apiKey.isNullOrBlank()) {
            showApiKeyDialog(firstRun = true)
            return
        }

        setLoading(true)
        resultText.text = "Searching the live web…"
        resultScroll.scrollTo(0, 0)

        executor.execute {
            try {
                val reply =
                    GroqClient().search(apiKey, query)

                runOnUiThread {
                    resultText.text = reply.text
                    Linkify.addLinks(
                        resultText,
                        Linkify.WEB_URLS
                    )
                    resultScroll.scrollTo(0, 0)
                    setLoading(false)
                    resultScroll.requestFocus()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    resultText.text =
                        "Search failed.\n\n" +
                            (
                                error.message
                                    ?: "Unknown error"
                            )
                    setLoading(false)
                    searchButton.requestFocus()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility =
            if (loading) View.VISIBLE else View.GONE

        searchButton.isEnabled = !loading
        voiceButton.isEnabled = !loading
        queryInput.isEnabled = !loading
    }

    private fun startVoiceSearch() {
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
            }
        }
    }

    private fun showApiKeyDialog(
        firstRun: Boolean = false
    ) {
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
                    "Enter your Groq API key. It is " +
                        "encrypted with Android Keystore " +
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
                        field.error =
                            "API key required"
                        return@setOnClickListener
                    }

                    keyStore.save(key)
                    dialog.dismiss()

                    Toast.makeText(
                        this,
                        "API key saved.",
                        Toast.LENGTH_SHORT
                    ).show()

                    queryInput.requestFocus()
                }
        }

        dialog.show()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
