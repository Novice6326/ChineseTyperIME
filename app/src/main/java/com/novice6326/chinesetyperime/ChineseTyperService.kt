package com.novice6326.chinesetyperime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Keyboard-core milestone. The QWERTY keys commit text through InputConnection.
 * Chinese phonetic conversion is intentionally not faked; it requires a real
 * dictionary/IME engine and will be integrated as a separate verified stage.
 */
class ChineseTyperService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var content = ""
    private var index = 0
    private var running = false
    private var paused = false
    private var shifted = false
    private var symbols = false
    private lateinit var status: TextView
    private lateinit var keyboard: LinearLayout

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.rgb(239, 241, 245))
        }
        status = TextView(this).apply {
            text = "键盘核心开发版｜逐字输入工具已保留"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(5, 3, 5, 5)
        }
        root.addView(status)
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(button("剪贴板") { loadClipboard() }, weight(1f, 42))
        toolbar.addView(button("逐字") { showTyperTools() }, weight(1f, 42))
        toolbar.addView(button("符号") { symbols = !symbols; renderKeys() }, weight(1f, 42))
        toolbar.addView(button("切换输入法") { switchToNextInputMethod(false) }, weight(1.2f, 42))
        root.addView(toolbar)
        keyboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keyboard)
        renderKeys()
        return root
    }

    private fun weight(w: Float, h: Int) = LinearLayout.LayoutParams(0, h, w).apply {
        setMargins(2, 2, 2, 2)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setPadding(1, 0, 1, 0)
        setOnClickListener { action() }
    }

    private fun renderKeys() {
        if (!::keyboard.isInitialized) return
        keyboard.removeAllViews()
        val rows = if (symbols) {
            listOf("1234567890", "@#\$%&-*+()", ".,?!:;'\"")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        rows.forEachIndexed { rowIndex, chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (rowIndex == 2 && !symbols) {
                row.addView(button(if (shifted) "⇧" else "↑") { shifted = !shifted; renderKeys() }, weight(1.25f, 48))
            }
            chars.forEach { ch ->
                val label = if (shifted && !symbols) ch.uppercaseChar().toString() else ch.toString()
                row.addView(button(label) {
                    currentInputConnection?.commitText(label, 1)
                    if (shifted && !symbols) { shifted = false; renderKeys() }
                }, weight(1f, 48))
            }
            if (rowIndex == 2 && !symbols) {
                val del = button("⌫") { deleteOneCharacter() }
                del.setOnLongClickListener { clearAllText(); true }
                row.addView(del, weight(1.25f, 48))
            }
            keyboard.addView(row)
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(button(if (symbols) "ABC" else "?123") { symbols = !symbols; renderKeys() }, weight(1.25f, 48))
        bottom.addView(button(",") { currentInputConnection?.commitText(",", 1) }, weight(0.7f, 48))
        bottom.addView(button("空格") { currentInputConnection?.commitText(" ", 1) }, weight(4f, 48))
        bottom.addView(button(".") { currentInputConnection?.commitText(".", 1) }, weight(0.7f, 48))
        bottom.addView(button("回车") {
            val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                ?: EditorInfo.IME_ACTION_NONE
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection?.performEditorAction(action)
            } else {
                currentInputConnection?.commitText("\n", 1)
            }
        }, weight(1.25f, 48))
        keyboard.addView(bottom)
    }

    private fun showTyperTools() {
        updateStatus("逐字输入：读取剪贴板后开始；新复制内容不会覆盖当前任务")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("读取") { loadClipboard() }, weight(1f, 44))
        row.addView(button("开始") { startTyping() }, weight(1f, 44))
        row.addView(button("暂停/继续") { togglePause() }, weight(1.3f, 44))
        row.addView(button("停止") { stopTyping() }, weight(1f, 44))
        keyboard.addView(row, 0)
    }

    private fun loadClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = cm.primaryClip
        val next = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        if (running) {
            paused = true
            handler.removeCallbacks(typeRunnable)
            updateStatus("检测到剪贴板变化：原任务已暂停，进度 \${index}/\${content.length} 保留；新内容未覆盖")
            return
        }
        content = next
        index = 0
        updateStatus(if (content.isEmpty()) "剪贴板为空" else "已读取 \${content.length} 个字符")
    }

    private val typeRunnable = Runnable { typeNext() }

    private fun startTyping() {
        if (content.isEmpty()) { updateStatus("请先读取剪贴板"); return }
        if (index >= content.length) index = 0
        running = true
        paused = false
        typeNext()
    }

    private fun togglePause() {
        if (!running) { updateStatus("当前没有正在输入的任务"); return }
        paused = !paused
        updateStatus(if (paused) "已暂停（进度 \${index}/\${content.length}）" else "继续输入中")
        if (paused) handler.removeCallbacks(typeRunnable) else typeNext()
    }

    private fun stopTyping() {
        running = false
        paused = false
        handler.removeCallbacks(typeRunnable)
        updateStatus("已停止，进度 \${index}/\${content.length}")
    }

    private fun deleteOneCharacter() {
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val last = before.last()
        val count = if (before.length >= 2 && Character.isLowSurrogate(last) &&
            Character.isHighSurrogate(before[before.length - 2])) 2 else 1
        connection.deleteSurroundingText(count, 0)
    }

    private fun clearAllText() {
        stopTyping()
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(100000, 0)?.length ?: 0
        val after = connection.getTextAfterCursor(100000, 0)?.length ?: 0
        connection.deleteSurroundingText(before, after)
        updateStatus("已清空当前输入框")
    }

    private fun typeNext() {
        if (!running || paused) return
        if (index >= content.length) {
            running = false
            updateStatus("逐字输入完成")
            return
        }
        val ch = content.substring(index, index + 1)
        currentInputConnection?.commitText(ch, 1)
        index++
        updateStatus("逐字输入中：\${index}/\${content.length}")
        val delay = Random.nextLong(90, 261) + if (ch in "，。！？；：、.!?;:\n") 350 else 0
        handler.postDelayed(typeRunnable, delay)
    }

    private fun updateStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    override fun onFinishInput() {
        stopTyping()
        super.onFinishInput()
    }
}
