package com.synx.unlocker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

/**
 * LSPosed 模块管理界面
 * 在模块列表中显示时的小图标入口
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = """
                ✅ Synx VIP Unlocker
                
                模块已激活！
                
                作用目标：com.synxapp.synx
                版本支持：2.27.0
                
                请在 LSPosed 管理器中
                勾选 Synx 应用启用此模块。
                
                Hook 列表：
                1. EntitlementInfo.isActive() → true
                2. EntitlementInfo.getExpirationDate() → 2099年
                3. CustomerInfo.getEntitlements() → 注入 Pro
                4. CustomerInfo.getActiveSubscriptions() → 注入订阅
                
                重启 Synx 应用后生效。
            """.trimIndent()
            setTextColor(Color.BLACK)
            textSize = 14f
            setPadding(48, 48, 48, 48)
            gravity = Gravity.START
        }
        setContentView(textView)
    }
}
