package com.sinovotec.sinovoble.common;

/**
 * @Description: BLE常量
 */
public class BleConstant {
    public static final String SERVICE_UUID_FM60            = "0000f6f6-0000-1000-8000-00805f9b34fb";     //蓝牙服务的 UUID
    public static final String SERVICE_UUID_FM67            = "0000f7f6-0000-1000-8000-00805f9b34fb";     //蓝牙服务的 UUID
    public static final String CHARACTERISTIC_UUID_FM60     = "0000f6f7-0000-1000-8000-00805f9b34fb";      //蓝牙服务下的 characteristic 的UUID
    public static final String CHARACTERISTIC_UUID_FM67     = "0000f7f7-0000-1000-8000-00805f9b34fb";      //蓝牙服务下的 characteristic 的UUID
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static final String SERVICE_UUID_DFU            = "0000fe59-0000-1000-8000-00805f9b34fb";        //dfu uuid
    public static final String CHARACTERISTIC_DFU1         = "8ec90002-f315-4f60-9fb8-838830daea50";        //dfu uuid
    public static final String CHARACTERISTIC_DFU2         = "8ec90001-f315-4f60-9fb8-838830daea50";        //dfu uuid
}
