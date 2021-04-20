package com.sinovotec.sinovoble.common;

import android.app.Activity;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import no.nordicsemi.android.dfu.DfuBaseService;

public class DfuService extends DfuBaseService {
    public DfuService() {
    }

    @Nullable
    @Override
    protected Class<? extends Activity> getNotificationTarget() {

        return NotificationActivity.class;
    }

    @Override
    protected boolean isDebug() {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
