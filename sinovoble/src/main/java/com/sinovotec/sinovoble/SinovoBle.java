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

public class SinovoBle  {
    private final String TAG = "SinovoBle";
    private String lockQRCode ;                   //锁的二维码，用户输入的，用于添加锁的
    private String userIMEI;                      //手机的imei，作为手机id
    private String phoneIMEI;                     //手机的imei，作为手机id
    private String lockMAC;                       //当前连接锁的蓝牙mac地址
    private String lockSNO;                       //手机与锁进行蓝牙通信使用的 校验码
    private String lockName;                      //锁的名称
    private String lockFirmVersion;
    private String lockType;
    private String lockGWid = "";                 //锁对应的网关id
    private String appFilePath = "";              // APP保存升级包的目录

    private String bleServiceUUID;                //蓝牙服务的UUID
    private String blecharacteristUUID;           //蓝牙特征字的UUID

    private boolean isBindMode  = false;          //是否为绑定模式
    private boolean isScanAgain = false;          //扫描停止后是否继续扫描
    private boolean isConnected = false;          //是否已经连接成功, 已经可以发送命令了
    private boolean isLinked    = false;          //是否连接上了，第一步连接上与否， 下面还需要 发现服务，设置 读写属性才能发送命令
    private boolean isScanOnly  = false;          //仅仅进行蓝牙扫描，不连接

    private boolean isConnectting = false;        //是否正在连接
    private String connectTime = "";              //记录下连接的时间
    private String connectingMac = "";            //当前正在连接的设备mac地址
    private int connType = 0;                     //指定连接方式，为0 表示用蓝牙连接，为1表示通过网关连接

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

    private  LoadLibJni myJniLib;


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

    public String getLockType() {
        return lockType;
    }

