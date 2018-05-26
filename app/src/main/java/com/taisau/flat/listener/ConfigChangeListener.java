package com.taisau.flat.listener;

/**
 * Created by whx on 2018-05-02
 */
public class ConfigChangeListener {
    private static OnServerConfigChangeListener listener;

    public static void setOnServerConfigChangeListener(OnServerConfigChangeListener onServerSettingChange) {
        listener = onServerSettingChange;
    }

    public static void OnConfigChange(float setting) {
        if (listener != null) {
            listener.OnConfigChange(setting);
        }
    }
    public static void OnConfigChange(boolean openAliveCheck) {
        if (listener != null) {
            listener.OnConfigChange(openAliveCheck);
        }
    }
    public interface OnServerConfigChangeListener {
        void OnConfigChange(float setting);
        void OnConfigChange(boolean openAliveCheck);
    }
}
