package com.sinovotec.sinovoble.callback;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleConnectLock;
import com.sinovotec.sinovoble.common.BleScanDevice;

import java.util.LinkedHashMap;

import static com.sinovotec.sinovoble.common.ComTool.byte2hex;
import static com.sinovotec.sinovoble.common.ComTool.calTimeDiff;
import static com.sinovotec.sinovoble.common.ComTool.getNowTime;

public class BleScanCallBack extends ScanCallback {
    private static BleScanCallBack instance;                //入口操作管理
    private static final String TAG = "SinovoBle";
    private boolean isScanning  = false;                    //是否正在扫描
    IScanCallBack iScanCallBack;                            //扫描结果回调

    private BleScanCallBack(IScanCallBack scanCallBack){
        this.iScanCallBack = scanCallBack;
        if (iScanCallBack == null){
            throw new NullPointerException("this scanCallback is null!");
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void setScanning(boolean scanning) {
        isScanning = scanning;
    }

    /**
     * 单例方式获取蓝牙通信入口
     */
    public static BleScanCallBack getInstance(IScanCallBack scanCallBack) {
        if (instance != null){
            return  instance;
        }

        synchronized (BleScanCallBack.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
            if (instance == null) {
                instance = new BleScanCallBack(scanCallBack){
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        SinovoBle.getInstance().setFoundSomeBLE(true);
                        BleScanDevice bleScanDevice = analyzeScanResult(result);

                        if (SinovoBle.getInstance().isDfuMode()){
                            Log.w(TAG,"dfu升级模式下，扫描得到的锁mac："+ bleScanDevice.GetDevice().getAddress());

                            //开始连接升级后，停止蓝牙扫描  add wrk 20211020
                            if (bleScanDevice.GetDevice().getAddress().equals(SinovoBle.getInstance().getDfu_mac())) {
                                Log.d(TAG,"DFU 升级模式下，扫描得到的mac地址 为 dfu的地址，可以连接进行升级");
                                SinovoBle.getInstance().stopScanBLE();
                               // new Handler(Looper.getMainLooper()).postDelayed(() -> SinovoBle.getInstance().connectBle(bleScanDevice.GetDevice()), 500);
                            }
                            return;
                        }

                        if (SinovoBle.getInstance().isGWConfigMode()){
                            Log.w(TAG,"网关配网模式下，扫描得到的锁mac："+ bleScanDevice.GetDevice().getAddress());
                            SinovoBle.getInstance().setScanAgain(false);
                            BleScanCallBack.getInstance(iScanCallBack).stopScan();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> SinovoBle.getInstance().connectGW(bleScanDevice.GetDevice()), 800);
                            return;
                        }

                        if (!SinovoBle.getInstance().isScanOnly()){
                            for (int i = 0; i< SinovoBle.getInstance().getScanLockList().size(); i++){
                                if (SinovoBle.getInstance().getScanLockList().get(i).GetDevice().getAddress().equals(bleScanDevice.GetDevice().getAddress())){

                                    //尝试进行自动连接
                                    if (SinovoBle.getInstance().isBindMode()) {
                                        Log.w(TAG, "绑定模式下，自动进行连接："+bleScanDevice.GetDevice().getAddress());
                                        SinovoBle.getInstance().setScanAgain(false);
                                        BleScanCallBack.getInstance(iScanCallBack).stopScan();
                                        SinovoBle.getInstance().connectBle(bleScanDevice.GetDevice());
                                    }else {
                                        Log.w(TAG, "非绑定模式下，getToConnectLockList size："+ SinovoBle.getInstance().getToConnectLockList().size());
                                        for (BleConnectLock bleConnectLock : SinovoBle.getInstance().getToConnectLockList()){
                                            String mac = bleConnectLock.getLockMac();
                                            Log.w(TAG,"lockmac:"+ mac + ",ble mac:"+ bleScanDevice.GetDevice().getAddress());
                                            if (bleScanDevice.GetDevice().getAddress().equals(mac)){

                                                Log.w(TAG, "[Ble connect] 开始连接之前，先停止扫描");
                                                SinovoBle.getInstance().setFoundlock(true);
                                                SinovoBle.getInstance().setScanAgain(false);
                                                BleScanCallBack.getInstance(iScanCallBack).stopScan();

                                                //add wrk 20210421,在开始连接某一把锁之后，就不能自动连接其他锁
                                                SinovoBle.getInstance().getToConnectLockList().clear();
                                                SinovoBle.getInstance().getToConnectLockList().add(bleConnectLock);

                                                Log.w(TAG, "[Ble connect] 开始进行自动连接xx:"+ bleScanDevice.GetDevice().getAddress());
                                                SinovoBle.getInstance().connectBle(bleScanDevice.GetDevice());
//                                                new Handler(Looper.getMainLooper()).postDelayed(() -> SinovoBle.getInstance().connectBle(bleScanDevice.GetDevice()), 1000);
                                                break;
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public  void  onScanFailed(int errorCode){
                        Log.e(TAG, "Failed to scan");
                        SinovoBle.getInstance().setFoundlock(false);
                        SinovoBle.getInstance().setFoundSomeBLE(false);
                    }
                };
            }
        }
        return instance;
    }

    /**
     *  处理蓝牙扫描得到的解析结果
     */
    BleScanDevice analyzeScanResult(ScanResult result) {
        ScanRecord mScanRecord = result.getScanRecord();
        byte[] manufacturerData = new byte[0];
        int mRssi = result.getRssi();
        BluetoothDevice scanLock = result.getDevice();
        String scanLockMac  = scanLock.getAddress();
        String scanLockType = scanLock.getName();

        if (mScanRecord != null) {
            SparseArray<byte[]> mandufacturerDatas = mScanRecord.getManufacturerSpecificData();
            for (int i=0; i<mandufacturerDatas.size(); i++){
                manufacturerData = mandufacturerDatas.get(i);
            }
        }

        String madvData = byte2hex(manufacturerData);

        String qrcode = "";
        if (madvData.length() >12){
            qrcode = madvData.substring(0,12);
        }

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("lockMac", scanLockMac);
        map.put("lockType", scanLockType);
        map.put("Rssi", mRssi);
        map.put("qrcode", qrcode);

        if (!SinovoBle.getInstance().isDfuMode()) {
            iScanCallBack.onDeviceFound(JSONObject.toJSONString(map));
        }
      //  Log.d(TAG, "Scan result：{ Mac address:" +scanLockMac + " Lock name："+scanLockType + " Rssi:"+mRssi + " Adv_data:"+byte2hex(manufacturerData)+"}");

        //将扫描到的设备 放入到 list中
        boolean deviceExist = false;

        //判断扫描到的锁 是不是已经存在扫描列表中，如果已经存在，则更新其信息
        for (int i = 0; i < SinovoBle.getInstance().getScanLockList().size(); i++) {
            BleScanDevice bleScanDevice = SinovoBle.getInstance().getScanLockList().get(i);
            if (bleScanDevice.GetDevice().getAddress().compareTo(scanLockMac)== 0){
                String nowtime = getNowTime();
                bleScanDevice.ReflashInf(scanLock, mRssi, manufacturerData, nowtime);
                deviceExist = true;
            }
        }

        //判断扫描到的锁 是不是已经存在扫描列表中，如果不存在，则添加此锁
        //增加判断条件，针对绑定的时候，如果该锁已经尝试过绑定了，不能再进入扫描结果中 再次来绑定了
        boolean deviceIn = true;

        if (!deviceExist){
            if (SinovoBle.getInstance().isBindMode()){
                //已经绑定列表中包含此锁，则不再添加
                if (SinovoBle.getInstance().getBondBleMacList().contains(scanLockMac)){
                    Log.i(TAG, "锁" +scanLockMac +" 已经存在 bondBleMacList中，已经绑定过,不是要连接的锁，不需要再添加");
                    deviceIn = false;
                }else  {
                    //如果广播包内容为空，旧的lock,需要兼容
                    if (manufacturerData == null || manufacturerData.length ==0){
                        Log.i(TAG, "锁" +scanLockMac +" 广播包内容为空，或是长度为0，兼容旧版本，允许连接它");
                        deviceIn = true;
                    }else {
                        String advData = byte2hex(manufacturerData);
                        //兼容第二批 旧锁
                        if (advData.length() >= 12){
                            String adLockid = advData.substring(0, 12);
                            if (adLockid.equals(SinovoBle.getInstance().getLockQRCode())) {
                                Log.w(TAG, "Adv_data's qrcode:" + adLockid + " is matches the qrcode(" + SinovoBle.getInstance().getLockQRCode() + ") entered by user,connect and stop scan");
                                SinovoBle.getInstance().setScanAgain(false);
                                BleScanCallBack.getInstance(iScanCallBack).stopScan();
                            }else {
                                String advtype = advData.substring(0, 2);

                                if (advtype.equals("02")){        //兼容第二批， 02 表示是广播的日志
                                    Log.w(TAG, "兼容第二批就锁，广播包为02开头，运行加入" + advData );
                                }else if (advtype.equals("01")) {
                                    if (advData.length() >= 14){
                                        adLockid = advData.substring(2, 14);
                                        if (adLockid.equals(SinovoBle.getInstance().getLockQRCode())) {
                                            Log.w(TAG, "Adv_data's qrcode:" + adLockid + " is matches the qrcode(" + SinovoBle.getInstance().getLockQRCode() + ") entered by user,connect and stop scan");
                                            SinovoBle.getInstance().setScanAgain(false);
                                            BleScanCallBack.getInstance(iScanCallBack).stopScan();
                                        }else {
                                            deviceIn = false;
                                            Log.w(TAG, "Adv_data's qrcode:" + adLockid + " is different from the qrcode(" + SinovoBle.getInstance().getLockQRCode() + ") entered by user,ignore");
                                        }
                                    }else {
                                        deviceIn = false;
                                        Log.w(TAG, "Adv_data's qrcode:" + adLockid + " is different from the qrcode(" + SinovoBle.getInstance().getLockQRCode() + ") entered by user,ignore");
                                    }
                                }else {
                                    deviceIn = false;
                                    Log.w(TAG, "Adv_data's qrcode:" + adLockid + " is different from the qrcode(" + SinovoBle.getInstance().getLockQRCode() + ") entered by user,ignore");
                                }
                            }
                        }else {
                            Log.i(TAG, "锁" +scanLockMac +" 存在广播包，但长度小于14，不合法，不连接他");
                            deviceIn = false;
                        }
                    }
                }
            }else {     //非绑定模式下，对比mac地址即可
                deviceIn = false;   //默认不符合加入
                for (int i = 0; i< SinovoBle.getInstance().getToConnectLockList().size(); i++){
                    BleConnectLock myConnectLock = SinovoBle.getInstance().getToConnectLockList().get(i);
                    Log.d(TAG, "自动连接连接中锁的mac：" + myConnectLock.getLockMac());
                    if (myConnectLock.getLockMac().equals(scanLockMac)){
                        Log.d(TAG,"[Ble connect]该设备是需要自动连接的设备："+ scanLockMac);
                        deviceIn = true;
                        break;
                    }
                }
            }

            if (deviceIn) {
                SinovoBle.getInstance().getScanLockList().add(new BleScanDevice(scanLock, mRssi, manufacturerData, getNowTime()));
                Log.i(TAG, "Get a new lock:" + scanLockMac + " time:" + getNowTime());
            }
        }

        //过滤 ，删除掉时间早于 10s之前的锁
        for (int i = 0; i < SinovoBle.getInstance().getScanLockList().size(); ){
            BleScanDevice bleScanDevice = SinovoBle.getInstance().getScanLockList().get(i);
            if (calTimeDiff(bleScanDevice.getJoinTime(),getNowTime())>10){
                SinovoBle.getInstance().getScanLockList().remove(i);
            }else {
                i++;
            }
        }

        return new BleScanDevice(scanLock, mRssi, manufacturerData, getNowTime());
    }

    //停止蓝牙扫描
    //参数 ，是否立即停止（自动扫描的话，停止之后会判断 是否还需要重新扫描）
    public void stopScan() {
        setScanning(false);
        SinovoBle.getInstance().removeScanHandlerMsg();

        if (SinovoBle.getInstance().getBluetoothAdapter() == null) {
            Log.d(TAG, "Bluetooth Adapter is null");
            return;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            return ;
        }

        SinovoBle.getInstance().getBluetoothAdapter().getBluetoothLeScanner().stopScan(instance);
        Log.d(TAG, "force to  stop ble scan ,scanAgain:"+ SinovoBle.getInstance().isScanAgain() +",bindmode:"+ SinovoBle.getInstance().isBindMode());
        if (SinovoBle.getInstance().isScanAgain() || SinovoBle.getInstance().isBindMode()) {
            Log.d(TAG, "now stop and scan again after 1000ms");
            SinovoBle.getInstance().getScanBleHandler().postDelayed(() -> SinovoBle.getInstance().bleScan(iScanCallBack), 1000);
        }

        if (!SinovoBle.getInstance().isBindMode() && !SinovoBle.getInstance().isScanOnly()
                && !SinovoBle.getInstance().isDfuMode() &&!SinovoBle.getInstance().isGWConfigMode()) {
//            if (SinovoBle.getInstance().getScanRound() == 3 && !SinovoBle.getInstance().isFoundSomeBLE()) {
//                int scanCount = SinovoBle.getInstance().getScanRound() - 1;
//                Log.e(TAG, "扫描了 " + scanCount + " 次，但扫不到任何设备");
//                iScanCallBack.onNotFoundBles20s();
//            }

//            if (SinovoBle.getInstance().getScanRound() == 6 && SinovoBle.getInstance().isFoundSomeBLE()
//                    && !SinovoBle.getInstance().isFoundlock() ) {
            if (SinovoBle.getInstance().getScanRound() == 6 && !SinovoBle.getInstance().isFoundlock() ) {
                int scanCount = SinovoBle.getInstance().getScanRound() - 1;
                Log.e(TAG, "扫描了 " + scanCount + " 次，可以扫描到其他ble，但扫描不到指定的锁");
                iScanCallBack.onNotFoundLock40s();
            }
        }
    }
}
