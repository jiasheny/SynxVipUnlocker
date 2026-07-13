package com.synx.unlocker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = """
                ✅ Synx VIP Unlocker

                模块已激活！

                作用目标：com.synxapp.synx
                版本支持：2.27.0

                功能：
                1. VIP/Pro 全功能解锁
                2. 繁体中文 → 简体中文翻译

                Hook 列表：
                • RevenueCat: isActive/getActive/...
                • SharedPreferences: 缓存拦截
                • MethodChannel: 数据拦截
                • AssetManager: 繁体→简体

                请在 LSPosed 中勾选 Synx 启用。
            """.trimIndent()
            setTextColor(Color.BLACK)
            textSize = 14f
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }
}
