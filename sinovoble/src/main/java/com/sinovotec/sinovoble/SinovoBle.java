package com.sinovotec.sinovoble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sinovotec.gwSmartConfig.GWSmartConfigCallback;
import com.sinovotec.sinovoble.callback.BleConnCallBack;
import com.sinovotec.sinovoble.callback.BleScanCallBack;
import com.sinovotec.sinovoble.callback.IConnectCallback;
import com.sinovotec.sinovoble.callback.IScanCallBack;
import com.sinovotec.sinovoble.common.BleData;
import com.sinovotec.sinovoble.common.BleConnectLock;
import com.sinovotec.sinovoble.common.BleConstant;
import com.sinovotec.sinovoble.common.BleScanDevice;
import com.sinovotec.sinovoble.common.BluetoothListenerReceiver;
import com.sinovotec.sinovoble.common.ComTool;
import com.sinovotec.encryptlib.LoadLibJni;
import com.sinovotec.sinovoble.common.DfuUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import blufi.espressif.BlufiCallback;
import blufi.espressif.BlufiClient;
import blufi.espressif.response.BlufiStatusResponse;
import no.nordicsemi.android.dfu.DfuProgressListener;

import static android.content.Context.WIFI_SERVICE;

public class SinovoBle {
    private final String TAG    = "SinovoBle";
    private String lockQRCode ;                   //??????????????????????????????????????????????????????
    private String userIMEI;                      //?????????imei???????????????id
    private String lockMAC;                       //????????????????????????mac??????
    private String lockSNO      = "";             //??????????????????????????????????????? ?????????
    private String lockName;                      //????????????
    private String appFilePath  = "";             // APP????????????????????????
    private String dfu_mac      = "";             //dfu ????????? ?????????mac??????

    private String bleServiceUUID;                //???????????????UUID
    private String blecharacteristUUID;           //??????????????????UUID

    private boolean isBindMode  = false;          //?????????????????????
    private boolean isScanAgain = false;          //?????????????????????????????????
    private boolean isConnected = false;          //????????????????????????, ???????????????????????????
    private boolean isLinked    = false;          //???????????????????????????????????????????????? ??????????????? ????????????????????? ??????????????????????????????
    private boolean isScanOnly  = false;          //????????????????????????????????????
    private boolean isDfuMode   = false;          //????????? dfu ????????????
    private boolean restartBLE  = false;          //????????????????????????????????????????????????????????????????????????????????? ??????
    private boolean isConnectting   = false;      //??????????????????
    private String connectTime      = "";         //????????????????????????

    private boolean isGWConfigMode  = false;       //??????????????????
    private String gwWifiPass       = "";           //?????????????????????wifi?????????

    private Context context;                       //?????????
    private BluetoothAdapter bluetoothAdapter;     //???????????????

    private final Handler scanBleHandler       = new Handler(Looper.getMainLooper());  //?????????????????????handler?????????5s??? ???????????? ?????????????????????
    private final Handler bindTimeoutHandler   = new Handler(Looper.getMainLooper());  //?????????????????????handler?????????2????????????????????????
    private final Handler autoConnetHandler    = new Handler(Looper.getMainLooper());  //?????????????????????handler??????????????????????????????????????????30s ???????????????????????????????????????????????????

    private final ArrayList<BleScanDevice> scanLockList ;         //?????????????????????
    private final ArrayList<String> bondBleMacList;               //???????????????????????????????????????????????? ,???????????????????????????????????????????????????????????????
    private ArrayList<BleConnectLock> toConnectLockList;         //???????????????????????????????????????mac?????????sno

    @SuppressLint("StaticFieldLeak")
    private static SinovoBle instance;                //??????????????????

    private IScanCallBack mBleScanCallBack;     //?????????????????????
    private IConnectCallback mConnCallBack;     //?????????????????????
    private BleConnectLock bleConnectLock_dfu;   //dfu?????????????????? ???????????????

    private LoadLibJni myJniLib;

    private boolean foundSomeBLE = false;           // ?????????????????????????????? ????????????????????????????????????
    private boolean foundlock = false;              //??????????????? ??????????????????
    private int  scanRound = 0;                     //?????????????????????????????? ???????????????????????????????????????5s??????1s

    private WifiManager mWifiManager;
    private BlufiClient blufiClient;                        //????????????????????? ????????????
    private byte[] wifiInfo;
    private GWSmartConfigCallback gwConfigCallback;         //?????????????????????????????????

    private final ArrayList<BleConnectLock> tmpConnectLockList;         //???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????

    /**
     * Instantiate  SinovoBle class
     * @return SinovoBle
     */
    public static SinovoBle getInstance() {
        if (instance == null) {
            synchronized (SinovoBle.class) {      //?????????,????????????????????????????????????synchronized(this)?????????????????????????????????????????????????????????????????????
                if (instance == null) {
                    instance = new SinovoBle();
                }
            }
        }
        return instance;
    }

    private SinovoBle() {
        scanLockList        = new ArrayList<>();
        bondBleMacList      = new ArrayList<>();
        toConnectLockList   = new ArrayList<>();
        tmpConnectLockList  = new ArrayList<>();
    }

    public String getLockQRCode() {
        return lockQRCode;
    }

    public boolean isBindMode() {
        return isBindMode;
    }

    public String getUserIMEI() {
        return userIMEI;
    }

    public String getLockMAC() {
        return lockMAC;
    }

    public boolean isScanAgain() {
        return isScanAgain;
    }

    public String getBleServiceUUID() {
        return bleServiceUUID;
    }

    public boolean isBleConnected() {
        return isConnected;
    }

