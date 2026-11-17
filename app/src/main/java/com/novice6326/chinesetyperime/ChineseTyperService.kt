package com.novice6326.chinesetyperime

import android.inputmethodservice.InputMethodService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

class ChineseTyperService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var content = ""
    private var index = 0
    private var running = false
    private var paused = false
    private lateinit var status: TextView

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10); setBackgroundColor(Color.rgb(245,245,245)) }
        status = TextView(this).apply { text = "先读取剪贴板中的文本"; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(4,4,4,10) }
        root.addView(status)
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("读取剪贴板") { loadClipboard() }, weight())
        row1.addView(button("开始") { startTyping() }, weight())
        root.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("暂停 / 继续") { togglePause() }, weight())
        row2.addView(button("停止") { stopTyping() }, weight())
        root.addView(row2)
        return root
    }

    private fun weight() = LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(4,4,4,4) }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun loadClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = cm.primaryClip
        content = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        index = 0
        updateStatus(if (content.isEmpty()) "剪贴板为空" else "已读取 1 个字符")
    }

    private fun startTyping() {
        if (content.isEmpty()) { updateStatus("请先读取剪贴板"); return }
        if (index >= content.length) index = 0
        running = true; paused = false
        typeNext()
    }

    private fun togglePause() {
        if (!running) { updateStatus("当前没有正在输入的任务"); return }
        paused = !paused
        updateStatus(if (paused) "已暂停（可继续）" else "继续输入中…")
        if (!paused) typeNext()
    }

    private fun stopTyping() { running = false; paused = false; handler.removeCallbacksAndMessages(null); updateStatus("已停止，进度 $index/1") }

    private fun typeNext() {
        if (!running || paused) return
        if (index >= content.length) { running = false; updateStatus("输入完成"); return }
        val ch = content.substring(index, index + 1)
        currentInputConnection?.commitText(ch, 1)
        index++
        updateStatus("输入中：$index/1")
        val delay = Random.nextLong(90, 261) + if (ch in "，。！？；：、.!?;:\\n") 350 else 0
        handler.postDelayed({ typeNext() }, delay)
    }

    private fun updateStatus(message: String) { if (::status.isInitialized) status.text = message }
    override fun onFinishInput() { stopTyping(); super.onFinishInput() }
}
