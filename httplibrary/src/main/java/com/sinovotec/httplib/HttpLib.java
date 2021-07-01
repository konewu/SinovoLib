package com.sinovotec.httplib;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpLib {

    private static final String TAG = "SinovoHttp";
    private static final String serverIP = "https://gws.qiksmart.com";
    private static final MediaType mediaType = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient = new OkHttpClient();

    private static HttpLib instance;
    private String accessToken;             //APP获取这个参数暂时保存在手机里，作为其他接口的调用凭证

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
     *get的方式请求
     *@return 返回null 登录异常
     */
    private String httpGet(String path){
        try {
            URL url = new URL(path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");
            //获得结果码
            int responseCode = connection.getResponseCode();
            if(responseCode ==200){
                //请求成功 获得返回的流
                InputStream is = connection.getInputStream();
                return convertStreamToString(is);
            }else {
                //请求失败
                return "";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     *get的方式请求
     *@return 返回null 登录异常
     */
    private String httpDownDfuFile(String path, String locktype, String fmversion, String savePath, String dfuMD5) throws Exception {
        try {
            URL url = new URL(path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setRequestMethod("GET");

            InputStream inStream = connection.getInputStream();// 通过输入流获取html数据
            byte[] data = readInputStream(inStream);// 得到html的二进制数据

            DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(savePath));//把byte写入文件
            dataOutputStream.write(data);
            dataOutputStream.flush();

            //对比下载文件的md5值 是否一致
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("srcFile",path);
            jsonObject.put("localFile",savePath);
            jsonObject.put("lockType",locktype);
            jsonObject.put("fmversion",fmversion);
            jsonObject.put("dfuMd5",dfuMD5);
            String checkOK = "00";      //默认下载文件的md5是不正确的，需要校验

            if (fileIsExists(savePath)) {
                File dfuFileLocal = new File(savePath);
                String md5Local = getFileMD5(dfuFileLocal);
                if (dfuMD5.equals(md5Local)){
                    checkOK = "01";
                }
            }
            jsonObject.put("checkOK",checkOK);
            return jsonObject.toJSONString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * http get  with thread
     */
    private void httpGetWithThread(String url, int getFlag){
        new Thread(() -> {
            Log.i(TAG, "http get,url:"+url);
            String result = httpGet(url);
            if (result.contains("!DOCTYPE html") || result.isEmpty()){
                callback(getFlag,connfailed("SYSTEM ERROR"));
            }else {
                callback(getFlag,result);
            }
        }).start();
    }

    private void httpGetImage(final String url, final String dataType){
        new Thread(() -> {
            URL imageurl = null;
            try {
                imageurl = new URL(url);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) Objects.requireNonNull(imageurl).openConnection();
                conn.setDoInput(true);
                conn.connect();

                InputStream is = conn.getInputStream();

                ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
                byte[] buff = new byte[4096]; //buff用于存放循环读取的临时数据
                int rc ;
                while ((rc = is.read(buff, 0, 100)) > 0) {
                    swapStream.write(buff, 0, rc);
                }
                byte[] in_b = swapStream.toByteArray(); //in_b为转换之后的结果

//                bitmap[0] = BitmapFactory.decodeStream(is);
             //   httpLibCallback.onGetLockImage(bitmap[0], dataType);
                if (dataType.length() >0) {
                    httpLibCallback.onGetLockImage(in_b, dataType);
                }else {
                    httpLibCallback.onGetUserAvatar(in_b);
                }
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                httpLibCallback.onGetLockImage(null,"error");
            }
        }).start();
    }

    /**
     * http get  with thread
     */
    private void httpDownloadWithThread(String url, String lockType, String fmversion ,String savaPath, String dfuMD5){
        new Thread(() -> {
            Log.i(TAG, "http download ,url:"+url);
            String result = null;
            try {
                result = httpDownDfuFile(url, lockType, fmversion, savaPath, dfuMD5);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (Objects.requireNonNull(result).contains("!DOCTYPE html") || result.isEmpty()){
                callback(22,connfailed("SYSTEM ERROR"));
            }else {
                callback(22,result);
            }
        }).start();
    }

    /**
     * http  get 请求，当前型号锁的 升级包
     */
    public void downloadDFUfile(String filePath, String lockType, String fmversion ,String savePath, String dfuMD5, boolean isRelease){
        String path = serverIP+"/dfu_release/"+filePath;
        if (!isRelease){
            path = serverIP+"/dfu_debug/"+filePath;
        }
        httpDownloadWithThread(path, lockType, fmversion, savePath, dfuMD5);
    }

    /**
     * to Send Data With Thread
     */
    private void toSendDataWithThread(String cmdStr) {
        Map<String, Object> mapTypes = JSON.parseObject(cmdStr);

        final String url        = Objects.requireNonNull(mapTypes.get("url")).toString();
        final String token      = Objects.requireNonNull(mapTypes.get("token")).toString();
        final int funcode       = Integer.parseInt(Objects.requireNonNull(mapTypes.get("funcode")).toString());
        final String postData   = Objects.requireNonNull(mapTypes.get("data")).toString();

        new Thread(() -> {
            try {
                String result = "";
                Log.i(TAG, "http post,url:"+url );
                if (token.equals("01")){
                    if (getAccessToken() !=null) {
                        result = postWithHeader(url, postData, getAccessToken());
                    }else {
                        callback(funcode,connfailed("ERROR: accessToken is null"));
                    }
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
                JSONObject mapTypes = JSONObject.parseObject(resultStr);
                if (mapTypes.containsKey("access_token")){
                    accessToken = Objects.requireNonNull(mapTypes.get("access_token")).toString();
                    Log.d(TAG, "登录成功之后，需要跟更新 access_token："+ accessToken );
                }
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
            case 21:
                httpLibCallback.onGetDFUInfo(resultStr);
                break;
            case 22:    //download file的返回
                httpLibCallback.onDownLoadFile(resultStr);
                break;
            case 23:    //恢复出厂设置， 服务器会自动推送给  相应的用户手上
                httpLibCallback.onResetLock(resultStr);
                break;
            case 24:    //查询锁的型号
                httpLibCallback.onGetLockType(resultStr);
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
        Log.d(TAG, "GWLIST 打印出 access_token：" + accessToken);
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
     * update information of the lock
     */
    public void resetLock(final JSONObject json){
        String urlPath = "/api/User/resetDevice";
        final String url = serverIP + urlPath;

        LinkedHashMap<String, Object> cmdMap = new LinkedHashMap<>();
        cmdMap.put("url",url);
        cmdMap.put("token","01");
        cmdMap.put("funcode","23");
        cmdMap.put("data",json);

        String cmdString = new JSONObject(cmdMap).toString();
        toSendDataWithThread(cmdString);
    }

    /**
     * Update the information of the login user
     */
    public void updateLoginUserInfo(String nickname, String age, String gender, String address, String shake_unlock, String vibration){
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
        map.put("shake_unlock", shake_unlock);
        map.put("vibration", vibration);
        cmdMap.put("data",new JSONObject(map));

        String cmdString = new JSONObject(cmdMap).toString();

        Log.i(TAG, "准备更新用户消息："+ cmdString);
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
     * http  get 请求，查询升级包信息
     */
    public void getDFUInfo(boolean isRelease){
        String path = serverIP+"/dfu_release/dfu.json";
        if (!isRelease){
            path = serverIP+"/dfu_debug/dfu.json";
        }
        httpGetWithThread(path, 21);
    }

    /**
     * http  get 请求，查询锁的类型型号
     */
    public void getLockType(){
        String path = serverIP+"/locktype/locktype.json";
        httpGetWithThread(path, 24);
    }

    /**
     * http  get 请求，查询指定锁的 图片
     */
    public void getUserAvatar(String url){
        httpGetImage(url, "");
    }

    /**
     * http  get 请求，查询指定锁的 图片
     */
    public void getLockImage(String url, String lockType){
        String path = serverIP+"/locktype/"+url;
        httpGetImage(path, lockType);
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

    /**
     * 将输入流转 字符串
     * @param is  InputStream
     * @return String
     */
    public String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line ;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\r\n");
            }
        } catch (IOException e){
            e.printStackTrace();
        }finally{
            try{
                is.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * 从输入流中获取数据
     * @param inStream 输入流
     * @return 字节数组
     * @throws Exception 异常
     */
    public byte[] readInputStream(InputStream inStream) throws Exception{
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len ;
        while( (len=inStream.read(buffer)) != -1 ){
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }

    //检查文件是否存在
    public boolean fileIsExists(String strFile) {
        try
        {
            File f=new File(strFile);
            if(!f.exists())
            {
                return false;
            }

        }
        catch (Exception e)
        {
            return false;
        }

        return true;
    }

    //计算 文件的 md5值
    public static String getFileMD5(File file) {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest ;
        FileInputStream in ;
        byte[] buffer = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return bytesToHexString(digest.digest());
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (byte b : src) {
            int v = b & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

}