    public Handler getBindTimeoutHandler() {
        return bindTimeoutHandler;
    }

    public Handler getAutoConnetHandler() {
        return autoConnetHandler;
    }

    public String getBlecharacteristUUID() {
        return blecharacteristUUID;
    }

    public String getLockSNO() {
        return lockSNO;
    }

    public String getLockName() {
        return lockName;
    }

    public ArrayList<String> getBondBleMacList() {
        return bondBleMacList;
    }

    public ArrayList<BleScanDevice> getScanLockList() {
        return scanLockList;
    }

    public ArrayList<BleConnectLock> getToConnectLockList() {
        return toConnectLockList;
    }

    public Context getContext() {
        return context;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public IConnectCallback getmConnCallBack() {
        return mConnCallBack;
    }

    public IScanCallBack getmBleScanCallBack() {
        return mBleScanCallBack;
    }

    public Handler getScanBleHandler() {
        return scanBleHandler;
    }

    public void setLockQRCode(String lockQRCode) {
        this.lockQRCode = lockQRCode;
    }

    public void setBindMode(boolean bindMode) {
        isBindMode = bindMode;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public void setUserIMEI(String uIMEI) {
        this.userIMEI = uIMEI;
    }

    public void setLockMAC(String lockMAC) {
        this.lockMAC = lockMAC;
    }

    public void setLockSNO(String lockSNO) {
        this.lockSNO = lockSNO;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }

    public void setScanAgain(boolean scanAgain) {
        isScanAgain = scanAgain;
    }

    public void setBleServiceUUID(String bleServiceUUID) {
        this.bleServiceUUID = bleServiceUUID;
    }

    public void setBlecharacteristUUID(String blecharacteristUUID) {
        this.blecharacteristUUID = blecharacteristUUID;
    }

    public void setToConnectLockList(ArrayList<BleConnectLock> toConnectLockList) {
        this.toConnectLockList = toConnectLockList;
    }

    public boolean isLinked() {
        return isLinked;
    }

    public void setLinked(boolean linked) {
        isLinked = linked;
    }

    public void setConnectting(boolean connectting) {
        isConnectting = connectting;
    }

    public boolean isConnectting() {
        return isConnectting;
    }

    public void setConnectTime(String connectTime) {
        this.connectTime = connectTime;
    }

    public String getConnectTime() {
        return connectTime;
    }

    public boolean isScanOnly() {
        return isScanOnly;
    }

    public void setScanOnly(boolean scanOnly) {
        isScanOnly = scanOnly;
    }

    public boolean isDfuMode() {
        return isDfuMode;
    }

    public void setDfuMode(boolean dfuMode) {
        isDfuMode = dfuMode;
    }

    public String getDfu_mac() {
        return dfu_mac;
    }

    public void setDfu_mac(String dfu_mac) {
        this.dfu_mac = dfu_mac;
    }

    public BleConnectLock getBleConnectLock_dfu() {
        return bleConnectLock_dfu;
    }

    public void setBleConnectLock_dfu(BleConnectLock bleConnectLock_dfu) {
        this.bleConnectLock_dfu = bleConnectLock_dfu;
    }

    public boolean isFoundSomeBLE() {
        return foundSomeBLE;
    }

    public boolean isFoundlock() {
        return foundlock;
    }

    public int getScanRound() {
        return scanRound;
    }

    public void setFoundlock(boolean foundlock) {
        this.foundlock = foundlock;
    }

    public void setFoundSomeBLE(boolean foundSomeBLE) {
        this.foundSomeBLE = foundSomeBLE;
    }

    public void setScanRound(int scanRound) {
        this.scanRound = scanRound;
    }

    public boolean isRestartBLE() {
        return restartBLE;
    }

    public void setRestartBLE(boolean restartBLE) {
        this.restartBLE = restartBLE;
    }

    public boolean isGWConfigMode() {
        return isGWConfigMode;
    }

    public String getGwWifiPass() {
        return gwWifiPass;
    }

    public void setGWConfigMode(boolean GWConfigMode) {
        isGWConfigMode = GWConfigMode;
    }

    public void setGwWifiPass(String gwWifiPass) {
        this.gwWifiPass = gwWifiPass;
    }

    public BlufiClient getBlufiClient() {
        return blufiClient;
    }

    public byte[] getWifiInfo() {
        return wifiInfo;
    }

    public void setWifiInfo(byte[] wifiInfo) {
        this.wifiInfo = wifiInfo;
    }

    public void setGwConfigCallback(GWSmartConfigCallback gwConfigCallback) {
        this.gwConfigCallback = gwConfigCallback;
    }

    public ArrayList<BleConnectLock> getTmpConnectLockList() {
        return tmpConnectLockList;
    }

    public LoadLibJni getMyJniLib() {
        if (myJniLib == null){
            if (LoadLibJni.LoadLib()){
                Log.d(TAG, "load SO lib success");
                myJniLib = new LoadLibJni();
            }else {
                Log.e(TAG, "load SO lib failed");
            }
        }
        return myJniLib;
    }

    public String getAppFilePath() {
        return appFilePath;
    }

    private final DfuProgressListener progressListener = new DfuProgressListener() {
        @Override
        public void onDeviceConnecting(@NonNull String s) {
            mConnCallBack.onDFUDeviceConnecting(s);
        }

        @Override
        public void onDeviceConnected(@NonNull String s) {
            mConnCallBack.onDFUDeviceConnected(s);
        }

        @Override
        public void onDfuProcessStarting(@NonNull String s) {
            mConnCallBack.onDfuProcessStarting(s);
        }

        @Override
        public void onDfuProcessStarted(@NonNull String s) {
            mConnCallBack.onDfuProcessStarted(s);
        }

        @Override
        public void onEnablingDfuMode(@NonNull String s) {
            mConnCallBack.onDFUEnablingDfuMode(s);
        }

        @Override
        public void onProgressChanged(@NonNull String s, int i, float v, float v1, int i1, int i2) {
            mConnCallBack.onDFUProgressChanged(i);
        }

        @Override
        public void onFirmwareValidating(@NonNull String s) {
            mConnCallBack.onDFUFirmwareValidating(s);
        }

        @Override
        public void onDeviceDisconnecting(String s) {
            mConnCallBack.onDFUDeviceDisconnecting(s);
        }

        @Override
        public void onDeviceDisconnected(@NonNull String s) {
            if (isDfuMode()) {
                SinovoBle.getInstance().setLinked(false);
                SinovoBle.getInstance().setConnected(false);
                SinovoBle.getInstance().setConnectting(false);
                BleData.getInstance().setExeCmding(false);
                setDfuMode(false);
                mConnCallBack.onDFUDeviceDisconnected(s);
            }
        }

        @Override
        public void onDfuCompleted(@NonNull String s) {
            SinovoBle.getInstance().setLinked(false);
            SinovoBle.getInstance().setConnected(false);
            SinovoBle.getInstance().setConnectting(false);
            BleData.getInstance().setExeCmding(false);
            setDfuMode(false);

            getToConnectLockList().clear();
            if (getBleConnectLock_dfu()!=null){
                getToConnectLockList().add(getBleConnectLock_dfu());
            }
            mConnCallBack.onDfuCompleted(s);
        }

        @Override
        public void onDfuAborted(@NonNull String s) {
            SinovoBle.getInstance().setLinked(false);
            SinovoBle.getInstance().setConnected(false);
            SinovoBle.getInstance().setConnectting(false);
            BleData.getInstance().setExeCmding(false);
            setDfuMode(false);

            getToConnectLockList().clear();
            if (getBleConnectLock_dfu()!=null){
                getToConnectLockList().add(getBleConnectLock_dfu());
            }
            mConnCallBack.onDfuAborted(s);
        }

        @Override
        public void onError(@NonNull String s, int i, int i1, String s1) {
            SinovoBle.getInstance().setLinked(false);
            SinovoBle.getInstance().setConnected(false);
            SinovoBle.getInstance().setConnectting(false);
            BleData.getInstance().setExeCmding(false);
            setDfuMode(false);

            getToConnectLockList().clear();
            if (getBleConnectLock_dfu()!=null){
                getToConnectLockList().add(getBleConnectLock_dfu());
            }
            mConnCallBack.onDFUError(s, i, i1, s1);
        }
    };

    /**
     * Initialize Ble
     * @param context context
     * @param iScanCallBack  ble scan callback
     * @param iConnectCallback  ble connect callback
     */
    public boolean init(Context context, IScanCallBack iScanCallBack, IConnectCallback iConnectCallback) {
        if (this.context == null && context != null) {
            this.context = context.getApplicationContext();

            BluetoothManager bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            mBleScanCallBack = iScanCallBack;
            mConnCallBack    = iConnectCallback;

            BluetoothListenerReceiver receiver = new BluetoothListenerReceiver();
            context.registerReceiver(receiver,makeFilter());
            appFilePath = Objects.requireNonNull(context.getExternalFilesDir("SinovoLib")).getPath();
            BleConnCallBack.getInstance().releaseBle();

            //?????????wifi
            mWifiManager = (WifiManager) this.context.getSystemService(WIFI_SERVICE);
        }

        //???????????????????????????????????????
        DfuUtils.getInstance().setmDfuProgressListener(context,progressListener);       //??????????????????
        return (bluetoothAdapter !=null && mBleScanCallBack !=null && mConnCallBack !=null);
    }

    //????????????
    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        //20181221 ??????????????????????????? ??? ???????????????????????????
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        //20210126 ????????????wifi??????????????????????????????????????????????????????
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return filter;
    }

    //??????????????????
    public void startBleScan(){
        if (SinovoBle.getInstance().isConnectting()){
            Log.e(TAG,"???????????????????????????????????????");
            return;
        }
        Log.d(TAG, "??????startBleScan  ?????????");
        getBondBleMacList().clear();                //clean the bondBleMacList before starting scan

        //??????????????????????????????????????????
        if (isBindMode() && !isScanOnly()) { bindTimeoutHandler.postDelayed(this::checkScanResult, 90*1000); }
        if (isBindMode() && isScanOnly())  { bindTimeoutHandler.postDelayed(this::checkScanResult, 10*1000); }

        setScanRound(0);
        setFoundSomeBLE(false);
        setFoundlock(false);

        //???????????????
        bleScan(getmBleScanCallBack());
    }

    //??????????????????
    private void checkScanResult(){
        Log.e(TAG,"??????????????????????????????90s, isconnecting:" + isConnectting);
        if (!isBleConnected() && !isScanOnly()){
            Log.d(TAG,"???????????????????????????????????????");
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("scanResult", "0");
            setScanAgain(false);
            setBindMode(false);
            getmBleScanCallBack().onScanTimeout(JSONObject.toJSONString(map));
        }

        if (isScanOnly()){
            setScanAgain(false);
            setBindMode(false);
            getmBleScanCallBack().onScanOnlyFinish();
        }
    }

    /**
     * * Bluetooth scan only
     */
    public void bleScanOnly(){
        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(true);
        setBindMode(true);
        setUserIMEI("");
        disconnBle();

        setScanAgain(true);
        startBleScan();
    }

    /**
     * * connect to lock via qrcode
     * @param qrcode  qrcode of the lock
     * @param userIMEI   lock will bind to the user
     */
    public void connectLockViaQRCode(String qrcode, String userIMEI){
        Log.d(TAG, "?????????????????? qrcode???"+ qrcode + ", IMEI:"+ userIMEI);
        if (qrcode.length() !=12 || userIMEI.length() != 12 ){
            Log.d(TAG, "qrcode or imei error");
            return;
        }

        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(false);
        setBindMode(true);
        setGWConfigMode(false);
        setLockQRCode(qrcode);
        setUserIMEI(userIMEI);
        SinovoBle.getInstance().getScanLockList().clear();  //?????????????????????

        disconnBle();
        setScanAgain(true);
        startBleScan();
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * @param autoConnectList  ??????????????????????????????
     */
    public void connectLockViaMacSno(final ArrayList<BleConnectLock> autoConnectList){
        if (mBleScanCallBack == null || mConnCallBack ==null){
            Log.e(TAG,"ScanCallBack or mConnCallBack is null???please init ble again");
            //??????????????????????????????
            return;
        }
        //????????????????????? ?????????????????????
        getToConnectLockList().clear();
        getToConnectLockList().addAll(autoConnectList);
        setScanOnly(false);
        setBindMode(false);
        setGWConfigMode(false);
        setScanAgain(true);
        startBleScan();
    }

    /**
     * * connect to esp32 ????????????
     * @param wifiSSID  WIFI SSID
     * @param wifiPass  WIFI PASS
     */
    public void configureGW(GWSmartConfigCallback GWSmartConfigCallback, String wifiSSID, String wifiPass){

        Log.d(TAG, "??????????????????????????? wifi ssid???"+ wifiSSID + ", wifi pass:"+ wifiPass);
        if (wifiSSID.isEmpty() || wifiPass.isEmpty() ){
            Log.d(TAG, "wifi ssid or pass is empty");
            return;
        }

        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(false);
        setBindMode(false);
        setGWConfigMode(true);
        SinovoBle.getInstance().getScanLockList().clear();  //?????????????????????

        getTmpConnectLockList().clear();
        getTmpConnectLockList().addAll(SinovoBle.getInstance().getToConnectLockList());

        Log.d(TAG, "??????????????????????????????????????????????????????????????????????????????"+getTmpConnectLockList().size());

        disconnBle();
        setScanAgain(true);
        startBleScan();

        setGwWifiPass(wifiPass);
        setGwConfigCallback(GWSmartConfigCallback);

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null) {
            setWifiInfo(getSSIDRawData(info));
        }
    }

    /**
     * ????????????????????????
     */
    public void finishConfigureGW(){
        BleConnCallBack.getInstance().setMyBleDevice(null);
        if (blufiClient != null) {
            blufiClient.requestCloseConnection();
            blufiClient.close();
            blufiClient = null;
        }

        SinovoBle.getInstance().setGWConfigMode(false);
        SinovoBle.getInstance().setLinked(false);
        SinovoBle.getInstance().setConnected(false);

        getToConnectLockList().clear();

        Log.d(TAG,"???????????????????????????????????? ???????????????tmpLockList:"+ getTmpConnectLockList().size());

        if (!getTmpConnectLockList().isEmpty()){
            connectLockViaMacSno(getTmpConnectLockList());
        }
    }

    private byte[] getSSIDRawData(WifiInfo info) {
        try {
            Method method = info.getClass().getMethod("getWifiSsid");
            method.setAccessible(true);
            Object wifiSsid = method.invoke(info);
            if (wifiSsid == null) {
                return null;
            }
            method = wifiSsid.getClass().getMethod("getOctets");
            method.setAccessible(true);
            return (byte[]) method.invoke(wifiSsid);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Try to connect device
     */
    public void connectGW(BluetoothDevice bluetoothDevice) {

        if (blufiClient != null) {
            blufiClient.close();
            blufiClient = null;
        }

        blufiClient = new BlufiClient(getContext(), bluetoothDevice);
        blufiClient.setGattCallback(BleConnCallBack.getInstance());
        blufiClient.setBlufiCallback(new BlufiCallbackMain());
        blufiClient.connect();

        BleConnCallBack.getInstance().setMyBleDevice(bluetoothDevice);
    }

    /**
     * ????????????,??????????????????????????????
     * @param userName string
     */
    public void addUser(String userName, String lockSNO, String lockMacAddress){
        if (userName.isEmpty() || userName.length()>10){
            Log.e(TAG, "Username is error???The length of the username is 0-10 characters");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LockSno is error???The length of LockSno is 6 characters");
            return ;
        }

        String data = lockSNO +ComTool.stringToAscii(userName);
        BleData.getInstance().exeCommand("02", data, false);
    }

    /**
     * ??????????????????????????????
     * @param userName ?????????
     * @param userNID   ?????????nid
     */
    public void updateUserName(String userName, String userNID, String lockSNO, String lockMacAddress){
        if (userNID.isEmpty() || userName.length()>10 ){
            Log.e(TAG, "parameter is error???The length of the username is 0-10 characters");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LockSno is error???The length of LockSno is 6 characters");
            return ;
        }

        String username = "";
        if (!userName.isEmpty()){
            username = ComTool.stringToAscii(userName);
        }
        String updateStr = lockSNO +userNID + username;
        BleData.getInstance().exeCommand("03", updateStr, false);
    }

    /**
     * ???????????????????????????,?????????????????????
     * @param userNID       ??????nid
     * @param dataType      ??????????????? 02 ???????????????03?????????????????????06?????????07????????????08??????????????????
     * @param data          ??????????????????????????????????????? ??????????????????/????????????????????????
     */
    public void addDataForUser(String userNID, String dataType, String data, String lockSNO, String lockMacAddress){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //???????????????
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (data.isEmpty()){
                Log.e(TAG,"Parameter error");
                return ;
            }

            String data_s = lockSNO +userNID + dataType + data;
            BleData.getInstance().exeCommand("05", data_s, false);
        }

        //????????????????????????????????????
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data_s = lockSNO +userNID + dataType ;
            BleData.getInstance().exeCommand("05", data_s, true);
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * @param dataType s
     * @param delID s
     */
    public void delData(String dataType, String delID, String lockSNO, String lockMacAddress){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        if (!dataType.equals("0e")){
            String data = lockSNO +dataType + delID ;
            BleData.getInstance().exeCommand("06", data, false);
        }else {
            //????????????
            String data = lockSNO + delID ;
            BleData.getInstance().exeCommand("1b", data, false);
        }
    }

    /**
     * ?????????????????????
     * @param userNid  ?????????id
     * @param codeType ???????????????
     * @param codeID   ?????????ID
     * @param newCode  ????????????
     */
    public void resetCode(String userNid, String codeType, String codeID, String newCode, String lockSNO, String lockMacAddress){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO + userNid + codeType + codeID + newCode;
        BleData.getInstance().exeCommand("0d", data, false);
    }

    /**
     * Set properties of the lock
     * @param dataType
     *                 01 set lockname???1-10 characters
     *                 02 set time of the lock???format :YYMMDDHHMMSS
     *                 03 set auto-lock time ,0 means disable auto-lock
     *                 04 set volume, 00 mute , 01-05 volume
     *                 05 set auto-create user
     *                 06 superuser's permission
     *                     permission of share code:  data is 01,03,05,07,09,11,13,15
     *                     permission of manager user: data is 02???03???06???07???10???11???14???15
     *                     permission of setting: data is 04???05???06???07???12???13???14???15
     *                     permission of checking log: data is 08???09???10???11???12???13???14???15
     *                 07 set the lock into DFU OTA , update Firmware
     * @param data  value
     */
    public void setLockInfo(String dataType, String data, String lockSNO, String lockMacAddress){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        //??????????????????
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return ;
            }

            String locknameAscii = ComTool.stringToAscii(data);
            String setData = lockSNO + locknameAscii;
            BleData.getInstance().exeCommand("11", setData, false);
        }

        //??????????????????
        if (dataType.equals("02")){
            String setData = lockSNO + data;
            BleData.getInstance().exeCommand("10", setData, false);
        }

        //??????????????????????????????
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.parseInt(data) <0 || Integer.parseInt(data)>240){
                    Log.e(TAG,"Parameter error");
                    return ;
                }

                sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = lockSNO + sixteen;
            BleData.getInstance().exeCommand("16", setData, false);
        }

