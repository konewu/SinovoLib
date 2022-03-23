package com.sinovotec.mqtt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleData;
import com.sinovotec.sinovoble.common.ComTool;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MqttLib {

    private static final String TAG = "SinovoMQTT";
    private static MqttLib instance;
    private iotMqttCallback iotMqttCallback;      //mqtt 通信结果回调

    private String PUB_TOPIC = "";    /* 自动Topic, 用于上报消息 */
    private String SUB_TOPIC = "";    /* 自动Topic, 用于接受消息 */

    private MqttAndroidClient mqttAndroidClient;
    private boolean isMqttOK = false;
    private boolean isMqttIniting = false;
    private final LinkedList<String> mqttCMDList = new LinkedList<>();          //mqtt 命令队列

    private boolean sendingCmdMQTT = false;

    private final Handler mqttSubscribeHandler = new Handler(Looper.getMainLooper());        //超时句柄， 发布主题的超时句柄，默认为5s
    private final Handler mqttSendHandler = new Handler(Looper.getMainLooper());        //超时句柄， app发送命令到网关上的超时，默认为 10s
    private final Handler bleSendHandler  = new Handler(Looper.getMainLooper());        //超时句柄， app发送命令经过网关到锁上的超时，默认为 20s

    private int  connectType = 0;           //0 未知，1为 wifi， 2为 手机数据

    public static MqttLib getInstance() {
        if (instance == null) {
            synchronized (MqttLib.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new MqttLib();
                }
            }
        }
        return instance;
    }

    public boolean isMqttOK() {
        return isMqttOK;
    }

    public void setMqttOK(boolean mqttOK) {
        isMqttOK = mqttOK;
    }

    public boolean isSendingCmdMQTT() {
        return sendingCmdMQTT;
    }

    public void setSendingCmdMQTT(boolean sendingCmdMQTT) {
        this.sendingCmdMQTT = sendingCmdMQTT;
    }

    public LinkedList<String> getMqttCMDList() {
        return mqttCMDList;
    }

    //mqtt ble 发送命令的句柄
    public Handler getBleSendHandler() {
        return bleSendHandler;
    }

    public boolean isMqttIniting() {
        return isMqttIniting;
    }

    public void setMqttIniting(boolean mqttIniting) {
        isMqttIniting = mqttIniting;
    }

    /**
     * 初始化MQTT ，采用的 一机一密方式
     * @param context    上下文对象
     * @param proKey     produceKey
     * @param deName     DeviceName
     * @param deSecret   DeviceSecret
     * @param region     DeviceRegion
     */
    public void init(Context context, String proKey, String deName, String deSecret, String region, iotMqttCallback callback){

        Log.d(TAG,"Initialize MQTT 采用一机一密方式");   //采用一机一密方式
        this.iotMqttCallback = callback;
        this.PUB_TOPIC = "/" + proKey + "/" + deName + "/user/update";
        this.SUB_TOPIC = "/" + proKey + "/" + deName + "/user/get";
        String host = "tcp://" + proKey + ".iot-as-mqtt."+region+".aliyuncs.com:443";
        String clientId = "";
        String userName = "";
        String passWord = "";

        setMqttIniting(true);

        /* 获取Mqtt建连信息clientId, username, password */
        AiotMqttOption aiotMqttOption = new AiotMqttOption().getMqttOption(proKey, deName, deSecret);
        if (aiotMqttOption == null) {
            iotMqttCallback.initFailed();
            setMqttOK(false);
            setMqttIniting(false);
            Log.e(TAG, "device info error");
        } else {
            clientId = aiotMqttOption.getClientId();
            userName = aiotMqttOption.getUsername();
            passWord = aiotMqttOption.getPassword();
        }

        /* 创建MqttConnectOptions对象并配置username和password */
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        mqttConnectOptions.setUserName(userName);
        mqttConnectOptions.setPassword(passWord.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(false);  //设置断开自动重连
        mqttConnectOptions.setKeepAliveInterval(120);

        /* 创建MqttAndroidClient对象, 并设置回调接口 */
        mqttAndroidClient = new MqttAndroidClient(context, host, clientId);

        mqttAndroidClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "connection lost--->" + cause);
                setMqttOK(false);
                setSendingCmdMQTT(false);
                setMqttIniting(false);

                iotMqttCallback.onConnectionLost();
                mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务

                //add wrk 20211101
                logoutMQTT();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String msg = new String(message.getPayload());
                Log.i(TAG, "收到 mqtt的信息，topic: " + topic + ", msg: " + msg);
                if (msg.contains("bledata")){
                    bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                }else {
                    mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
                }

                String gateway_id="", type="", mac="",data="",  uuid="",appId="";
                JSONObject jsonObject = JSON.parseObject(msg);

                if (jsonObject.containsKey("data"))         { data = Objects.requireNonNull(jsonObject.get("data")).toString(); }
                if (jsonObject.containsKey("appId"))        { appId = Objects.requireNonNull(jsonObject.get("appId")).toString(); }
                if (jsonObject.containsKey("type"))         { type = Objects.requireNonNull(jsonObject.get("type")).toString(); }
                if (jsonObject.containsKey("uuid"))         { uuid = Objects.requireNonNull(jsonObject.get("uuid")).toString(); }
                if (jsonObject.containsKey("gateway_id"))   { gateway_id = Objects.requireNonNull(jsonObject.get("gateway_id")).toString(); }
                if (jsonObject.containsKey("mac"))          { mac = Objects.requireNonNull(jsonObject.get("mac")).toString(); }

                //如果是蓝牙数据，则需要先解析
                if (type.equals("bledata")){
                    setSendingCmdMQTT(false);

                    if (!getMqttCMDList().isEmpty()) {
                        JSONObject listcmdJson = JSONObject.parseObject(getMqttCMDList().get(0));
                        String listcmdFun = Objects.requireNonNull(listcmdJson.get("data")).toString().substring(0,4);
                        String databackFun = data.substring(0,4);

                        if (listcmdFun.equalsIgnoreCase(databackFun)){
                            Log.d(TAG, "已经收到此数据的回复，功能码："+databackFun+", 从队列中删除该命令");
                            getMqttCMDList().removeFirst();

                            //如果队列中还有命令，则尝试去发送它
                            if (!getMqttCMDList().isEmpty()){
                                String cmd2send = getMqttCMDList().getFirst();
                                Log.d(TAG, "命令行队列还有命令，需要继续发送" + cmd2send);
                                if (isMqttOK){
                                    setSendingCmdMQTT(true);
                                    Log.d(TAG, "send command via mqtt:" + cmd2send);
                                    publishMessage(cmd2send);
                                }else{
                                    setSendingCmdMQTT(false);
                                    Log.d(TAG, "messageArrived MQTT is not ready, it cann't send command:" + cmd2send);
                                    if (iotMqttCallback != null) {
                                        iotMqttCallback.onPublishFailed();
                                    }
                                }
                            }
                        }
                    }

                    //先删除 队列中删除 对应的数据
                    if (!BleData.getInstance().getCommandList().isEmpty()) {
                        Log.d(TAG,"receive data from mqtt，delete the first command in list");
                        BleData.getInstance().getCommandList().removeFirst();
                    }

                    //解析数据
                    LinkedHashMap<String, Object> resultmap = BleData.getInstance().getDataFromBle(data.toLowerCase(), mac.toLowerCase());
                    Log.d(TAG, "数据处理后的结果：" + JSON.toJSONString(resultmap));

                    resultmap.put("appId", appId);
                    resultmap.put("type", type);
                    resultmap.put("uuid", uuid);
                    resultmap.put("gateway_id", gateway_id);
                    resultmap.put("mac", mac);
                    iotMqttCallback.onMsgArrived(topic,JSON.toJSONString(resultmap));
                }else {
                    //如果推送的锁离线 在线的状态，则需要重设 发送命令的标记
                    if (type.equalsIgnoreCase("lockStatus")){
                        setSendingCmdMQTT(false);

                        //如果队列中还有命令，则尝试去发送它
                        if (!getMqttCMDList().isEmpty()){
                            String cmd2send = getMqttCMDList().getFirst();
                            if (isMqttOK){
                                setSendingCmdMQTT(true);
                                Log.d(TAG, "send command via mqtt:" + cmd2send);
                                publishMessage(cmd2send);
                            }else{
                                setSendingCmdMQTT(false);
                                Log.d(TAG, "messageArrived2 MQTT is not ready, it cann't send command:" + cmd2send);
                                if (iotMqttCallback != null) {
                                    iotMqttCallback.onPublishFailed();
                                }
                            }
                        }
                    }

                    if (iotMqttCallback != null) {
                        iotMqttCallback.onMsgArrived(topic, msg);
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                iotMqttCallback.onDeliveryComplete();
            }
        });

        /* Mqtt建连 */
        try {
            mqttAndroidClient.connect(mqttConnectOptions,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w(TAG, "connect mqtt succeed，now to subscribeTopic");
                    subscribeTopic(SUB_TOPIC);
                    mqttSubscribeHandler.postDelayed(() -> checkDataReceive("subscribeTopic"), 5*1000);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "connect mqtt failed ,error info:" + exception);
                    setMqttOK(false);
                    setMqttIniting(false);
                    iotMqttCallback.onConnectFailed();
                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    /**
     * 订阅特定的主题
     * @param topic mqtt topic
     */
    private void subscribeTopic(String topic) {
        try {
            if (mqttAndroidClient == null){
                Log.i(TAG, "subscribed failed, mqttAndroidClient == null");
                setMqttOK(false);
                setMqttIniting(false);
                setSendingCmdMQTT(false);
                mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务
                if (iotMqttCallback != null) {
                    iotMqttCallback.onSubscribeFailed();
                }
                return;
            }

            mqttAndroidClient.subscribe(topic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "subscribed succeed");
                    setMqttOK(true);
                    setMqttIniting(false);
                    setSendingCmdMQTT(false);
                    mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务
                    if (iotMqttCallback != null) {
                        iotMqttCallback.onSubscribeSuccess();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "subscribed failed");
                    setMqttOK(false);
                    setMqttIniting(false);
                    setSendingCmdMQTT(false);
                    mqttSubscribeHandler.removeCallbacksAndMessages(null);    //取消定时任务
                    if (iotMqttCallback != null) {
                        iotMqttCallback.onSubscribeFailed();
                    }
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向默认的主题/user/update发布消息
     * @param payload 消息载荷
     */
    private void publishMessage(String payload) {
        try {
            if (mqttAndroidClient == null){
                Log.d(TAG, "publishMessage failed, mqttAndroidClient is null");
                return;
            }

            if (!mqttAndroidClient.isConnected()) {
                mqttAndroidClient.connect();
            }

            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setQos(0);
            mqttAndroidClient.publish(PUB_TOPIC, message,null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.i(TAG, "publish succeed!，data："+ payload);

                    if (iotMqttCallback != null) {
                        iotMqttCallback.onPublishSuccess();
                    }

                    //延迟30秒后再检测 是否已经收到锁端的恢复
                    if (payload.contains("send2lock")){
                        bleSendHandler.postDelayed(() -> checkDataReceive("send2lock"), 30*1000);
                    }
                    if (payload.contains("send2GW")){
                        bleSendHandler.postDelayed(() -> checkDataReceive("send2GW"), 10*1000);
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "publish failed! ");
                    if (iotMqttCallback != null) {
                        iotMqttCallback.onPublishFailed();
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    //Logout mqtt
    public void logoutMQTT(){
        //设置mqtt为 还没注册
        setMqttOK(false);
        setMqttIniting(false);
        setSendingCmdMQTT(false);

        try {
            if (mqttAndroidClient != null){
                mqttAndroidClient.unsubscribe(SUB_TOPIC);
                mqttAndroidClient.unregisterResources();
                mqttAndroidClient.setCallback(null);
                mqttAndroidClient = null;
                Log.e(TAG,"logoutMQTT disconnect mqtt，exit");
            }
            // mqttAndroidClient.disconnect();

        } catch (MqttException e) {
            e.printStackTrace();
        }
        //注销蓝牙连接
        SinovoBle.getInstance().disconnBle();
    }

    /**
     * MQTT建连选项类，输入设备三元组productKey, deviceName和deviceSecret, 生成Mqtt建连参数clientId，username和password.
     */
    static class AiotMqttOption {
        private String username = "";
        private String password = "";
        private String clientId = "";

        public String getUsername() { return this.username;}
        public String getPassword() { return this.password;}
        public String getClientId() { return this.clientId;}

        /**
         * 获取Mqtt建连选项对象
         * @param productKey 产品秘钥
         * @param deviceName 设备名称
         * @param deviceSecret 设备机密
         * @return AiotMqttOption对象或者NULL
         */
        public AiotMqttOption getMqttOption(String productKey, String deviceName, String deviceSecret) {
            if (productKey == null || deviceName == null || deviceSecret == null) {
                return null;
            }

            try {
                String timestamp = Long.toString(System.currentTimeMillis());

                // clientId
                this.clientId = productKey + "." + deviceName + "|timestamp=" + timestamp +
                        ",_v=paho-android-1.0.0,securemode=2,signmethod=hmacsha256|";

                // userName
                this.username = deviceName + "&" + productKey;

                // password
                String macSrc = "clientId" + productKey + "." + deviceName + "deviceName" +
                        deviceName + "productKey" + productKey + "timestamp" + timestamp;
                String algorithm = "HmacSHA256";
                Mac mac = Mac.getInstance(algorithm);
                SecretKeySpec secretKeySpec = new SecretKeySpec(deviceSecret.getBytes(), algorithm);
                mac.init(secretKeySpec);
                byte[] macRes = mac.doFinal(macSrc.getBytes());
                password = String.format("%064x", new BigInteger(1, macRes));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return this;
        }
    }

    /**
     * 通过mqtt 发送命令
     * @param gatewayid 网关id
     * @param type  发送的数据类型
     * @param uuid  锁的UUID
     * @param mac   锁的mac地址
     * @param data  发送的数据
     */
    private void getDataToSend(final String gatewayid, final String type, final String uuid, final String mac, final String data){
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayid);
        map.put("type", type);
        map.put("uuid", uuid);
        map.put("mac", mac.toUpperCase());      //mac地址要大写
        map.put("data", data);
        JSONObject json = new JSONObject(map);

        if (getMqttCMDList().contains(json.toString())){
            Log.d(TAG,"mqtt 命令队列中已经存在此命令，不在重复添加:"+json.toString());
        }else{
            Log.d(TAG,"mqtt 命令队列中不存在此命令，需要添加:"+json.toString());
            getMqttCMDList().add(json.toString());
        }

        if (isSendingCmdMQTT()){
            Log.d(TAG,"当前正在发送mqtt命令。需要等待上一条命令发送结束");
        }else {
            if (isMqttOK){
                setSendingCmdMQTT(true);
                Log.d(TAG, "send command via mqtt:" + json.toString());
                publishMessage(json.toString());
            }else{
                setSendingCmdMQTT(false);
                Log.d(TAG, "getDataToSend2 MQTT is not ready, it cann't send command:" + json.toString());
                if (iotMqttCallback != null) {
                    iotMqttCallback.onPublishFailed();
                }
            }
        }
    }

    /**
     * 直接传递的json格式的数据 ， 绑定的时候用的
     * @param jsonData string
     */
    public void getDataToSend(final String jsonData){
        if (isMqttOK){
            Log.d(TAG, "send command via mqtt: " + jsonData);
            publishMessage(jsonData);
        }else{
            Log.d(TAG, "getDataToSend MQTT is not ready, it cann't send command:" + jsonData);
            if (iotMqttCallback != null) {
                iotMqttCallback.onPublishFailed();
            }
        }
    }

    /**
     * 直接推送数据到服务器端
     * @param jsonData string
     */
    public void pushDataToMqtt(final String jsonData){
        if (isMqttOK){
            Log.d(TAG, "pushDataToMqtt via mqtt: " + jsonData);
            publishMessage(jsonData);
        }else{
            Log.d(TAG, "pushDataToMqtt MQTT is not ready, it cann't send command:" + jsonData);
            if (iotMqttCallback != null) {
                iotMqttCallback.onPublishFailed();
            }
        }
    }

    private void checkDataReceive(String dataType){
        if (dataType.equals("send2lock")){
            Log.d(TAG,"The command sent to lock via mqtt has timed out, 12s");
            setSendingCmdMQTT(false);

            //add wrk 20211101 , 超时后将此命令删除
            if (!getMqttCMDList().isEmpty()) {
                Log.d(TAG,"超时，删除命令此命令："+ getMqttCMDList().getFirst());
                getMqttCMDList().removeFirst();
            }

            //如果队列中还有命令，则尝试去发送它
            if (!getMqttCMDList().isEmpty()){
                String cmd2send = getMqttCMDList().getFirst();
                if (isMqttOK){
                    setSendingCmdMQTT(true);
                    Log.d(TAG, "send command via mqtt:" + cmd2send);
                    publishMessage(cmd2send);
                }else{
                    setSendingCmdMQTT(false);
                    Log.d(TAG, "timout,MQTT is not ready, it cann't send command:" + cmd2send);
                    if (iotMqttCallback != null) {
                        iotMqttCallback.onPublishFailed();
                    }
                }
            }
            if (iotMqttCallback != null) {
                iotMqttCallback.onReceiveBLETimeout();
            }
        }

        if (dataType.equals("send2GW")){
            Log.d(TAG,"The command sent to gateway via mqtt has timed out, 10s");
            if (iotMqttCallback != null) {
                iotMqttCallback.onReceiveMQTTTimeout();
            }
        }

        if (dataType.equals("subscribeTopic") && !isMqttOK()){
            Log.d(TAG,"subscribeTopic timed out, 5s");
            if (iotMqttCallback != null) {
                iotMqttCallback.onSubscribeFailed();
            }
        }
    }

    /**
     * 创建用户,默认创建的是普通用户
     * @param userName string
     */
    public void addUser(String gatewayid, String type, String uuid, String mac, String sno, String userName){
        if (userName.isEmpty() || userName.length()>10){
            return;
        }

        String data = sno + ComTool.stringToAscii(userName);
        String datasend = mqttCommand("02", data,mac);
        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 编辑用户，修改用户名
     * @param userName 用户名
     * @param userNID   用户的nid
     */
    public void updateUserName(String gatewayid, String type, String uuid, String mac, String sno, String userName, String userNID){
        if (userNID.isEmpty() || userName.length()>10 ){
            Log.e(TAG,"Parameter error");
            return;
        }

        String username = "";
        if (!userName.isEmpty()){
            username = ComTool.stringToAscii(userName);
        }

        String updateStr = sno +userNID + username;
        String datasend = mqttCommand("03", updateStr, mac);
      //  Log.d(TAG,"调用 addUser 来添加用户:"+ datasend);
        getDataToSend(gatewayid, type, uuid, mac,datasend);

    }

    /**
     * 为用户添加一组数据,密码、卡、指纹
     * @param userNID       用户nid
     * @param dataType      数据类型， 02 普通密码，03超级用户密码，06是卡，07是指纹，08是防胁迫指纹
     * @param password      添加密码时具体的密码内容， 如果是添加卡/指纹时，留空即可
     */
    public void addDataForUser(String gatewayid, String type, String uuid, String mac, String sno, String userNID, String dataType, String password){
        if (userNID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }
        String datasend = "";

        //添加密码时
        if (dataType.equals("02") || dataType.equals("03") || dataType.equals("05")){
            if (password.isEmpty()){
                Log.e(TAG,"Parameter error");
                return;
            }
            String data = sno +userNID + dataType + password;
            datasend = mqttCommand("05", data, mac);
         //   Log.d(TAG,"调用 addUser 来添加用户:"+ datasend);
        }

        //添加卡、指纹、防胁迫指纹
        if (dataType.equals("06") || dataType.equals("07") || dataType.equals("08")){
            String data = sno +userNID + dataType ;
            datasend = mqttCommand("05", data, mac);
        }

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 删除某一项数据，删除一组密码、卡、指纹、绑定
     * @param dataType s
     * @param delID s
     */
    public void delData(String gatewayid, String type, String uuid, String mac, String sno, String dataType, String delID){
        if (delID.isEmpty() || dataType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String datasend ;

        if (!dataType.equals("0e")){
            String data = sno +dataType + delID ;
            datasend = mqttCommand("06", data, mac);
        }else {
            //删除绑定
            String data = sno + delID ;
            datasend = mqttCommand("1b", data, mac);
        }
        getDataToSend(gatewayid, type, uuid, mac,datasend);

    }

    /**
     * 修改用户的密码
     * @param userNid  用户的id
     * @param codeType 密码的类型
     * @param codeID   密码的ID
     * @param newCode  新的密码
     */
    public void resetCode(String gatewayid, String type, String uuid, String mac, String sno,
                          String userNid, String codeType, String codeID, String newCode){
        if (userNid.isEmpty() || codeType.isEmpty() || codeID.isEmpty() || newCode.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }
        String data = sno + userNid + codeType + codeID + newCode;
        String datasend = mqttCommand("0d", data, mac);
        getDataToSend(gatewayid, type, uuid, mac,datasend);
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
     *
     * @param data  value
     */
    public void setLock(String gatewayid, String type, String uuid, String mac, String sno, String dataType, String data){

        String datasend = "";
        //设置锁的名称
        if (dataType.equals("01")){
            if (data.length() >10 || data.length() == 0){
                Log.e(TAG,"Parameter error");
                return;
            }

            String locknameAscii = ComTool.stringToAscii(data);
            String setData = sno + locknameAscii;
            datasend = mqttCommand("11", setData, mac);
        }

        //设置锁的时间
        if (dataType.equals("02")){
            String setData = sno + data;
            datasend = mqttCommand("10", setData, mac);
        }

        //设置锁的自动锁门时间
        if (dataType.equals("03")){
            String sixteen = "";
            if (!data.isEmpty()){
                if(Integer.parseInt(data) <0 || Integer.parseInt(data)>240){
                    Log.e(TAG,"Parameter error");
                    return;
                }

                sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
            }

            String setData = sno + sixteen;
            datasend = mqttCommand("16", setData, mac);
        }

        //设置静音 和绑定后自动创建用户
        if (dataType.equals("04") || dataType.equals("05")){
            if (Integer.parseInt(data)<0 ){
                Log.e(TAG,"Parameter error");
                return;
            }

            if (dataType.equals("04")){
                String sixteen = Integer.toHexString(Integer.parseInt(data));
                if (sixteen.length() <2){
                    sixteen = "0"+sixteen;
                }
                String setData = sno + sixteen;
                datasend = mqttCommand("1c", setData, mac);
            }else {
                String setData = sno + data;
                datasend = mqttCommand("09", setData, mac);
            }
        }

        //设置超级用户的权限
        if (dataType.equals("06")){
            String setData = sno + data;
            datasend = mqttCommand("23", setData, mac);
        }
        getDataToSend(gatewayid, type, uuid, mac,datasend);
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
     *                 12 get the info of the lock
     */
    public void getLockInfo(String gatewayid, String type, String uuid, String mac, String sno, String dataType){
        String datasend = "";

        if (dataType.equals("01")){ datasend = mqttCommand("12", sno, mac); }
        if (dataType.equals("02")){ datasend = mqttCommand("0e", sno, mac); }
        if (dataType.equals("03")){ datasend = mqttCommand("0f", sno, mac); }
        if (dataType.equals("04")){ datasend = mqttCommand("1a", sno, mac); }

        //add by wrk at 20210315
        if (dataType.equals("05")){ datasend = mqttCommand("11", sno, mac); }
        if (dataType.equals("06")){ datasend = mqttCommand("10", sno, mac); }
        if (dataType.equals("07")){ datasend = mqttCommand("16", sno, mac); }
        if (dataType.equals("08")){ datasend = mqttCommand("1c", sno, mac); }
        if (dataType.equals("09")){ datasend = mqttCommand("09", sno, mac); }
        if (dataType.equals("10")){ datasend = mqttCommand("23", sno, mac); }
        if (dataType.equals("11")){ datasend = mqttCommand("1f", sno, mac); }
        if (dataType.equals("12")){ datasend = mqttCommand("2c", sno, mac); }

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 同步数据，包括用户信息
     */
    public void getAllUsers(String gatewayid, String type, String uuid, String mac, String sno){
        String data = sno +"00";
        String  datasend = mqttCommand("13", data, mac);
        getDataToSend(gatewayid, type, uuid, mac, datasend);
    }

    /**
     * 同步日志
     * @param logID  ，表示当前的日志id ,日志量比较大，所以支持从指定的id开始同步，如果 id为 ff ，则同步所有的日志
     */
    public void getLog(String gatewayid, String type, String uuid, String mac, String sno, String logID){

        String data = sno + logID;
        String datasend = mqttCommand("17", data, mac);

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 启用/禁用 动态密码
     * @param dynamicCode  对应的 动态密码
     * @param enable  00 表示禁用， 01 表示启动
     */
    public void doDynamicCode(String gatewayid, String type, String uuid, String mac, String sno, String dynamicCode, String enable){
        if (dynamicCode.isEmpty() ||!(enable.equals("00") || enable.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }

        String data = sno + enable + dynamicCode;
        String datasend = mqttCommand("20", data, mac);

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }


    /**
     * 修改密码的属性，改普通密码、超级用户密码
     * @param oldCodeType  该密码原来的 类型 ，02 普通密码，03 超级用户密码
     * @param codeID       密码的id
     * @param newCodeType  新的密码类型 ，02 普通密码，03 超级用户密码; 该字段为空，则表示查询此密码的类型
     */
    public void updateCodeType(String gatewayid, String type, String uuid, String mac, String sno,
                               String oldCodeType,String codeID, String newCodeType){
        if (oldCodeType.isEmpty() || codeID.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        if (oldCodeType.equals(newCodeType)){
            return;
        }

        String data = "";
        if (newCodeType.equals("02")){
            data = sno + oldCodeType + codeID + "00";
        }
        if (newCodeType.equals("03")){
            data = sno + oldCodeType + codeID + "01";
        }
        if (newCodeType.isEmpty()){
            data = sno + oldCodeType + codeID + "02";
        }
        String datasend = mqttCommand("07", data, mac);
        getDataToSend(gatewayid, type, uuid, mac,datasend);

    }

    /**
     * 校验密码
     * @param password 密码
     */
    public void verifyCode(String gatewayid, String type, String uuid, String mac, String sno, String password){
        if (password.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String data = sno+ password;
        String datasend = mqttCommand("08", data, mac);

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 通知锁端断开蓝牙连接
     */
    public void toDisconnBle(String gatewayid, String type, String uuid, String mac, String sno){
      //  Log.d(TAG,"调用 toDisconnBle 来断开蓝牙连接");
        String datasend = mqttCommand("1e", sno, mac);
        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    //停止mqtt 继续发送命令
    public void stopMqttSendCmd(){
        //同时清除 mqtt端的命令队列  --- add 20210916
        mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
        bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
        MqttLib.getInstance().getMqttCMDList().clear();
        MqttLib.getInstance().getBleSendHandler().removeCallbacksAndMessages(null);
        setSendingCmdMQTT(false);
        Log.d(TAG, "调用 stopMqttSendCmd 来停止 mqtt 队列命令的发送");
    }

    /**
     * 开关门操作
     * @param unlockType 00 表示锁门，01表示开门
     */
    public void toUnlock(String gatewayid, String type, String uuid, String mac, String sno, String unlockType, String code){
        if (unlockType.isEmpty() || code.isEmpty() || !(unlockType.equals("00") || unlockType.equals("01"))){
            Log.e(TAG,"Parameter error");
            return;
        }
        String data = sno + unlockType + code;
        String datasend = mqttCommand("0a", data, mac);

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    /**
     * 清空数据
     * @param datakType 表示清空数据的类型；
     *                  00 表示清空用户，不会删除管理员
     *                  0e 表示清空所有的绑定手机
     *                  0c 表示恢复出厂设置
     */
    public void cleanData(String gatewayid, String type, String uuid, String mac, String sno, String datakType){
        if (datakType.isEmpty()){
            Log.e(TAG,"Parameter error");
            return;
        }

        String datasend;

        //清空绑定的手机
        if (datakType.equals("0e")){
            datasend = mqttCommand("1b", sno, mac);
        }else {
            String data = sno + datakType;
            datasend = mqttCommand("0c", data, mac);
        }

        getDataToSend(gatewayid, type, uuid, mac,datasend);
    }

    public String mqttCommand(String funcode, String data,String mac){
        return SinovoBle.getInstance().getMyJniLib().encryptAes(funcode, data.toLowerCase(), mac.replace(":",""));
    }

    public void removeBleHandlerCallback(){
        bleSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
    }

    public void removeMqttHandlerCallback(){
        mqttSendHandler.removeCallbacksAndMessages(null);    //取消定时任务
    }

    public void setConnectType(int connectType) {
        this.connectType = connectType;
    }
}
