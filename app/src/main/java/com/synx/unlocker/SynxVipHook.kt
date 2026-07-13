package com.synx.unlocker

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.*
import java.util.*

class SynxVipHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "SynxVipUnlocker"
        private const val TARGET = "com.synxapp.synx"
        private const val CLS_PLUGIN = "com.revenuecat.purchases_flutter.PurchasesFlutterPlugin"
        private const val CLS_CUSTOMER_INFO = "com.revenuecat.purchases.CustomerInfo"
        private const val CLS_ENT_INFO = "com.revenuecat.purchases.EntitlementInfo"
        private const val CLS_ENT_INFOS = "com.revenuecat.purchases.EntitlementInfos"

        // 非空哨兵对象 — 用于跳过 void 方法的原始执行
        // Xposed 检查 param.result != null 来决定是否跳过原始方法
        // 对 void 方法，初始 result 为 null，所以必须设非空值才能跳过
        private val SENTINEL = Object()

        @Volatile private var cachedFakeEnt: Any? = null
        @Volatile private var cachedCL: ClassLoader? = null
        @Volatile private var appContext: android.content.Context? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET) return
        val cl = lpparam.classLoader

        XposedBridge.log("[$TAG] ============================================")
        XposedBridge.log("[$TAG] Synx loaded (pid=${android.os.Process.myPid()})")

        // 捕获 Application Context（用于翻译资源读取）
        hookAppContext(cl)

        // 核心策略：直接拦截 PurchasesFlutterPlugin.onMethodCall
        hookPluginOnMethodCall(cl)

        // 备用：RevenueCat 对象层 Hook
        hookRevenueCatObjects(cl)

        // 备用：SharedPreferences 缓存拦截
        hookSharedPreferences(cl)

        // 翻译：繁体→简体
        hookTranslation(cl)

        XposedBridge.log("[$TAG] All hooks installed!")
        XposedBridge.log("[$TAG] ============================================")
    }

    // ===============================================================
    //  核心 Hook: 拦截 PurchasesFlutterPlugin.onMethodCall
    //  直接在 Flutter 方法通道层伪造 pro 状态
    // ===============================================================
    private fun hookPluginOnMethodCall(cl: ClassLoader) {
        try {
            // PurchasesFlutterPlugin.onMethodCall(MethodCall call, Result result)
            val methodCallCls = cl.loadClass("io.flutter.plugin.common.MethodCall")
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")

            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl,
                "onMethodCall", methodCallCls, resultCls,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val call = p.args[0]
                            val result = p.args[1]
                            val method = XposedHelpers.callMethod(call, "method") as? String ?: return

                            XposedBridge.log("[$TAG] RC method call: $method")

                            when (method) {
                                "getCustomerInfo", "getPurchaserInfo" -> {
                                    val fakeData = buildFakeCustomerInfoMap()
                                    XposedHelpers.callMethod(result, "success", fakeData)
                                    p.result = SENTINEL  // 非空哨兵值，阻止原始 void 方法执行
                                    XposedBridge.log("[$TAG] ✓ Faked getCustomerInfo → pro active")
                                }
                                "getOfferings" -> {
                                    val fakeOfferings = buildFakeOfferingsMap()
                                    XposedHelpers.callMethod(result, "success", fakeOfferings)
                                    p.result = SENTINEL
                                    XposedBridge.log("[$TAG] ✓ Faked getOfferings")
                                }
                                "checkTrialOrIntroductoryPriceEligibility" -> {
                                    XposedHelpers.callMethod(result, "success", emptyMap<String, Any>())
                                    p.result = SENTINEL
                                    XposedBridge.log("[$TAG] ✓ Faked trial eligibility")
                                }
                                "getDeviceAppInfo", "getAppUserID", "isAnonymous" -> {
                                    // 让这些方法正常执行
                                }
                                "syncPurchases", "restorePurchases" -> {
                                    // 让正常执行，但确保结果是 success
                                }
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("[$TAG] onMethodCall hook error: ${e.message}")
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] PurchasesFlutterPlugin.onMethodCall()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] PurchasesFlutterPlugin: $t")
            // 备用方案：尝试 hook CommonKt
            hookCommonKt(cl)
        }
    }

    /**
     * 构造伪造的 CustomerInfo Map
     * 这是 RevenueCat Flutter 插件返回给 Dart 的数据结构
     */
    private fun buildFakeCustomerInfoMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()

        // entitlements
        val ent = LinkedHashMap<String, Any?>()
        val active = LinkedHashMap<String, Any?>()
        val all = LinkedHashMap<String, Any?>()

        val pro = LinkedHashMap<String, Any?>()
        pro["identifier"] = "pro"
        pro["isActive"] = true
        pro["willRenew"] = true
        pro["periodType"] = "NORMAL"
        pro["productIdentifier"] = "synx_pro_lifetime"
        pro["store"] = "PLAY_STORE"
        pro["isSandbox"] = false
        pro["ownershipType"] = "PURCHASED"
        pro["expirationDate"] = null  // lifetime = null
        pro["latestPurchaseDate"] = "2025-01-01T00:00:00Z"
        pro["originalPurchaseDate"] = "2025-01-01T00:00:00Z"
        pro["unsubscribeDetectedAt"] = null
        pro["billingIssueDetectedAt"] = null
        pro["verification"] = "NOT_REQUESTED"

        active["pro"] = pro
        all["pro"] = pro
        ent["active"] = active
        ent["all"] = all
        ent["verification"] = "NOT_REQUESTED"
        m["entitlements"] = ent

        // activeSubscriptions
        m["activeSubscriptions"] = listOf("synx_pro_lifetime")

        // allPurchasedProductIds
        m["allPurchasedProductIds"] = listOf("synx_pro_lifetime", "synx_pro_monthly", "synx_pro_yearly")

        // 其他字段
        m["originalAppUserId"] = "synx_pro_user"
        m["managementURL"] = null
        m["schemaVersion"] = 1
        m["requestDate"] = "2025-01-01T00:00:00Z"
        m["originalPurchaseDate"] = "2025-01-01T00:00:00Z"
        m["firstSeen"] = "2025-01-01T00:00:00Z"
        m["latestExpirationDate"] = null

        return m
    }

    /**
     * 构造伪造的 Offerings Map
     */
    private fun buildFakeOfferingsMap(): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        // 空 offerings — 不显示购买页面
        m["currentOfferingIdentifier"] = "default"
        m["offerings"] = emptyList<Any>()
        m["allOfferings"] = emptyMap<String, Any>()
        return m
    }

    // ===============================================================
    //  备用: RevenueCat 对象层 Hook
    // ===============================================================
    private fun hookRevenueCatObjects(cl: ClassLoader) {
        // EntitlementInfo.isActive() → true
        tryHook(cl, CLS_ENT_INFO, "isActive") { p -> if (p.result != true) p.result = true }
        // EntitlementInfo.getWillRenew() → true
        tryHook(cl, CLS_ENT_INFO, "getWillRenew") { p -> if (p.result != true) p.result = true }
        // EntitlementInfo.getExpirationDate() → 2099
        tryHook(cl, CLS_ENT_INFO, "getExpirationDate") { p -> if (p.result == null) p.result = Date(4102358399000L) }
        // CustomerInfo.getLatestExpirationDate() → 2099
        tryHook(cl, CLS_CUSTOMER_INFO, "getLatestExpirationDate") { p -> if (p.result == null) p.result = Date(4102358399000L) }

        // EntitlementInfos.getActive() → 注入 pro
        tryHookMap(cl, CLS_ENT_INFOS, "getActive") { m ->
            if (m.isNullOrEmpty()) {
                val fake = getOrCreateFakeEnt(cl)
                if (fake != null) linkedMapOf("pro" to fake) else null
            } else null
        }

        // CustomerInfo.getActiveSubscriptions() → 非空
        tryHookSet(cl, CLS_CUSTOMER_INFO, "getActiveSubscriptions") { s ->
            if (s.isNullOrEmpty()) setOf("synx_pro_lifetime") else null
        }

        // CustomerInfo.getAllPurchasedProductIds() → 含 pro
        tryHookSet(cl, CLS_CUSTOMER_INFO, "getAllPurchasedProductIds") { s ->
            if (s == null || !s.contains("synx_pro_lifetime")) {
                HashSet(s ?: emptySet()).apply { add("synx_pro_lifetime") }
            } else null
        }
    }

    // ===============================================================
    //  备用: CommonKt Hook (如果 Plugin hook 失败)
    // ===============================================================
    private fun hookCommonKt(cl: ClassLoader) {
        try {
            // Hook CommonKt.getCustomerInfo 回调
            val cls = cl.loadClass("com.revenuecat.purchases.hybridcommon.CommonKt")
            XposedBridge.log("[$TAG] [✓] Found CommonKt class")
        } catch (_: Throwable) {
            XposedBridge.log("[$TAG] CommonKt not found, relying on plugin hook only")
        }
    }

    // ===============================================================
    //  SharedPreferences 拦截
    // ===============================================================
    private fun hookSharedPreferences(cl: ClassLoader) {
        val proKeys = listOf("pro", "subscription", "premium", "vip", "is_pro", "isPro",
            "has_access", "hasAccess", "is_premium", "isPremium",
            "is_subscribed", "isSubscribed", "is_active", "isActive",
            "is_unlocked", "isUnlocked", "plan", "membership", "unlock", "entitlement", "purchased")

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl", cl,
                "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.result != true && proKeys.any { key.lowercase().contains(it) }) {
                            p.result = true
                            XposedBridge.log("[$TAG] SP.getBoolean('$key') → true")
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] SharedPreferencesImpl.getBoolean()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] SP.getBoolean: $t")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl\$EditorImpl", cl,
                "putBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.args[1] != true && proKeys.any { key.lowercase().contains(it) }) {
                            p.args[1] = true
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] EditorImpl.putBoolean()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] putBoolean: $t")
        }
    }

    // ===============================================================
    //  捕获 Application Context
    // ===============================================================
    private fun hookAppContext(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", cl,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        appContext = p.thisObject as? android.content.Context
                        XposedBridge.log("[$TAG] App context captured")
                    }
                })
            XposedBridge.log("[$TAG] [✓] Application.onCreate()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] Application.onCreate: $t")
        }
    }

    // ===============================================================
    //  翻译: 繁体 → 简体
    //  通过模块 Context 读取 res/raw 资源
    // ===============================================================
    private fun hookTranslation(cl: ClassLoader) {
        hookAssetOpen(cl, "open", false)
        hookAssetOpen(cl, "open", true)
    }

    private fun hookAssetOpen(cl: ClassLoader, methodName: String, withIntParam: Boolean) {
        try {
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val path = p.args[0] as? String ?: return
                    if (!path.contains("zh-Hant") || !path.contains("common.toml")) return

                    XposedBridge.log("[$TAG] Intercepted zh-Hant TOML ($methodName): $path")
                    val ctx = appContext ?: run {
                        XposedBridge.log("[$TAG] No app context yet")
                        return
                    }
                    try {
                        // 通过模块包名创建 Context，读取模块的 res/raw 资源
                        val moduleCtx = ctx.createPackageContext("com.synx.unlocker",
                            android.content.Context.CONTEXT_IGNORE_SECURITY)
                        val res = moduleCtx.resources
                        val resId = res.getIdentifier("zh_cn_common", "raw", "com.synx.unlocker")
                        if (resId != 0) {
                            (p.result as? InputStream)?.close()
                            val bytes = res.openRawResource(resId).readBytes()
                            p.result = ByteArrayInputStream(bytes)
                            XposedBridge.log("[$TAG] [✓] Replaced zh-Hant → zh-CN (${bytes.size} bytes)")
                        } else {
                            XposedBridge.log("[$TAG] [✗] raw/zh_cn_common not found in module")
                        }
                    } catch (e: Exception) {
                        XposedBridge.log("[$TAG] Translation failed: ${e.message}")
                    }
                }
            }

            if (withIntParam) {
                XposedHelpers.findAndHookMethod(
                    "android.content.res.AssetManager", cl,
                    methodName, String::class.java, Int::class.javaPrimitiveType, hook)
            } else {
                XposedHelpers.findAndHookMethod(
                    "android.content.res.AssetManager", cl,
                    methodName, String::class.java, hook)
            }
            XposedBridge.log("[$TAG] [✓] AssetManager.$methodName(${if (withIntParam) "String,int" else "String"})")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] AssetManager.$methodName: $t")
        }
    }

    // ===============================================================
    //  通用 Hook 工具
    // ===============================================================
    private fun tryHook(cl: ClassLoader, cls: String, method: String, after: (XC_MethodHook.MethodHookParam) -> Unit) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) { after(p) }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    private fun tryHookMap(cl: ClassLoader, cls: String, method: String, after: (Map<*, *>?) -> Map<*, *>?) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val r = after(p.result as? Map<*, *>)
                    if (r != null) p.result = r
                }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    private fun tryHookSet(cl: ClassLoader, cls: String, method: String, after: (Set<*>?) -> Set<*>?) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val r = after(p.result as? Set<*>)
                    if (r != null) p.result = r
                }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    // ===============================================================
    //  构造伪造的 EntitlementInfo 对象（备用）
    // ===============================================================
    @Synchronized
    private fun getOrCreateFakeEnt(cl: ClassLoader): Any? {
        if (cachedFakeEnt != null && cachedCL === cl) return cachedFakeEnt
        return try {
            val ei = cl.loadClass(CLS_ENT_INFO)
            val pt = cl.loadClass("com.revenuecat.purchases.PeriodType")
            val st = cl.loadClass("com.revenuecat.purchases.Store")
            val ot = cl.loadClass("com.revenuecat.purchases.OwnershipType")
            val jo = cl.loadClass("org.json.JSONObject")

            val ctor = ei.getDeclaredConstructor(
                String::class.java, Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!, pt,
                Date::class.java, Date::class.java, Date::class.java,
                st, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Date::class.java, Date::class.java, ot, jo)
            ctor.isAccessible = true

            val now = Date()
            val ent = ctor.newInstance(
                "pro", true, true, enumVal(pt, "NORMAL"),
                now, now, Date(4102358399000L), enumVal(st, "PLAY_STORE"),
                "synx_pro_lifetime", null, false, null, null,
                enumVal(ot, "PURCHASED"), jo.getConstructor().newInstance())
            cachedFakeEnt = ent; cachedCL = cl
            ent
        } catch (t: Throwable) { null }
    }

    private fun enumVal(cls: Class<*>, name: String): Any? =
        cls.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
}
