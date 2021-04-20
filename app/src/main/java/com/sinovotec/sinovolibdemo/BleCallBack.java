package com.sinovotec.sinovolibdemo;

import android.os.Message;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;

import java.util.Objects;

public class BleCallBack {
    private final String TAG = "SinovoBle";
    private static BleCallBack instance;          //入口操作管理

    public static BleCallBack getInstance() {
        if (instance == null) {
            synchronized (BleCallBack.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new BleCallBack();
                }
            }
        }
        return instance;
    }

    //BLE Scan callback
    private final IScanCallBack mBleScanCallBack = new IScanCallBack() {
        @Override
        public void onDeviceFound(String scanResult) {

        }

        @Override
        public void onScanOnlyFinish() {

        }

        @Override
        public void onScanTimeout(String scanResult) {

        }
    };

    //BLE connect callback
    private final IConnectCallback mConnCallBack = new IConnectCallback() {
        @Override
        public void onConnectSuccess(String macAddress) {
            Log.w(TAG,"BLE connected, mac address:" + macAddress);
            callHandler(4,"");
            callHandler(1,"BLE connected");
            callHandler(0,"");
        }

        @Override
        public void onConnectFailure() {
            Log.e(TAG,"BLE disconnect");
            callHandler(1,"BLE disconnected");
            callHandler(2,"");
        }

        @Override
        public void onBleDisconnect(String macaddress) {
            Log.e(TAG,"BLE disconnect");
            callHandler(1,"BLE disconnected");
            callHandler(2,"");
        }

        @Override
        public void onConnectLockViaQRCode(String result) {
            Log.w(TAG,"add lock's result:"+ result);
            JSONObject maps = JSONObject.parseObject(result);
            String funCode = Objects.requireNonNull(maps.get("funCode")).toString();
            String errCode = Objects.requireNonNull(maps.get("errCode")).toString();

            if (funCode.equals("00") && errCode.equals("00") && !maps.containsKey("lockMac")){
                Log.d(TAG,"Waiting for user's input");
                callHandler(1,"Press SET button on lock");
                return;
            }

            if (funCode.equals("00") && errCode.equals("04") && !maps.containsKey("lockMac")){
                callHandler(1,"Add Lock Timeout");
                return;
            }

            callHandler(4,"");
            if (errCode.equals("00") || errCode.equals("0b") ){
                String lockmac  = Objects.requireNonNull(maps.get("lockMac")).toString();
                String lockSno  = Objects.requireNonNull(maps.get("lockSno")).toString();
                String lockQRCode   = Objects.requireNonNull(maps.get("lockQRCode")).toString();
                String code     = Objects.requireNonNull(maps.get("code")).toString();

                callHandler(0,"");
                String resultshow = "Lock Mac: "+lockmac + "\nLock SNO: " + lockSno + "\nLock QRCode: " +lockQRCode + "\nCode: " +code;

                if (MyApp.getInstance().getShowResult()!=null) MyApp.getInstance().getShowResult().setText(resultshow);
                if (MyApp.getInstance().getCodeET()!=null) MyApp.getInstance().getCodeET().setText(code);
                if (MyApp.getInstance().getShowResult()!=null) MyApp.getInstance().setCode(code);
                if (MyApp.getInstance().getShowResult()!=null) MyApp.getInstance().setQrcode_input(lockQRCode);
            }
        }

        @Override
        public void onCreateUser(String result) {

        }

        @Override
        public void onUpdateUser(String result) {

        }

        @Override
        public void onAddData(String result) {


        }

        @Override
        public void onDelData(String result) {

        }

        @Override
        public void onVerifyCode(String result) {

        }


        @Override
        public void onUnlock(String result) {
            Log.w(TAG,"unlock result:"+ result);

        //    Map<String, Object> maps = (Map<String, Object>) JSON.parse(result);
            JSONObject maps = JSONObject.parseObject(result);
            String funCode = Objects.requireNonNull(maps.get("funCode")).toString();
            String errCode = Objects.requireNonNull(maps.get("errCode")).toString();

            if (funCode.equals("0a") && errCode.equals("00")){
                String opType = Objects.requireNonNull(maps.get("opType")).toString();
                MyApp.getInstance().setLockStatus(opType);
                String msg ;
                if (opType.equals("01")){
                    msg = "Unlocked";
                    callHandler(5,"Lock");
                }else {
                    msg = "Locked";
                    callHandler(5,"Unlock");
                }
                callHandler(1, msg);
            }
        }

        @Override
        public void onCleanData(String result) {

        }

        @Override
        public void onLockInfo(String result) {
            Log.w(TAG,"onRequestLockInfo result:"+ result);
            JSONObject maps = JSONObject.parseObject(result);
            String funCode = Objects.requireNonNull(maps.get("funCode")).toString();
            String errCode = Objects.requireNonNull(maps.get("errCode")).toString();

            //lock status
            if (funCode.equals("0f") && errCode.equals("00")){
                String lockStatus = Objects.requireNonNull(maps.get("lockStatus")).toString();
                MyApp.getInstance().setLockStatus(lockStatus);

                if (lockStatus.equals("00")){
                    callHandler(5,"Unlock");
                }
                if (lockStatus.equals("01")){
                    callHandler(5,"Lock");
                }
            }

            //lock type and version
            if (funCode.equals("1a") && errCode.equals("00")) {
                String fwVersion1 = Objects.requireNonNull(maps.get("fwVersion1")).toString();
                String fwVersion2 = Objects.requireNonNull(maps.get("fwVersion2")).toString();
                String fwVerTime = Objects.requireNonNull(maps.get("fwVerTime")).toString();
                String fwType = Objects.requireNonNull(maps.get("fwType")).toString();

                String fwVerion = Integer.valueOf(fwVersion1) + "." + fwVersion2 + "." + fwVerTime;
                MyApp.getInstance().setLocktype(fwType);
                MyApp.getInstance().setLockversion(fwVerion);

                callHandler(6,fwType);
                callHandler(7,fwVerion);
            }

            //lock power
            if (funCode.equals("0e") && errCode.equals("00")) {
                String power = Objects.requireNonNull(maps.get("power")).toString() + "%";
                callHandler(8,power);
                MyApp.getInstance().setLockpower(power);
            }

        }

        @Override
        public void onRequestData(String result) {

        }

        @Override
        public void onRequestLog(String result) {

        }

        @Override
        public void onDynamicCodeStatus(String result) {

        }

        @Override
        public void onAuthorOther(String result) {

        }

        @Override
        public void onLockFrozen(String result) {

        }

        @Override
        public void onReceiveDataFailed() {

        }

        @Override
        public void onBluetoothOff() {

        }

        @Override
        public void onBluetoothOn() {

        }

        @Override
        public void onConnectedViaWifi(String wifiSSID) {

        }

        @Override
        public void onConnectedViaMobile() {

        }

        @Override
        public void onInternetDisconned() {

        }

        @Override
        public void onFaildGetInternetInfo() {

        }

        @Override
        public void onWifiOn() {

        }

        @Override
        public void onWifiOff() {

        }

        @Override
        public void onScreenOn() {

        }

        @Override
        public void onScreenOff() {

        }

        @Override
        public void onDFUDeviceConnecting(String result) {

        }

        @Override
        public void onDFUDeviceConnected(String result) {

        }

        @Override
        public void onDfuProcessStarting(String result) {

        }

        @Override
        public void onDfuProcessStarted(String result) {

        }

        @Override
        public void onDFUEnablingDfuMode(String result) {

        }

        @Override
        public void onDFUProgressChanged(int progress) {

        }

        @Override
        public void onDFUFirmwareValidating(String result) {

        }

        @Override
        public void onDFUDeviceDisconnecting(String result) {

        }

        @Override
        public void onDFUDeviceDisconnected(String result) {

        }

        @Override
        public void onDfuCompleted(String result) {

        }

        @Override
        public void onDfuAborted(String result) {

        }

        @Override
        public void onDFUError(String s, int i, int i1, String s1) {

        }
    };

    public IConnectCallback getmConnCallBack() {
        return mConnCallBack;
    }

    public IScanCallBack getmBleScanCallBack() {
        return mBleScanCallBack;
    }


    public void callHandler(int what, String message) {
        if (MyApp.getInstance().getHandlerUI() != null) {
            Log.d(TAG, "更新UI， what：" + what + ", message:" + message);
            Message msg = MyApp.getInstance().getHandlerUI().obtainMessage();
            msg.obj = message;   //从这里把道你想传递的数据放进去就版行了
            msg.what = what;
            MyApp.getInstance().getHandlerUI().sendMessage(msg);
        }
    }
}
