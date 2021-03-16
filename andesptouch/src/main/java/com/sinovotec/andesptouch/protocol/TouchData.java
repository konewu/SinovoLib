package com.sinovotec.andesptouch.protocol;

import com.sinovotec.andesptouch.util.ByteUtil;

public class TouchData {
    private final byte[] mData;

    public TouchData(String string) {
        mData = ByteUtil.getBytesByString(string);
    }

    public TouchData(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data can't be null");
        }
        mData = data;
    }

    public byte[] getData() {
        return mData;
    }
}
