package com.sinovotec.sinovoble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import no.nordicsemi.android.dfu.DfuProgressListener;

public class SinovoBle {
    private final String TAG    = "SinovoBle";
    private String lockQRCode ;                   //锁的二维码，用户输入的，用于添加锁的
    private String userIMEI;                      //手机的imei，作为手机id
    private String lockMAC;                       //当前连接锁的蓝牙mac地址
    private String lockSNO      = "";             //手机与锁进行蓝牙通信使用的 校验码
    private String lockName;                      //锁的名称
    private String appFilePath  = "";             // APP保存升级包的目录
    private String dfu_mac      = "";             //dfu 升级时 设备的mac地址

    private String bleServiceUUID;                //蓝牙服务的UUID
    private String blecharacteristUUID;           //蓝牙特征字的UUID

    private boolean isBindMode  = false;          //是否为绑定模式
    private boolean isScanAgain = false;          //扫描停止后是否继续扫描
    private boolean isConnected = false;          //是否已经连接成功, 已经可以发送命令了
    private boolean isLinked    = false;          //是否连接上了，第一步连接上与否， 下面还需要 发现服务，设置 读写属性才能发送命令
    private boolean isScanOnly  = false;          //仅仅进行蓝牙扫描，不连接
    private boolean isDfuMode   = false;          //是否为 dfu 升级模式
    private boolean restartBLE  = false;          //是否重启了手机蓝牙，如果是重启的，则本次蓝牙关闭不通知 前端
    private boolean isConnectting   = false;      //是否正在连接
    private String connectTime      = "";         //记录下连接的时间
    private String connectingMac    = "";         //当前正在连接的设备mac地址

    private Context context;                       //上下文
    private BluetoothAdapter bluetoothAdapter;     //蓝牙适配器

    private final Handler scanBleHandler       = new Handler(Looper.getMainLooper());  //设置定时任务的handler，扫描5s后 定时调用 停止扫描的函数
    private final Handler bindTimeoutHandler   = new Handler(Looper.getMainLooper());  //设置定时任务的handler，绑定2分钟后，超时失败
    private final Handler autoConnetHandler    = new Handler(Looper.getMainLooper());  //设置自动连接的handler，一般情况下，自动连接超时为30s ，但偶尔有异常，连接失败了回调通知

    private final ArrayList<BleScanDevice> scanLockList ;         //保存扫描的结果
    private final ArrayList<String> bondBleMacList;               //保存在绑定时，已经尝试连接过的锁 ,避免已经尝试过绑定不合适的锁，还会重复绑定
    private ArrayList<BleConnectLock> toConnectLockList;         //自动连接的设备列表，内容为mac地址，sno

    @SuppressLint("StaticFieldLeak")
    private static SinovoBle instance;                //入口操作管理

    private IScanCallBack mBleScanCallBack;     //蓝牙扫描的回调
    private IConnectCallback mConnCallBack;     //蓝牙连接的回调
    private BleConnectLock bleConnectLock_dfu;   //dfu之前先记录下 原来的设备

    private LoadLibJni myJniLib;

    private boolean foundSomeBLE = false;           // 记录下可以扫描到锁， 但不一定是我们要连接的锁
    private boolean foundlock = false;              //能够扫描到 我们需要的锁
    private int  scanRound = 0;                     //第几轮扫描，每次扫描 都是分多轮扫描，每一轮扫描5s，停1s

