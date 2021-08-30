package com.sinovotec.espblufi;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sinovotec.sinovoble.SinovoBle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import blufi.espressif.BlufiCallback;
import blufi.espressif.BlufiClient;
import blufi.espressif.params.BlufiConfigureParams;
import blufi.espressif.params.BlufiParameter;
import blufi.espressif.response.BlufiStatusResponse;

import static android.content.Context.WIFI_SERVICE;

public class GWEspblufi {
    private final String TAG    = "SinovoBle";

    @SuppressLint("StaticFieldLeak")
    private static GWEspblufi instance;                //入口操作管理
    private BluetoothDevice mDevice;
    private BlufiClient mBlufiClient;                        //网关配网需要的 实例对象
    private byte[] wifiInfo;
    private Context mContext;

    public BlufiClient getBlufiClient() {
        return mBlufiClient;
    }

    public void setBlufiClient(BlufiClient blufiClient) {
        this.mBlufiClient = blufiClient;
    }

    public byte[] getWifiInfo() {
        return wifiInfo;
    }

    public void setWifiInfo(byte[] wifiInfo) {
        this.wifiInfo = wifiInfo;
    }

    public Context getmContext() {
        return mContext;
    }

    public void setmContext(Context mContext) {
        this.mContext = mContext;
    }

    public static GWEspblufi getInstance() {
        if (instance == null) {
            synchronized (GWEspblufi.class) {
                if (instance == null) {
                    instance = new GWEspblufi();
                }
            }
        }
        return instance;
    }


    /**
     * * connect to esp32 网关配网
     * @param wifiSSID  WIFI SSID
     * @param wifiPass  WIFI PASS
     */
    public void configureGW(Context context, String wifiSSID, String wifiPass){
        setmContext(context.getApplicationContext());

        WifiManager mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        Log.d(TAG, "准备配置网关的网络 wifi ssid："+ wifiSSID + ", wifi pass:"+ wifiPass);
        if (wifiSSID.isEmpty() || wifiPass.isEmpty() ){
            Log.d(TAG, "wifi ssid or pass is empty");
            return;
        }

        SinovoBle.getInstance().disconnBle();

        WifiInfo info = mWifiManager.getConnectionInfo();
        if (info != null) {
            setWifiInfo(getSSIDRawData(info));
        }

        SinovoBle.getInstance().setScanOnly(false);
        SinovoBle.getInstance().setBindMode(false);
        SinovoBle.getInstance().setGWConfigMode(true);
        SinovoBle.getInstance().setScanAgain(true);
        SinovoBle.getInstance().startBleScan();
    }

    private byte[] getSSIDRawData(WifiInfo info) {
        try {
            Method method = info.getClass().getMethod("getWifiSsid");
            method.setAccessible(true);
            Object wifiSsid = method.invoke(info);
            if (wifiSsid == null) {
                return null;
            }
            method = wifiSsid.getClass().getMethod("getOctets");
            method.setAccessible(true);
            return (byte[]) method.invoke(wifiSsid);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Try to connect device
     */
    public void connect(BluetoothDevice bluetoothDevice) {

        if (mBlufiClient != null) {
            mBlufiClient.close();
            mBlufiClient = null;
        }

        mBlufiClient = new BlufiClient(getmContext(), bluetoothDevice);
        mBlufiClient.setGattCallback(new GattCallback());
        mBlufiClient.setBlufiCallback(new BlufiCallbackMain());
        mBlufiClient.connect();
    }


    /**
     * mBlufiClient call onCharacteristicWrite and onCharacteristicChanged is required
     */
    private class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String devAddr = gatt.getDevice().getAddress();
            Log.d(TAG,String.format(Locale.ENGLISH, "onConnectionStateChange addr=%s, status=%d, newState=%d",
                    devAddr, status, newState));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.d(TAG,String.format("Connected %s", devAddr));
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        gatt.close();
                        Log.d(TAG,String.format("Disconnected %s", devAddr));
                        break;
                }
            } else {
                gatt.close();
                Log.d(TAG,String.format(Locale.ENGLISH, "Disconnect %s, status=%d", devAddr, status));
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG,String.format(Locale.ENGLISH, "onMtuChanged status=%d, mtu=%d", status, mtu));
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mBlufiClient.setPostPackageLengthLimit(20);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG,String.format(Locale.ENGLISH, "onServicesDiscovered status=%d", status));
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect();
                Log.d(TAG,String.format(Locale.ENGLISH, "Discover services error status %d", status));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG,String.format(Locale.ENGLISH, "onDescriptorWrite status=%d", status));

            if (status == BluetoothGatt.GATT_SUCCESS) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG,"配置网关");
                    BlufiConfigureParams params = new BlufiConfigureParams();
                    params.setOpMode(BlufiParameter.OP_MODE_STA);
                    params.setStaSSIDBytes(SinovoBle.getInstance().getWifiInfo());
                    params.setStaPassword("ZYG13632808272.");

                    getBlufiClient().configure(params);
                }, 500);

            }


        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect();
                Log.d(TAG,String.format(Locale.ENGLISH, "WriteChar error status %d", status));
            }
        }
    }

    private class BlufiCallbackMain extends BlufiCallback {
        @Override
        public void onGattPrepared(BlufiClient client, BluetoothGatt gatt, BluetoothGattService service,
                                   BluetoothGattCharacteristic writeChar, BluetoothGattCharacteristic notifyChar) {
            if (service == null) {
                Log.d(TAG,"Discover service failed");
                gatt.disconnect();
                return;
            }
            if (writeChar == null) {
                Log.d(TAG,"Get write characteristic failed");
                gatt.disconnect();
                return;
            }
            if (notifyChar == null) {
                Log.d(TAG,"Get notification characteristic failed");
                gatt.disconnect();
                return;
            }

            Log.d(TAG,"Discover service and characteristics success");

            Log.d(TAG,"Request MTU " + 512);
            boolean requestMtu = gatt.requestMtu(512);
            if (!requestMtu) {
                Log.d(TAG,"Request mtu failed");
            }
        }

        @Override
        public void onPostConfigureParams(BlufiClient client, int status) {
            if (status == STATUS_SUCCESS) {
                Log.d(TAG,"Post configure params complete");
            } else {
                Log.d(TAG,"Post configure params failed, code=" + status);
            }
        }

        @Override
        public void onDeviceStatusResponse(BlufiClient client, int status, BlufiStatusResponse response) {
            if (status == STATUS_SUCCESS) {
                Log.d(TAG,String.format("Receive device status response:\n%s", response.generateValidInfo()));
            } else {
                Log.d(TAG,"Device status response error, code=" + status);
            }
        }

        @Override
        public void onReceiveCustomData(BlufiClient client, int status, byte[] data) {
            if (status == STATUS_SUCCESS) {
                String customStr = new String(data);
                Log.d(TAG,String.format("Receive custom data:\n%s", customStr));
            } else {
                Log.d(TAG,"Receive custom data error, code=" + status);
            }
        }

        @Override
        public void onError(BlufiClient client, int errCode) {
            Log.d(TAG,String.format(Locale.ENGLISH, "Receive error code %d", errCode));
        }
    }

}
