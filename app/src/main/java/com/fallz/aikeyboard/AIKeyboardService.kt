package com.fallz.aikeyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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

/**
 * AI Keyboard - Gboard-style layout
 *
 * Layout (top to bottom):
 *   top toolbar      : ⌨ app  📋 clip  ☰ menu            (icon buttons only, dark)
 *   suggestion strip : Rapikan | (slot 2) | (slot 3)      (3 placeholder slots)
 *   letter row 1     : q w e r t y u i o p                (rounded, dark, number hint)
 *   letter row 2     : a s d f g h j k l
 *   letter row 3     : ⇧ z x c v b n m ⌫
 *   bottom row       : ?123 😊 🌐 ID·DE   . ↵                (Globe, space, period, enter)
 *
 * Klip & Tanya AI dipindah dari "tab gede" jadi "expanded panel" — dibuka dari toolbar,
 * ngeganti body, tombol back balik ke keyboard utama.
 */
class AIKeyboardService : InputMethodService() {

    private enum class Panel { KEYBOARD, CLIPBOARD, AI, EMOJI }

    private var currentPanel = Panel.KEYBOARD
    private var isShift = false
    private var isSymbolsMode = false // false = abc, true = numbers/symbols
    private var symbolPage = 0 // 0 = "123", 1 = "=+/", 2 = "abc" shortcut stays in letters
    private var emojiCategory = 0

    private lateinit var rootView: LinearLayout
    private lateinit var toolbarView: LinearLayout
    private lateinit var suggestionStripView: LinearLayout
    private lateinit var bodyContainer: LinearLayout
    private lateinit var keyboardContainer: LinearLayout

    private lateinit var statusView: TextView
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

