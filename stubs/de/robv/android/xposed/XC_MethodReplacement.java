package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {
    @Override
    protected void beforeHookedMethod(MethodHookParam param) {}

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}
