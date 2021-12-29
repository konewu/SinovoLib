package com.sinovotec.httplib;

public interface HttpLibCallback {

  //用户注册的回调
  void onUserRegister(String result);

  //用户登录的回调
  void onUserLogin(String result);

  //获取验证码的回调
  void onGetVerifyCode(String result);

  //修改密码的回调
  void onModifyPass(String result);

  //添加网关的回调
  void onAddGateway(String result);

  //删除网关的回调
  void onDelGateway(String result);

  //修改网关名称的回调
  void onModifyGwName(String result);

  //获取网关列表的回调
  void onGetGwList(String result);

  //添加锁的回调
  void onAddLock(String result);

  //修改锁信息的回调
  void onUpdateLock(String result);

  //获取网关下所有锁的回调
  void onGetLockList(String result);

  //删除网关下锁的回调
  void onDelLock(String result);

  //添加、修改用户信息
  void onUpdateLoginUserInfo(String result);

  //添加用户头像
  void onUpdateUserAvatar(String result);

  //share the lock to other users
  void onShareLock(String result);

  //记录分享数据的返回
  void onAddShareData(String result);

  //获取分享数据的列表
  void onGetShareData(String result);

  //更新分享密码的数据
  void onUpdateShareData(String result);

  //删除分享密码的数据
  void onDelShareData(String result);

  //删除锁端的数据
  void onDelDeviceData(String result);

  //管理员从服务器端彻底删除锁
  void onRemoveLock(String result);

  //http get 请求,  获取头像
  void onGetUserAvatar(byte[] imageData);

  void onGetDFUInfo(String result);

  void onGetLockType(String result);

  void onGetLockImage(byte[] imageData, String imageType);

  //http download file 下载文件
  void onDownLoadFile(String result);

  //恢复出厂设置后，调用此接口，服务器会 通过mqtt 推送通知其他用户
  void onResetLock(String result);

  //删除网关下的子设备 锁，同时断开网关与锁的蓝牙连接
  void onDelGwSubLock(String result);

  //上传日志文件
  void onUploadFile(String result);
}
