package com.sinovotec.sinovoble.common;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

import static android.content.Context.ACTIVITY_SERVICE;



/**
 * DFU 工具类
 */
public class DfuUtils {

    private static DfuUtils dfuUtils;
    private DfuServiceController serviceController;
    private DfuServiceInitiator starter;

    public static DfuUtils getInstance() {
        if (dfuUtils == null) {
            synchronized (DfuUtils.class) {
                if (dfuUtils == null) {
                    dfuUtils = new DfuUtils();

                }
            }
        }
        return dfuUtils;
    }

    public void setmDfuProgressListener(Context mContext, DfuProgressListener dfuProgressListener) {
        DfuServiceListenerHelper.registerProgressListener(mContext, dfuProgressListener); //监听升级进度
    }

    //开始升级
    public void startUpdate(Context mContext, String deviceMac, String deviceName, String filepath) {
        //闪退问题解决 兼容   启动前台通知的问题，因为这个库在升级的时候会在通知栏显示进度，
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(mContext);
        }

        starter = new DfuServiceInitiator(deviceMac)
                .setDeviceName(deviceName)       //设备名称
                .setKeepBond(false)             //保持设备绑定 官方demo为false
                .setForceDfu(true)
                .setNumberOfRetries(10)
                .setPacketsReceiptNotificationsEnabled(false)
                .setPacketsReceiptNotificationsValue(12)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);    //官方ndemo为true

        starter.setZip(filepath);
        serviceController = starter.start(mContext, DfuService.class);          //启动升级服务

        String TAG = "SinovoDfu";
        Log.d(TAG,"开始了升级服务器： 升级服务是否在运行："+ isDfuServiceRunning(mContext));
    }

    //暂停升级
    public void pauseDevice(Context mContext) {
        if (isDfuServiceRunning(mContext) && serviceController != null) {
            serviceController.pause();
        }
    }

    //销毁升级
    public void abortDevice(Context mContext) {
        if (isDfuServiceRunning(mContext) && serviceController != null) {
            serviceController.abort();
        }
    }

    /**
     * 判断dfu状态
     * @return bool
     */
    private boolean isDfuServiceRunning(Context mContext) {
        final ActivityManager manager = (ActivityManager) mContext.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DfuService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    //退出 dfu
    public void dispose(Context mContext,DfuProgressListener dfuProgressListener) {
        DfuServiceListenerHelper.unregisterProgressListener(mContext, dfuProgressListener);
        if (isDfuServiceRunning(mContext)) {
            if (serviceController != null) {
                serviceController.abort();
                mContext.stopService(new Intent(mContext, DfuService.class));
            }
        }
        if (starter != null) {
            starter = null;
        }
        if (serviceController != null) {
            serviceController = null;
        }
    }

}

