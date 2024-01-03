package com.sinovotec.gwSmartConfig;

public interface GWSmartConfigCallback {
    void onConfigSUCCESS(String msg);  // 配置成功

    void onConfigFailed(String msg);  // 配置失败

    void onConfigTimeOut(String msg);  // 配置超时
}