    /**
     * Instantiate  SinovoBle class
     * @return SinovoBle
     */
    public static SinovoBle getInstance() {
        if (instance == null) {
            synchronized (SinovoBle.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
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

    public String getConnectingMac() {
        return connectingMac;
    }

    public void setConnectingMac(String connectingMac) {
        this.connectingMac = connectingMac;
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
        }

        //先注册进度以及升级状态回调
        DfuUtils.getInstance().setmDfuProgressListener(context,progressListener);//升级状态回调
        return (bluetoothAdapter !=null && mBleScanCallBack !=null && mConnCallBack !=null);
    }

    //监听广播
    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        //20181221 增加，对屏幕的亮起 和 熄灭的广播进行监控
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        //20210126 增加，对wifi开启关闭、网络连接状态的广播进行监控
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return filter;
    }

    //开始进行扫描
    public void startBleScan(){
        if (SinovoBle.getInstance().isConnectting()){
            Log.e(TAG,"当前正在连接，不能进行扫描");
            return;
        }
        Log.d(TAG, "调用startBleScan  来扫描");
        getBondBleMacList().clear();                //clean the bondBleMacList before starting scan

        //绑定模式下，设置绑定超时检测
        if (isBindMode() && !isScanOnly()) { bindTimeoutHandler.postDelayed(this::checkScanResult, 90*1000); }
        if (isBindMode() && isScanOnly())  { bindTimeoutHandler.postDelayed(this::checkScanResult, 10*1000); }

        setScanRound(0);
        setFoundSomeBLE(false);
        setFoundlock(false);

        //扫描的函数
        bleScan(getmBleScanCallBack());
    }

    //绑定超时检测
    private void checkScanResult(){
        Log.e(TAG,"绑定超时检测，超时为1分钟, isconnecting:" + isConnectting);
        if (!isBleConnected() && !isScanOnly()){
            Log.d(TAG,"绑定超时检测，需要告知回调");
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

        Log.d(TAG, "准备绑定锁的 qrcode："+ qrcode + ", IMEI:"+ userIMEI);
        if (qrcode.length() !=12 || userIMEI.length() != 12 ){
            Log.d(TAG, "qrcode or imei error");
            return;
        }

        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(false);
        setBindMode(true);
        setLockQRCode(qrcode);
        setUserIMEI(userIMEI);
        SinovoBle.getInstance().getScanLockList().clear();  //先清空绑定列表

        disconnBle();
        setScanAgain(true);
        startBleScan();
    }

//    /**
//     * * connect to lock via mac
//     * @param macaddress  mac of the lock， such as：C9:E9:CA:94:83:01
//     */
//    public void connectLockViaMac(String macaddress){
//
//        Log.d(TAG, "准备连接锁，指定的mac地址："+ macaddress);
//        if (macaddress.length() !=17 ){
//            Log.d(TAG, "macaddress error");
//            return;
//        }
//
//        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
//            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
//        }
//        setScanOnly(false);
//        setBindMode(true);
//        SinovoBle.getInstance().getScanLockList().clear();  //先清空绑定列表
//        disconnBle();
//        setScanAgain(false);
//        //直接连接，不扫描
//
//
//    }

    /**
     * 非绑定模式下，自动连接指定的锁，可以指定多把，蓝牙先扫描到哪一把就连哪一把
     * @param autoConnectList  需要自动连接的锁列表
     */
    public void connectLockViaMacSno(final ArrayList<BleConnectLock> autoConnectList){
        if (mBleScanCallBack == null || mConnCallBack ==null){
            Log.e(TAG,"ScanCallBack or mConnCallBack is null");
            //出错了，需要提示客户
            return;
        }
        //保存用户指定的 自动连接锁列表
        setToConnectLockList(autoConnectList);
        setScanOnly(false);
        setBindMode(false);
        setScanAgain(true);
        startBleScan();
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName string
     */
    public void addUser(String userName, String lockSNO){
        if (userName.isEmpty() || userName.length()>10){
            Log.e(TAG, "Username is error");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO +ComTool.stringToAscii(userName);
        BleData.getInstance().exeCommand("02", data, false);
    }

    /**
     * 编辑用户，修改用户名
     * @param userName 用户名
     * @param userNID   用户的nid
     */
    public void updateUserName(String userName, String userNID, String lockSNO){
        if (userNID.isEmpty() || userName.length()>10 ){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
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
     * 为用户添加一组数据,密码、卡、指纹
     * @param userNID       用户nid
     * @param dataType      数据类型， 02 普通密码，03超级用户密码，06是卡，07是指纹，08是防胁迫指纹
     * @param data          添加密码时具体的密码内容， 如果是添加卡/指纹时，留空即可
     */
    public void addDataForUser(String userNID, String dataType, String data, String lockSNO){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (data.isEmpty()){
                Log.e(TAG,"Parameter error");
                return ;
            }

            String data_s = lockSNO +userNID + dataType + data;
            BleData.getInstance().exeCommand("05", data_s, false);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data_s = lockSNO +userNID + dataType ;
            BleData.getInstance().exeCommand("05", data_s, true);
        }
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType s
     * @param delID s
     */
    public void delData(String dataType, String delID, String lockSNO){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
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
            //删除绑定
            String data = lockSNO + delID ;
            BleData.getInstance().exeCommand("1b", data, false);
        }
    }

    /**
     * 修改用户的密码
     * @param userNid  用户的id
     * @param codeType 密码的类型
     * @param codeID   密码的ID
     * @param newCode  新的密码
     */
    public void resetCode(String userNid, String codeType, String codeID, String newCode, String lockSNO){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
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
     *                 01 set lockname，1-10 characters
     *                 02 set time of the lock，format :YYMMDDHHMMSS
     *                 03 set auto-lock time ,0 means disable auto-lock
     *                 04 set volume, 00 mute , 01-05 volume
     *                 05 set auto-create user
     *                 06 superuser's permission
     *                     permission of share code:  data is 01,03,05,07,09,11,13,15
     *                     permission of manager user: data is 02，03，06，07，10，11，14，15
     *                     permission of setting: data is 04，05，06，07，12，13，14，15
     *                     permission of checking log: data is 08，09，10，11，12，13，14，15
     *                 07 set the lock into DFU OTA , update Firmware
     * @param data  value
     */
    public void setLockInfo(String dataType, String data, String lockSNO){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return ;
            }

            String locknameAscii = ComTool.stringToAscii(data);
            String setData = lockSNO + locknameAscii;
            BleData.getInstance().exeCommand("11", setData, false);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = lockSNO + data;
            BleData.getInstance().exeCommand("10", setData, false);
        }

        //设置锁的自动锁门时间
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

        //设置静音 和绑定后自动创建用户
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
                Log.e(TAG, "准备设置音量大小为："+ sixteen + ",加上 sno："+ setData);
                BleData.getInstance().exeCommand("1c", setData, false);
            }else {
                String setData = lockSNO + data;
                BleData.getInstance().exeCommand("09", setData, false);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = lockSNO + data;
            BleData.getInstance().exeCommand("23", setData, false);
        }

        //设置进入ota升级模式
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
     *                 08 get the mute setting
     *                 09 get the auto-create setting
     *                 10 get the superUser's priority
     *                 11 get the basetime of the lock
     */
    public void getLockInfo(String dataType ,String lockSNO){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
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

    }

    /**
     * 同步数据，包括用户信息
     */
    public void getAllUsers(String lockSNO){
        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
        }

        String data = lockSNO +"00";
        BleData.getInstance().exeCommand("13", data, false);
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public void getLog(String logID, String lockSNO){

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }
        String data = lockSNO + logID;
        BleData.getInstance().exeCommand("17", data, false);
    }

    /**
     * 启用/禁用 动态密码
     * @param dynamicCode  对应的 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     */
    public void doDynamicCode(String dynamicCode, String enable, String lockSNO){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO + enable + dynamicCode;
        BleData.getInstance().exeCommand("20", data, false);
    }


    /**
     * 修改密码的属性，改普通密码、超级用户密码
     * @param oldCodeType  该密码原来的 类型 ，02 普通密码，03 超级用户密码
     * @param codeID       密码的id
     * @param newCodeType  新的密码类型 ，02 普通密码，03 超级用户密码; 该字段为空，则表示查询此密码的类型
     */
    public void updateCodeType(String oldCodeType, String codeID, String newCodeType, String lockSNO){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (oldCodeType.equals(newCodeType)){
            Log.e(TAG,"The new Code type is the same as the old Code type");
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
     * 校验密码
     * @param password 密码
     */
    public void verifyCode(String password, String lockSNO){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }

        String data = lockSNO + password;
        BleData.getInstance().exeCommand("08", data, true);
    }

//    /**
//     * 通知锁端断开蓝牙连接
//     */
//    public void toDisconnBle(String lockSNO){
//        if (lockSNO.length() != 6){
//            Log.e(TAG, "LOCK SNO is error");
//            return ;
//        }
//
//        BleData.getInstance().exeCommand("1e", lockSNO, true);
//    }

    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public void toUnlock(String unlockType, String code, String lockSNO){
        if (unlockType.isEmpty() || code.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
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
     * 清空数据
     * @param datakType 表示清空数据的类型；
     *                  00 表示清空用户，不会删除管理员
     *                  0c 表示恢复出厂设置
     *
     */
    public void cleanData(String datakType, String lockSNO){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return ;
        }

        if (lockSNO.length() != 6){
            Log.e(TAG, "LOCK SNO is error");
            return ;
        }
        String data = lockSNO + datakType;
        BleData.getInstance().exeCommand("0c", data, false);
    }


    //用户取消了绑定
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

    //生成区间动态密码
    /**
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

        //区间密码的结束时间 必须比 开始时间大 10分钟以上
        if (diff <10*60){
            return "Error:The end time must be more than 10 minutes longer than the start time";
        }

        if ((starttime_tmp.length() == 5 && starttime_tmp.contains(":")) && (endtime_tmp.length() == 5 && endtime_tmp.contains(":"))){
            String macaddr = lockmac.replace(":","");
            result =  getMyJniLib().getIntervalCode(macaddr, starttime, endtime, codeType);
        }else {
            result = "Error:The time format is wrong, such as 12:34";
        }

        return result;
    }

    /**
     * Calculate one-time code or Timed code
     * @param lockmac lock mac address, such as 00A051F4D44A
     * @param basetime basetime， such as 2021-03-23 19:14
     * @param starttime start time, such as 2020-07-08 17:18
     * @param valid  Effective time period, such as 3 Days, 1 Hours,  1 Months
     * @param type  0: one-time 1:Timed code
     * @return code
     */
    public String calcDyCode(String lockmac, String basetime,  String starttime, String valid, int type){

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
                String macaddr = lockmac.replace(":","");
                result = getMyJniLib().getDyCode(macaddr, String.valueOf(diff), starttime, validV, validT, codeType);
            }else {
                result = "Error: Basetime cannot be later than start time";
            }
        }else {
            result = "Error: The format of the start time is incorrect";
        }
        return  result;
    }

    //重启手机蓝牙，如果手机蓝牙本来就是关闭，直接打开；
    //如果手机蓝牙是开启的，则关闭，2秒后再打开
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
     *  定义扫描的实现逻辑；
     *  开始扫描之后，需要扫描一段时间之后 先停下来；否则会一直扫描； 一直扫描的话，部分机型会扫描不到设备
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

        if (toConnectLockList.isEmpty() && !isBindMode()){
            Log.e(TAG, "toConnectLockList is empty and is not bindmode");
            setScanAgain(false);
            return ;
        }

        if (BleScanCallBack.getInstance(iScanCallBack).isScanning()){
            Log.e(TAG, "It's scanning. please wait");
            return ;
        }

        setScanRound(getScanRound() + 1);
        SinovoBle.getInstance().removeScanHandlerMsg();

        //更加 UUID 来过滤
        List<ScanFilter> filters = new ArrayList<>();

        if (SinovoBle.getInstance().isDfuMode()){
            Log.d(TAG, "DFU MAC:"+ getDfu_mac());
            ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_DFU)).build();
            filters.add(filter1);
        }else {
            ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM60)).build();
            ScanFilter filter2 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM67)).build();

            //科技侠的锁  "00001910-0000-1000-8000-00805f9b34fb";
