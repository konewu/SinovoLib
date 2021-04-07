package com.sinovotec.httplib;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpLib {

    private static final String TAG = "Sinovoble";
    private static final String serverIP = "https://gws.qiksmart.com";
    private static final MediaType mediaType = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient();

    private static HttpLib instance;
    private String accessToken;             //APP获取这个参数暂时保存在手机里，作为其他接口的调用凭证
//    private String reflashToken;            //APP获取这个参数暂时保存在手机里，作为其他接口的调用凭证
//    private String productKey;              //app接收mqtt推送用
//    private String deviceName;              //app接收mqtt推送用
//    private String deviceSecret;            //app接收mqtt推送用
//    private String appRegion;               //app接收mqtt推送用
//    private String subscribeTopic;          //app接收mqtt推送用
//    private String publishtopic;            //app接收mqtt推送用

//    private String gatewayID_add;           //当前锁添加的网关id
    private final HttpLibCallback httpLibCallback;  //http发送数据的回调

    private int exeCmdNo = -1;

    private HttpLib(HttpLibCallback callBack){
        this.httpLibCallback = callBack;
        if (httpLibCallback == null){
            throw new NullPointerException("this sendDataCallback is null!");
        }
    }

    public static HttpLib getInstance(HttpLibCallback callBack) {
        if (instance == null) {
            synchronized (HttpLib.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (instance == null) {
                    instance = new HttpLib(callBack);
                }
            }
        }
        return instance;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    //http post request without access_token
    private  String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).string();
        }
    }

    //http post request with access_token
    private  String postWithHeader(String url, String json, String headerValue) throws IOException {
        RequestBody body = RequestBody.create(json, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .header("Access_Token", headerValue)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).string();
        }
    }

    //failed to connect to http server
    private String  connfailed(String err){
        Log.e(TAG, "connection failed:" + err);
        LinkedHashMap<String, Object> maperr = new LinkedHashMap<>();
        LinkedHashMap<String, Object> maperrinfo = new LinkedHashMap<>();
        maperrinfo.put("code","0000000");
        maperrinfo.put("msg","Unable to connect to server");
        maperr.put("error",maperrinfo);
        JSONObject json2 =new JSONObject(maperr);
        return json2.toString();
    }


    /**
     * to Send Data With Thread
     */
    private void toSendDataWithThread(String cmdStr){
        Map<String, Object> mapTypes = JSON.parseObject(cmdStr);

        final String url        = Objects.requireNonNull(mapTypes.get("url")).toString();
        final String token      = Objects.requireNonNull(mapTypes.get("token")).toString();
        final int funcode       = Integer.parseInt(Objects.requireNonNull(mapTypes.get("funcode")).toString());
        final String postData   = Objects.requireNonNull(mapTypes.get("data")).toString();

        new Thread(() -> {
            try {
                String result ;
                Log.d(TAG, "http post,url:"+url + ", postData:" + postData);
                if (token.equals("01")){
                    result = postWithHeader(url, postData, getAccessToken());
                }else {
                    result = post(url, postData);
                }
                if (result.contains("!DOCTYPE html")){
                    callback(funcode,connfailed("SYSTEM ERROR"));
                }else {
                    callback(funcode,result);
                }
            } catch (IOException e) {
                callback(funcode,connfailed(e.toString()));
                e.printStackTrace();
            }
        }).start();
    }

    public void callback(int funcode, String resultStr){
        switch (funcode) {
            case 0:
                httpLibCallback.onUserRegister(resultStr);
                break;
            case 1:
                httpLibCallback.onUserLogin(resultStr);
                break;
            case 2:
                httpLibCallback.onGetVerifyCode(resultStr);
                break;
            case 3:
                httpLibCallback.onModifyPass(resultStr);
                break;
            case 4:
                httpLibCallback.onAddGateway(resultStr);
                break;
            case 5:
                httpLibCallback.onDelGateway(resultStr);
                break;
            case 6:
                httpLibCallback.onModifyGwName(resultStr);
                break;
            case 7:
                exeCmdNo = -1;
                httpLibCallback.onGetGwList(resultStr);
                break;
            case 8:
                httpLibCallback.onAddLock(resultStr);
                break;
            case 9:
                httpLibCallback.onUpdateLock(resultStr);
                break;
            case 10:
            case 11:
                httpLibCallback.onGetLockList(resultStr);
                break;
            case 12:
                httpLibCallback.onDelLock(resultStr);
                break;
            case 13:
                httpLibCallback.onUpdateLoginUserInfo(resultStr);
                break;
            case 14:
                httpLibCallback.onUpdateUserAvatar(resultStr);
                break;
            case 15:
                httpLibCallback.onShareLock(resultStr);
                break;
            case 16:
                httpLibCallback.onAddShareData(resultStr);
                break;
            case 17:
                httpLibCallback.onGetShareData(resultStr);
                break;
            case 18:
                httpLibCallback.onUpdateShareData(resultStr);
                break;
            case 19:
                httpLibCallback.onDelShareData(resultStr);
                break;
            case 20:
                httpLibCallback.onRemoveLock(resultStr);
                break;
        }
    }


    /**
     * Register new user
     * @param account  User's account
     * @param password  user's password
     */
    public void userRegister(String account, String password){
        String urlPath = "/api/User/register";
        final String url = serverIP + urlPath;
        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","00");       //注册时，不需要token
        cmdMap.put("funcode","00");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("auth", account);
        map.put("password", password);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * login
     * @param account  User's account
     * @param password  user's password
     */
    public void userLogin(String account, String password){
        String urlPath = "/api/User/login";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","00");       //login without token
        cmdMap.put("funcode","01");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("auth", account);
        map.put("password", password);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * Get the verification code before changing the password
     * @param account  User's account
     */
    public void getVerfyCode(String account){
        String urlPath = "/api/User/forgotPassword";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","00");
        cmdMap.put("funcode","02");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("auth", account);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * change password
     * @param account  User's account
     * @param verifycode  the verification code
     * @param newPass  new password
     */
    public void modifyPass(String account, String verifycode, String newPass){
        String urlPath = "/api/User/modifyPassword";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","00");       //修改密码时，不需要token
        cmdMap.put("funcode","03");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("auth", account);
        map.put("verifycode", verifycode);
        map.put("newPassword", newPass);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * add gateway
     * @param gatewayID    the gateway's ID
     * @param gatewayName  the gateway's name
     */
    public void addGateway(String gatewayID, String gatewayName){
        String urlPath = "/api/User/addGateway";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","04");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayID);
        map.put("gateway_name", gatewayName);
        cmdMap.put("data",new JSONObject(map));

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * delete gateway
     * @param gatewayID    the gateway's ID
     */
    public void delGateway(String gatewayID){
        String urlPath = "/api/User/deleteGateway";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","05");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayID);
        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * change gateway's name
     * @param gatewayID   the gateway's ID
     */
    public void modifyGWName(String gatewayID, String gatewayName){
        String urlPath = "/api/User/updateGateway";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","06");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("gateway_id", gatewayID);
        map.put("gateway_name", gatewayName);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * get the gateway's list
     */
    public void getGatewayList(){
        String urlPath = "/api/User/getGatewayList";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","07");
        cmdMap.put("data","");

        if (exeCmdNo !=7) {
            exeCmdNo = 7;
            String cmdString = new JSONObject(cmdMap).toString();
            toSendDataWithThread(cmdString);
        }
    }

    /**
     * Add lock to http server
     */
    public void addLock(final JSONObject json){
        String urlPath = "/api/User/addDevice";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","08");
        cmdMap.put("data",json);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * update information of the lock
     */
    public void updateLock(final JSONObject json){
        String urlPath = "/api/User/updateDevice";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","09");
        cmdMap.put("data",json);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * Get the list of lock for the user
     */
    public void getLockList(){
        String urlPath = "/api/User/getDeviceList";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","11");
        cmdMap.put("data","");

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * delete lock from http server
     */
    public void delLock(String lockID){
        String urlPath = "/api/User/deleteDevice";
        final String url = serverIP + urlPath;
        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","12");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("device_id", lockID);
        cmdMap.put("data",new JSONObject(map));

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * Update the information of the login user
     */
    public void updateLoginUserInfo(String nickname, String age, String gender, String address){
        String urlPath = "/api/User/addUserInfo";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","13");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("nickname", nickname);
        map.put("age", age);
        map.put("gender", gender);
        map.put("location", address);
        cmdMap.put("data",new JSONObject(map));

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * Update avatar for the login user
     * @param bitmap  avatar
     */
    public void updateUserAvatar(final Bitmap bitmap){
        String base64Img = bitmapToBase64(bitmap);
        String urlPath = "/api/User/addAvatar";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","14");
        cmdMap.put("data",base64Img);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * Share the lock to other users
     */
    public void shareLock(final JSONObject json){
        String urlPath = "/api/User/shareDevice";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","15");
        cmdMap.put("data",json);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * add share data to http server
     */
    public void addShareData(final JSONObject json){
        String urlPath = "/api/User/addShareData";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","16");
        cmdMap.put("data",json);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * get list of sharedData from http server
     */
    public void getShareDataList(final JSONObject json){
        String urlPath = "/api/User/getShareData";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","17");
        cmdMap.put("data",json);
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * update shareData on http server
     */
    public void updateShareData(final JSONObject json){
        String urlPath = "/api/User/updateShareData";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","18");
        cmdMap.put("data",json);
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * delete shareData from http server
     */
    public void delShareData(final JSONObject json){
        String urlPath = "/api/User/delShareData";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","19");
        cmdMap.put("data",json);
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }


    /**
     * 彻底从服务器上删除锁，管理员权限才能操作
     */
    public void removelock(String lockID){
        String urlPath = "/api/User/removeDevice";
        final String url = serverIP + urlPath;
        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","20");

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("device_id", lockID);

        cmdMap.put("data",new JSONObject(map));
        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /*
     * bitmap转base64
     * */
    private static String bitmapToBase64(Bitmap bitmap) {
        String result = null;
        ByteArrayOutputStream baos = null;
        try {
            if (bitmap != null) {
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

                baos.flush();
                baos.close();

                byte[] bitmapBytes = baos.toByteArray();
                result = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
}
