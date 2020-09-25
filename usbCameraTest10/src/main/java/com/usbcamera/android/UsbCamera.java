package com.usbcamera.android;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;
import com.usbcamera.android.bean.FormatsBean;

import java.util.List;

public class UsbCamera {

    private static final String TAG = "UsbCamera";

    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int PREVIEW_MODE = UVCCamera.FRAME_FORMAT_MJPEG;

    private Context mContext;
    private UsbDeviceUtil mUsbDeviceUtil;

    private List<UsbDevice> mListUsbDev;

    private UVCCameraHandler mCameraHandler;
    private CameraViewInterface mUVCCameraView;

    public FormatsBean getmFormatsBean() { return mFormatsBean; }

    private FormatsBean mFormatsBean;

    /**
     * 初始化 SDK
     * @param usbDeviceUtil
     * @param uvcCameraHandler
     */
    public void initSDK(Context context, UsbDeviceUtil usbDeviceUtil, UVCCameraHandler uvcCameraHandler){
        mContext = context;
        mUsbDeviceUtil = usbDeviceUtil;
        mCameraHandler = uvcCameraHandler;
    }

    /**
     * 申请设备权限
     * @param devId
     */
    public void requestPermission(int devId){
        List<UsbDevice> listUsbDev = mUsbDeviceUtil.getDeviceList();
        final Object item = listUsbDev.get(devId);
        if (item instanceof UsbDevice) {
            //动态申请权限
            UsbDevice device = (UsbDevice)item;
            mUsbDeviceUtil.requestPermission((UsbDevice)item);
        }
    }


    /**
     * 获取设备支持分辨率和视频格式
     * 测试实验
     * @param devId
     * @return
     */
    public FormatsBean getSupportSize(int devId){
        List<UsbDevice> listUsbDev = mUsbDeviceUtil.getDeviceList();
        final Object item = listUsbDev.get(devId);
        if (item instanceof UsbDevice) {

            try {
                UsbDevice device = (UsbDevice)item;
                //USBMonitor.UsbControlBlock ctrlBlock = new USBMonitor.UsbControlBlock(mUSBMonitor, device);

//                final UVCCamera camera = new UVCCamera();
//                camera.open(ctrlBlock);
//                String jsonStr = camera.getSupportedSize();
//                camera.close();
//                mFormatsBean = FormatsBean.convertFromJsonStr(jsonStr);
                return mFormatsBean;
            } catch (final Exception e) {
                return null;
            }
        }
        return null;
    }



}