    private val rowsAlpha = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )
    private val rowsAlphaShift = listOf(
        "QWERTYUIOP",
        "ASDFGHJKL",
        "ZXCVBNM"
    )

    // Symbol pages. Page 0: 1 2 3 4..., Page 1: = + - _ etc, Page 2: punctuation
    private val symbolPages = listOf(
        listOf(
            "1234567890",
            "!@#" + "$" + "%^&*()",
            "`~/\\()\"'<>"  // double-quote escaped as \"; final 8 chars
        ),
        listOf(
            "=+-*/%",
            "[]{}<>€¥£",
            ".,;:?!#"
        ),
        listOf(
            "@#$&_",
            "|~\\\"'=^",
            "()[]{}"
        )
    )

    // Emoji categories (simplified — single categories, all unicode ranges per page)
    private val emojiPages = listOf(
        // Smileys
        "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 💀 👻 👽 🤖 💩 😺 😸 😹 😻 😼 😽 🙀 😿 😾"
        // Hearts & symbols
        ,
        "❤ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💔 ❣ 💕 💞 💓 💗 💖 💘 💝 💟 ☮ ✝ ☪ 🕉 ☸ ✡ 🔯 🕎 ☯ ☦ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛ 🉑 ☢ ☣ 📵 🚫 ⛔ 💯 💢 ♨ 🈶 🈚 🈸 🈺 🈷 ✴ 🆚 💮 🉐 ㊙ 🏾 ㊗ 🈴 🆎 🆑 🅾 🆘 ❌ 🅰 🅱 🆖 🆗 🆙 🆚 🈁 🆠 🆡 🈂 🔠 🔡 🔢 🔣 🔤 🅿 🈯"
        // Useful glyphs
        ,
        "👍 👎 👌 ✌ 🤞 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝ 👋 🤚 🖐 ✋ 🖖 👏 🙌 🤝 🙏 ✍ 💅 🤳 💪 🦾 🦵 🦿 🦶 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁 👅 👄 💋 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👮 👷 💂 🕵 👼 🎅 🤶 🧙 🧝 🧛 🧟 🧞 🧜 👻 👹 👺 👽 🤖 💩 💀 ☠"
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

    // Build view tree sekali, swap body via visibility
    override fun onCreateInputView(): View {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101012"))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        toolbarView = buildToolbar()
        suggestionStripView = buildSuggestionStrip()
        bodyContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        rootView.addView(toolbarView)
        rootView.addView(suggestionStripView)
        rootView.addView(bodyContainer)

        renderBody()
        return rootView
    }

    // ---------- TOOLBAR (top icon row, Gboard style) ----------

    private fun buildToolbar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = makeDrawable(0xFF1C1C1E.toInt(), 8f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Huruf "A" gede buat switch input method
        bar.addView(makeIconKey("A", bg = 0xFF2C2C2E.toInt()) {
            // back to keyboard view kalau lagi di panel
            switchPanel(Panel.KEYBOARD)
            // trigger system input picker
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })

        // Clipboard
        bar.addView(makeIconKey("📋", bg = 0xFF2C2C2E.toInt()) {
            switchPanel(Panel.CLIPBOARD)
        })

        // Search/translation placeholder (just decorative)
        bar.addView(makeIconKey("🔍", bg = 0xFF2C2C2E.toInt()) {
            Toast.makeText(this, "Pencarian: belum aktif", Toast.LENGTH_SHORT).show()
        })

        // Rapikan (AI rewrite) - diletakkan di toolbar juga sebagai shortcut
        bar.addView(makeIconKey("✨", bg = 0xFF3B5BFE.toInt(), tintWhite = true) {
            onAiRewriteFromToolbar()
        })

        // Spacer
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        // Mic placeholder
        bar.addView(makeIconKey("🎙", bg = 0xFF2C2C2E.toInt()) {
            Toast.makeText(this, "Voice input: belum aktif", Toast.LENGTH_SHORT).show()
        })

        // Tanya AI
        bar.addView(makeIconKey("💬", bg = 0xFF2C2C2E.toInt()) {
            switchPanel(Panel.AI)
        })

        // Menu dots (pakai sebagai "settings" stub)
        bar.addView(makeIconKey("⋮", bg = 0xFF2C2C2E.toInt()) {
            showHelpDialog()
        })

        return bar
    }

    private fun makeIconKey(
        label: String,
        bg: Int,
        tintWhite: Boolean = false,
        onClick: () -> Unit
    ): View {
        val v = TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (tintWhite) Color.WHITE else Color.parseColor("#D0D0D3"))
            background = makeDrawable(bg, 6f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36),
            ).apply {
                marginEnd = dp(4)
            }
        }
        v.setOnClickListener { onClick() }
        return v
    }

    // ---------- SUGGESTION STRIP (3 slots) ----------

    private fun buildSuggestionStrip(): LinearLayout {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }

        // Slot 1: tombol Rapikan (fitur utama AI)
        strip.addView(makeSuggestionChip("Rapikan", accent = true) {
            onRapikanPressed()
        })

        // Slot 2 & 3: kosong (siap diisi prediksi nanti)
        strip.addView(makeSuggestionChip("", accent = false) {
            // placeholder
        })
        strip.addView(makeSuggestionChip("", accent = false) {
            // placeholder
        })

        // weight trick supaya 3 chip rata memenuhi width
        for (i in 0 until strip.childCount) {
            val child = strip.getChildAt(i)
            (child.layoutParams as? LinearLayout.LayoutParams)?.apply {
                width = 0
                height = dp(40)
                weight = 1f
                marginEnd = if (i < strip.childCount - 1) dp(4) else 0
            }
            child.setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        return strip
    }

    private fun makeSuggestionChip(text: String, accent: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (text.isEmpty()) Color.TRANSPARENT else Color.WHITE)
            background = makeDrawable(
                if (accent) 0xFF3B5BFE.toInt() else 0xFF1C1C1E.toInt(),
                6f
            )
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            }
        }
    }

    // ---------- BODY SWAPPER (keyboard vs klip vs ai) ----------

    private fun renderBody() {
        bodyContainer.removeAllViews()
        when (currentPanel) {
            Panel.KEYBOARD -> {
                keyboardContainer = buildKeyboardContainer()
                bodyContainer.addView(keyboardContainer)
            }
            Panel.CLIPBOARD -> {
                bodyContainer.addView(buildClipboardPanel())
            }
            Panel.AI -> {
                bodyContainer.addView(buildAiPanel())
            }
            Panel.EMOJI -> {
                bodyContainer.addView(buildEmojiPanel())
            }
        }
    }

    private fun switchPanel(panel: Panel) {
        currentPanel = panel
        renderBody()
    }

    // ---------- KEYBOARD CONTAINER ----------

    private fun buildKeyboardContainer(): LinearLayout {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusView = TextView(this).apply {
            text = ""
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#9A9A9F"))
            setPadding(dp(4), dp(2), dp(4), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(statusView)

        val rows = when {
            isSymbolsMode -> symbolPages[symbolPage]
            isShift -> rowsAlphaShift
            else -> rowsAlpha
        }
        if (isSymbolsMode) {
            // Symbols: same structure as ABC mode (row1, row2 full-width, row3 with action keys)
            container.addView(buildLetterRow(rows[0]))
            container.addView(buildLetterRow(rows[1]))

            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            // Left action: cycle symbol pages OR switch back to ABC
            val shiftLabel = when (symbolPage) {
                0 -> "=+/"
                1 -> "ABC"
                else -> "?123"
            }
            val isLastPage = symbolPage == symbolPages.size - 1
            row3.addView(makeRow3ActionKey(shiftLabel, weight = 1.0f) {
                if (isLastPage) {
                    // Back to letters
                    isSymbolsMode = false
                    symbolPage = 0
                } else {
                    symbolPage++
                }
                renderBody()
            })
            row3.addView(buildLetterRow(rows[2]))
            row3.addView(makeRow3ActionKey("⌫", weight = 1.0f) {
                handleBackspace()
            })
            container.addView(row3)
            container.addView(buildBottomRow())
        } else {
            // ABC mode: row1 full-bleed, row3 with shift+backspace on sides
            container.addView(buildLetterRow(rows[0]))
            container.addView(buildLetterRow(rows[1]))

            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            row3.addView(makeRow3ActionKey(if (isShift) "⇪" else "⇧", weight = 1.0f) {
                isShift = !isShift
                renderBody()
            })
            row3.addView(buildLetterRow(rows[2]))
            row3.addView(makeRow3ActionKey("⌫", weight = 1.0f) {
                handleBackspace()
            })
            container.addView(row3)
            container.addView(buildBottomRow())
        }
        return container
    }

    private fun buildLetterRow(letters: String): LinearLayout {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }

        for (c in letters) {
            val displayChar = c.toString()
            val isLetterMode = !isSymbolsMode
            val hint = if (isLetterMode) when (c.lowercaseChar()) {
                'q' -> '1'; 'w' -> '2'; 'e' -> '3'; 'r' -> '4'; 't' -> '5'
                'y' -> '6'; 'u' -> '7'; 'i' -> '8'; 'o' -> '9'; 'p' -> '0'
                else -> null
            } else null

            val charToType = if (isShift && isLetterMode) displayChar.uppercase() else displayChar.lowercase()

            // Single TextView per key; hint shown top-left via SpannableString
            val keyView = TextView(this).apply {
                background = makeDrawable(0xFF2C2C2E.toInt(), 8f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
                    marginEnd = dp(3)
                }
                setPadding(0, dp(4), 0, dp(4))
                if (hint != null) {
                    // Build multi-styled text: small hint on first line, big char second
                    val ss = SpannableString("${hint}\n${charToType}")
                    ss.setSpan(
                        RelativeSizeSpan(0.45f),
                        0, 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ss.setSpan(
                        ForegroundColorSpan(Color.parseColor("#7A7A80")),
                        0, 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ss.setSpan(
                        RelativeSizeSpan(1.8f),
                        2, 3,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text = ss
                    gravity = Gravity.CENTER
                } else {
                    text = charToType
                    textSize = 24f
                }
                setOnClickListener { typeChar(charToType) }
            }
            rowLayout.addView(keyView)
        }
        return rowLayout
    }

    private fun buildBottomRow(): LinearLayout {
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }

        // ?123 / ABC toggle
        bottomRow.addView(makeSpecialKey(
            label = if (isSymbolsMode) "ABC" else "?123",
            weight = 1.4f
        ) {
            isSymbolsMode = !isSymbolsMode
            renderBody()
        })

        // emoji panel
        bottomRow.addView(makeSpecialKey(label = "😊", weight = 1.0f) {
            switchPanel(Panel.EMOJI)
        })

        // globe (switch input method)
        bottomRow.addView(makeSpecialKey(label = "🌐", weight = 1.0f) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        })

        // space dengan label bahasa (kayak Gboard ID·DE)
        bottomRow.addView(makeSpecialKey(label = "ID • DE", weight = 4.5f, bigSpace = true) {
            typeChar(" ")
        })

        // period
        bottomRow.addView(makeSpecialKey(label = ".", weight = 1.0f) {
            typeChar(".")
        })

        // enter
        bottomRow.addView(makeSpecialKey(label = "↵", weight = 1.4f, accent = true) {
            handleEnter()
        })

        return bottomRow
    }

    private fun makeSpecialKey(
        label: String,
        weight: Float,
        bigSpace: Boolean = false,
        accent: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val bgColor = if (accent) 0xFF3B5BFE.toInt() else 0xFF1F1F22.toInt()
        return TextView(this).apply {
            text = label
            textSize = if (bigSpace) 14f else 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = makeDrawable(bgColor, 6f)
            layoutParams = LinearLayout.LayoutParams(0, dp(60), weight).apply {
                marginEnd = dp(3)
            }
            setPadding(dp(0), dp(0), dp(0), dp(0))
            setOnClickListener { onClick() }
        }
    }

    // Reused Gboard-style QWERTY action key (shift & backspace di row 3)
    private fun makeRow3ActionKey(label: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = makeDrawable(0xFF1F1F22.toInt(), 6f)
            layoutParams = LinearLayout.LayoutParams(0, dp(60), weight).apply {
                marginEnd = dp(3)
            }
            setOnClickListener { onClick() }
        }
    }

    @Suppress("unused")
    private fun makeRow3Spacer(weight: Float): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(56), weight).apply {
                marginEnd = dp(3)
            }
        }
    }

    // ---------- KLIP (clipboard) panel ----------

    private fun buildClipboardPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(0xFF18181A.toInt(), 8f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "📋 Riwayat Klip"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        val clearBtn = makeIconKey("Hapus", bg = 0xFF3A3A3D.toInt()) {
            saveClipHistory(mutableListOf())
            renderBody()
        }
        header.addView(clearBtn)
        // back to keyboard
        header.addView(makeIconKey("⌨", bg = 0xFF3A3A3D.toInt()) {
            switchPanel(Panel.KEYBOARD)
        })
        panel.addView(header)

        val history = loadClipHistory()
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val itemsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(0), dp(8), dp(0), dp(8))
        }

        if (history.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Belum ada yang di-copy"
                textSize = 13f
                setTextColor(Color.parseColor("#7A7A80"))
                setPadding(dp(8), dp(16), dp(8), dp(16))
            }
            itemsRow.addView(empty)
        } else {
            for (item in history) {
                val chip = TextView(this).apply {
                    text = if (item.length > 24) item.take(24) + "…" else item
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    background = makeDrawable(0xFF2C2C2E.toInt(), 6f)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener {
                        currentInputConnection?.commitText(item, 1)
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dp(6)
                chip.layoutParams = lp
                itemsRow.addView(chip)
            }
        }
        scroll.addView(itemsRow)
        panel.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // info di bawah
        val info = TextView(this).apply {
            text = "Tap chip buat paste ke field yang aktif. Riwayat maks 15 item."
            textSize = 11f
            setTextColor(Color.parseColor("#7A7A80"))
            setPadding(dp(4), dp(8), dp(4), dp(0))
        }
        panel.addView(info)
        return panel
    }

    // ---------- EMOJI panel ----------

    private val emojiSet = listOf(
        "😀","😁","😂","🤣","😊","😍","😘","😜","🤔","😎",
        "😢","😭","😡","🥺","😴","🥳","😱","🤯","🙄","😏",
        "👍","👎","👌","✌️","🤞","👏","🙏","💪","🤝","👋",
        "❤","🔥","✨","🎉","💯","⭐","☀","🌙","💀","🤡",
        "🐱","🐶","🍕","🍔","☕","🎮","⚽","🚗","💰","📱",
        // Faces
        "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇",
        "🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
        "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸",
        "🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹",
        "😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡",
        "🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓",
        // Hands & people
        "👍","👎","👌","✌","🤞","🤟","🤘","🤙","👈","👉",
        "👆","🖕","👇","☝","👋","🤚","🖐","✋","🖖","👏",
        "🙌","🤝","🙏","✍","💅","🤳","💪","🦾","🦵","🦿",
        "🦶","👂","🦻","👃","🧠","👀","👁","👅","👄","💋",
        "👶","🧒","👦","👧","🧑","👨","👩","🧓","👴","👵",
        // Hearts & symbols
        "❤","🧡","💛","💚","💙","💜","🤎","🖤","🤍","💔",
        "❣","💕","💞","💓","💗","💖","💘","💝","💟","♥",
        // Food
        "🍕","🍔","🍟","🌭","🥪","🌮","🌯","🥙","🧆","🥚",
        "🍳","🥘","🍲","🥣","🥗","🍝","🍜","🍣","🍱","🥟",
        "🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧",
        // Animals
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
        "🦁","🐮","🐷","🐽","🐸","🐵","🙈","🙉","🙊","🐒",
        // Activities & travel
        "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
        "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐",
        "✈","🚀","🛸","🚁","🛶","⛵","🚤","🛥","🛳","⛴"
    )

    private fun buildEmojiPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(0xFF18181A.toInt(), 8f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "😊 Emoji"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        header.addView(makeIconKey("⌫", bg = 0xFF3A3A3D.toInt()) {
            handleBackspace()
        })
        header.addView(makeIconKey("⌨", bg = 0xFF3A3A3D.toInt()) {
            switchPanel(Panel.KEYBOARD)
        })
        panel.addView(header)

        // Category tabs
        val tabs = listOf("Smiley", "Hands", "Heart", "Food", "Animal", "Travel")
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        tabs.forEachIndexed { idx, label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = makeDrawable(
                    if (idx == emojiCategory) 0xFF3B5BFE.toInt() else 0xFF2C2C2E.toInt(),
                    6f
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(28)
                ).apply { marginEnd = dp(4) }
                setOnClickListener {
                    emojiCategory = idx
                    renderBody()
                }
            }
            tabRow.addView(chip)
        }
        panel.addView(tabRow)

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(180)
            )
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Pick emoji subset per category, drawn from emojiSet
        val categorySize = 100
        val startIdx = emojiCategory * categorySize
        val endIdx = minOf(startIdx + categorySize, emojiSet.size)
        val subset = if (startIdx < emojiSet.size) {
            emojiSet.subList(startIdx, endIdx)
        } else emptyList()

        val perRow = 8
        var i = 0
        while (i < subset.size) {
            val rowChunk = subset.subList(i, minOf(i + perRow, subset.size))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
            }
            for (emoji in rowChunk) {
                val cell = TextView(this).apply {
                    text = emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        marginEnd = dp(2)
                    }
                    setPadding(0, dp(4), 0, dp(4))
                    setOnClickListener { typeChar(emoji) }
                }
                row.addView(cell)
            }
            grid.addView(row)
            i += perRow
        }
        if (subset.isEmpty()) {
            val empty = TextView(this).apply {
                text = "(kategori habis)"
                textSize = 12f
                setTextColor(Color.parseColor("#7A7A80"))
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
            grid.addView(empty)
        }
        scroll.addView(grid)
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
        if (currentPanel == Panel.CLIPBOARD) renderBody()
    }

    // ---------- TANYA AI panel ----------

    private fun buildAiPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(0xFF18181A.toInt(), 8f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "✨ Tanya AI"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        header.addView(makeIconKey("⌨", bg = 0xFF3A3A3D.toInt()) {
            switchPanel(Panel.KEYBOARD)
        })
        panel.addView(header)

        val queryLabel = TextView(this).apply {
            text = "Pertanyaan:"
            textSize = 11f
            setTextColor(Color.parseColor("#9A9A9F"))
            setPadding(dp(0), dp(6), dp(0), dp(2))
        }
        panel.addView(queryLabel)

        aiQueryText = TextView(this).apply {
            text = if (aiQueryBuilder.isEmpty()) "(ketik pake tombol di bawah)" else aiQueryBuilder.toString()
            textSize = 14f
            setTextColor(if (aiQueryBuilder.isEmpty()) Color.parseColor("#6E6E73") else Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = makeDrawable(0xFF2C2C2E.toInt(), 6f)
        }
        panel.addView(aiQueryText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(0), dp(6), dp(0), dp(6))
        }
        actionRow.addView(makeIconKey("Tanya", bg = 0xFF3B5BFE.toInt(), tintWhite = true) {
            onAskAiPressed()
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        actionRow.addView(makeIconKey("Hapus", bg = 0xFF3A3A3D.toInt()) {
            aiQueryBuilder.clear()
            aiQueryText.text = "(ketik pake tombol di bawah)"
            aiQueryText.setTextColor(Color.parseColor("#6E6E73"))
            aiResponseText.text = ""
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        actionRow.addView(makeIconKey("Sisip", bg = 0xFF3A3A3D.toInt()) {
            val reply = aiResponseText.text?.toString().orEmpty()
            if (reply.isNotBlank() && reply != "⏳ Mikir...") {
                currentInputConnection?.commitText(reply, 1)
            }
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        panel.addView(actionRow)

        // Compact response area (smaller to give room for keyboard)
        val responseScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120)
            )
            background = makeDrawable(0xFF1F1F22.toInt(), 6f)
        }
        aiResponseText = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#CCFFCC"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        responseScroll.addView(aiResponseText)
        panel.addView(responseScroll)

        // Re-add full keyboard so user can keep typing the question
        // (same buildKeyboardContainer that the main view uses)
        // For AI panel we render keyboard without status text
        val kbd = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = when {
            isSymbolsMode -> symbolPages[symbolPage]
            isShift -> rowsAlphaShift
            else -> rowsAlpha
        }
        if (isSymbolsMode) {
            kbd.addView(buildLetterRow(rows[0]))
            kbd.addView(buildLetterRow(rows[1]))
            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            val shiftLabel = when (symbolPage) {
                0 -> "=+/"; 1 -> "ABC"; else -> "?123"
            }
            val isLastPage = symbolPage == symbolPages.size - 1
            row3.addView(makeRow3ActionKey(shiftLabel, weight = 1.0f) {
                if (isLastPage) { isSymbolsMode = false; symbolPage = 0 } else symbolPage++
                renderBody()
            })
            row3.addView(buildLetterRow(rows[2]))
            row3.addView(makeRow3ActionKey("⌫", weight = 1.0f) { handleBackspace() })
            kbd.addView(row3)
        } else {
            kbd.addView(buildLetterRow(rows[0]))
            kbd.addView(buildLetterRow(rows[1]))
            val row3 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(1), dp(1), dp(1))
            }
            row3.addView(makeRow3ActionKey(if (isShift) "⇪" else "⇧", weight = 1.0f) {
                isShift = !isShift
                renderBody()
            })
            row3.addView(buildLetterRow(rows[2]))
            row3.addView(makeRow3ActionKey("⌫", weight = 1.0f) { handleBackspace() })
            kbd.addView(row3)
        }
        kbd.addView(buildBottomRow())
        panel.addView(kbd)

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

    // ---------- TYPING HELPERS ----------

    private fun typeChar(text: String) {
        if (currentPanel == Panel.AI) {
            aiQueryBuilder.append(text)
            if (::aiQueryText.isInitialized) {
                aiQueryText.text = aiQueryBuilder.toString()
                aiQueryText.setTextColor(Color.WHITE)
            }
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun handleBackspace() {
        if (currentPanel == Panel.AI) {
            if (aiQueryBuilder.isNotEmpty()) {
                aiQueryBuilder.deleteCharAt(aiQueryBuilder.length - 1)
                if (::aiQueryText.isInitialized) {
                    aiQueryText.text = if (aiQueryBuilder.isEmpty()) "(ketik pake tombol di bawah)" else aiQueryBuilder.toString()
                    aiQueryText.setTextColor(
                        if (aiQueryBuilder.isEmpty()) Color.parseColor("#6E6E73") else Color.WHITE
                    )
                }
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        if (currentPanel == Panel.AI) {
            onAskAiPressed()
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    // ---------- AI ACTIONS ----------

    private fun onRapikanPressed() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        if (before.isBlank()) {
            Toast.makeText(this, "Belum ada teks buat dirapikan", Toast.LENGTH_SHORT).show()
            return
        }
        statusView.text = "⏳ Merapikan..."
        thread {
            val prompt = "Rapikan ejaan & tata bahasa kalimat berikut tanpa mengubah makna aslinya. " +
                "Balas HANYA dengan teks hasilnya, tanpa tanda kutip, tanpa penjelasan apapun:\n\n$before"
            val result = callKiosapiRaw(prompt)
            Handler(mainLooper).post {
                if (result != null) {
                    ic.deleteSurroundingText(before.length, 0)
                    ic.commitText(result, 1)
                    statusView.text = "✓ Sudah dirapikan"
                } else {
                    statusView.text = "⚠️ Gagal konek AI"
                }
            }
        }
    }

    private fun onAiRewriteFromToolbar() = onRapikanPressed()

    // ---------- HELP / ABOUT ----------

    private fun showHelpDialog() {
        Toast.makeText(this, "AI Keyboard v2 — Gboard style", Toast.LENGTH_SHORT).show()
    }

    // ---------- KIOSAPI ----------

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

    // ---------- UTILS ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun makeDrawable(color: Int, cornerDp: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = cornerDp * resources.displayMetrics.density
        return d
    }
}
