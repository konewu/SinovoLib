package com.sinovotec.sinovoble.callback;

/**
 * @Description: 连接设备回调
 */
public interface IConnectCallback {
    //连接成功
    void onConnectSuccess(String macAddress);

    //连接失败
    void onConnectFailure(String macAddress);

    //连接断开, 会自动重试
    void onBleDisconnect(String macaddress);

    //添加锁
    void onConnectLockViaQRCode(String result);

    //创建用户,默认创建的是普通用户
    void onCreateUser(String result);

    //更新用户
    void onUpdateUser(String result);

    //增加数据
    void onAddData(String result);

    //删除数据
    void onDelData(String result);

    //校验密码是否正确
    void onVerifyCode(String result);

    //设置锁的属性，锁名、绑定后自动创建用户、自动锁门时间、静音设置、超级用户权限设置
   // void onSetLockInfo(String result);

    //开关门
    void onUnlock(String result);

    //清空用户、密码、卡、指纹、恢复出厂设置、绑定列表
    void onCleanData(String result);

    //查询锁端的信息,电量、锁状态、锁的型号和固件版本、查询锁的mac地址、查询指定数据的最新修改时间、管理员是否存在
    void onLockInfo(String result);

    //查询数据，同步用户、绑定列表
    void onRequestData(String result);

    //查询日志
    void onRequestLog(String result);

    //启用禁用 动态密码的返回结果
    void onDynamicCodeStatus(String result);

    //锁锁死的返回
    void onLockFrozen(String result);

    //发送数据没有回应
    void onReceiveDataFailed();

    //监听广播
    //关闭手机蓝牙时的返回
    void onBluetoothOff();

    //打开手机蓝牙时的返回
    void onBluetoothOn();

    //手机通过wifi连接
    void onConnectedViaWifi(String wifiSSID);

    //手机通过数据网络连接
    void onConnectedViaMobile();

    //手机网络断开
    void onInternetDisconned();

    //获取不到手机网络
    void onFaildGetInternetInfo();

    //打开手机的wifi
    void onWifiOn();

    //关闭手机的wifi
    void onWifiOff();

    //手机屏幕亮屏
    void onScreenOn();

    //手机屏幕锁屏
    void onScreenOff();

    //dfu 升级, 正在连接设备
    void onDFUDeviceConnecting(String result);

    void onDFUDeviceConnected(String result);

    void onDfuProcessStarting(String result);

    //dfu 升级, 开始升级
    void onDfuProcessStarted(String result);

    void onDFUEnablingDfuMode(String result);

    //dfu 升级, 升级的进度结果
    void onDFUProgressChanged(int progress);

    void onDFUFirmwareValidating(String result);

    void onDFUDeviceDisconnecting(String result);

    //dfu 升级 蓝牙连接断开
    void onDFUDeviceDisconnected(String result);

    //dfu 升级 完成
    void onDfuCompleted(String result);

    //dfu 升级 取消
    void onDfuAborted(String result);

    //dfu 升级 错误
    void onDFUError( String s, int i, int i1, String s1);

}
