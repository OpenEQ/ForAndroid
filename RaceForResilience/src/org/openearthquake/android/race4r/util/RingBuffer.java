package org.openearthquake.android.race4r.util;

import android.util.Log;

public class RingBuffer {
    public static String TAG = "race4r_ringbuffer";

    private int pointer;
    private byte[] buffer;
    private int byteNum;
    private int maxSize;

    public RingBuffer(int elementCount, int elementSize) {
        int arraySize = elementSize * elementCount;
        buffer = new byte[arraySize];
        pointer = 0;
        maxSize = elementCount;
        byteNum = elementSize;

        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = 0;
        }
    }

    // バッファにデータをプッシュする
    public void pushData(byte[] data){
        if (data.length != byteNum) {
            Log.d(TAG, "elements size is not equal initial value.\n");
            return ;
        }

        for (int i = 0; i < byteNum; i++) {
            buffer[pointer * byteNum + i] = data[i];
        }
        pointer++;

        if (pointer == maxSize) {
            pointer=0;
        }
    }
}