package com.fallz.aikeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class AIKeyboardService : InputMethodService() {

    private enum class Tab { KEYBOARD, CLIPBOARD, AI }

    private var currentTab = Tab.KEYBOARD
    private var isShift = false

    private lateinit var contentContainer: LinearLayout
    private lateinit var tabKeyboardBtn: Button
    private lateinit var tabClipBtn: Button
    private lateinit var tabAiBtn: Button

    private lateinit var aiQueryText: TextView
    private lateinit var aiResponseText: TextView
    private val aiQueryBuilder = StringBuilder()

    private lateinit var prefs: SharedPreferences
    private lateinit var clipboardManager: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) addToClipHistory(text)
        }
    }

    private val rows = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("ai_keyboard_prefs", Context.MODE_PRIVATE)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF1E1E1E.toInt())
        root.setPadding(8, 8, 8, 8)

        root.addView(buildTabRow())

        contentContainer = LinearLayout(this)
        contentContainer.orientation = LinearLayout.VERTICAL
        root.addView(contentContainer)
        renderContentPanel()

        for (row in rows) {
            root.addView(buildLetterRow(row))
        }
        root.addView(buildBottomRow())

        return root
    }

    // ---------- TAB ROW ----------

    private fun buildTabRow(): LinearLayout {
        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL

        tabKeyboardBtn = Button(this)
        tabKeyboardBtn.text = "⌨ Keyboard"
        tabKeyboardBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tabKeyboardBtn.setOnClickListener { switchTab(Tab.KEYBOARD) }
        tabRow.addView(tabKeyboardBtn)

        tabClipBtn = Button(this)
        tabClipBtn.text = "📋 Klip"
        tabClipBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tabClipBtn.setOnClickListener { switchTab(Tab.CLIPBOARD) }
        tabRow.addView(tabClipBtn)

        tabAiBtn = Button(this)
        tabAiBtn.text = "✨ Tanya AI"
        tabAiBtn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tabAiBtn.setOnClickListener { switchTab(Tab.AI) }
        tabRow.addView(tabAiBtn)

        highlightActiveTab()
        return tabRow
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        highlightActiveTab()
        renderContentPanel()
    }

    private fun highlightActiveTab() {
        val active = 0xFF3D5AFE.toInt()
        val inactive = 0xFF3A3A3A.toInt()
        tabKeyboardBtn.setBackgroundColor(if (currentTab == Tab.KEYBOARD) active else inactive)
        tabClipBtn.setBackgroundColor(if (currentTab == Tab.CLIPBOARD) active else inactive)
        tabAiBtn.setBackgroundColor(if (currentTab == Tab.AI) active else inactive)
    }

    // ---------- CONTENT PANEL (berubah sesuai tab) ----------

    private fun renderContentPanel() {
        contentContainer.removeAllViews()
        when (currentTab) {
            Tab.KEYBOARD -> contentContainer.addView(buildNormalPanel())
            Tab.CLIPBOARD -> contentContainer.addView(buildClipboardPanel())
            Tab.AI -> contentContainer.addView(buildAiPanel())
        }
    }

    private fun buildNormalPanel(): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        panel.setPadding(8, 8, 8, 16)

        val status = TextView(this)
        status.text = "Ketik seperti biasa. Tab \"Tanya AI\" buat nanya sesuatu."
        status.setTextColor(0xFFCCCCCC.toInt())
        status.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        panel.addView(status)

        val rapikanBtn = Button(this)
        rapikanBtn.text = "Rapikan"
        rapikanBtn.setOnClickListener { onAiRewritePressed(status) }
        panel.addView(rapikanBtn)

        return panel
    }

    // ---------- PAPAN KLIP ----------

    private fun buildClipboardPanel(): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(8, 8, 8, 8)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        val title = TextView(this)
        title.text = "Riwayat Klip"
        title.setTextColor(0xFFCCCCCC.toInt())
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        val clearBtn = Button(this)
        clearBtn.text = "Kosongkan"
        clearBtn.setOnClickListener {
            saveClipHistory(mutableListOf())
            renderContentPanel()
        }
        header.addView(clearBtn)
        panel.addView(header)

        val history = loadClipHistory()
        val scroll = HorizontalScrollView(this)
        val itemsRow = LinearLayout(this)
        itemsRow.orientation = LinearLayout.HORIZONTAL

        if (history.isEmpty()) {
            val empty = TextView(this)
            empty.text = "Belum ada yang di-copy"
            empty.setTextColor(0xFF888888.toInt())
            empty.setPadding(8, 16, 8, 16)
            itemsRow.addView(empty)
        } else {
            for (item in history) {
                val chip = Button(this)
                chip.text = if (item.length > 24) item.take(24) + "…" else item
                chip.setOnClickListener {
                    currentInputConnection?.commitText(item, 1)
                }
                itemsRow.addView(chip)
            }
        }
        scroll.addView(itemsRow)
        panel.addView(scroll)

        return panel
    }

    private fun loadClipHistory(): MutableList<String> {
        val raw = prefs.getString("clip_history", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        return list
    }

    private fun saveClipHistory(list: MutableList<String>) {
        val arr = JSONArray()
        for (item in list) arr.put(item)
        prefs.edit().putString("clip_history", arr.toString()).apply()
    }

    private fun addToClipHistory(text: String) {
        val list = loadClipHistory()
        list.remove(text)
        list.add(0, text)
        while (list.size > 15) list.removeAt(list.size - 1)
        saveClipHistory(list)
        if (currentTab == Tab.CLIPBOARD) renderContentPanel()
    }

    // ---------- TANYA AI ----------

    private fun buildAiPanel(): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(8, 8, 8, 8)

        val queryLabel = TextView(this)
        queryLabel.text = "Pertanyaan (ketik pake keyboard di bawah):"
        queryLabel.setTextColor(0xFF888888.toInt())
        panel.addView(queryLabel)

        aiQueryText = TextView(this)
        aiQueryText.text = if (aiQueryBuilder.isEmpty()) "Ketik pertanyaan lo…" else aiQueryBuilder.toString()
        aiQueryText.setTextColor(0xFFFFFFFF.toInt())
        aiQueryText.setPadding(8, 8, 8, 8)
        aiQueryText.setBackgroundColor(0xFF2A2A2A.toInt())
        panel.addView(aiQueryText)

        val actionRow = LinearLayout(this)
        actionRow.orientation = LinearLayout.HORIZONTAL

        val askBtn = Button(this)
        askBtn.text = "Tanya"
        askBtn.setOnClickListener { onAskAiPressed() }
        actionRow.addView(askBtn)

        val clearBtn = Button(this)
        clearBtn.text = "Hapus"
        clearBtn.setOnClickListener {
            aiQueryBuilder.clear()
            aiQueryText.text = "Ketik pertanyaan lo…"
            aiResponseText.text = ""
        }
        actionRow.addView(clearBtn)

        val insertBtn = Button(this)
        insertBtn.text = "Sisipkan ke teks"
        insertBtn.setOnClickListener {
            val reply = aiResponseText.text?.toString().orEmpty()
            if (reply.isNotBlank()) {
                currentInputConnection?.commitText(reply, 1)
            }
        }
        actionRow.addView(insertBtn)

        panel.addView(actionRow)

        val responseScroll = ScrollView(this)
        responseScroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 220
        )
        aiResponseText = TextView(this)
        aiResponseText.setTextColor(0xFFCCFFCC.toInt())
        aiResponseText.setPadding(8, 8, 8, 8)
        responseScroll.addView(aiResponseText)
        panel.addView(responseScroll)

        return panel
    }

    private fun onAskAiPressed() {
        val question = aiQueryBuilder.toString().trim()
        if (question.isBlank()) {
            Toast.makeText(this, "Ketik pertanyaan dulu", Toast.LENGTH_SHORT).show()
            return
        }
        aiResponseText.text = "⏳ Mikir..."
        thread {
            val result = callKiosapiRaw(question)
            Handler(mainLooper).post {
                aiResponseText.text = result ?: "⚠️ Gagal konek AI"
            }
        }
    }

    // ---------- KEYBOARD ROWS (dipake di semua tab buat ngetik) ----------

    private fun buildLetterRow(letters: String): LinearLayout {
        val rowLayout = LinearLayout(this)
        rowLayout.orientation = LinearLayout.HORIZONTAL
        for (c in letters) {
            val btn = Button(this)
            btn.text = c.toString()
            btn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            btn.setOnClickListener {
                val out = if (isShift) c.uppercaseChar().toString() else c.toString()
                typeChar(out)
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
        space.setOnClickListener { typeChar(" ") }
        bottomRow.addView(space)

        val enter = Button(this)
        enter.text = "⏎"
        enter.setOnClickListener { handleEnter() }
        bottomRow.addView(enter)

        return bottomRow
    }

    // Ngetik masuk ke query AI kalau lagi di tab AI, kalau engga langsung commit ke app
    private fun typeChar(text: String) {
        if (currentTab == Tab.AI) {
            aiQueryBuilder.append(text)
            aiQueryText.text = aiQueryBuilder.toString()
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun handleBackspace() {
        if (currentTab == Tab.AI) {
            if (aiQueryBuilder.isNotEmpty()) {
                aiQueryBuilder.deleteCharAt(aiQueryBuilder.length - 1)
                aiQueryText.text = if (aiQueryBuilder.isEmpty()) "Ketik pertanyaan lo…" else aiQueryBuilder.toString()
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        if (currentTab == Tab.AI) {
            onAskAiPressed()
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    // ---------- KIOSAPI ----------

    private fun onAiRewritePressed(statusView: TextView) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        if (before.isBlank()) {
            Toast.makeText(this, "Ketik dulu sesuatu", Toast.LENGTH_SHORT).show()
            return
        }
        statusView.text = "⏳ Mikir..."
        thread {
            val prompt = "Rapikan ejaan & tata bahasa kalimat berikut tanpa mengubah makna aslinya. " +
                "Balas HANYA dengan teks hasilnya, tanpa tanda kutip, tanpa penjelasan apapun:\n\n$before"
            val result = callKiosapiRaw(prompt)
            Handler(mainLooper).post {
                if (result != null) {
                    ic.deleteSurroundingText(before.length, 0)
                    ic.commitText(result, 1)
                }
                statusView.text = "Ketik seperti biasa. Tab \"Tanya AI\" buat nanya sesuatu."
            }
        }
    }

    private fun callKiosapiRaw(promptText: String): String? {
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
            msg.put("content", promptText)
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
