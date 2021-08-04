package com.sinovotec.sinovoble.common;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.sinovotec.mqtt.MqttLib;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import java.util.Objects;

/**
 *  用于监听手机的蓝牙开启与关闭的
 */

public class BluetoothListenerReceiver extends BroadcastReceiver {
    final String TAG = "SinovoBleLib";
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        //监控手机蓝牙的开关
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(Objects.requireNonNull(action))) {
            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            switch (blueState) {
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.e(TAG, "onReceive---------Bluetooth is turning on");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.e(TAG, "onReceive---------Bluetooth is on");
                    //重新初始化 蓝牙
                    SinovoBle.getInstance().init(SinovoBle.getInstance().getContext(),
                                SinovoBle.getInstance().getmBleScanCallBack(), SinovoBle.getInstance().getmConnCallBack());

                    BleConnCallBack.getInstance().setmBluetoothGatt(null);
                    if (SinovoBle.getInstance().getmConnCallBack() != null) {
                        SinovoBle.getInstance().setConnected(false);
                        SinovoBle.getInstance().setConnectting(false);
                        SinovoBle.getInstance().setLinked(false);
                        SinovoBle.getInstance().getmConnCallBack().onBluetoothOn();
                    }
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.e(TAG, "onReceive---------Bluetooth is turning off");
                    if (SinovoBle.getInstance().getmConnCallBack()!= null) {
                        if (!SinovoBle.getInstance().isRestartBLE()) {
                            SinovoBle.getInstance().getmConnCallBack().onBluetoothOff();
                        }
                        SinovoBle.getInstance().setRestartBLE(false);
                    }

                    if (SinovoBle.getInstance().isBleConnected()){
                        BleConnCallBack.getInstance().releaseBle();
                        if (SinovoBle.getInstance().getmConnCallBack()!= null) {
                            SinovoBle.getInstance().setConnected(false);
                            SinovoBle.getInstance().setLinked(false);
                        }
                    }

                    break;
                case BluetoothAdapter.STATE_OFF:
                    Log.e(TAG, "onReceive---------Bluetooth is off");
                    BleConnCallBack.getInstance().releaseBle();
                    break;
            }
        }

        //监控手机的网络
        // 监听网络连接，包括wifi和移动数据的打开和关闭,以及连接上可用的连接都会接到监听
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = connectivityManager.getActiveNetworkInfo();

            if (info != null) {
                //如果当前的网络连接成功并且网络连接可用
                if (NetworkInfo.State.CONNECTED == info.getState() && info.isAvailable()) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI){
                        String wifiSSID = ComTool.getWifiName(context.getApplicationContext());
                        Log.i(TAG, "连接到wifi " + wifiSSID);
                        MqttLib.getInstance().setConnectType(1);
                    }else if (info.getType() == ConnectivityManager.TYPE_MOBILE){
                        Log.e(TAG, "连上 数据网络");
                        MqttLib.getInstance().setConnectType(2);
                    }
                } else {
                    MqttLib.getInstance().setConnectType(0);
                    Log.e(TAG,  "网络断开");
                }
            }else {
                MqttLib.getInstance().setConnectType(0);
                Log.e(TAG, "获取不到网络信息");
            }
        }

        //wifi打开与否
        if (Objects.equals(intent.getAction(), WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
            if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
                Log.i(TAG, "系统关闭wifi");
                MqttLib.getInstance().setConnectType(0);
            } else if (wifistate == WifiManager.WIFI_STATE_ENABLED) {
                Log.i(TAG, "系统开启wifi");
            }
        }

        //熄屏 亮屏的广播
        switch (action) {
            case Intent.ACTION_SCREEN_OFF:  //收到熄屏广播
                Log.d(TAG, "收到熄屏广播");
                break;
            case Intent.ACTION_SCREEN_ON:   //收到亮屏广播
                Log.d(TAG, "收到亮屏广播");
                break;
        }
    }
}
