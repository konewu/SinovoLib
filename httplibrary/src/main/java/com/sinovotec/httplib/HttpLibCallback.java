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

    //http get 请求
    void onHttpGet(String result);

    //http download file 下载文件
    void onDownLoadFile(String result);

    //在服务器端添加锁的用户数据
//    void onAddLockUser(String result);

    //在服务器端删除锁的用户数据
//    void onDelLockUser(String result);

    //在服务器端更新锁的用户数据
//    void onUpdateLockUser(String result);

    //从服务器端获取锁的所有用户数据
//    void onGetLockUserList(String result);

    //从服务器端清空所有的用户
//    void onCleanLockUser(String result);

    //添加密码
//    void onAddLockCode(String result);

    //删除密码
//    void onDelLockCode(String result);

    //更新密码
//    void onUpdateLockCode(String result);

    //添加卡
//    void onAddLockCard(String result);

    //删除卡
//    void onDelLockCard(String result);

    //添加指纹
//    void onAddLockFinger(String result);

    //删除指纹
//    void onDelLockFinger(String result);

    //添加防胁迫指纹
//    void onAddLockWarmFinger(String result);

    //删除防胁迫指纹
//    void onDelLockWarmFinger(String result);

}
