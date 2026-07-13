package com.synx.unlocker

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.*
import java.util.*

/**
 * Synx VIP Unlocker + 简体中文翻译 - LSPosed Module
 *
 * 目标: com.synxapp.synx v2.27.0
 *
 * Hook 清单:
 *
 * 【VIP 破解 - RevenueCat 层】
 *   1. EntitlementInfo.isActive()           → true
 *   2. EntitlementInfo.getExpirationDate()  → 2099年
 *   3. EntitlementInfo.getWillRenew()       → true
 *   4. EntitlementInfos.getActive()         → 注入 pro 权益
 *   5. CustomerInfo.getActiveSubscriptions() → 注入订阅
 *   6. CustomerInfo.getLatestExpirationDate() → 2099年
 *   7. CustomerInfo.getAllPurchasedProductIds() → 注入 pro 产品
 *   8. CustomerInfo.getEntitlements()       → 确保非空
 *
 * 【VIP 破解 - SharedPreferences 缓存层】
 *   9. SharedPreferences.getBoolean(key)     → pro/subscription 相关键返回 true
 *  10. Editor.putBoolean(key, false)        → 拦截写入 pro=false
 *
 * 【VIP 破解 - Flutter MethodChannel 层】
 *  11. MethodChannel.Result.success()       → 拦截 RevenueCat 返回数据
 *
 * 【简体中文翻译】
 *  12. AssetManager.open("zh-Hant/common.toml") → 返回简体版本
 */
class SynxVipHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "SynxVipUnlocker"
        private const val TARGET = "com.synxapp.synx"

        private const val CLS_CUSTOMER_INFO     = "com.revenuecat.purchases.CustomerInfo"
        private const val CLS_ENTITLEMENT_INFO   = "com.revenuecat.purchases.EntitlementInfo"
        private const val CLS_ENTITLEMENT_INFOS  = "com.revenuecat.purchases.EntitlementInfos"

        @Volatile private var cachedFakeEnt: Any? = null
        @Volatile private var cachedCL: ClassLoader? = null

        // 需要拦截的 SharedPreferences 关键词
        private val PRO_KEYS = listOf(
            "pro", "subscription", "premium", "vip", "is_pro", "isPro",
            "has_access", "hasAccess", "is_premium", "isPremium",
            "is_subscribed", "isSubscribed", "is_active", "isActive",
            "is_unlocked", "isUnlocked", "plan", "membership",
            "unlock", "entitlement", "purchased"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET) return
        val cl = lpparam.classLoader

        XposedBridge.log("[$TAG] ============================================")
        XposedBridge.log("[$TAG] Synx loaded (pid=${android.os.Process.myPid()})")
        XposedBridge.log("[$TAG] Installing comprehensive hooks...")

        // ---- VIP: RevenueCat 层 ----
        hook_isActive(cl)
        hook_getExpirationDate(cl)
        hook_getWillRenew(cl)
        hook_EntitlementInfos_getActive(cl)
        hook_getActiveSubscriptions(cl)
        hook_getLatestExpirationDate(cl)
        hook_getAllPurchasedProductIds(cl)
        hook_getEntitlements(cl)

        // ---- VIP: SharedPreferences 缓存层 ----
        hook_SharedPreferences_getBoolean(cl)
        hook_SharedPreferences_edit_putBoolean(cl)

        // ---- VIP: MethodChannel 拦截 ----
        hook_MethodChannel_Result(cl)

        // ---- 简体中文翻译 ----
        hook_AssetManager_forTranslation(cl)

        XposedBridge.log("[$TAG] All hooks installed!")
        XposedBridge.log("[$TAG] ============================================")
    }

    // ===============================================================
    //  Hook 1: EntitlementInfo.isActive() → true
    // ===============================================================
    private fun hook_isActive(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_ENTITLEMENT_INFO, cl, "isActive",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (p.result != true) p.result = true
                    }
                })
            log("✓", "EntitlementInfo.isActive()")
        } catch (t: Throwable) { log("✗", "isActive: $t") }
    }

    // ===============================================================
    //  Hook 2: EntitlementInfo.getExpirationDate() → 2099
    // ===============================================================
    private fun hook_getExpirationDate(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_ENTITLEMENT_INFO, cl, "getExpirationDate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (p.result == null) p.result = Date(4102358399000L)
                    }
                })
            log("✓", "EntitlementInfo.getExpirationDate()")
        } catch (t: Throwable) { log("✗", "getExpirationDate: $t") }
    }

    // ===============================================================
    //  Hook 3: EntitlementInfo.getWillRenew() → true
    // ===============================================================
    private fun hook_getWillRenew(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_ENTITLEMENT_INFO, cl, "getWillRenew",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (p.result != true) p.result = true
                    }
                })
            log("✓", "EntitlementInfo.getWillRenew()")
        } catch (t: Throwable) { log("✗", "getWillRenew: $t") }
    }

    // ===============================================================
    //  Hook 4: EntitlementInfos.getActive() → 注入 pro
    // ===============================================================
    private fun hook_EntitlementInfos_getActive(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_ENTITLEMENT_INFOS, cl, "getActive",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val m = p.result as? Map<*, *>
                        if (m == null || m.isEmpty()) {
                            val fake = getOrCreateFakeEnt(cl)
                            if (fake != null) {
                                p.result = linkedMapOf("pro" to fake)
                            }
                        }
                    }
                })
            log("✓", "EntitlementInfos.getActive()")
        } catch (t: Throwable) { log("✗", "getActive: $t") }
    }

    // ===============================================================
    //  Hook 5: CustomerInfo.getActiveSubscriptions() → 非空
    // ===============================================================
    private fun hook_getActiveSubscriptions(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_CUSTOMER_INFO, cl, "getActiveSubscriptions",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val s = p.result as? Set<*>
                        if (s == null || s.isEmpty()) {
                            p.result = setOf("synx_pro_monthly", "synx_pro_yearly")
                        }
                    }
                })
            log("✓", "CustomerInfo.getActiveSubscriptions()")
        } catch (t: Throwable) { log("✗", "getActiveSubscriptions: $t") }
    }

    // ===============================================================
    //  Hook 6: CustomerInfo.getLatestExpirationDate() → 2099
    // ===============================================================
    private fun hook_getLatestExpirationDate(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_CUSTOMER_INFO, cl, "getLatestExpirationDate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (p.result == null) p.result = Date(4102358399000L)
                    }
                })
            log("✓", "CustomerInfo.getLatestExpirationDate()")
        } catch (t: Throwable) { log("✗", "getLatestExpirationDate: $t") }
    }

    // ===============================================================
    //  Hook 7: CustomerInfo.getAllPurchasedProductIds() → 含 pro
    // ===============================================================
    private fun hook_getAllPurchasedProductIds(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_CUSTOMER_INFO, cl, "getAllPurchasedProductIds",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val s = p.result as? Set<*>
                        if (s == null || s.isEmpty() || !s.contains("synx_pro_lifetime")) {
                            val m = HashSet(s ?: emptySet())
                            m.add("synx_pro_lifetime")
                            m.add("synx_pro_monthly")
                            p.result = m
                        }
                    }
                })
            log("✓", "CustomerInfo.getAllPurchasedProductIds()")
        } catch (t: Throwable) { log("✗", "getAllPurchasedProductIds: $t") }
    }

    // ===============================================================
    //  Hook 8: CustomerInfo.getEntitlements() → 确保非空
    // ===============================================================
    private fun hook_getEntitlements(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(CLS_CUSTOMER_INFO, cl, "getEntitlements",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        // 确保返回的 EntitlementInfos 不为 null
                        // getActive() 已经被 Hook 4 覆盖
                    }
                })
            log("✓", "CustomerInfo.getEntitlements()")
        } catch (t: Throwable) { log("✗", "getEntitlements: $t") }
    }

    // ===============================================================
    //  Hook 9: SharedPreferences.getBoolean() → pro 键返回 true
    // ===============================================================
    private fun hook_SharedPreferences_getBoolean(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl", cl,
                "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        val result = p.result as? Boolean ?: return
                        if (!result && isProKey(key)) {
                            p.result = true
                            XposedBridge.log("[$TAG] SP.getBoolean('$key'): false → true")
                        }
                    }
                })
            log("✓", "SharedPreferencesImpl.getBoolean()")
        } catch (t: Throwable) { log("✗", "SP.getBoolean: $t") }
    }

    // ===============================================================
    //  Hook 10: Editor.putBoolean() → 拦截 pro=false 写入
    // ===============================================================
    private fun hook_SharedPreferences_edit_putBoolean(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl\$EditorImpl", cl,
                "putBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        val value = p.args[1] as? Boolean ?: return
                        if (!value && isProKey(key)) {
                            p.args[1] = true
                            XposedBridge.log("[$TAG] SP.putBoolean('$key'): false → true")
                        }
                    }
                })
            log("✓", "EditorImpl.putBoolean()")
        } catch (t: Throwable) { log("✗", "putBoolean: $t") }
    }

    // ===============================================================
    //  Hook 11: MethodChannel.Result.success() → 拦截 RevenueCat 数据
    // ===============================================================
    private fun hook_MethodChannel_Result(cl: ClassLoader) {
        try {
            // Hook MethodChannel.Result.success(Object)
            // 当返回数据包含 entitlements/customerInfo 时修改
            val resultClass = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler\$1",
                cl, "success", Any::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.args[0] ?: return
                            // 如果返回的是 Map 且包含 entitlements 相关数据
                            if (result is Map<*, *>) {
                                val modified = modifyMethodChannelResult(result)
                                if (modified != null) {
                                    p.args[0] = modified
                                }
                            }
                        } catch (e: Exception) {
                            // 静默失败，不影响正常流程
                        }
                    }
                })
            log("✓", "MethodChannel.Result.success()")
        } catch (t: Throwable) { log("✗", "MethodChannel: $t") }
    }

    /**
     * 修改 MethodChannel 返回的数据
     * 如果包含 customerInfo/entitlements 相关数据，注入 pro 状态
     */
    @Suppress("UNCHECKED_CAST")
    private fun modifyMethodChannelResult(result: Map<*, *>): Map<*, *>? {
        var modified = false
        val newMap = LinkedHashMap<String, Any?>(result)

        // 检查是否是 customerInfo 响应
        if (newMap.containsKey("entitlements") || newMap.containsKey("activeSubscriptions")) {
            // 注入 activeSubscriptions
            val subs = newMap["activeSubscriptions"]
            if (subs == null || (subs is List<*> && subs.isEmpty())) {
                newMap["activeSubscriptions"] = listOf("synx_pro_monthly", "synx_pro_yearly")
                modified = true
            }

            // 注入 entitlements
            val entitlements = newMap["entitlements"]
            if (entitlements is Map<*, *>) {
                val newEnt = LinkedHashMap<String, Any?>(entitlements)
                val active = newEnt["active"]
                if (active == null || (active is Map<*, *> && active.isEmpty())) {
                    val fakeActive = LinkedHashMap<String, Any>()
                    fakeActive["pro"] = createFakeEntitlementMap()
                    newEnt["active"] = fakeActive
                    newEnt["all"] = fakeActive
                    modified = true
                }
                newMap["entitlements"] = newEnt
            }

            // 注入其他 pro 标志
            if (!newMap.containsKey("originalAppUserId")) {
                newMap["originalAppUserId"] = "synx_pro_user"
                modified = true
            }
        }

        return if (modified) newMap else null
    }

    /**
     * 创建假 entitlement Map (用于 MethodChannel 通信)
     */
    private fun createFakeEntitlementMap(): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["identifier"] = "pro"
        m["isActive"] = true
        m["willRenew"] = true
        m["periodType"] = "NORMAL"
        m["productIdentifier"] = "synx_pro_lifetime"
        m["store"] = "PLAY_STORE"
        m["isSandbox"] = false
        m["ownershipType"] = "PURCHASED"
        m["expirationDate"] = "2099-12-31T23:59:59Z"
        m["latestPurchaseDate"] = "2025-01-01T00:00:00Z"
        m["originalPurchaseDate"] = "2025-01-01T00:00:00Z"
        return m
    }

    // ===============================================================
    //  Hook 12: AssetManager → 繁体 TOML 替换为简体
    // ===============================================================
    private fun hook_AssetManager_forTranslation(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.res.AssetManager", cl,
                "open", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val path = p.args[0] as? String ?: return
                        // 拦截 zh-Hant TOML 加载
                        if (path.contains("zh-Hant") && path.contains("common.toml")) {
                            XposedBridge.log("[$TAG] Intercepted zh-Hant TOML: $path")
                            try {
                                val original = p.result as? InputStream
                                original?.close()
                                // 读取模块自带的简体 TOML
                                val moduleApk = findModuleApkPath(cl)
                                if (moduleApk != null) {
                                    val moduleRes = android.content.res.Resources.getSystem()
                                    // 使用 ZipFile 读取模块 APK 中的 raw 资源
                                    val zf = java.util.zip.ZipFile(moduleApk)
                                    val entry = zf.getEntry("res/raw/zh_cn_common.toml")
                                        ?: zf.getEntry("resources.arsc")
                                    if (entry != null && entry.name == "res/raw/zh_cn_common.toml") {
                                        p.result = zf.getInputStream(entry)
                                        XposedBridge.log("[$TAG] ✓ Replaced with zh-CN TOML")
                                    }
                                    zf.close()
                                }
                            } catch (e: Exception) {
                                XposedBridge.log("[$TAG] Translation replace failed: ${e.message}")
                            }
                        }
                    }
                })
            log("✓", "AssetManager (translation)")
        } catch (t: Throwable) { log("✗", "AssetManager: $t") }
    }

    /**
     * 查找模块 APK 路径
     */
    private fun findModuleApkPath(cl: ClassLoader): String? {
        return try {
            // 通过模块类的 ClassLoader 获取 APK 路径
            val classLoader = SynxVipHook::class.java.classLoader
            // LSPosed 模块的 APK 路径
            val apkPathField = classLoader.javaClass.superclass.getDeclaredField("path")
            apkPathField.isAccessible = true
            apkPathField.get(classLoader) as? String
        } catch (e: Exception) {
            // 回退：从 XposedBridge 获取模块路径
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Application", cl,
                    "onCreate",
                    object : XC_MethodHook() {})
                // 尝试获取 APK 路径
                val dataDir = android.os.Environment.getDataDirectory().absolutePath
                "$dataDir/app/SynxVipUnlocker*/base.apk"
            } catch (e2: Exception) {
                null
            }
        }
    }

    // ===============================================================
    //  工具方法
    // ===============================================================

    private fun isProKey(key: String): Boolean {
        val lower = key.lowercase()
        return PRO_KEYS.any { lower.contains(it.lowercase()) }
    }

    @Synchronized
    private fun getOrCreateFakeEnt(cl: ClassLoader): Any? {
        if (cachedFakeEnt != null && cachedCL === cl) return cachedFakeEnt
        return try {
            val eiCls = cl.loadClass(CLS_ENTITLEMENT_INFO)
            val ptCls = cl.loadClass("com.revenuecat.purchases.PeriodType")
            val stCls = cl.loadClass("com.revenuecat.purchases.Store")
            val otCls = cl.loadClass("com.revenuecat.purchases.OwnershipType")
            val joCls = cl.loadClass("org.json.JSONObject")

            val ctor = eiCls.getDeclaredConstructor(
                String::class.java, Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!, ptCls,
                Date::class.java, Date::class.java, Date::class.java,
                stCls, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Date::class.java, Date::class.java,
                otCls, joCls
            )
            ctor.isAccessible = true

            val now = Date()
            val future = Date(4102358399000L)
            val ent = ctor.newInstance(
                "pro", true, true,
                enumVal(ptCls, "NORMAL"),
                now, now, future,
                enumVal(stCls, "PLAY_STORE"),
                "synx_pro_lifetime", null, false,
                null, null,
                enumVal(otCls, "PURCHASED"),
                joCls.getConstructor().newInstance()
            )
            cachedFakeEnt = ent
            cachedCL = cl
            XposedBridge.log("[$TAG] Fake EntitlementInfo('pro') created")
            ent
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Fake ent failed: $t")
            null
        }
    }

    private fun enumVal(cls: Class<*>, name: String): Any? {
        return cls.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
    }

    private fun log(status: String, msg: String) {
        XposedBridge.log("[$TAG] [$status] $msg")
    }
}
