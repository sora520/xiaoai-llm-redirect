package com.xiaoai.llmredirect;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * 小爱同学 (com.miui.voiceassist) LLM 重定向模块
 *
 * 适配版本: 8.0.17.3013 (versionCode 508000017)
 *
 * 反编译确认的调用链:
 *   i5.b (静态 BuildConfig 持有者, MIFY_LLM_BASE_URL/MIFY_MODEL_NAME)
 *     -> x5.h1 SettingsRepositoryImpl (DataStore flows, 组装 LlmConfig)
 *       -> d8.a LlmConfig (baseUrl / apiKey / model / provider)
 *         -> b8.h OpenAIClient / b8.i OpenAIResponsesClient / b8.b AnthropicClient
 *            请求 {baseUrl}/chat/completions, Authorization: Bearer {apiKey}, SSE 流式
 *
 *   意图路由(独立小模型): dd.r0 (apiUrl/apiKey/model)
 *     -> dd.a / dd.m LLMJudge -> {miclaw}/osbot/api/intent/v2/chat/completions
 *
 *   手动模式: za1.h AiModeManager 暴露 AUTO/QUICK/EXPERT 当前模式。
 *   自动分档: dd.j0$f RouteDecision 持有 DIRECT/FAST/STANDARD/DEEP 档位；这个版本中
 *     STANDARD/DEEP 的原始模型名也可能是 xiaomi/mimo，不能只按模型名区分。
 *
 *   定时任务: com.aios.osbot.event.timer (本地 AlarmManager + cron + 本地DB)
 *     会员门控: w8.b MembershipRepository.checkTimerAccess() -> ALLOWED/BLOCKED_*
 *     (云端 /osbot/api/user/v2/status 查 level), 可 hook 为 ALLOWED 解锁
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.miui.voiceassist";
    private static final String MODULE_PKG = "com.xiaoai.llmredirect";
    private static final String TAG = "XiaoaiLLM";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        // 双参构造: 明确指向 ConfigActivity 写入的 shared_prefs/config.xml
        // (单参构造只会找 <包名>_preferences.xml 默认文件, 读不到我们的配置)
        final XSharedPreferences prefs = new XSharedPreferences(MODULE_PKG, "config");
        prefs.makeWorldReadable();
        if (prefs.getFile() == null || !prefs.getFile().exists()) {
            XposedBridge.log("[" + TAG + "] 未找到模块配置文件，请在模块界面填写并保存");
            return;
        }
        prefs.reload();
        if (!prefs.getBoolean("enabled", false)) {
            XposedBridge.log("[" + TAG + "] 模块未启用，跳过");
            return;
        }

        final String baseUrl = trimSlashes(prefs.getString("base_url", ""));
        final String apiKey = trim(prefs.getString("api_key", ""));
        final String mainModel = trim(prefs.getString("model", ""));
        // 分档模型: 留空 = 跟随主模型
        final String fastModel = orMain(trim(prefs.getString("fast_model", "")), mainModel);
        final String proModel = orMain(trim(prefs.getString("pro_model", "")), mainModel);
        // 路由独立配置, 留空回退主配置
        String routerBase = trimSlashes(prefs.getString("router_base_url", ""));
        final String routerKey = orMain(trim(prefs.getString("router_api_key", "")), apiKey);
        final String routerModel = orMain(trim(prefs.getString("router_model", "")), mainModel);
        if (routerBase.isEmpty()) routerBase = baseUrl;

        boolean hookRouter = prefs.getBoolean("hook_router", true);
        boolean redirectAux = prefs.getBoolean("redirect_aux", false);
        boolean unlockTimer = prefs.getBoolean("unlock_timer", true);
        final boolean verbose = prefs.getBoolean("verbose", false);

        if (baseUrl.isEmpty()) {
            XposedBridge.log("[" + TAG + "] base_url 为空，跳过 hook");
            return;
        }

        final ClassLoader cl = lpparam.classLoader;
        int ok = 0, total = 0;

        // ---- 主对话链路: d8.a LlmConfig ----
        ok += hit(hookStringGetter(cl, "d8.a", "getBaseUrl", baseUrl, verbose)); total++;
        ok += hit(hookStringGetter(cl, "d8.a", "getApiKey", apiKey, verbose)); total++;
        ok += hit(hookRouteDecisionModel(cl, fastModel, proModel, verbose)); total++;
        ok += hit(hookTierModel(cl, mainModel, fastModel, proModel, verbose)); total++;

        // ---- 意图路由链路: dd.r0 (需要完整 endpoint) ----
        if (hookRouter) {
            ok += hit(hookStringGetter(cl, "dd.r0", "getApiUrl", routerBase + "/chat/completions", verbose)); total++;
            ok += hit(hookStringGetter(cl, "dd.r0", "getApiKey", routerKey, verbose)); total++;
            ok += hit(hookStringGetter(cl, "dd.r0", "getModel", routerModel, verbose)); total++;
        }

        // ---- 辅助服务(embeddings/rerank/默认 fallback) ----
        if (redirectAux) {
            ok += hit(hookStringGetter(cl, "i5.b", "getMIFY_LLM_BASE_URL", baseUrl, verbose)); total++;
            ok += hit(hookStringGetter(cl, "i5.k", "getMifyLlmBaseUrl", baseUrl, verbose)); total++;
        }

        // ---- 定时任务解锁: 绕过会员门控 (本地功能, 执行走已重定向的 LLM) ----
        if (unlockTimer) {
            ok += hit(hookTimerUnlock(cl)); total++;
        }

        XposedBridge.log("[" + TAG + "] 已加载: baseUrl=" + baseUrl
                + ", model=" + mainModel + " (fast=" + fastModel + ", pro=" + proModel + ")"
                + ", apiKey=" + (apiKey.isEmpty() ? "<empty>" : "***len=" + apiKey.length())
                + (hookRouter ? " | router=" + routerBase + ", model=" + routerModel : " | router=off")
                + (unlockTimer ? " | timer=unlocked" : "")
                + " | hook 成功 " + ok + "/" + total
                + (ok < total ? " (失败处可能版本不匹配)" : ""));
    }

    private static int hit(boolean ok) {
        return ok ? 1 : 0;
    }

    private static String orMain(String value, String main) {
        return value.isEmpty() ? main : value;
    }

    /**
     * hook 一个无参 String getter, 固定返回 replacement.
     * replacement 为空串则不安装 hook (保留原值)。
     */
    private boolean hookStringGetter(ClassLoader cl, String cls, String method,
                                     final String replacement, final boolean verbose) {
        if (replacement == null || replacement.isEmpty()) {
            return true; // 未配置, 视为不需要
        }
        try {
            Class<?> clazz = XposedHelpers.findClass(cls, cl);
            XposedHelpers.findAndHookMethod(clazz, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    param.setResult(replacement);
                    if (verbose) {
                        boolean secret = "getApiKey".equals(method);
                        XposedBridge.log("[" + TAG + "] " + cls + "." + method + ": "
                                + (secret ? maskSecret(orig) : orig) + " -> "
                                + (secret ? maskSecret(replacement) : replacement));
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] hook " + cls + "." + method + " 失败: " + t);
            return false;
        }
    }

    /** 自动模式必须按 RouteDecision.tier 映射，因为深度档也会返回 xiaomi/mimo。 */
    private boolean hookRouteDecisionModel(ClassLoader cl, final String fastModel,
                                           final String proModel, final boolean verbose) {
        if (fastModel.isEmpty() && proModel.isEmpty()) {
            return true;
        }
        try {
            Class<?> clazz = XposedHelpers.findClass("dd.j0$f", cl);
            XposedHelpers.findAndHookMethod(clazz, "getModel", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String orig = (String) param.getResult();
                    Object tier = param.thisObject.getClass().getMethod("getTier").invoke(param.thisObject);
                    String tierName = tier == null ? "" : String.valueOf(tier);
                    String mapped = mapRouteTierModel(tierName, orig, fastModel, proModel);
                    if (!same(mapped, orig)) {
                        param.setResult(mapped);
                        if (verbose) {
                            XposedBridge.log("[" + TAG + "] dd.j0$f.getModel: tier="
                                    + tierName + ", " + orig + " -> " + mapped);
                        }
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] hook dd.j0$f.getModel 失败: " + t);
            return false;
        }
    }

    /**
     * 最终请求模型映射。手动专家模式由 AiModeManager 决定；其余情况保留自动档位
     * 已写入的自定义模型，再用原始模型名兼容快速模式和旧路径。
     */
    private boolean hookTierModel(ClassLoader cl, final String mainModel,
                                  final String fastModel, final String proModel,
                                  final boolean verbose) {
        if (mainModel.isEmpty()) {
            return true; // 未配置模型, 保留原模型名
        }
        try {
            Class<?> clazz = XposedHelpers.findClass("d8.a", cl);
            final Method expertModeMethod = findExpertModeMethod(cl);
            XposedHelpers.findAndHookMethod(clazz, "getModel", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String orig = (String) param.getResult();
                    boolean expertMode = isExpertMode(expertModeMethod);
                    String mapped = mapTierModel(orig, expertMode, mainModel, fastModel, proModel);
                    if (!same(mapped, orig)) {
                        param.setResult(mapped);
                        if (verbose) {
                            XposedBridge.log("[" + TAG + "] d8.a.getModel: mode="
                                    + (expertMode ? "EXPERT" : "other") + ", " + orig + " -> " + mapped);
                        }
                    }
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] hook d8.a.getModel 失败: " + t);
            return false;
        }
    }

    static String mapRouteTierModel(String tier, String orig, String fastModel, String proModel) {
        if ("DIRECT".equals(tier) || "FAST".equals(tier)) {
            return fastModel;
        }
        if ("STANDARD".equals(tier) || "DEEP".equals(tier)) {
            return proModel;
        }
        return orig;
    }

    static String mapTierModel(String orig, boolean expertMode, String mainModel,
                               String fastModel, String proModel) {
        if (expertMode) {
            return proModel;
        }
        if (orig == null || orig.isEmpty()) {
            return mainModel;
        }
        // RouteDecision 已经写入的自定义模型不能再次按名字映射回主模型。
        if (orig.equals(mainModel) || orig.equals(fastModel) || orig.equals(proModel)) {
            return orig;
        }
        String lower = orig.toLowerCase();
        if (lower.contains("pro")) {
            return proModel;
        }
        if (lower.contains("mimo") || lower.contains("qwen") || lower.contains("fast") || lower.contains("flash")) {
            return fastModel;
        }
        return mainModel;
    }

    private static Method findExpertModeMethod(ClassLoader cl) {
        try {
            Class<?> modeManager = XposedHelpers.findClassIfExists("za1.h", cl);
            if (modeManager == null) {
                return null;
            }
            Method method = modeManager.getDeclaredMethod("isExpectMode");
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] 获取专家模式状态失败: " + t);
            return null;
        }
    }

    private static boolean isExpertMode(Method method) {
        if (method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean same(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * 定时任务会员门控绕过:
     * w8.b (MembershipRepository).checkTimerAccess 是 suspend fun, 同步返回枚举值
     * w8.b$e.ALLOWED 对挂起调用方完全合法 (非 COROUTINE_SUSPENDED 即为最终值)。
     */
    private boolean hookTimerUnlock(ClassLoader cl) {
        try {
            Class<?> repo = XposedHelpers.findClass("w8.b", cl);
            Class<?> accessEnum = XposedHelpers.findClass("w8.b$e", cl);
            final Object allowed = XposedHelpers.getStaticObjectField(accessEnum, "ALLOWED");
            XposedBridge.hookAllMethods(repo, "checkTimerAccess", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return allowed;
                }
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] 解锁定时任务失败: " + t);
            return false;
        }
    }

    private static String trimSlashes(String s) {
        s = trim(s);
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String maskSecret(Object value) {
        if (value == null) {
            return "<null>";
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? "<empty>" : "***len=" + text.length();
    }
}
