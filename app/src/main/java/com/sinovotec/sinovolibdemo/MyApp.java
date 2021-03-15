package com.sinovotec.sinovolibdemo;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.widget.EditText;
import android.widget.TextView;

import com.qmuiteam.qmui.widget.roundwidget.QMUIRoundButton;

public class MyApp extends Application {
    @SuppressLint("StaticFieldLeak")
    public static MyApp myApp;
    private Handler handlerUI;              //更新UI 的handler
    private Context myContext;
    private TextView showStatus;
    private TextView showResult;
    private TextView showlockType;
    private TextView showlockver;
    private TextView showlocktime;
    private TextView showlockpower;
    private EditText codeET;
    private ProgressDialog progressDialog;
    private QMUIRoundButton lockBtn;
    private String lockStatus = "";
    private String code = "";
    private String locktype = "";
    private String lockversion = "";
    private String lockpower = "";
    private String qrcode_input = "";

    private TextView showMacStatus;
    private TextView showMacResult;
    private TextView showMaclockType;
    private TextView showMaclockver;
    private TextView showMaclocktime;
    private TextView showMaclockpower;
    private QMUIRoundButton MaclockBtn;

    public static MyApp getInstance() {
        if (myApp == null) {
            synchronized (MyApp.class) {      //同步锁,一个线程访问一个对象中的synchronized(this)同步代码块时，其他试图访问该对象的线程将被阻塞
                if (myApp == null) {
                    myApp = new MyApp();
                }
            }
        }
        return myApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        myApp = this;
    }

    public Handler getHandlerUI() {
        return handlerUI;
    }

    public void setHandlerUI(Handler handlerUI) {
        this.handlerUI = handlerUI;
    }

    public Context getMyContext() {
        return myContext;
    }

    public void setMyContext(Context myContext) {
        this.myContext = myContext;
    }

    public TextView getShowStatus() {
        return showStatus;
    }

    public void setShowStatus(TextView showStatus) {
        this.showStatus = showStatus;
    }

    public ProgressDialog getProgressDialog() {
        return progressDialog;
    }

    public void setProgressDialog(ProgressDialog progressDialog) {
        this.progressDialog = progressDialog;
    }

    public TextView getShowResult() {
        return showResult;
    }

    public void setShowResult(TextView showResult) {
        this.showResult = showResult;
    }

    public EditText getCodeET() {
        return codeET;
    }

    public void setCodeET(EditText codeET) {
        this.codeET = codeET;
    }

    public String getLockStatus() {
        return lockStatus;
    }

    public void setLockStatus(String lockStatus) {
        this.lockStatus = lockStatus;
    }

    public QMUIRoundButton getLockBtn() {
        return lockBtn;
    }

    public void setLockBtn(QMUIRoundButton lockBtn) {
        this.lockBtn = lockBtn;
    }

    public TextView getShowlockpower() {
        return showlockpower;
    }

    public TextView getShowlocktime() {
        return showlocktime;
    }

    public TextView getShowlockType() {
        return showlockType;
    }

    public TextView getShowlockver() {
        return showlockver;
    }

    public void setShowlockpower(TextView showlockpower) {
        this.showlockpower = showlockpower;
    }

    public void setShowlocktime(TextView showlocktime) {
        this.showlocktime = showlocktime;
    }

    public void setShowlockType(TextView showlockType) {
        this.showlockType = showlockType;
    }

    public void setShowlockver(TextView showlockver) {
        this.showlockver = showlockver;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public String getLockpower() {
        return lockpower;
    }

    public String getLocktype() {
        return locktype;
    }

    public String getLockversion() {
        return lockversion;
    }

    public void setLockpower(String lockpower) {
        this.lockpower = lockpower;
    }

    public void setLocktype(String locktype) {
        this.locktype = locktype;
    }

    public void setLockversion(String lockversion) {
        this.lockversion = lockversion;
    }

    public String getQrcode_input() {
        return qrcode_input;
    }

    public void setQrcode_input(String qrcode_input) {
        this.qrcode_input = qrcode_input;
    }

    public TextView getShowMacStatus() {
        return showMacStatus;
    }

    public void setShowMacStatus(TextView showMacStatus) {
        this.showMacStatus = showMacStatus;
    }

    public TextView getShowMaclockType() {
        return showMaclockType;
    }

    public void setShowMaclockType(TextView showMaclockType) {
        this.showMaclockType = showMaclockType;
    }

    public QMUIRoundButton getMaclockBtn() {
        return MaclockBtn;
    }

    public void setMaclockBtn(QMUIRoundButton maclockBtn) {
        MaclockBtn = maclockBtn;
    }

    public TextView getShowMaclockpower() {
        return showMaclockpower;
    }

    public void setShowMaclockpower(TextView showMaclockpower) {
        this.showMaclockpower = showMaclockpower;
    }

    public TextView getShowMaclocktime() {
        return showMaclocktime;
    }

    public void setShowMaclocktime(TextView showMaclocktime) {
        this.showMaclocktime = showMaclocktime;
    }

    public TextView getShowMaclockver() {
        return showMaclockver;
    }

    public void setShowMaclockver(TextView showMaclockver) {
        this.showMaclockver = showMaclockver;
    }

    public TextView getShowMacResult() {
        return showMacResult;
    }

    public void setShowMacResult(TextView showMacResult) {
        this.showMacResult = showMacResult;
    }

}
