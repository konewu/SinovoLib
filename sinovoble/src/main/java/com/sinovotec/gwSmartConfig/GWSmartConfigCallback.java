package com.sinovotec.gwSmartConfig;

public interface GWSmartConfigCallback {
    void onConfigSUCCESS(String msg);
    void onConfigFailed(String msg);
    void onConfigTimeOut(String msg);
}
