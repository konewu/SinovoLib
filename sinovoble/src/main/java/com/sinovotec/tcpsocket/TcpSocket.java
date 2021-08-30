package com.sinovotec.tcpsocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sinovotec.mqtt.MqttLib;
import com.sinovotec.mqtt.iotMqttCallback;
import com.sinovotec.sinovoble.common.BleData;
import com.sinovotec.sinovoble.common.ComTool;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Objects;

public class TcpSocket {

    private static final String TAG = "SinovoBle";
    private static final int timeOut = 3*1000;
    private static TcpSocket instance;
    private static boolean isConnected = false;
    static Socket socket = null;
    static OutputStream writer = null;
    static InputStream reader = null;

    boolean isSending = false;      //是否正在发送命令中
    static String cmd = "";
    private final Handler tcpSendHander = new Handler(Looper.getMainLooper());  //通过mqtt 发送蓝牙命令的超时句柄

    private final iotMqttCallback iotMqttCallback;      //mqtt 通信结果回调
    private final LinkedList<String> commandList = new LinkedList<>();      //所有需要执行的命令 都放入到list中排队 ，等待执行

    public TcpSocket(iotMqttCallback mqttCallback) {
        this.iotMqttCallback = mqttCallback;
    }

    //实例化
    public static TcpSocket getInstance(iotMqttCallback mqttCallback) {
        if (instance == null) {
            synchronized (TcpSocket.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new TcpSocket(mqttCallback);
                }
            }
        }
        return instance;
    }

    //tcp socket 连接
    public void sendData(String serverIP, int port, String msg){
        if (!msg.isEmpty() && !commandList.contains(msg)){
            commandList.add(msg);
        }
        if (isSending){
            Log.i(TAG, "当前正在发送命令，先放入队列："+ msg);
            return;
        }
        cmd = commandList.getFirst();
        Log.i(TAG, "从队列中读取准备发送的命令："+ cmd);

        isSending = true;
        if (isConnected){
            Log.i(TAG, "当前socket为已经连接的状态,直接发送数据");

            new Thread() {
                public void run() {
                    try {
                        if (isServerClose()) {
                            Log.i(TAG, "TCP Socket连接已经断开1");
                            sendFaild(cmd);
                            closeSocket();
                        }else {
                            Thread.sleep(200);
                            writer.write(cmd.getBytes());
                            writer.flush();
                            tcpSendHander.postDelayed(() -> checkDataReceive(cmd), timeOut);
                            Log.w(TAG, "TCP Socket发送数据成功:" + cmd);
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                        sendFaild(cmd);
                        closeSocket();
                        tcpSendHander.removeCallbacksAndMessages(null);    //取消定时任务
                    }
                }
            }.start();
        }else {
            new Thread() {
                public void run() {
                    try {
                        Log.w(TAG, "开始进行tcpsocket 连接");
                        tcpSendHander.postDelayed(() -> checkDataReceive(cmd), timeOut);
                        socket = new Socket(serverIP, port);
                        writer = socket.getOutputStream();
                        isConnected = true;
                        tcpSendHander.removeCallbacksAndMessages(null);    //取消定时任务

                        if (isServerClose()) {
                            sendFaild(cmd);
                            closeSocket();
                        }else {
                            Thread.sleep(200);
                            writer.write(cmd.getBytes());
                            writer.flush();
                            tcpSendHander.postDelayed(() -> checkDataReceive(cmd), timeOut);
//                            Log.w(TAG, "TCP Socket发送数据成功:" + cmd);
                        }

                        while (true) {
                            reader = socket.getInputStream();
                            DataInputStream input = new DataInputStream(reader);
                            byte[] b = new byte[10240];

                            while (true) {
                                if (isServerClose()) {
                                    sendFaild(cmd);
                                    break;
                                }

                                int length = input.read(b);
//                                Log.i(TAG, "打印出字节的大小："+ length);

                                if (length != -1) {
                                    String msgFromTcp = new String(b, 0, length, "gb2312");
                                    msgFromTcp = msgFromTcp.replaceAll("\\p{C}", "");   //去掉不可见的字符
//                                    Log.e(TAG, "收到tcp 服务端的数据：" + msgFromTcp);
//                                    Log.e(TAG, "当前发送的数据：" + cmd);
//                                    MqttLib.getInstance().setTcpSendFaild(0);   //收到回复，表示发送成功
//                                    MqttLib.getInstance().setTcpSendFaildInter(0);

                                    String[] strarray = msgFromTcp.split("[}]");
                                    for (String msgSub: strarray) {
                                        msgSub = msgSub + "}";
                                        if (ComTool.isJson(msgSub)) {

                                            JSONObject jsonObject = JSON.parseObject(msgSub);

                                            String type = jsonObject.getString("type");
                                            if (type.equals("bledata")){  //需要先解析数据
                                                String data = jsonObject.getString("data");
                                                String mac  = jsonObject.getString("mac");
                                                String appId = jsonObject.getString("appId");
                                                String uuid  = jsonObject.getString("uuid");
                                                String gateway_id = jsonObject.getString("gateway_id");

                                                LinkedHashMap<String, Object> resultmap = BleData.getInstance().getDataFromBle(data.toLowerCase(), mac.toLowerCase());
//                                                Log.d(TAG, "数据处理后的结果：" + JSON.toJSONString(resultmap));

                                                resultmap.put("appId", appId);
                                                resultmap.put("type", type);
                                                resultmap.put("uuid", uuid);
                                                resultmap.put("gateway_id", gateway_id);
                                                resultmap.put("mac", mac);

                                                iotMqttCallback.onMsgArrived("TCP Socket", JSON.toJSONString(resultmap));
                                            }else {
                                                iotMqttCallback.onMsgArrived("TCP Socket",msgSub);
                                            }
                                        }else {
                                            Log.w(TAG,"非json格式的数据不处理，以免引起异常");
                                        }
                                    }


                                    //收到tcp的回复，同时将 mqtt 的超时检测取消
                                    if (msgFromTcp.contains("bledata")) {
                                        MqttLib.getInstance().removeBleHandlerCallback();
                                    } else {
                                        MqttLib.getInstance().removeMqttHandlerCallback();
                                    }

                                    if (!cmd.isEmpty()) {
                                        JSONObject sendMap = JSON.parseObject(cmd);
                                        boolean sendOK = false;   //标记是否正确收到回复，然后发送下一条

                                        String[] tcparray = msgFromTcp.split("[}]");
                                        for (String msgSub : tcparray) {
                                            msgSub = msgSub + "}";
                                            if (ComTool.isJson(msgSub)) {
                                                //需要判断 服务器回复过来的数据功能码 是否与发送的一致
                                                JSONObject resultMap = JSON.parseObject(msgSub);
                                                //发给网关处理的数据
                                                if (Objects.requireNonNull(resultMap.get("type")).toString().equals("get_lock_connect_status") ||
                                                        Objects.requireNonNull(resultMap.get("type")).toString().equals("disconnectLock") ||
                                                        Objects.requireNonNull(resultMap.get("type")).toString().equals("reboot") ||
                                                        Objects.requireNonNull(resultMap.get("type")).toString().equals("query_bindLock")) {
                                                    if (Objects.requireNonNull(resultMap.get("type")).toString().equals(Objects.requireNonNull(sendMap.get("type")).toString())) {
                                                        sendOK = true;
                                                    }
                                                }
                                                //发回蓝牙端的数据
                                                if (Objects.requireNonNull(resultMap.get("type")).toString().equals("bledata")) {
                                                    if (sendMap.containsKey("data")) {
                                                        String resultData = Objects.requireNonNull(resultMap.get("data")).toString();
                                                        String sendData = Objects.requireNonNull(sendMap.get("data")).toString();
                                                        String sendFun = sendData.substring(0, 4).toLowerCase();
                                                        String resultFun = resultData.substring(0, 4).toLowerCase();

                                                        Log.e(TAG, "解析发送的数据，功能吗：" + sendFun + "  " + resultFun);
                                                        if (sendFun.equals(resultFun)) {
                                                            Log.v(TAG, "回复的数据与发送的功能码一样，认为成功收到锁的回复，继续发送下一条");
                                                            sendOK = true;
                                                        }
                                                    }
                                                }
                                            } else {
                                                Log.w(TAG, "非json格式的数据不处理，以免引起异常");
                                            }
                                        }

                                        //发送下一条数据
                                        if (sendOK) {
                                            Log.w(TAG, "发送下一条数据,并取消定时任务");
                                            tcpSendHander.removeCallbacksAndMessages(null);    //取消定时任务

                                            if (commandList.size() > 0) {
                                                commandList.removeFirst();
                                            }else {
                                                Log.i(TAG, "队列中没有命令，设置 isSending 为false");
                                                cmd = "";
                                                isSending = false;
                                            }

                                            //收到回复后，执行下一条命令
                                            if (commandList.size() > 0) {
                                                cmd = commandList.getFirst();
                                                if (isServerClose()) {
                                                    Log.i(TAG, "TCP Socket连接已经断开4");
                                                    sendFaild(cmd);
                                                    break;
                                                } else {
                                                    Thread.sleep(200);
                                                    writer.write(cmd.getBytes());
                                                    writer.flush();
                                                    tcpSendHander.postDelayed(() -> checkDataReceive(cmd), timeOut);
                                                    Log.w(TAG, "TCP Socket发送数据成功:" + cmd);
                                                }
                                            } else {
                                                Log.i(TAG, "队列中没有命令，设置 isSending 为false");
                                                cmd = "";
                                                isSending = false;
                                            }
                                        }
                                    }
                                }
                            }
                            if (!isConnected){
                                Log.i(TAG, "TCP Socket未连接");
                                closeSocket();
                                break;
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                        Log.e(TAG, "连接失败");
                        sendFaild(cmd);
                        closeSocket();
                        tcpSendHander.removeCallbacksAndMessages(null);    //取消定时任务
                    }
                }
            }.start();
        }
    }

    public void sendFaild(String msg){
        Log.i(TAG, "命令："+msg+" 发送失败");
//        if (MqttLib.getInstance().getTcpSendFaild() == 0){
//            MqttLib.getInstance().setTcpSendFaildInter(System.currentTimeMillis());
//        }
//        MqttLib.getInstance().setTcpSendFaild(MqttLib.getInstance().getTcpSendFaild() + 1);
        commandList.clear();
        isConnected = false;
        isSending = false;
        if (!msg.isEmpty()) {
            MqttLib.getInstance().getDataToSend(msg);
        }
    }

    //tcp 数据发送之后，2s后是否收到回复
    public void checkDataReceive(String msgcmd){
        Log.i(TAG, "数据通过tcp socket发送出去 3秒没有收到回复 "+ msgcmd);

        if (!msgcmd.isEmpty()){
            MqttLib.getInstance().getDataToSend(msgcmd);
        }

        if (isConnected){
            if (commandList.size()>0){
                commandList.removeFirst();
            }else {
                isSending = false;
                cmd = "";
                return;
            }

            if (commandList.size()>0){
                cmd = commandList.getFirst();
                isSending = true;
                new Thread() {
                    public void run() {
                        try {
                            if (isServerClose()) {
                                Log.i(TAG, "TCP Socket连接已经断开1");
                                sendFaild(cmd);
                                closeSocket();
                            }else {
                                Thread.sleep(200);
                                writer.write(cmd.getBytes());
                                writer.flush();
                                tcpSendHander.postDelayed(() -> checkDataReceive(cmd), timeOut);
                                Log.w(TAG, "TCP Socket发送数据成功:" + cmd);
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                            sendFaild(cmd);
                            closeSocket();
                            tcpSendHander.removeCallbacksAndMessages(null);    //取消定时任务
                        }
                    }
                }.start();
            }else {
                isSending = false;
                cmd = "";
            }
        }

    }

    //关闭socket
    public void  closeSocket(){
        try {
            //关闭socket
            if (null != socket) {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.getInputStream().close();
                socket.getOutputStream().close();
                socket.close();
                Log.d(TAG,"关闭socket");
            }
            commandList.clear();
            isConnected = false;
            isSending = false;
        } catch (IOException e) {
            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }

    /**
     * 判断是否断开连接，断开返回true,没有返回false
     * @return bool
     */
    public Boolean isServerClose(){
        try{
            socket.sendUrgentData(0xFF);//发送1个字节的紧急数据，默认情况下，服务器端没有开启紧急数据处理，不影响正常通信
            return false;
        }catch(Exception se){
            return true;
        }
    }

}
