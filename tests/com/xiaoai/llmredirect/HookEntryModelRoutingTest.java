package com.xiaoai.llmredirect;

public final class HookEntryModelRoutingTest {
    public static void main(String[] args) {
        String main = "main-model";
        String fast = "fast-model";
        String pro = "expert-model";

        expect(fast, HookEntry.mapRouteTierModel("DIRECT", "xiaomi/mimo", fast, pro));
        expect(fast, HookEntry.mapRouteTierModel("FAST", "xiaomi/mimo", fast, pro));
        expect(pro, HookEntry.mapRouteTierModel("STANDARD", "xiaomi/mimo", fast, pro));
        expect(pro, HookEntry.mapRouteTierModel("DEEP", "xiaomi/mimo", fast, pro));
        expect("original", HookEntry.mapRouteTierModel("UNKNOWN", "original", fast, pro));

        expect(pro, HookEntry.mapTierModel("xiaomi/mimo", true, main, fast, pro));
        expect(pro, HookEntry.mapTierModel("anything", true, main, fast, pro));
        expect(pro, HookEntry.mapTierModel(pro, false, main, fast, pro));
        expect(fast, HookEntry.mapTierModel(fast, false, main, fast, pro));
        expect(main, HookEntry.mapTierModel(main, false, main, fast, pro));
        expect(pro, HookEntry.mapTierModel("xiaomi/mimo-pro", false, main, fast, pro));
        expect(fast, HookEntry.mapTierModel("xiaomi/mimo", false, main, fast, pro));
        expect(main, HookEntry.mapTierModel("other", false, main, fast, pro));
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }
}