//            String uuid3 = "00001910-0000-1000-8000-00805f9b34fb";
//            ScanFilter filter3 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid3)).build();
            filters.add(filter1);
            filters.add(filter2);
//            filters.add(filter3);
        }

        Log.d(TAG, "Start scanning");
        //扫描间隔，开始扫描之后，间隔到指定时间，然后停止扫描，再重新开始扫描
        int scanRepeatInterval = 5 * 1000;
        scanBleHandler.postDelayed(() -> BleScanCallBack.getInstance(iScanCallBack).stopScan(), scanRepeatInterval);

        BleScanCallBack.getInstance(iScanCallBack).setScanning(true);
        ScanSettings bleScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  //扫描到结果，立马报告
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, bleScanSettings, BleScanCallBack.getInstance(iScanCallBack));      //根据指定参数来过滤
    }

    //取消蓝牙扫描的定时任务
    public void removeScanHandlerMsg(){
        scanBleHandler.removeCallbacksAndMessages(null);    //取消定时任务
    }

    /**
     * 连接蓝牙设备
     * @param bluetoothDevice  待连接的设备
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
        SinovoBle.getInstance().setConnectingMac(bluetoothDevice.getAddress());

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

        Log.d(TAG, "[Ble connect] connectGatt to：" + bluetoothDevice.getAddress());
    }

    //进行 dfu 升级, dfu 升级的mac地址与正常通信的不一样，需要加+1
    public void upgradeFW(Context context, String mac  ,String dfuPath){

        SinovoBle.getInstance().setLinked(false);
        SinovoBle.getInstance().setConnected(false);
        SinovoBle.getInstance().setConnectting(false);
        BleData.getInstance().setExeCmding(false);

        //先保存下 原来自动连接列表的锁
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

        //将mac地址转16进制再加1
        long macvaleu = Long.parseLong(hexmac,16) + 1;
        String upmac = Long.toHexString(macvaleu);
        StringBuilder upmacTmp = new StringBuilder();
        for (int i=0; i<12-upmac.length();i++){
            upmacTmp.append("0");
        }

        upmacTmp.append(upmac);
        StringBuilder upMacAddress = new StringBuilder();
        //加上冒号
        for (int i=0; i<upmacTmp.length(); i++){
            upMacAddress.append(upmacTmp.substring(i,i+1));
            if (i%2 == 1 && i!= upmacTmp.length()-1){
                upMacAddress.append(":");
            }
        }
        Log.d(TAG,"生成的mac地址："+ upMacAddress.toString().toUpperCase());

        SinovoBle.getInstance().setDfu_mac(upMacAddress.toString().toUpperCase());
        SinovoBle.getInstance().setDfuMode(true);
        setScanAgain(true);
        startBleScan();
        DfuUtils.getInstance().startUpdate(context,upMacAddress.toString().toUpperCase(),"lockDFU", dfuPath);
    }

    //取消升级
    public void cancelUpgrade(Context context){
        DfuUtils.getInstance().abortDevice(context);
    }

    //对外提供断开连接
    public void disconnBle(){
        SinovoBle.getInstance().getToConnectLockList().clear();
        SinovoBle.getInstance().setScanAgain(false);
        BleConnCallBack.getInstance().disConectBle();
    }
}