        //???????????? ??????????????????????????????
        if (dataType.equals("04") || dataType.equals("05")){
            if (data.isEmpty()){
                data = "02";
            }
            if (Integer.parseInt(data)<0 ){
                Log.e(TAG,"Parameter error");
                return ;
            }

            if (dataType.equals("04")){
                String sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
                String setData = lockSNO + sixteen;
                Log.e(TAG, "??????????????????????????????"+ sixteen + ",?????? sno???"+ setData);
                BleData.getInstance().exeCommand("1c", setData, false);
            }else {
                String setData = lockSNO + data;
                BleData.getInstance().exeCommand("09", setData, false);
            }
        }

        //???????????????????????????
        if (dataType.equals("06")){
            String setData = lockSNO + data;
            BleData.getInstance().exeCommand("23", setData, false);
        }

        //????????????ota????????????
        if (dataType.equals("07")){
            BleData.getInstance().exeCommand("19", lockSNO, false);
        }
    }

    /**
     * get information of the lock
     * @param dataType
     *                 01 get admin's info
     *                 02 get power of the lock
     *                 03 get status of the lock ,is it unlocked?
     *                 04 get hardware information
     *                 05 get the lockname
     *                 06 get time of the lock
     *                 07 get auto-lock time of the lock
     *                 08 get the volume setting
     *                 09 get the auto-create setting
     *                 10 get the superUser's priority
     *                 11 get the basetime of the lock
     *                 12 get lock info???power???status???auto-lock???volume???auto-create???superUser???firmware???
     */
    public void getLockInfo(String dataType ,String lockSNO, String lockMacAddress){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (dataType.equals("01")){ BleData.getInstance().exeCommand("12", lockSNO, false); }
        if (dataType.equals("02")){ BleData.getInstance().exeCommand("0e", lockSNO, false); }
        if (dataType.equals("03")){ BleData.getInstance().exeCommand("0f", lockSNO, false); }
        if (dataType.equals("04")){ BleData.getInstance().exeCommand("1a", lockSNO, false); }

        //add by wrk at 20210315
        if (dataType.equals("05")){ BleData.getInstance().exeCommand("11", lockSNO, false); }
        if (dataType.equals("06")){ BleData.getInstance().exeCommand("10", lockSNO, false); }
        if (dataType.equals("07")){ BleData.getInstance().exeCommand("16", lockSNO, false); }
        if (dataType.equals("08")){ BleData.getInstance().exeCommand("1c", lockSNO, false); }
        if (dataType.equals("09")){ BleData.getInstance().exeCommand("09", lockSNO, false); }
        if (dataType.equals("10")){ BleData.getInstance().exeCommand("23", lockSNO, false); }
        if (dataType.equals("11")){ BleData.getInstance().exeCommand("1f", lockSNO, false); }
        if (dataType.equals("12")){ BleData.getInstance().exeCommand("2c", lockSNO, false); }
    }

    /**
     * ?????????????????????????????????
     */
    public void getAllUsers(String lockSNO, String lockMacAddress){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        String data = lockSNO +"00";
        BleData.getInstance().exeCommand("13", data, false);
    }

    /**
     * ????????????
     * @param logID  ????????????????????????id ,?????????????????????????????????????????????id????????????????????? id??? ff ???????????????????????????
     */
    public void getLog(String logID, String lockSNO, String lockMacAddress){

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        String data = lockSNO + logID;
        BleData.getInstance().exeCommand("17", data, false);
    }

    /**
     * ??????/?????? ????????????
     * @param dynamicCode  ????????? ????????????
     * @param enable  00 ??????????????? 01 ????????????
     */
    public void doDynamicCode(String dynamicCode, String enable, String lockSNO, String lockMacAddress){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        String data = lockSNO + enable + dynamicCode;
        BleData.getInstance().exeCommand("20", data, false);
    }

    /**
     * ????????????????????????????????????????????????????????????
     * @param oldCodeType  ?????????????????? ?????? ???02 ???????????????03 ??????????????????
     * @param codeID       ?????????id
     * @param newCodeType  ?????????????????? ???02 ???????????????03 ??????????????????; ???????????????????????????????????????????????????
     */
    public void updateCodeType(String oldCodeType, String codeID, String newCodeType, String lockSNO, String lockMacAddress){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (oldCodeType.equals(newCodeType)){
            Log.e(TAG,"The new Code type is the same as the old Code type");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        if (newCodeType.equals("02")){
            String data = lockSNO + oldCodeType + codeID + "00";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.equals("03")){
            String data = lockSNO + oldCodeType + codeID + "01";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.isEmpty()){
            String data = lockSNO + oldCodeType + codeID + "02";
            BleData.getInstance().exeCommand("07", data, false);
        }
    }

    /**
     * ????????????
     * @param password ??????
     */
    public void verifyCode(String password, String lockSNO, String lockMacAddress){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO + password;
        BleData.getInstance().exeCommand("08", data, true);
    }

    /**
     * ???????????????
     * @param unlockType 00 ???????????????01????????????
     */
    public void toUnlock(String unlockType, String code, String lockSNO, String lockMacAddress){
        if (unlockType.isEmpty() || code.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO + unlockType + code;
        BleData.getInstance().exeCommand("0a", data, true);
    }

    /**
     * ????????????
     * @param datakType ??????????????????????????????
     *                  00 ??????????????????????????????????????????
     *                  0c ????????????????????????
     */
    public void cleanData(String datakType, String lockSNO, String lockMacAddress){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        //??????mac?????? ????????????????????????mac??????????????????
        String macTmp = SinovoBle.getInstance().getLockMAC().replace(":","");
        if (!macTmp.equalsIgnoreCase(lockMacAddress.replace(":",""))){
            Log.e(TAG, "getLockMAC() is "+ SinovoBle.getInstance().getLockMAC() +"???lockMacAddress:"+lockMacAddress + ",not match");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }
        String data = lockSNO + datakType;
        BleData.getInstance().exeCommand("0c", data, false);
    }

    //?????????????????????
    public void cancelAddLock(){
        if (!isBindMode()){
            return;
        }

        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanAgain(false);
        setBindMode(false);
        BleConnCallBack.getInstance().disConectBle();
    }

    /**????????????????????????
     * Calculate periodic code
     * @param lockmac  lock mac address, such as 00A051F4D44A
     * @param starttime  start time, such as 12:23
     * @param endtime    end time, such as 12:23
     * @return code
     */
    public String calcDyCode(String lockmac, String starttime, String endtime){
        ArrayList<String> codetypelist = new ArrayList<>();
        codetypelist.add("2");
        codetypelist.add("5");
        codetypelist.add("8");

        String result ;
        int rndNum =  Integer.parseInt(ComTool.getRndNumber(1,2,10)) ;
        String codeType  = codetypelist.get(rndNum);

        String starttime_tmp = starttime.replace(" ","");
        String endtime_tmp = endtime.replace(" ","");

        long diff = ComTool.calDateDiff(5,starttime_tmp,endtime_tmp);

        //??????????????????????????? ????????? ??????????????? 10????????????
        if (diff <10*60){
            return "Error:The end time must be more than 10 minutes longer than the start time";
        }

        if ((starttime_tmp.length() == 5 && starttime_tmp.contains(":")) && (endtime_tmp.length() == 5 && endtime_tmp.contains(":"))){
            String macaddr = lockmac.replace(":","").toLowerCase();
            result =  getMyJniLib().getIntervalCode(macaddr, starttime, endtime, codeType);
        }else {
            result = "Error:The time format is wrong, such as 12:34";
        }

        return result;
    }

    /**
     * Calculate one-time code or Timed code
     * @param lockmac lock mac address, such as 00A051F4D44A
     * @param basetime basetime??? such as 2021-03-23 19:14
     * @param starttime start time, such as 2020-07-08 17:18
     * @param valid  Effective time period, such as 3 Days, 1 Hours,  1 Months
     * @param type  0: one-time 1:Timed code
     * @return code
     */
    public String calcDyCode(String lockmac, String basetime,  String starttime, String valid, int type){
        Log.d(TAG, "??????calcDyCode() ????????????????????????lockmac???"+ lockmac + ",basetime:"+ basetime
                + ",starttime:"+ starttime + ",valid:"+valid +",type:"+type );
        ArrayList<String> codetypelist = new ArrayList<>();
        if (type == 0) {
            codetypelist.add("3");
            codetypelist.add("6");
            codetypelist.add("9");
        }else {
            codetypelist.add("1");
            codetypelist.add("4");
            codetypelist.add("7");
        }
        String result ;

        int rndNum =  Integer.parseInt(ComTool.getRndNumber(1,2,10)) ;
        String codeType = codetypelist.get(rndNum);
        String validTmp = valid.replace("Days","d").replace("Hours","h").replace("Months","m");

        String[] starttime_ary = starttime.split(" ");
        if ((starttime_ary.length == 2 && starttime.contains(":"))){
            long diff = ComTool.getTimeDiff(basetime, starttime, 1);
            String validV = validTmp.split(" ")[0];
            String validT = validTmp.split(" ")[1];
            if (diff > 0){
                String macaddr = lockmac.replace(":","").toLowerCase();
                Log.d(TAG, "????????????MyJniLib() ????????????????????????mac???"+ macaddr + ",diff:"+ diff
                        + ",starttime:"+ starttime + ",validv:"+validV +",validT:"+validT +",codeType:" + codeType);
                result = getMyJniLib().getDyCode(macaddr, String.valueOf(diff), starttime, validV, validT, codeType);
            }else {
                result = "Error: Basetime cannot be later than start time";
            }
        }else {
            result = "Error: The format of the start time is incorrect";
        }
        return  result;
    }

    //???????????????????????????????????????????????????????????????????????????
    //?????????????????????????????????????????????2???????????????
    public void restartBLE(){
        if (SinovoBle.getInstance().getBluetoothAdapter() != null){
            if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
                setRestartBLE(false);
                SinovoBle.getInstance().getBluetoothAdapter().enable();
            }else {
                setRestartBLE(true);
                SinovoBle.getInstance().getBluetoothAdapter().disable();
                new Handler(Looper.getMainLooper()).postDelayed(() -> SinovoBle.getInstance().getBluetoothAdapter().enable(), 1000);
            }
        }
    }

    /**
     *  ??????????????????????????????
     *  ??????????????????????????????????????????????????? ??????????????????????????????????????? ??????????????????????????????????????????????????????
     */
    public void bleScan(final IScanCallBack iScanCallBack){

        if (SinovoBle.getInstance().getBluetoothAdapter() == null){
            setScanAgain(false);
            Log.e(TAG, "Bluetooth Adapter is null");
            return ;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
            setScanAgain(false);
            Log.e(TAG, "Bluetooth not enabled");
            return ;
        }

        if (toConnectLockList.isEmpty() && !isBindMode() && !isGWConfigMode()){
            Log.e(TAG, "toConnectLockList is empty and is not bindmode, not GWConfigMode");
            setScanAgain(false);
            return ;
        }

        if (BleScanCallBack.getInstance(iScanCallBack).isScanning()){
            Log.e(TAG, "It's scanning. ignore");
            return ;
        }

        setScanRound(getScanRound() + 1);
        SinovoBle.getInstance().removeScanHandlerMsg();

        //?????? UUID ?????????
        List<ScanFilter> filters = new ArrayList<>();

        if (SinovoBle.getInstance().isGWConfigMode()){
            Log.d(TAG, "??????????????????");
            ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_GW)).build();
            filters.add(filter1);
        }else {
            if (SinovoBle.getInstance().isDfuMode()){
                Log.d(TAG, "DFU MAC:"+ getDfu_mac());
                ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_DFU)).build();
                filters.add(filter1);
            }else {
                ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM60)).build();
                ScanFilter filter2 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM67)).build();

                filters.add(filter1);
                filters.add(filter2);
            }
        }

        Log.d(TAG, "Start scanning");
        //??????????????????????????????????????????????????????????????????????????????????????????????????????
        int scanRepeatInterval = 5 * 1000;
        scanBleHandler.postDelayed(() -> BleScanCallBack.getInstance(iScanCallBack).stopScan(), scanRepeatInterval);

        BleScanCallBack.getInstance(iScanCallBack).setScanning(true);
        ScanSettings bleScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  //??????????????????????????????
                .build();
        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, bleScanSettings, BleScanCallBack.getInstance(iScanCallBack));      //???????????????????????????
    }

    //?????????????????????????????????
    public void removeScanHandlerMsg(){
        scanBleHandler.removeCallbacksAndMessages(null);    //??????????????????
    }

    /**
     * ??????????????????
     * @param bluetoothDevice  ??????????????????
     */
    public void connectBle(final BluetoothDevice bluetoothDevice) {
        if (SinovoBle.getInstance().getBluetoothAdapter() == null || bluetoothDevice == null) {
            SinovoBle.getInstance().setConnectting(false);
            Log.e(TAG, "Bluetooth Adapter is null");
            return;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()) {
            SinovoBle.getInstance().setConnectting(false);
            Log.e(TAG, "Bluetooth not enabled");
            return;
        }

        if (SinovoBle.getInstance().isLinked() || SinovoBle.getInstance().isBleConnected()){
            SinovoBle.getInstance().setConnectting(false);
            Log.e(TAG, "It's connected. Ignore this connection request");
            return;
        }

        if (SinovoBle.getInstance().isConnectting()){
            Log.e(TAG, "It's connecting. Ignore this connection request");
            return;
        }

        setConnectTime(ComTool.getNowTime());
        SinovoBle.getInstance().setConnectting(true);
        BleConnCallBack.getInstance().setMyBleDevice(bluetoothDevice);

        //add wrk 20210819
        BleConnCallBack.getInstance().releaseBle();

        Handler mHandler = new Handler(getContext().getMainLooper());
        mHandler.post(() -> {
            if (BleConnCallBack.getInstance().getmBluetoothGatt() !=null) {
                BleConnCallBack.getInstance().getmBluetoothGatt().close();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                BleConnCallBack.getInstance().setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(),
                        false, BleConnCallBack.getInstance(), BluetoothDevice.TRANSPORT_LE));
            } else {
                BleConnCallBack.getInstance().setmBluetoothGatt(bluetoothDevice.connectGatt(getContext(),
                        false, BleConnCallBack.getInstance()));
            }
        });

        Log.d(TAG, "[Ble connect] connectGatt to???" + bluetoothDevice.getAddress());
    }

    //?????? dfu ??????, dfu ?????????mac?????????????????????????????????????????????+1
    public void upgradeFW(Context context, String mac  ,String dfuPath){
        SinovoBle.getInstance().setLinked(false);
        SinovoBle.getInstance().setConnected(false);
        SinovoBle.getInstance().setConnectting(false);
        BleData.getInstance().setExeCmding(false);

        //???????????? ??????????????????????????????
        if (getToConnectLockList().size() >0){
            BleConnectLock bleConnectLock = getToConnectLockList().get(0);
            if (!bleConnectLock.getLockSno().isEmpty()){
                setBleConnectLock_dfu(bleConnectLock);
            }
        }

        String hexmac;
        if (mac.contains(":")){
            hexmac = mac.replace(":","");
        }else {
            hexmac = mac;
        }

        //???mac?????????16????????????1
        long macvaleu = Long.parseLong(hexmac,16) + 1;
        String upmac = Long.toHexString(macvaleu);
        StringBuilder upmacTmp = new StringBuilder();
        for (int i=0; i<12-upmac.length();i++){
            upmacTmp.append("0");
        }

        upmacTmp.append(upmac);
        StringBuilder upMacAddress = new StringBuilder();
        //????????????
        for (int i=0; i<upmacTmp.length(); i++){
            upMacAddress.append(upmacTmp.substring(i,i+1));
            if (i%2 == 1 && i!= upmacTmp.length()-1){
                upMacAddress.append(":");
            }
        }
        Log.d(TAG,"?????????mac?????????"+ upMacAddress.toString().toUpperCase());

        SinovoBle.getInstance().setDfu_mac(upMacAddress.toString().toUpperCase());
        SinovoBle.getInstance().setDfuMode(true);
        setScanAgain(true);
        startBleScan();
        DfuUtils.getInstance().startUpdate(context,upMacAddress.toString().toUpperCase(),"lockDFU", dfuPath);
    }

    //????????????
    public void cancelUpgrade(Context context){
        DfuUtils.getInstance().abortDevice(context);
    }

    //????????????????????????
    public void disconnBle(){
        SinovoBle.getInstance().getToConnectLockList().clear();
        SinovoBle.getInstance().setScanAgain(false);
        BleConnCallBack.getInstance().disConectBle();

        BleData.getInstance().getCommandList().clear();
        SinovoBle.getInstance().setLockMAC("");

        //???????????????????????? ??????
        BleConnCallBack.getInstance().setMyBleDevice(null);
    }

    private class BlufiCallbackMain extends BlufiCallback {
        @Override
        public void onGattPrepared(BlufiClient client, BluetoothGatt gatt, BluetoothGattService service,
                                   BluetoothGattCharacteristic writeChar, BluetoothGattCharacteristic notifyChar) {
            if (service == null) {
                Log.d(TAG, "Discover service failed");
                gatt.disconnect();
                return;
            }
            if (writeChar == null) {
                Log.d(TAG, "Get write characteristic failed");
                gatt.disconnect();
                return;
            }
            if (notifyChar == null) {
                Log.d(TAG, "Get notification characteristic failed");
                gatt.disconnect();
                return;
            }

            Log.d(TAG, "Discover service and characteristics success");

            Log.d(TAG, "Request MTU " + 512);
            boolean requestMtu = gatt.requestMtu(512);
            if (!requestMtu) {
                Log.d(TAG, "Request mtu failed");
            }
        }

        @Override
        public void onPostConfigureParams(BlufiClient client, int status) {
            if (status == STATUS_SUCCESS) {
                Log.d(TAG, "Post configure params complete");
                BleConnCallBack.getInstance().setMyBleDevice(null);
            } else {
                Log.d(TAG, "Post configure params failed, code=" + status);
            }
        }

        @Override
        public void onDeviceStatusResponse(BlufiClient client, int status, BlufiStatusResponse response) {
            if (status == STATUS_SUCCESS) {
                String result = response.generateValidInfo();
                Log.d(TAG, "Receive device status response:" + result);
                String[] res = result.split("bssid:");
                String gwID = "";
                if (res.length >0){
                    gwID = res[1].trim().toUpperCase();
                }
                Log.d(TAG, "???????????????????????????id???" + gwID);
                BleConnCallBack.getInstance().setMyBleDevice(null);
                gwConfigCallback.onConfigSUCCESS(gwID);
            } else {
                Log.d(TAG, "Device status response error, code=" + status);
            }
        }

        @Override
        public void onReceiveCustomData(BlufiClient client, int status, byte[] data) {
            if (status == STATUS_SUCCESS) {
                String customStr = new String(data);
                Log.d(TAG, String.format("Receive custom data:\n%s", customStr));
            } else {
                Log.d(TAG, "Receive custom data error, code=" + status);
            }
        }

        @Override
        public void onError(BlufiClient client, int errCode) {
            Log.d(TAG, String.format(Locale.ENGLISH, "Receive error code %d", errCode));
        }
    }
}
