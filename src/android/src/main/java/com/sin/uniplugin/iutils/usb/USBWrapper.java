package com.sin.uniplugin.iutils.usb;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.sin.android.usb.USBUtil;
import com.sin.uniplugin.iutils.IUCallback;
import com.sin.uniplugin.iutils.IUException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class USBWrapper {
    public static final int CODE_DEVICE_NOT_FOUND = 1000;
    public static final int CODE_DEVICE_OPEN_FAIL = 1001;
    public static final int CODE_DEVICE_WRITE_FAIL = 1002;
    public static final int CODE_DEVICE_READ_FAIL = 1003;

    public class Device {
        Context context;
        UsbManager usbManager;
        UsbDevice usbDevice;
        public boolean isOpened = false;
        UsbDeviceConnection usbDeviceConnection;
        UsbInterface usbInterface = null;

        public Device(Context context, UsbManager usbManager, UsbDevice usbDevice) {
            this.context = context;
            this.usbManager = usbManager;
            this.usbDevice = usbDevice;
        }

        public boolean checkPermission(boolean autoRequest) {
            if (!usbManager.hasPermission(usbDevice)) {
                if (autoRequest) {
                    PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(USB_PERMISSION), 0);
                    usbManager.requestPermission(usbDevice, mPermissionIntent);
                }
                return false;
            }
            return true;
        }

        public JSONObject toJson() {
            JSONObject res = getUsbDeviceJson(this.usbDevice);
            res.put("IsOpened", this.isOpened);
            return res;
        }

        public void open() {
            usbDeviceConnection = usbManager.openDevice(usbDevice);
            isOpened = true;
        }

        public void close() {
            if (usbDeviceConnection != null) {
                if (usbInterface != null) {
                    usbDeviceConnection.releaseInterface(usbInterface);
                    usbInterface = null;
                }
                usbDeviceConnection.close();
                usbDeviceConnection = null;
            }
            isOpened = false;
        }

        public int bulkTransfer(int endpoint,
                                byte[] buffer, int timeout) {
            Log.i(TAG, "bulkTransfer Endpoint=" + endpoint + " Buffer=" + buffer + " Len=" + buffer.length + " Timeout=" + timeout);
            if (usbInterface == null) {
                usbInterface = usbDevice.getInterface(0);
                usbDeviceConnection.claimInterface(usbInterface, true);
            }

            UsbEndpoint usbEndpoint = null;

            for (int i = 0; i < usbInterface.getEndpointCount(); ++i) {
                UsbEndpoint ue = usbInterface.getEndpoint(i);
                Log.i(TAG, "ep " + i + " type=" + ue.getType() + " dir=" + ue.getDirection());
                if (endpoint > 0 && ue.getEndpointNumber() == endpoint) {
                    usbEndpoint = ue;
                    break;
                }
//                else if (ue.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ue.getDirection() == UsbConstants.USB_DIR_IN) {
//
//                } else if (ue.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK && ue.getDirection() == UsbConstants.USB_DIR_OUT) {
//                    usbEndpoint = ue;
//                }
            }
            if (usbEndpoint == null) {
                throw new IUException("未指定端点或端点不存在:" + endpoint);
            }
            Log.i(TAG, "toep num=" + usbEndpoint.getEndpointNumber() + " max=" + usbEndpoint.getMaxPacketSize() + " type=" + usbEndpoint.getType() + " dir=" + usbEndpoint.getDirection());
            int size = 0;
            int MAX = 16384;
            int start = 0;
            while (start < buffer.length) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    int len = Math.min(MAX, buffer.length - start);
                    int r = usbDeviceConnection.bulkTransfer(usbEndpoint, buffer, start, len, timeout);
                    Log.i(TAG, "send start=" + start + " len=" + len + " ret=" + r);
                    if (r < 0) {
                        size = -1;
                        break;
                    } else {
                        size += 0;
                    }
                    start += len;
                } else {
                    size = usbDeviceConnection.bulkTransfer(usbEndpoint, buffer, buffer.length, timeout);
                    break;
                }
            }
            Log.i(TAG, "ret size=" + size);
            return size;
        }

        @Override
        public String toString() {
            return this.toJson().toString();
        }
    }


    static String TAG = "USBWrap";
    Context context;
    UsbManager usbManager;
    static String USB_PERMISSION = "USB_PERMISSION";
    Map<Integer, Device> deviceMap = new HashMap<>();
    private USBCallback permissionCallback;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    boolean r = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (r) {
                        Log.i(TAG, "授权成功");
                    } else {
                        Log.e(TAG, "授权失败");
                    }
                    if (permissionCallback != null) {
                        permissionCallback.boolCallback(r);
                    }
                }
            }
        }
    };

    private USBWrapper(Context context) {
        this.context = context;
        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        filter.addAction(USB_PERMISSION);
        this.context.registerReceiver(mUsbReceiver, filter);
        this.usbManager = USBUtil.getUsbManager(context);
    }

    @SuppressLint("StaticFieldLeak")
    private static USBWrapper instance;

    public static USBWrapper getInstance(Context context) {
        if (instance == null) {
            instance = new USBWrapper(context);
        }
        return instance;
    }

    static private JSONObject getUsbDeviceJson(UsbDevice u) {
        JSONObject ju = new JSONObject();
        ju.put("DeviceId", u.getDeviceId());
        ju.put("DeviceName", u.getDeviceName());
        ju.put("ProductId", u.getProductId());
        ju.put("VendorId", u.getVendorId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ju.put("Version", u.getVersion());
        }
        ju.put("InterfaceCount", u.getInterfaceCount());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ju.put("ManufacturerName", u.getManufacturerName());
            ju.put("ConfigurationCount", u.getConfigurationCount());
            ju.put("SerialNumber", u.getSerialNumber());
        }
        ju.put("DeviceClass", u.getDeviceClass());
        ju.put("DeviceProtocol", u.getDeviceProtocol());
        return ju;
    }

    public JSONObject getUsbDevices() {
        List<JSONObject> jusbs = new ArrayList<>();
        List<UsbDevice> usbs = USBUtil.getUsbDevices(context, -1, -1);
        Log.e(TAG, "Usb count--" + usbs.size());
        for (int i = 0; i < usbs.size(); ++i) {
            UsbDevice u = usbs.get(i);
            Log.e(TAG, "Usb " + i + " " + u.getDeviceName());
            jusbs.add(getUsbDeviceJson(u));
        }
        JSONObject res = new JSONObject();
        res.put("count", jusbs.size());
        res.put("devices", jusbs);
        return res;
    }

    public JSONObject getUsbDevice(int vendorId, int productId) {
        UsbDevice usb = USBUtil.getUsbDevice(context, vendorId, productId);
        if (usb == null)
            throw new IUException("设备不存在(vendorId=" + vendorId + ",productId=" + productId + ")", CODE_DEVICE_NOT_FOUND);
        return getUsbDeviceJson(usb);
    }

    private Device openDevice(Device device, final USBCallback deviceCallback) {
        if (device.isOpened) {
            // 已经打开
            deviceCallback.deviceCallback(device);
        } else {
            // 需要打开
            final Device fdevice = device;
            final USBCallback openCallbac = new USBCallback() {
                public void boolCallback(boolean res) {
                    if (res) {
                        // 有权限打开
                        try {
                            fdevice.open();
                            deviceCallback.deviceCallback(fdevice);
                            return;
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (deviceMap.containsKey(fdevice.usbDevice.getDeviceId())) {
                                deviceMap.remove(fdevice.usbDevice.getDeviceId());
                            }
                        }
                    }
                    deviceCallback.deviceCallback(null);
                }
            };
            if (device.checkPermission(false)) {
                // 有权限直接打开
                openCallbac.boolCallback(true);
            } else {
                // 无权限，申请授权
                permissionCallback = new USBCallback() {
                    @Override
                    public void boolCallback(boolean res) {
                        openCallbac.boolCallback(res);
                    }
                };
                device.checkPermission(true);
            }
        }
        return device;
    }

    private Device openUsb(UsbDevice usb, final USBCallback callback) {
        int deviceId = usb.getDeviceId();
        Device device = null;
        if (!deviceMap.containsKey(deviceId)) {
            device = new Device(context, usbManager, usb);
            deviceMap.put(deviceId, device);
        }
        return openDevice(deviceMap.get(deviceId), callback);
    }

    public Device openDevice(int deviceId, final USBCallback deviceCallback) {
        if (!deviceMap.containsKey(deviceId)) {
            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = devices.values().iterator();
            List<UsbDevice> retDevices = new ArrayList<UsbDevice>();
            UsbDevice usb = null;
            while (deviceIterator.hasNext()) {
                UsbDevice d = deviceIterator.next();
                if (d.getDeviceId() == deviceId) {
                    usb = d;
                    break;
                }
            }
            if (usb == null)
                throw new IUException("设备不存在" + deviceId, 1000);
            return openUsb(usb, deviceCallback);
        } else {
            return openDevice(deviceMap.get(deviceId), deviceCallback);
        }
    }

    public Device openDevice(int vendorId, int productId, final USBCallback deviceCallback) {
        UsbDevice usb = USBUtil.getUsbDevice(context, vendorId, productId);
        if (usb == null)
            throw new IUException("设备不存在(vendorId=" + vendorId + ",productId=" + productId + ")", CODE_DEVICE_NOT_FOUND);
        Device device = openUsb(usb, deviceCallback);
        return device;
    }
//
//    public Device sendData(int vendorId, int productId, final UCallback callback) {
//        Device device = openDevice(vendorId, productId, new UCallback() {
//            @Override
//            void jsonCallback(JSONObject res) {
//                super.jsonCallback(res);
//            }
//        });
//        return null;
//    }
}
