package com.sinovotec.sinovolibdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;
import com.sinovotec.sinovoble.SinovoBle;

import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    private AppBarConfiguration mAppBarConfiguration;
    private static final int REQUEST_CODE_QRCODE_PERMISSIONS = 1;   //请求权限
    private final String TAG = "SinovoLib";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        MyApp.getInstance().setMyContext(this);

        setHandlerUI();
        //初始化蓝牙库
        if(SinovoBle.getInstance().init(this, BleCallBack.getInstance().getmBleScanCallBack(), BleCallBack.getInstance().getmConnCallBack())){
            Log.d(TAG, "BLE Initialize OK");
        }
    }

    //////////////开启gps，解决 部分机型 无法搜索蓝牙的问题，如oppo
    /**
     * 判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
     * @param context 上下文
     * @return true   表示开启
     */
    private boolean isLocationEnable(final Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = false;
        boolean network = false;
        if (locationManager != null) {
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
        if (gps || network) {
            Log.d(TAG,"GPS 已经打开了");
            return true;
        }

        Log.d(TAG,"GPS 还没打开了");
        return false;
    }

    //跳转到 开启定位权限的页面 ，部分手机需要开启定位权限才能进行搜索
    private void setLocationService() {
        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        this.startActivityForResult(locationIntent, 2);
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestCodeQRCodePermissions();

        /*
         *  add by wrk ,for ble 20180912
         *  判断系统是否需要动态获取权限。andrroid 6.0 以上的设备，位置权限需要动态申请(BLE设备扫描蓝牙需要此权限)
         *  Build.VERSION.SDK_INT 是一个常量，表示当前设备的android版本
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "已经具有 位置信息的权限，检查是否具有 GPS定位的权限");
            if (!isLocationEnable(this)) {
                Toast.makeText(this, getString(R.string.openlocation), Toast.LENGTH_SHORT).show();
                setLocationService();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }


    //子线程通过主线程来更新UI
    public void setHandlerUI(){
        MyApp.getInstance().setHandlerUI(new Handler(Looper.getMainLooper()){
            @SuppressLint({"SetTextI18n", "ResourceAsColor"})
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        if (MyApp.getInstance().getShowStatus() != null) {
                            MyApp.getInstance().getShowStatus().setText("Connected");
                            MyApp.getInstance().getShowStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(), R.color.colorBackground_green));
                        }
                        if (MyApp.getInstance().getShowMacStatus() != null) {
                            MyApp.getInstance().getShowMacStatus().setText("Connected");
                            MyApp.getInstance().getShowMacStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(), R.color.colorBackground_green));
                        }
                        break;
                    case 1:
                        if (msg.obj != null) {
                            Toast.makeText(MyApp.getInstance().getMyContext(), msg.obj.toString(), Toast.LENGTH_LONG).show();
                        }
                        break;
                    case 2:
                        if (MyApp.getInstance().getShowStatus() != null) {
                            MyApp.getInstance().getShowStatus().setText("Unconnected");
                            MyApp.getInstance().getShowStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(), R.color.colorBackground_darkgray));
                        }
                        if (MyApp.getInstance().getShowMacStatus() != null) {
                            MyApp.getInstance().getShowMacStatus().setText("Unconnected");
                            MyApp.getInstance().getShowMacStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(), R.color.colorBackground_darkgray));
                        }
                        break;
                    case 3:
                        if (MyApp.getInstance().getProgressDialog() == null) {
                            MyApp.getInstance().setProgressDialog(new ProgressDialog(MyApp.getInstance().getMyContext()));
                            MyApp.getInstance().getProgressDialog().setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        }
                        MyApp.getInstance().getProgressDialog().setMessage(msg.obj.toString());
                        MyApp.getInstance().getProgressDialog().setCancelable(true);
                        MyApp.getInstance().getProgressDialog().show();
                        break;
                    case 4:
                        if (MyApp.getInstance().getProgressDialog() != null) {
                            if (MyApp.getInstance().getProgressDialog().isShowing()) {
                                MyApp.getInstance().getProgressDialog().dismiss();
                            }
                        }
                        break;
                    case 5:
                        if (MyApp.getInstance().getLockBtn() !=null){
                            MyApp.getInstance().getLockBtn().setText(msg.obj.toString());
                        }
                        if (MyApp.getInstance().getMaclockBtn() !=null){
                            MyApp.getInstance().getMaclockBtn().setText(msg.obj.toString());
                        }
                        break;
                    case 6:
                        if (MyApp.getInstance().getShowlockType() !=null){
                            MyApp.getInstance().getShowlockType().setText(msg.obj.toString());
                        }
                        if (MyApp.getInstance().getShowMaclockType() !=null){
                            MyApp.getInstance().getShowMaclockType().setText(msg.obj.toString());
                        }
                        break;
                    case 7:
                        if (MyApp.getInstance().getShowlockver() !=null){
                            MyApp.getInstance().getShowlockver().setText(msg.obj.toString());
                        }
                        if (MyApp.getInstance().getShowMaclockver() !=null){
                            MyApp.getInstance().getShowMaclockver().setText(msg.obj.toString());
                        }
                        break;
                    case 8:
                        if (MyApp.getInstance().getShowlockpower() !=null){
                            MyApp.getInstance().getShowlockpower().setText(msg.obj.toString());
                        }
                        if (MyApp.getInstance().getShowMaclockpower() !=null){
                            MyApp.getInstance().getShowMaclockpower().setText(msg.obj.toString());
                        }
                        break;
                }
            }
        });
    }


    /**
     *  动态请求权限
     * @param requestCode r
     * @param permissions p
     * @param grantResults g
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        Log.d(TAG, "Grant："+ perms);
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Log.d(TAG, "Refuse："+ perms);
        Toast.makeText(this, getResources().getString(R.string.appaskper), Toast.LENGTH_SHORT).show();
        goIntentSetting();
    }

    //跳转到app设置界面
    private void goIntentSetting() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", this.getPackageName(), null);
        intent.setData(uri);
        try {
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterPermissionGranted(REQUEST_CODE_QRCODE_PERMISSIONS)
    private void requestCodeQRCodePermissions() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, getString(R.string.needermisson), REQUEST_CODE_QRCODE_PERMISSIONS, perms);
        }
    }
}