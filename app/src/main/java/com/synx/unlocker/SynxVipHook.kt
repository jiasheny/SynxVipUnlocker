package com.synx.unlocker

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.*
import java.util.*
import java.util.zip.ZipFile

class SynxVipHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "SynxVipUnlocker"
        private const val TARGET = "com.synxapp.synx"
        private const val CLS_CUSTOMER_INFO     = "com.revenuecat.purchases.CustomerInfo"
        private const val CLS_ENTITLEMENT_INFO   = "com.revenuecat.purchases.EntitlementInfo"
        private const val CLS_ENTITLEMENT_INFOS  = "com.revenuecat.purchases.EntitlementInfos"

        @Volatile private var cachedFakeEnt: Any? = null
        @Volatile private var cachedCL: ClassLoader? = null
        @Volatile private var moduleApkPath: String? = null

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

        // 获取模块 APK 路径（用于翻译资源读取）
        moduleApkPath = getModuleApkPath(cl)

        XposedBridge.log("[$TAG] ============================================")
        XposedBridge.log("[$TAG] Synx loaded (pid=${android.os.Process.myPid()})")
        XposedBridge.log("[$TAG] Module APK: $moduleApkPath")

        // ---- VIP: RevenueCat 层 ----
        hook(cl, CLS_ENTITLEMENT_INFO, "isActive") { p -> if (p.result != true) p.result = true }
        hook(cl, CLS_ENTITLEMENT_INFO, "getWillRenew") { p -> if (p.result != true) p.result = true }
        hookDate(cl, CLS_ENTITLEMENT_INFO, "getExpirationDate")
        hookDate(cl, CLS_CUSTOMER_INFO, "getLatestExpirationDate")

        hookMap(cl, CLS_ENTITLEMENT_INFOS, "getActive") { m ->
            if (m.isNullOrEmpty()) {
                val fake = getOrCreateFakeEnt(cl)
                if (fake != null) linkedMapOf("pro" to fake) else null
            } else null
        }

        hookSet(cl, CLS_CUSTOMER_INFO, "getActiveSubscriptions") { s ->
            if (s.isNullOrEmpty()) setOf("synx_pro_monthly", "synx_pro_yearly") else null
        }

        hookSet(cl, CLS_CUSTOMER_INFO, "getAllPurchasedProductIds") { s ->
            if (s == null || !s.contains("synx_pro_lifetime")) {
                val m = HashSet(s ?: emptySet()); m.add("synx_pro_lifetime"); m
            } else null
        }

        // ---- VIP: SharedPreferences 缓存层 ----
        hookSharedPreferences(cl)

        // ---- VIP: MethodChannel 拦截 ----
        hookMethodChannel(cl)

        // ---- 简体中文翻译 ----
        hookTranslation(cl)

        XposedBridge.log("[$TAG] All hooks installed!")
        XposedBridge.log("[$TAG] ============================================")
    }

    // ===============================================================
    //  通用 Hook 工具
    // ===============================================================

    private fun hook(cl: ClassLoader, cls: String, method: String, after: (XC_MethodHook.MethodHookParam) -> Unit) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) { after(p) }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    private fun hookDate(cl: ClassLoader, cls: String, method: String) {
        hook(cl, cls, method) { p -> if (p.result == null) p.result = Date(4102358399000L) }
    }

    private fun hookMap(cl: ClassLoader, cls: String, method: String, after: (Map<*, *>?) -> Map<*, *>?) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val m = p.result as? Map<*, *>
                    val r = after(m)
                    if (r != null) p.result = r
                }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    private fun hookSet(cl: ClassLoader, cls: String, method: String, after: (Set<*>?) -> Set<*>?) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val s = p.result as? Set<*>
                    val r = after(s)
                    if (r != null) p.result = r
                }
            })
            XposedBridge.log("[$TAG] [✓] $cls.$method()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] $cls.$method(): $t")
        }
    }

    // ===============================================================
    //  SharedPreferences 拦截
    // ===============================================================
    private fun hookSharedPreferences(cl: ClassLoader) {
        // getBoolean(key, default) → pro 键返回 true
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl", cl,
                "getBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.result != true && isProKey(key)) {
                            p.result = true
                            XposedBridge.log("[$TAG] SP.getBoolean('$key') → true")
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] SharedPreferencesImpl.getBoolean()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] SP.getBoolean: $t")
        }

        // putBoolean(key, false) → 拦截 pro=false
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.SharedPreferencesImpl\$EditorImpl", cl,
                "putBoolean", String::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val key = p.args[0] as? String ?: return
                        if (p.args[1] != true && isProKey(key)) {
                            p.args[1] = true
                            XposedBridge.log("[$TAG] SP.putBoolean('$key') false → true")
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] EditorImpl.putBoolean()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] putBoolean: $t")
        }
    }

    // ===============================================================
    //  MethodChannel 拦截 RevenueCat 返回数据
    // ===============================================================
    private fun hookMethodChannel(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler\$1",
                cl, "success", Any::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val result = p.args[0] as? Map<*, *> ?: return
                            val modified = modifyChannelResult(result)
                            if (modified != null) p.args[0] = modified
                        } catch (_: Exception) {}
                    }
                })
            XposedBridge.log("[$TAG] [✓] MethodChannel.Result.success()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] MethodChannel: $t")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun modifyChannelResult(result: Map<*, *>): Map<*, *>? {
        if (!result.containsKey("entitlements") && !result.containsKey("activeSubscriptions")) return null
        var modified = false
        val m = LinkedHashMap<String, Any?>(result)

        // 注入 activeSubscriptions
        val subs = m["activeSubscriptions"]
        if (subs == null || (subs is List<*> && subs.isEmpty())) {
            m["activeSubscriptions"] = listOf("synx_pro_monthly", "synx_pro_yearly")
            modified = true
        }

        // 注入 entitlements
        val ents = m["entitlements"]
        if (ents is Map<*, *>) {
            val ne = LinkedHashMap<String, Any?>(ents)
            val active = ne["active"]
            if (active == null || (active is Map<*, *> && active.isEmpty())) {
                val fa = LinkedHashMap<String, Any>()
                fa["pro"] = fakeEntMap()
                ne["active"] = fa
                ne["all"] = fa
                modified = true
            }
            m["entitlements"] = ne
        }

        return if (modified) m else null
    }

    private fun fakeEntMap(): Map<String, Any> {
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
    //  翻译：繁体 → 简体
    // ===============================================================
    private fun hookTranslation(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.res.AssetManager", cl,
                "open", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val path = p.args[0] as? String ?: return
                        if (!path.contains("zh-Hant") || !path.contains("common.toml")) return

                        XposedBridge.log("[$TAG] Intercepted zh-Hant TOML: $path")
                        val apk = moduleApkPath ?: return
                        try {
                            (p.result as? InputStream)?.close()
                            val zf = ZipFile(apk)
                            val entry = zf.getEntry("res/raw/zh_cn_common.toml")
                            if (entry != null) {
                                val bytes = zf.getInputStream(entry).readBytes()
                                p.result = ByteArrayInputStream(bytes)
                                XposedBridge.log("[$TAG] [✓] Replaced zh-Hant → zh-CN (${bytes.size} bytes)")
                            }
                            // 不 close zf，因为 InputStream 需要它
                        } catch (e: Exception) {
                            XposedBridge.log("[$TAG] Translation replace failed: ${e.message}")
                        }
                    }
                })
            XposedBridge.log("[$TAG] [✓] AssetManager (translation)")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] AssetManager: $t")
        }
    }

    // ===============================================================
    //  获取模块 APK 路径
    // ===============================================================
    private fun getModuleApkPath(cl: ClassLoader): String? {
        return try {
            // 尝试从 ClassLoader 的 path 字段获取
            var cls: Class<*>? = cl.javaClass
            while (cls != null) {
                try {
                    val field = cls.getDeclaredField("path")
                    field.isAccessible = true
                    val path = field.get(cl) as? String
                    if (path != null && path.endsWith(".apk")) return path
                } catch (_: NoSuchFieldException) {}
                cls = cls.superclass
            }
            null
        } catch (_: Exception) { null }
    }

    // ===============================================================
    //  工具方法
    // ===============================================================
    private fun isProKey(key: String): Boolean {
        val l = key.lowercase()
        return PRO_KEYS.any { l.contains(it.lowercase()) }
    }

    @Synchronized
    private fun getOrCreateFakeEnt(cl: ClassLoader): Any? {
        if (cachedFakeEnt != null && cachedCL === cl) return cachedFakeEnt
        return try {
            val eiCls = cl.loadClass(CLS_ENTITLEMENT_INFO)
            val pt = cl.loadClass("com.revenuecat.purchases.PeriodType")
            val st = cl.loadClass("com.revenuecat.purchases.Store")
            val ot = cl.loadClass("com.revenuecat.purchases.OwnershipType")
            val jo = cl.loadClass("org.json.JSONObject")

            val ctor = eiCls.getDeclaredConstructor(
                String::class.java, Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!, pt,
                Date::class.java, Date::class.java, Date::class.java,
                st, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Date::class.java, Date::class.java,
                ot, jo
            )
            ctor.isAccessible = true

            val now = Date()
            val future = Date(4102358399000L)
            val ent = ctor.newInstance(
                "pro", true, true, enumVal(pt, "NORMAL"),
                now, now, future, enumVal(st, "PLAY_STORE"),
                "synx_pro_lifetime", null, false, null, null,
                enumVal(ot, "PURCHASED"), jo.getConstructor().newInstance()
            )
            cachedFakeEnt = ent; cachedCL = cl
            XposedBridge.log("[$TAG] Fake EntitlementInfo('pro') created")
            ent
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Fake ent failed: $t")
            null
        }
    }

    private fun enumVal(cls: Class<*>, name: String): Any? =
        cls.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
}
