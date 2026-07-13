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

        @Volatile private var cachedFakeEnt: Any? = null
        @Volatile private var cachedCL: ClassLoader? = null
        @Volatile private var appContext: android.content.Context? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET) return
        val cl = lpparam.classLoader

        XposedBridge.log("[$TAG] ============================================")
        XposedBridge.log("[$TAG] Synx loaded (pid=${android.os.Process.myPid()})")

        hookAppContext(cl)

        // 核心: 直接替换 PurchasesFlutterPlugin 的独立方法
        // 这些方法经 androguard 确认存在
        hookSetupPurchases(cl)      // 让 RevenueCat 初始化"成功"
        hookIsConfigured(cl)         // 返回 true
        hookGetCustomerInfo(cl)     // 返回伪造 pro 数据
        hookGetOfferings(cl)        // 返回空 offerings
        hookCheckTrial(cl)          // 返回符合试用条件
        hookGetAppUserID(cl)        // 返回有效用户 ID
        hookIsAnonymous(cl)         // 返回 false

        // 备用: RevenueCat 对象层
        hookRevenueCatObjects(cl)

        // 备用: SharedPreferences
        hookSharedPreferences(cl)

        // 翻译
        hookTranslation(cl)

        XposedBridge.log("[$TAG] All hooks installed!")
        XposedBridge.log("[$TAG] ============================================")
    }

    // ===============================================================
    //  核心: 替换 getCustomerInfo(Result) → 返回伪造 pro 数据
    // ===============================================================
    private fun hookGetCustomerInfo(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "getCustomerInfo", resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[0]
                        XposedHelpers.callMethod(result, "success", buildFakeCustomerInfoMap())
                        XposedBridge.log("[$TAG] ✓ getCustomerInfo → pro active")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.getCustomerInfo()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] getCustomerInfo: $t")
        }
    }

    // ===============================================================
    //  核心: 替换 getOfferings(Result) → 返回空 offerings
    // ===============================================================
    private fun hookGetOfferings(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "getOfferings", resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[0]
                        XposedHelpers.callMethod(result, "success", buildFakeOfferingsMap())
                        XposedBridge.log("[$TAG] ✓ getOfferings → empty")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.getOfferings()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] getOfferings: $t")
        }
    }

    // ===============================================================
    //  核心: 替换 checkTrialOrIntroductoryPriceEligibility → 符合条件
    // ===============================================================
    private fun hookCheckTrial(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl,
                "checkTrialOrIntroductoryPriceEligibility",
                ArrayList::class.java, resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[1]
                        XposedHelpers.callMethod(result, "success", emptyMap<String, Any>())
                        XposedBridge.log("[$TAG] ✓ checkTrial → eligible")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.checkTrial()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] checkTrial: $t")
        }
    }

    // ===============================================================
    //  setupPurchases → 让初始化成功
    //  签名: setupPurchases(String, String, String, Boolean, Boolean,
    //                       String, Boolean, Boolean, Boolean, String, Result)
    // ===============================================================
    private fun hookSetupPurchases(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            val boolObj = java.lang.Boolean::class.java
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "setupPurchases",
                String::class.java, String::class.java, String::class.java,
                boolObj, boolObj, String::class.java,
                boolObj, boolObj, boolObj, String::class.java,
                resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[10]
                        XposedHelpers.callMethod(result, "success", null)
                        XposedBridge.log("[$TAG] ✓ setupPurchases → success (faked)")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.setupPurchases()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] setupPurchases: $t")
        }
    }

    // ===============================================================
    //  isConfigured → 返回 true
    // ===============================================================
    private fun hookIsConfigured(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "isConfigured", resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[0]
                        XposedHelpers.callMethod(result, "success", true)
                        XposedBridge.log("[$TAG] ✓ isConfigured → true")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.isConfigured()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] isConfigured: $t")
        }
    }

    // ===============================================================
    //  getAppUserID → 返回有效用户 ID
    // ===============================================================
    private fun hookGetAppUserID(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "getAppUserID", resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[0]
                        XposedHelpers.callMethod(result, "success", "synx_pro_user")
                        XposedBridge.log("[$TAG] ✓ getAppUserID → synx_pro_user")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.getAppUserID()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] getAppUserID: $t")
        }
    }

    // ===============================================================
    //  isAnonymous → 返回 false
    // ===============================================================
    private fun hookIsAnonymous(cl: ClassLoader) {
        try {
            val resultCls = cl.loadClass("io.flutter.plugin.common.MethodChannel\$Result")
            XposedHelpers.findAndHookMethod(
                CLS_PLUGIN, cl, "isAnonymous", resultCls,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        val result = param.args[0]
                        XposedHelpers.callMethod(result, "success", false)
                        XposedBridge.log("[$TAG] ✓ isAnonymous → false")
                        return null
                    }
                })
            XposedBridge.log("[$TAG] [✓] Plugin.isAnonymous()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] isAnonymous: $t")
        }
    }

    // ===============================================================
    //  伪造数据构造
    // ===============================================================
    private fun buildFakeCustomerInfoMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
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
        pro["expirationDate"] = null
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
        m["activeSubscriptions"] = listOf("synx_pro_lifetime")
        m["allPurchasedProductIds"] = listOf("synx_pro_lifetime", "synx_pro_monthly", "synx_pro_yearly")
        m["originalAppUserId"] = "synx_pro_user"
        m["managementURL"] = null
        m["schemaVersion"] = 1
        m["requestDate"] = "2025-01-01T00:00:00Z"
        m["originalPurchaseDate"] = "2025-01-01T00:00:00Z"
        m["firstSeen"] = "2025-01-01T00:00:00Z"
        m["latestExpirationDate"] = null
        return m
    }

    private fun buildFakeOfferingsMap(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["currentOfferingIdentifier"] = null
        m["offerings"] = emptyList<Any?>()
        m["allOfferings"] = emptyMap<String, Any?>()
        return m
    }

    // ===============================================================
    //  备用: RevenueCat 对象层
    // ===============================================================
    private fun hookRevenueCatObjects(cl: ClassLoader) {
        tryHook(cl, CLS_ENT_INFO, "isActive") { p -> if (p.result != true) p.result = true }
        tryHook(cl, CLS_ENT_INFO, "getWillRenew") { p -> if (p.result != true) p.result = true }
        tryHook(cl, CLS_ENT_INFO, "getExpirationDate") { p -> if (p.result == null) p.result = Date(4102358399000L) }
        tryHook(cl, CLS_CUSTOMER_INFO, "getLatestExpirationDate") { p -> if (p.result == null) p.result = Date(4102358399000L) }

        tryHookMap(cl, CLS_ENT_INFOS, "getActive") { m ->
            if (m.isNullOrEmpty()) {
                val fake = getOrCreateFakeEnt(cl)
                if (fake != null) linkedMapOf("pro" to fake) else null
            } else null
        }
        tryHookSet(cl, CLS_CUSTOMER_INFO, "getActiveSubscriptions") { s ->
            if (s.isNullOrEmpty()) setOf("synx_pro_lifetime") else null
        }
        tryHookSet(cl, CLS_CUSTOMER_INFO, "getAllPurchasedProductIds") { s ->
            if (s == null || !s.contains("synx_pro_lifetime")) {
                HashSet(s ?: emptySet()).apply { add("synx_pro_lifetime") }
            } else null
        }
    }

    // ===============================================================
    //  SharedPreferences
    // ===============================================================
    private fun hookSharedPreferences(cl: ClassLoader) {
        val keys = listOf("pro", "subscription", "premium", "vip", "is_pro", "isPro",
            "has_access", "hasAccess", "is_premium", "isPremium",
            "is_subscribed", "isSubscribed", "is_active", "isActive",
            "is_unlocked", "isUnlocked", "plan", "membership", "unlock", "entitlement", "purchased")
        try {
            XposedHelpers.findAndHookMethod("android.app.SharedPreferencesImpl", cl,
                "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.result != true && keys.any { key.lowercase().contains(it) }) {
                            p.result = true
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] SP.getBoolean()")
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] SP: $t") }
        try {
            XposedHelpers.findAndHookMethod("android.app.SharedPreferencesImpl\$EditorImpl", cl,
                "putBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.args[1] != true && keys.any { key.lowercase().contains(it) }) p.args[1] = true
                    }
                })
            XposedBridge.log("[$TAG] [✓] SP.putBoolean()")
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] putBoolean: $t") }
    }

    // ===============================================================
    //  Application Context
    // ===============================================================
    private fun hookAppContext(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application", cl, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        appContext = p.thisObject as? android.content.Context
                        XposedBridge.log("[$TAG] App context captured")
                    }
                })
            XposedBridge.log("[$TAG] [✓] Application.onCreate()")
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] App context: $t") }
    }

    // ===============================================================
    //  翻译: 繁体→简体
    // ===============================================================
    private fun hookTranslation(cl: ClassLoader) {
        for (withInt in listOf(false, true)) {
            try {
                val hook = object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val path = p.args[0] as? String ?: return
                        if (!path.contains("zh-Hant") || !path.contains("common.toml")) return
                        XposedBridge.log("[$TAG] Intercepted TOML: $path")
                        val ctx = appContext ?: return
                        try {
                            val mctx = ctx.createPackageContext("com.synx.unlocker",
                                android.content.Context.CONTEXT_IGNORE_SECURITY)
                            val id = mctx.resources.getIdentifier("zh_cn_common", "raw", "com.synx.unlocker")
                            if (id != 0) {
                                (p.result as? InputStream)?.close()
                                val bytes = mctx.resources.openRawResource(id).readBytes()
                                p.result = ByteArrayInputStream(bytes)
                                XposedBridge.log("[$TAG] [✓] Replaced → zh-CN (${bytes.size} bytes)")
                            }
                        } catch (e: Exception) { XposedBridge.log("[$TAG] Translation: ${e.message}") }
                    }
                }
                if (withInt) {
                    XposedHelpers.findAndHookMethod("android.content.res.AssetManager", cl,
                        "open", String::class.java, Int::class.javaPrimitiveType, hook)
                } else {
                    XposedHelpers.findAndHookMethod("android.content.res.AssetManager", cl,
                        "open", String::class.java, hook)
                }
                XposedBridge.log("[$TAG] [✓] AssetManager.open(${if (withInt) "String,int" else "String"})")
            } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] AssetManager: $t") }
        }
    }

    // ===============================================================
    //  工具
    // ===============================================================
    private fun tryHook(cl: ClassLoader, cls: String, method: String, after: (XC_MethodHook.MethodHookParam) -> Unit) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) { after(p) }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] $cls.$method(): $t") }
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
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] $cls.$method(): $t") }
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
        } catch (t: Throwable) { XposedBridge.log("[$TAG] [✗] $cls.$method(): $t") }
    }

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
            val ent = ctor.newInstance(
                "pro", true, true, enumVal(pt, "NORMAL"),
                Date(), Date(), Date(4102358399000L), enumVal(st, "PLAY_STORE"),
                "synx_pro_lifetime", null, false, null, null,
                enumVal(ot, "PURCHASED"), jo.getConstructor().newInstance())
            cachedFakeEnt = ent; cachedCL = cl; ent
        } catch (t: Throwable) { null }
    }

    private fun enumVal(cls: Class<*>, name: String): Any? =
        cls.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
}