    public void setLockFirmVersion(String lockFirmVersion) {
        this.lockFirmVersion = lockFirmVersion;
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

    public void setmBleScanCallBack(IScanCallBack mBleScanCallBack) {
        this.mBleScanCallBack = mBleScanCallBack;
    }

    public void setmConnCallBack(IConnectCallback mConnCallBack) {
        this.mConnCallBack = mConnCallBack;
    }

    public void setToConnectLockList(ArrayList<BleConnectLock> toConnectLockList) {
        this.toConnectLockList = toConnectLockList;
    }

    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    public String getLockFirmVersion() {
        return lockFirmVersion;
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

    public int getConnType() {
        return connType;
    }

    public void setConnType(int connType) {
        this.connType = connType;
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
            mConnCallBack.onDFUDeviceDisconnected(s);
        }

        @Override
        public void onDfuCompleted(@NonNull String s) {
            mConnCallBack.onDfuCompleted(s);
        }

        @Override
        public void onDfuAborted(@NonNull String s) {
            mConnCallBack.onDfuAborted(s);
        }

        @Override
        public void onError(@NonNull String s, int i, int i1, String s1) {
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
            phoneIMEI = ComTool.createDir(appFilePath);
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
        setScanAgain(true);

        //绑定模式下，设置绑定超时检测
        if (isBindMode() && !isScanOnly()) {
            bindTimeoutHandler.postDelayed(this::checkScanResult, 90*1000);
        }

        if (isBindMode() && isScanOnly()) {
            bindTimeoutHandler.postDelayed(this::checkScanResult, 10*1000);
        }

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
        setScanAgain(true);
        setBindMode(true);
        setUserIMEI("");
        toConnectLockList.clear();
        disconnBle();
        startBleScan();
    }

    /**
     * * connect to lock via qrcode
     * @param qrcode  qrcode of the lock
     * @param userIMEI   lock will bind to the user
     */
    public void connectLockViaQRCode(String qrcode, String userIMEI){
        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(false);
        setScanAgain(true);
        setBindMode(true);
        setLockQRCode(qrcode);
        if (!userIMEI.isEmpty()) {
            setUserIMEI(userIMEI);
        }else {
            phoneIMEI = ComTool.createDir(appFilePath);
            setUserIMEI(phoneIMEI);
        }

        toConnectLockList.clear();
        disconnBle();
        startBleScan();
    }

    /**
     * * connect to lock via qrcode
     * @param qrcode  qrcode of the lock
     */
    public void connectLockViaQRCode(String qrcode){
        if (SinovoBle.getInstance().getBindTimeoutHandler() != null) {
            SinovoBle.getInstance().getBindTimeoutHandler().removeCallbacksAndMessages(null);
        }
        setScanOnly(false);
        setScanAgain(true);
        setBindMode(true);
        setLockQRCode(qrcode);
        phoneIMEI = ComTool.createDir(appFilePath);
        setUserIMEI(phoneIMEI);

        toConnectLockList.clear();
        disconnBle();
        startBleScan();
    }

    /**
     * 非绑定模式下，自动连接指定的锁，可以指定多把，蓝牙先扫描到哪一把就连哪一把
     * @param autoConnectList  需要自动连接的锁列表
     * @connectViaScan 为 true的时候，表示需要先扫描，扫描到 列表中的锁再连接，为false 则表示直接连接，不需要经过扫描
     */
    public void connectLockViaMacSno(final ArrayList<BleConnectLock> autoConnectList, Boolean connectViaScan){
        if (mBleScanCallBack == null || mConnCallBack ==null){
            Log.e(TAG,"ScanCallBack or mConnCallBack is null");
            //出错了，需要提示客户
            return;
        }

        //保存用户指定的 自动连接锁列表
        setToConnectLockList(autoConnectList);

        setScanOnly(false);
        setBindMode(false);
        if (connectViaScan) {
            Log.d(TAG,"Scan first according to setup needs");
            SinovoBle.getInstance().startBleScan();
        }else {
            //只连接队列中的第一把锁，连接的时候 先停止蓝牙扫描，这样连接效率高一些，同时连接速度也快一些
            if (!autoConnectList.isEmpty()){
                setScanAgain(false);
                BleScanCallBack.getInstance(mBleScanCallBack).stopScan();
                BleConnectLock bleConnectLock = autoConnectList.get(0);

                //add 20210128
                autoConnectList.clear();
                autoConnectList.add(bleConnectLock);

                Log.w(TAG, "autoConnectLock :connect to "+ bleConnectLock.getLockMac() + ",there are "+autoConnectList.size()+" locks in autoConnectLock's list");
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(bleConnectLock.getLockMac());

                //延迟 300ms 再去连接
                SinovoBle.getInstance().setScanAgain(false);
                connectBle(device);

                //目前测试来看，最长超时返回为 31s，绝对部分是 30s
                autoConnetHandler.postDelayed(() -> BleConnCallBack.getInstance().disConectBle(), 33*1000);
            }
        }
    }

    //检查是否有错
    private int checkEnvir(){
        if (SinovoBle.getInstance().getLockSNO() == null){
            Log.e(TAG,"SNO error");
            return -1;
        }

        if (SinovoBle.getInstance().getLockSNO().length() != 6){
            return -1;
        }
        return 0;
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName string
     */
    public int addUser(String userName){
        if (userName.isEmpty() || userName.length()>10){
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +ComTool.stringToAscii(userName);
        BleData.getInstance().exeCommand("02", data, false);
        return 0;
    }

    /**
     * 编辑用户，修改用户名
     * @param userName 用户名
     * @param userNID   用户的nid
     */
    public int updateUserName(String userName, String userNID){
        if (userNID.isEmpty() || userName.length()>10 ){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String username = "";
        if (!userName.isEmpty()){
            username = ComTool.stringToAscii(userName);
        }
        String updateStr = SinovoBle.getInstance().getLockSNO() +userNID + username;
        BleData.getInstance().exeCommand("03", updateStr, false);
        return 0;
    }

    /**
     * 为用户添加一组数据,密码、卡、指纹
     * @param userNID       用户nid
     * @param dataType      数据类型， 02 普通密码，03超级用户密码，06是卡，07是指纹，08是防胁迫指纹
     * @param data          添加密码时具体的密码内容， 如果是添加卡/指纹时，留空即可
     */
    public int addDataForUser(String userNID, String dataType, String data){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (data.isEmpty()){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String data_s = SinovoBle.getInstance().getLockSNO() +userNID + dataType + data;
            BleData.getInstance().exeCommand("05", data_s, false);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data_s = SinovoBle.getInstance().getLockSNO() +userNID + dataType ;
            BleData.getInstance().exeCommand("05", data_s, true);
        }
        return 0;
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType s
     * @param delID s
     */
    public int delData(String dataType, String delID){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (!dataType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO() +dataType + delID ;
            BleData.getInstance().exeCommand("06", data, false);
        }else {
            //删除绑定
            String data = SinovoBle.getInstance().getLockSNO() + delID ;
            BleData.getInstance().exeCommand("1b", data, false);
        }
        return 0;
    }

    /**
     * 修改用户的密码
     * @param userNid  用户的id
     * @param codeType 密码的类型
     * @param codeID   密码的ID
     * @param newCode  新的密码
     */
    public int resetCode(String userNid, String codeType, String codeID, String newCode){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + userNid + codeType + codeID + newCode;
        BleData.getInstance().exeCommand("0d", data, false);
        return 0;
    }


    /**
     * Set properties of the lock
     * @param dataType
     *                 01 set lockname，1-10 characters
     *                 02 set time of the lock，format :YYMMDDHHMMSS
     *                 03 set auto-lock time ,0 means disable auto-lock
     *                 04 set mute mode, 00 means disable the mute mode
     *                 05 set auto-create user
     *                 06 superuser's permission
     *                     permission of share code:  data is 01,03,05,07,09,11,13,15
     *                     permission of manager user: data is 02，03，06，07，10，11，14，15
     *                     permission of setting: data is 04，05，06，07，12，13，14，15
     *                     permission of checking log: data is 08，09，10，11，12，13，14，15
     *                 07 set the lock into DFU OTA , update Firmware
     * @param data  value
     */
    public int setLockInfo(String dataType, String data){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String locknameAscii = ComTool.stringToAscii(data);
            String setData = SinovoBle.getInstance().getLockSNO() + locknameAscii;
            BleData.getInstance().exeCommand("11", setData, false);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleData.getInstance().exeCommand("10", setData, false);
        }

        //设置锁的自动锁门时间
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.parseInt(data) <0 || Integer.parseInt(data)>240){
                    Log.e(TAG,"Parameter error");
                    return 2;
                }

                sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = SinovoBle.getInstance().getLockSNO() + sixteen;
            BleData.getInstance().exeCommand("16", setData, false);
        }

        //设置静音 和绑定后自动创建用户
        if (dataType.equals("04") || dataType.equals("05")){
            if (data.isEmpty()){
                data = "02";
            }
            if (Integer.parseInt(data)<0 || Integer.parseInt(data)>2){
                Log.e(TAG,"Parameter error");
                return 2;
            }

            String setData = SinovoBle.getInstance().getLockSNO() + data;
            if (dataType.equals("04")){
                BleData.getInstance().exeCommand("1c", setData, false);
            }else {
                BleData.getInstance().exeCommand("09", setData, false);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = SinovoBle.getInstance().getLockSNO() + data;
            BleData.getInstance().exeCommand("23", setData, false);
        }

        //设置进入ota升级模式
        if (dataType.equals("07")){
            String setData = SinovoBle.getInstance().getLockSNO() ;
            BleData.getInstance().exeCommand("19", setData, false);
        }

        return 0;
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
    public int getLockInfo(String dataType){
        int result = checkEnvir();
        if (result !=0){ return result; }

        String data = SinovoBle.getInstance().getLockSNO();
        if (dataType.equals("01")){ BleData.getInstance().exeCommand("12", data, false); }
        if (dataType.equals("02")){ BleData.getInstance().exeCommand("0e", data, false); }
        if (dataType.equals("03")){ BleData.getInstance().exeCommand("0f", data, false); }
        if (dataType.equals("04")){ BleData.getInstance().exeCommand("1a", data, false); }

        //add by wrk at 20210315
        if (dataType.equals("05")){ BleData.getInstance().exeCommand("11", data, false); }
        if (dataType.equals("06")){ BleData.getInstance().exeCommand("10", data, false); }
        if (dataType.equals("07")){ BleData.getInstance().exeCommand("16", data, false); }
        if (dataType.equals("08")){ BleData.getInstance().exeCommand("1c", data, false); }
        if (dataType.equals("09")){ BleData.getInstance().exeCommand("09", data, false); }
        if (dataType.equals("10")){ BleData.getInstance().exeCommand("23", data, false); }
        if (dataType.equals("11")){ BleData.getInstance().exeCommand("1f", data, false); }

        return 0;
    }

    /**
     * 同步数据，包括用户信息
     */
    public int getAllUsers(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +"00";
        BleData.getInstance().exeCommand("13", data, false);
        return 0;
    }

    /**
     * 同步数据，包括用户信息 和绑定的手机
     */
    public int getAllBoundPhone(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() +"0e";
        BleData.getInstance().exeCommand("13", data, false);

        return 0;
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public int getLog(String logID){

        int result = checkEnvir();
        if (result !=0){
            return result;
        }
        String data = SinovoBle.getInstance().getLockSNO() + logID;
        BleData.getInstance().exeCommand("17", data, false);

        return 0;
    }

    /**
     * 启用/禁用 动态密码
     * @param dynamicCode  对应的 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     */
    public void doDynamicCode(String dynamicCode, String enable){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }

        int result = checkEnvir();
        if (result !=0){
            return;
        }

        String data = SinovoBle.getInstance().getLockSNO() + enable + dynamicCode;
        BleData.getInstance().exeCommand("20", data, false);
    }


    /**
     * 修改密码的属性，改普通密码、超级用户密码
     * @param oldCodeType  该密码原来的 类型 ，02 普通密码，03 超级用户密码
     * @param codeID       密码的id
     * @param newCodeType  新的密码类型 ，02 普通密码，03 超级用户密码; 该字段为空，则表示查询此密码的类型
     */
    public int updateCodeType(String oldCodeType, String codeID, String newCodeType){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        if (oldCodeType.equals(newCodeType)){
            return 0;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        if (newCodeType.equals("02")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "00";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.equals("03")){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "01";
            BleData.getInstance().exeCommand("07", data, false);
        }
        if (newCodeType.isEmpty()){
            String data = SinovoBle.getInstance().getLockSNO() + oldCodeType + codeID + "02";
            BleData.getInstance().exeCommand("07", data, false);
        }
        return 0;
    }

    /**
     * 校验密码
     * @param password 密码
     */
    public int verifyCode(String password){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + password;
        BleData.getInstance().exeCommand("08", data, true);
        return 0;
    }

    /**
     * 通知锁端断开蓝牙连接
     */
    public int toDisconnBle(){
        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO();
        BleData.getInstance().exeCommand("1e", data, true);
        return 0;
    }

    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public int toUnlock(String unlockType, String code){
        if (unlockType.isEmpty() || code.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        String data = SinovoBle.getInstance().getLockSNO() + unlockType + code;
        BleData.getInstance().exeCommand("0a", data, true);

        return 0;
    }

    /**
     * 清空数据
     * @param datakType 表示清空数据的类型；
     *                  00 表示清空用户，不会删除管理员
     *                  0e 表示清空所有的绑定手机
     *                  0c 表示恢复出厂设置
     *
     */
    public int cleanData(String datakType){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return 2;
        }

        int result = checkEnvir();
        if (result !=0){
            return result;
        }

        //清空绑定的手机
        if (datakType.equals("0e")){
            String data = SinovoBle.getInstance().getLockSNO();
            BleData.getInstance().exeCommand("1b", data, false);
        }else {
            String data = SinovoBle.getInstance().getLockSNO() + datakType;
            BleData.getInstance().exeCommand("0c", data, false);
        }
        return 0;
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
            result =  myJniLib.getIntervalCode(macaddr, starttime, endtime, codeType);
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
        String result;

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
                result = myJniLib.getDyCode(macaddr, String.valueOf(diff), starttime, validV, validT, codeType);
            }else {
                result = "Error: Basetime cannot be later than start time";
            }
        }else {
            result = "Error: The format of the start time is incorrect";
        }
        return  result;
    }

    /**
     *  定义扫描的实现逻辑；
     *  开始扫描之后，需要扫描一段时间之后 先停下来；否则会一直扫描； 一直扫描的话，部分机型会扫描不到设备
     * @return  int, 定义如下
     *  1：表示已经在扫描，无需重复扫描
     *  -1：蓝牙适配器为空，需要确认手机是否支持蓝牙
     *  -2：手机蓝牙未开启
     *  0：正常扫描
     */
    public int bleScan(final IScanCallBack iScanCallBack){

        if (SinovoBle.getInstance().getBluetoothAdapter() == null){
            setScanAgain(false);
            Log.e(TAG, "Bluetooth Adapter is null");
            return -1;
        }

        if (!SinovoBle.getInstance().getBluetoothAdapter().isEnabled()){
            setScanAgain(false);
            Log.e(TAG, "Bluetooth not enabled");
            return -2;
        }

        if (toConnectLockList.isEmpty() && !isBindMode()){
            setScanAgain(false);
            return 1;
        }

        if (BleScanCallBack.getInstance(iScanCallBack).isScanning()){
            Log.e(TAG, "It's scanning. Ignore this scan request");
            return 1;
        }

        SinovoBle.getInstance().removeScanHandlerMsg();

        //更加 UUID 来过滤
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter1 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM60)).build();
        ScanFilter filter2 = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleConstant.SERVICE_UUID_FM67)).build();
        filters.add(filter1);
        filters.add(filter2);

        Log.d(TAG, "Start scanning");
        //扫描间隔，开始扫描之后，间隔到指定时间，然后停止扫描，再重新开始扫描
        int scanRepeatInterval = 6 * 1000;
        scanBleHandler.postDelayed(() -> BleScanCallBack.getInstance(iScanCallBack).stopScan(), scanRepeatInterval);

        BleScanCallBack.getInstance(iScanCallBack).setScanning(true);
        ScanSettings bleScanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)  //扫描到结果，立马报告
                .build();

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, bleScanSettings, BleScanCallBack.getInstance(iScanCallBack));      //根据指定参数来过滤
        return 0;
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
            String nowtime = ComTool.getNowTime();
            if (getConnectTime()!=null && ComTool.calTimeDiff(getConnectTime(), nowtime) <20) {
                Log.e(TAG, "It's connecting. Ignore this connection request");
                return;
            }
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

        Log.d(TAG, "connectGatt to：" + bluetoothDevice.getAddress());
    }


    //进行 dfu 升级, dfu 升级的mac地址与正常通信的不一样，需要加+1
    public void upgradeFW(Context context, String mac, String dfuPath){
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
        Log.d(TAG,"生成的mac地址："+ upMacAddress);

        DfuUtils.getInstance().startUpdate(context,upMacAddress.toString().toUpperCase(),"DfuTarg", dfuPath);
    }

    //取消升级
    public void cancelUpgrade(Context context){
        DfuUtils.getInstance().abortDevice(context);
    }

    //对外提供断开连接
    public void disconnBle(){
        BleConnCallBack.getInstance().disConectBle();
    }

    //对外提供断开连接  close 释放资源
    public void releaseBle(){
        BleConnCallBack.getInstance().releaseBle();
    }

    public String getLockGWid() {
        return lockGWid;
    }

    public void setLockGWid(String lockGWid) {
        this.lockGWid = lockGWid;
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

}
