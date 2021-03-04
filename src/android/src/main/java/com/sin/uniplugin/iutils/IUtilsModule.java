package com.sin.uniplugin.iutils;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sin.uniplugin.iutils.usb.USBCallback;
import com.sin.uniplugin.iutils.usb.USBWrapper;

import java.util.List;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;


public class IUtilsModule extends UniModule {

    String TAG = "IUtilsModule";
    public static int REQUEST_CODE = 1000;

    private JSONObject getResponse(Exception e) {
        JSONObject r = new JSONObject();
        r.put("error", e.getMessage());
        if (e instanceof IUException) {
            r.put("code", ((IUException) e).getCode());
        }
        return r;
    }

    private JSONObject getResponse(JSONObject d) {
        JSONObject r = new JSONObject();
        r.put("data", d);
        r.put("code", 0);
        return r;
    }

    //run ui thread
    @UniJSMethod(uiThread = false)
    public JSONObject test() {
        Log.e(TAG, "test--");
        JSONObject data = new JSONObject();
        data.put("code", "successX");
        data.put("time", System.currentTimeMillis());
        return data;
    }


    // USB封装
    static USBWrapper usbWrapper;

    private USBWrapper getUsbWrapper() {
        if (usbWrapper == null) {
            usbWrapper = USBWrapper.getInstance(mUniSDKInstance.getContext());
        }
        return usbWrapper;
    }

    @UniJSMethod(uiThread = false)
    public void getUsbDevices(JSONObject options, final UniJSCallback callback) {
        /**
         * 获取USB设备列表
         */
        Log.e(TAG, "getUsbList:" + options);
        JSONObject res = getUsbWrapper().getUsbDevices();
        if (callback != null) {
            callback.invoke(getResponse(res));
        }
    }

    @UniJSMethod(uiThread = false)
    public void getUsbDevice(JSONObject options, final UniJSCallback callback) {
        /**
         * 获取USB设备列表
         */
        Log.e(TAG, "getUsbDevice:" + options);
        try {
            JSONObject res = getUsbWrapper().getUsbDevice(options.get("vendorId") == null ? 0 : options.getInteger("vendorId"), options.get("productId") == null ? 0 : options.getInteger("productId"));
            if (callback != null) {
                callback.invoke(getResponse(res));
            }
        } catch (Exception e) {
            callback.invoke(getResponse(e));
        }
    }

    @UniJSMethod(uiThread = false)
    public void usbBulkTransfer(final JSONObject options, final UniJSCallback callback) {
        /**
         * 通过USB接口传输数据
         * options = {
         *     deviceId: 00, // or
         *     vendorId: 00,
         *     productId: 00,
         *     data: bytes,
         *     timeout: 2000,
         * }
         */
        Log.e(TAG, "usbBulkTransfer:" + options);
        try {
            new Thread() {
                @Override
                public void run() {
                    final int endpoint = options.get("endpoint") == null ? 0 : options.getInteger("endpoint");
                    final byte[] data = options.get("data") == null ? null : Base64.decode(options.getString("data"), Base64.DEFAULT);
                    final int timeout = options.get("timeout") == null ? 5000 : options.getInteger("timeout");
                    USBCallback openCallback = new USBCallback() {
                        @Override
                        public void deviceCallback(final USBWrapper.Device device) {
                            Log.i(TAG, " openCallback " + device);

                            if (device != null && device.isOpened) {
                                // 打开成功，开始发送
                                byte[] buf = data;
                                JSONObject res;
                                if (buf == null) {
                                    // 读取
                                    buf = new byte[16384];
                                }
                                try {
                                    int size = device.bulkTransfer(endpoint, buf, timeout);
                                    JSONObject rd = new JSONObject();
                                    rd.put("size", size);
                                    res = getResponse(rd);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    res = getResponse(e);
                                }
                                if (callback != null) {
                                    callback.invoke(res);
                                }
                            } else if (callback != null) {
                                callback.invoke(getResponse(new IUException("打开设备失败", USBWrapper.CODE_DEVICE_OPEN_FAIL)));
                            }
                        }
                    };
                    try {


                        if (options.containsKey("deviceId") && options.getInteger("deviceId") != null) {
                            getUsbWrapper().openDevice(options.getInteger("deviceId"), openCallback);
                        } else {
                            getUsbWrapper().openDevice(options.get("vendorId") == null ? 0 : options.getInteger("vendorId"), options.get("productId") == null ? 0 : options.getInteger("productId"), openCallback);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (callback != null) {
                            callback.invoke(getResponse(e));
                        }
                    }
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null) {
                callback.invoke(getResponse(e));
            }
        }
    }

    @Override
    public void onActivityDestroy() {
        super.onActivityDestroy();
    }
}
