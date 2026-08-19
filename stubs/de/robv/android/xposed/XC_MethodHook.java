package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public Object param;

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;

        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public Throwable getThrowable() { return null; }
        public void setThrowable(Throwable throwable) {}
        public Object getExtra(String key) { return null; }
    }

    public class Unhook {
        public void unhook() {}
    }
}
