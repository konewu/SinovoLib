package com.sinovotec.gwSmartConfig;

import android.content.Context;
import android.util.Log;

import com.sinovotec.andesptouch.AndEsptouch;
import com.sinovotec.andesptouch.AndEsptouchHelper;

public class GWSmartConfig {
    private static final String TAG = "SinovoBle";
    private static GWSmartConfig instance;
    private AndEsptouch andEsptouch;

    public static GWSmartConfig getInstance() {
        if (instance == null) {
            synchronized (GWSmartConfig.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new GWSmartConfig();
                }
            }
        }
        return instance;
    }

    //开始配网
    public void startConifg(Context context, GWSmartConfigCallback GWSmartConfigCallback, String WifiSSID, String WifiPass){
        String bssid = AndEsptouchHelper.getInstance(context).getBSSID();
        andEsptouch = new AndEsptouch.Builder(context).setSSID(WifiSSID)
                .setBSSID(bssid).setPassWord(WifiPass).build();
        andEsptouch.startConfig();
        andEsptouch.setOnEsptouchTaskListener((code, message) -> {
            Log.d(TAG, "配网完成，收到的回应 code:" + code + "message:" + message);

            if (code == AndEsptouch.RESULT_CONFIG_SUCCESS) {
                if (andEsptouch!=null){ andEsptouch.stopConfig(); }
                if (message.indexOf("BSSID:") == 0){
                    String gatewayID = message.substring(6,18).toUpperCase();
                    GWSmartConfigCallback.onConfigSUCCESS(gatewayID);
                }
            } else if (code == AndEsptouch.RESULT_CONFIG_TIMEOUT) {
                GWSmartConfigCallback.onConfigFailed(message);
            } else {
                GWSmartConfigCallback.onConfigTimeOut(message);
            }
        });
    }

    //退出配网
    public void exitConfig() {
        andEsptouch.stopConfig();
        andEsptouch.cancelAsyncTask();
    }
}
