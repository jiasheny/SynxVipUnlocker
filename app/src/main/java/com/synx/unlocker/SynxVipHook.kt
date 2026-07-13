package com.synx.unlocker

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.*
import java.lang.reflect.Field

/**
 * Synx VIP Unlocker - LSPosed Module
 *
 * 破解 Synx (com.synxapp.synx) 的 VIP/Pro 功能。
 *
 * 目标版本: Synx 2.27.0
 *
 * 原理:
 *   Synx 使用 RevenueCat 管理订阅，通过 purchases_flutter 插件桥接到 Flutter 层。
 *   Dart 层获取 CustomerInfo 后检查 entitlements，设置 isProMode 标志。
 *   SubscriptionEnforcement 类根据此标志限制或启用 Pro 功能。
 *
 * Hook 清单:
 *   1. EntitlementInfo.isActive()          → 始终 true
 *   2. EntitlementInfo.getExpirationDate() → 返回 2099 年远期日期
 *   3. EntitlementInfos.getActive()        → 空时注入伪造的 pro 权益
 *   4. CustomerInfo.getActiveSubscriptions() → 空时注入假订阅集合
 */
class SynxVipHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "SynxVipUnlocker"
        private const val TARGET_PACKAGE = "com.synxapp.synx"

        private const val CLS_CUSTOMER_INFO    = "com.revenuecat.purchases.CustomerInfo"
        private const val CLS_ENTITLEMENT_INFO  = "com.revenuecat.purchases.EntitlementInfo"
        private const val CLS_ENTITLEMENT_INFOS = "com.revenuecat.purchases.EntitlementInfos"

        // 缓存：在当前进程生命周期内，每个 ClassLoader 只需创建一次 fake entitlement
        @Volatile private var cachedFakeEntitlement: Any? = null
        @Volatile private var cachedClassLoader: ClassLoader? = null
    }

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        val cl = lpparam.classLoader
        XposedBridge.log("[$TAG] --------------------------------------------------")
        XposedBridge.log("[$TAG] Synx app (pid=${android.os.Process.myPid()}) loaded")
        XposedBridge.log("[$TAG] Hooking RevenueCat subscription layer...")
        XposedBridge.log("[$TAG] --------------------------------------------------")

        hook_isActive(cl)
        hook_getExpirationDate(cl)
        hook_EntitlementInfos_getActive(cl)
        hook_getActiveSubscriptions(cl)

        XposedBridge.log("[$TAG] All hooks installed. VIP features should be unlocked.")
    }

    // ---------------------------------------------------------------
    // Hook 1: EntitlementInfo.isActive() → always true
    // ---------------------------------------------------------------
    private fun hook_isActive(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLS_ENTITLEMENT_INFO, cl, "isActive",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result != true) {
                            param.result = true
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] [✓] EntitlementInfo.isActive()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] EntitlementInfo.isActive() : $t")
        }
    }

    // ---------------------------------------------------------------
    // Hook 2: EntitlementInfo.getExpirationDate() → far future
    // ---------------------------------------------------------------
    private fun hook_getExpirationDate(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLS_ENTITLEMENT_INFO, cl, "getExpirationDate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == null) {
                            param.result = Date(4102358399000L) // 2099-12-31T23:59:59Z
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] [✓] EntitlementInfo.getExpirationDate()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] EntitlementInfo.getExpirationDate() : $t")
        }
    }

    // ---------------------------------------------------------------
    // Hook 3: EntitlementInfos.getActive() → never empty
    //         RevenueCat 中 getActive() 从 all Map 中过滤 isActive==true 的条目。
    //         如果用户没有购买过任何订阅，all Map 为空，getActive() 自然也空。
    //         此 Hook 在返回空时注入一个 "pro" 权益。
    // ---------------------------------------------------------------
    private fun hook_EntitlementInfos_getActive(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLS_ENTITLEMENT_INFOS, cl, "getActive",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activeMap = param.result as? Map<*, *>
                        if (activeMap == null || activeMap.isEmpty()) {
                            val fake = getOrCreateFakeEntitlement(cl)
                            if (fake != null) {
                                val m = LinkedHashMap<String, Any>()
                                m["pro"] = fake
                                param.result = m
                            }
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] [✓] EntitlementInfos.getActive()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] EntitlementInfos.getActive() : $t")
        }
    }

    // ---------------------------------------------------------------
    // Hook 4: CustomerInfo.getActiveSubscriptions() → never empty
    // ---------------------------------------------------------------
    private fun hook_getActiveSubscriptions(cl: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLS_CUSTOMER_INFO, cl, "getActiveSubscriptions",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val subs = param.result as? Set<*>
                        if (subs == null || subs.isEmpty()) {
                            param.result = setOf("synx_pro_monthly", "synx_pro_yearly")
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] [✓] CustomerInfo.getActiveSubscriptions()")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] [✗] CustomerInfo.getActiveSubscriptions() : $t")
        }
    }

    // ---------------------------------------------------------------
    // 构造伪造的 EntitlementInfo("pro", isActive=true, …)
    // ---------------------------------------------------------------

    @Synchronized
    private fun getOrCreateFakeEntitlement(cl: ClassLoader): Any? {
        if (cachedFakeEntitlement != null && cachedClassLoader === cl) {
            return cachedFakeEntitlement
        }
        return try {
            val entitlementInfoClass = cl.loadClass(CLS_ENTITLEMENT_INFO)

            // 枚举类
            val periodTypeClass     = cl.loadClass("com.revenuecat.purchases.PeriodType")
            val storeClass          = cl.loadClass("com.revenuecat.purchases.Store")
            val ownershipTypeClass  = cl.loadClass("com.revenuecat.purchases.OwnershipType")

            val periodNormal    = enumValue(periodTypeClass, "NORMAL")
            val storePlay       = enumValue(storeClass, "PLAY_STORE")
            val ownershipPurch  = enumValue(ownershipTypeClass, "PURCHASED")

            val jsonObj = cl.loadClass("org.json.JSONObject").getConstructor().newInstance()

            // EntitlementInfo 构造函数签名 (RevenueCat v8，不含 VerificationResult):
            // (String, boolean, boolean, PeriodType,
            //  Date, Date, Date, Store,
            //  String, String, boolean,
            //  Date, Date, OwnershipType,
            //  JSONObject)
            val ctor = entitlementInfoClass.getDeclaredConstructor(
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                periodTypeClass,
                Date::class.java,
                Date::class.java,
                Date::class.java,
                storeClass,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Date::class.java,
                Date::class.java,
                ownershipTypeClass,
                jsonObj.javaClass
            )
            ctor.isAccessible = true

            val now = Date()
            val farFuture = Date(4102358399000L) // 2099

            val ent = ctor.newInstance(
                "pro",                  // identifier
                true,                   // isActive
                true,                   // willRenew
                periodNormal,           // periodType = NORMAL
                now,                    // originalPurchaseDate
                now,                    // latestPurchaseDate
                farFuture,              // expirationDate
                storePlay,              // store = PLAY_STORE
                "synx_pro_lifetime",    // productIdentifier
                null,                   // productPlanIdentifier
                false,                  // isSandbox
                null,                   // unsubscribeDetectedAt
                null,                   // billingIssueDetectedAt
                ownershipPurch,         // ownershipType = PURCHASED
                jsonObj                 // rawData
            )

            cachedFakeEntitlement = ent
            cachedClassLoader = cl
            XposedBridge.log("[$TAG] Fake EntitlementInfo('pro') created")
            ent
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Cannot create fake EntitlementInfo: $t")
            null
        }
    }

    private fun enumValue(enumClass: Class<*>, name: String): Any? {
        return try {
            enumClass.enumConstants?.firstOrNull { (it as Enum<*>).name == name }
        } catch (_: Exception) { null }
    }
}
