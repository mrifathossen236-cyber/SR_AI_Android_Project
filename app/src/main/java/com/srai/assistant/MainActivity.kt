package com.srai.assistant

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var apiKey: EditText
    private lateinit var prompt: EditText
    private lateinit var output: TextView
    private var latestAnswer = ""
    private var tts: TextToSpeech? = null
    private val voiceRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("sr_ai", MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFF08111F.toInt())
        }
        scroll.addView(layout)
        setContentView(scroll)

        fun label(text: String, size: Float = 16f) {
            layout.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(0xFFEAF4FF.toInt())
                setPadding(0, 12, 0, 8)
            })
        }
        fun button(text: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            })
        }

        label("SR AI", 30f)
        label("Your voice + text Android assistant")
        label("Google AI Studio / Gemini API Key")
        apiKey = EditText(this).apply {
            hint = "Paste Gemini API key"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9FB0C5.toInt())
            setText(prefs.getString("api_key", ""))
        }
        layout.addView(apiKey)
        button("Save API Key") {
            prefs.edit().putString("api_key", apiKey.text.toString().trim()).apply()
            toast("API key saved on this device")
        }

        label("Ask SR AI")
        prompt = EditText(this).apply {
            hint = "Type a command or question..."
            minLines = 2
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9FB0C5.toInt())
        }
        layout.addView(prompt)
        button("🎙 Speak") { startVoiceInput() }
        button("Send to Gemini") { handleCommand(prompt.text.toString().trim()) }
        button("Open an installed app") {
            val name = prompt.text.toString().trim()
            if (name.isNotEmpty()) openInstalledApp(name) else toast("Type an app name first")
        }
        button("Search the web") {
            val q = prompt.text.toString().trim()
            if (q.isNotEmpty()) openSearch(q) else toast("Type something to search")
        }
        button("Enable screen typing permission") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("Find SR AI in Accessibility and enable it. Only enable if you trust this app.")
        }
        button("Type latest AI answer into focused field") {
            if (latestAnswer.isBlank()) toast("No AI answer yet")
            else if (SRAccessibilityService.typeIntoFocusedField(latestAnswer)) toast("Text inserted")
            else toast("Enable SR AI Accessibility and focus a text field first")
        }

        label("AI Response / Generated Code")
        output = TextView(this).apply {
            text = "Your response will appear here."
            textSize = 15f
            setTextColor(0xFFB9E7FF.toInt())
            setTextIsSelectable(true)
            setPadding(12, 16, 12, 16)
            setBackgroundColor(0xFF122238.toInt())
        }
        layout.addView(output)
        button("Speak response") {
            if (latestAnswer.isNotBlank()) tts?.speak(latestAnswer, TextToSpeech.QUEUE_FLUSH, null, "sr-answer")
        }
    }

    private fun handleCommand(text: String) {
        if (text.isBlank()) { toast("Write or speak a command"); return }
        val lower = text.lowercase(Locale.getDefault())
        when {
            lower.startsWith("open ") || lower.startsWith("launch ") || lower.contains(" ওপেন ") -> {
                val app = text.replaceFirst(Regex("(?i)^(open|launch)\\s+"), "").trim()
                if (!openInstalledApp(app)) askGemini(text)
            }
            lower.startsWith("search ") || lower.startsWith("সার্চ ") -> openSearch(text.substringAfter(" ").trim())
            else -> askGemini(text)
        }
    }

    private fun askGemini(question: String) {
        val key = prefs.getString("api_key", "")?.trim().orEmpty()
        if (key.isEmpty()) { toast("Add your Google AI Studio API key first"); return }
        output.text = "SR AI is thinking..."
        executor.execute {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" +
                    URLEncoder.encode(key, "UTF-8")
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject()
                    .put("contents", org.json.JSONArray().put(JSONObject().put("parts",
                        org.json.JSONArray().put(JSONObject().put("text",
                            "You are SR AI, a helpful Android assistant. Provide useful answers and code. Do not claim to have performed device actions. User request: $question")))))
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                if (conn.responseCode !in 200..299) throw Exception("Gemini API ${conn.responseCode}: $raw")
                val json = JSONObject(raw)
                val answer = json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                runOnUiThread {
                    latestAnswer = answer
                    output.text = answer
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread { output.text = "Error: ${e.message}" }
            }
        }
    }

    private fun openInstalledApp(query: String): Boolean {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(query, ignoreCase = true)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) { startActivity(intent); return true }
        }
        toast("Could not find an installed app matching: $query")
        return false
    }

    private fun openSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            startActivity(intent)
        } catch (_: Exception) { toast("No browser available") }
    }

    private fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to SR AI")
            }
            startActivityForResult(intent, voiceRequest)
        } catch (_: Exception) { toast("Voice recognition is not available on this device") }
    }

    @Deprecated("Deprecated by Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == voiceRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            prompt.setText(spoken)
            if (spoken.isNotBlank()) handleCommand(spoken)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("bn", "BD")
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
