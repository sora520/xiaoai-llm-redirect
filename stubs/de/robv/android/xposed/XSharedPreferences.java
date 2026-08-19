package de.robv.android.xposed;

import java.io.File;

public class XSharedPreferences {
    public XSharedPreferences(String packageName) {}
    public XSharedPreferences(String packageName, String prefFileName) {}
    public File getFile() { return null; }
    public boolean makeWorldReadable() { return false; }
    public void reload() {}
    public String getString(String key, String def) { return def; }
    public boolean getBoolean(String key, boolean def) { return def; }
}
