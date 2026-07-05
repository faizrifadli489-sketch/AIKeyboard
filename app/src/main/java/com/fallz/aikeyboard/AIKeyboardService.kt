package com.fallz.aikeyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class AIKeyboardService : InputMethodService() {

    private lateinit var aiStatus: TextView
    private var isShift = false

    private val rows = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF1E1E1E.toInt())
        root.setPadding(8, 8, 8, 8)

        root.addView(buildAiBar())

        for (row in rows) {
            root.addView(buildLetterRow(row))
        }

        root.addView(buildBottomRow())

        return root
    }

    // Baris paling atas: status AI + tombol AI
    private fun buildAiBar(): LinearLayout {
        val aiBar = LinearLayout(this)
        aiBar.orientation = LinearLayout.HORIZONTAL
        aiBar.setPadding(8, 8, 8, 16)

        aiStatus = TextView(this)
        aiStatus.text = "✨ AI siap bantu ngetik"
        aiStatus.setTextColor(0xFFCCCCCC.toInt())
        aiStatus.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        aiBar.addView(aiStatus)

        val aiButton = Button(this)
        aiButton.text = "Rapikan"
        aiButton.setOnClickListener { onAiRewritePressed() }
        aiBar.addView(aiButton)

        return aiBar
    }

    private fun buildLetterRow(letters: String): LinearLayout {
        val rowLayout = LinearLayout(this)
        rowLayout.orientation = LinearLayout.HORIZONTAL
        for (c in letters) {
            val btn = Button(this)
            btn.text = c.toString()
            btn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            btn.setOnClickListener {
                val out = if (isShift) c.uppercaseChar().toString() else c.toString()
                commitTyped(out)
            }
            rowLayout.addView(btn)
        }
        return rowLayout
    }

    private fun buildBottomRow(): LinearLayout {
        val bottomRow = LinearLayout(this)
        bottomRow.orientation = LinearLayout.HORIZONTAL

        val shift = Button(this)
        shift.text = "⇧"
        shift.setOnClickListener {
            isShift = !isShift
            Toast.makeText(this, if (isShift) "Shift ON" else "Shift OFF", Toast.LENGTH_SHORT).show()
        }
        bottomRow.addView(shift)

        val backspace = Button(this)
        backspace.text = "⌫"
        backspace.setOnClickListener { handleBackspace() }
        bottomRow.addView(backspace)

        val space = Button(this)
        space.text = "Space"
        space.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        space.setOnClickListener { commitTyped(" ") }
        bottomRow.addView(space)

        val enter = Button(this)
        enter.text = "⏎"
        enter.setOnClickListener { handleEnter() }
        bottomRow.addView(enter)

        return bottomRow
    }

    private fun commitTyped(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    // Tombol "Rapikan" -> ambil teks sebelum kursor, kirim ke Kiosapi, ganti dengan hasilnya
    private fun onAiRewritePressed() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        if (before.isBlank()) {
            Toast.makeText(this, "Ketik dulu sesuatu", Toast.LENGTH_SHORT).show()
            return
        }
        aiStatus.text = "⏳ Mikir..."
        thread {
            val result = callKiosapi(before)
            Handler(mainLooper).post {
                if (result != null) {
                    ic.deleteSurroundingText(before.length, 0)
                    ic.commitText(result, 1)
                    aiStatus.text = "✨ AI siap bantu ngetik"
                } else {
                    aiStatus.text = "⚠️ Gagal konek AI"
                }
            }
        }
    }

    private fun callKiosapi(inputText: String): String? {
        return try {
            val url = URL("https://api.kiosapi.id/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.KIOSAPI_KEY}")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val messages = JSONArray()
            val msg = JSONObject()
            msg.put("role", "user")
            msg.put(
                "content",
                "Rapikan ejaan & tata bahasa kalimat berikut tanpa mengubah makna aslinya. " +
                    "Balas HANYA dengan teks hasilnya, tanpa tanda kutip, tanpa penjelasan apapun:\n\n$inputText"
            )
            messages.put(msg)

            val body = JSONObject()
            body.put("model", "google/gemini-2.5-flash")
            body.put("messages", messages)

            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
