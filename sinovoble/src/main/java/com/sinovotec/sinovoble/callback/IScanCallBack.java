package com.sinovotec.sinovoble.callback;

public interface IScanCallBack {
    //发现设备
    void onDeviceFound(String scanResult);

    //扫描完成
    void onScanOnlyFinish();

    //扫描超时
    void onScanTimeout(String scanResult);

    //20s内扫描不到任何设备
    void onNotFoundBles20s();

    //40s内扫描不到指定的锁
    void onNotFoundLock40s();

    //测试
//    void onScanTest(String scanResult);
}
