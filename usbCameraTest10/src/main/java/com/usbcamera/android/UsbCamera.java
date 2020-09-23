package com.usbcamera.android;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class UsbCamera {

    private static final String TAG = "UsbCamera";

    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int PREVIEW_MODE = UVCCamera.FRAME_FORMAT_MJPEG;

    private Context mContext;
    private USBMonitor mUSBMonitor;
    private UVCCameraHandler mCameraHandler;
    private CameraViewInterface mUVCCameraView;

    private DeviceListAdapter mDeviceListAdapter;
    private List<String> mFormatList;
    private List<String> mResolutionList;

    public DeviceListAdapter getmDeviceListAdapter() { return mDeviceListAdapter; }


    /**
     * 初始化 SDK
     * @param usbMonitor
     * @param uvcCameraHandler
     */
    public void initSDK(Context context, USBMonitor usbMonitor, UVCCameraHandler uvcCameraHandler){
        mContext = context;
        mUSBMonitor = usbMonitor;
        mCameraHandler = uvcCameraHandler;
    }

    /**
     * 初始化设备列表
     */
    public void initDevs(){
        //设备列表
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(mContext, com.serenegiant.uvccamera.R.xml.device_filter);
        mDeviceListAdapter = new DeviceListAdapter(mContext, mUSBMonitor.getDeviceList(filter.get(0)));
    }

    /**
     * 申请设备权限
     * @param devId
     */
    public void requestPermission(int devId){
        final Object item = mDeviceListAdapter.getItem(devId);
        if (item instanceof UsbDevice) {
            //动态申请权限
            UsbDevice device = (UsbDevice)item;
            mUSBMonitor.usbCamera_requestPermission((UsbDevice)item);
        }
    }


    /**
     * 获取设备支持分辨率和视频格式
     * 测试实验
     * @param devId
     * @return
     */
    public String getSupportSize(int devId){
        final Object item = mDeviceListAdapter.getItem(devId);
        if (item instanceof UsbDevice) {
            String strSupportSize = "";
            try {
                UsbDevice device = (UsbDevice)item;
                USBMonitor.UsbControlBlock ctrlBlock = new USBMonitor.UsbControlBlock(mUSBMonitor, device);

                final UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                strSupportSize = camera.getSupportedSize();
                camera.close();

                parseJSONWithJSONObject(strSupportSize);

            } catch (final Exception e) {
            } finally {
                return strSupportSize;
            }
        }

        return "";
    }

    private void parseJSONWithJSONObject(String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            JSONArray jsonArray = json.getJSONArray("formats");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String index = jsonObject.getString("index");
                String type = jsonObject.getString("type");
                String guidFormat = jsonObject.getString("guidFormat");
                String sizeV = jsonObject.getString("size");

                Log.d(TAG, "index is " + index);
                Log.d(TAG, "type is " + type);
                Log.d(TAG, "guidFormat is " + guidFormat);
                Log.d(TAG, "size is " + sizeV);

                JSONArray sizeArray = jsonObject.getJSONArray("size");
                for (int m = 0; m < sizeArray.length(); m++) {
                    JSONObject value = sizeArray.getJSONObject(m);
                    //获取到title值
                    String size = value.getString("size");
                    Log.d(TAG, "size is " + size);
                    // String title = value.optString("title");

                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
