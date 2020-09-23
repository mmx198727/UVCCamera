package com.usbcamera.usbcameratest9;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, CameraDialog.CameraDialogParent{

    private USBMonitor mUSBMonitor;
    private UVCCameraHandler mCameraHandler;
    private CameraViewInterface mUVCCameraView;

    private static final int PREVIEW_WIDTH = 1920;
    private static final int PREVIEW_HEIGHT = 1080;
    private static final int PREVIEW_MODE = UVCCamera.FRAME_FORMAT_MJPEG;

    private Button mStartBtn;
    private Button mStopBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View view = findViewById(R.id.camera_view);
        mUVCCameraView = (CameraViewInterface)view;
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                2, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);


        mStartBtn = (Button) findViewById(R.id.start_btn);
        mStartBtn.setOnClickListener(this);

        mStopBtn = (Button) findViewById(R.id.stop_btn);
        mStopBtn.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        if (mUVCCameraView != null)
            mUVCCameraView.onResume();
    }

    @Override
    protected void onStop() {
        mCameraHandler.close();
        if (mUVCCameraView != null)
            mUVCCameraView.onPause();
//        setCameraButton(false);
//        mCaptureButton.setVisibility(View.INVISIBLE);
        mUSBMonitor.unregister();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        super.onDestroy();
    }


    /**
     * USBMonitor响应
     */
    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            mCameraHandler.open(ctrlBlock);
            startPreview();
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (mCameraHandler != null) {
                mCameraHandler.close();
                //setCameraButton(false);
            }
        }
        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };


    /**
     * 开始预览
     */
    Surface mSurface = null;
    private void startPreview() {
        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        if (mSurface != null) {
            mSurface.release();
        }
        mSurface = new Surface(st);
        mCameraHandler.startPreview(mSurface);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mCaptureButton.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * CameraDialog.CameraDialogParent接口——获取USBMonitor
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    /**
     * CameraDialog.CameraDialogParent接口——对话框点击事件（确定或取消）
     * @param canceled
     */
    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            //setCameraButton(false);
        }
    }

    /**
     * 按钮响应
     * @param v
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_btn:
                if (!mCameraHandler.isOpened()) {
                    CameraDialog.showDialog(MainActivity.this);
                }
                break;
            case R.id.stop_btn:
                if (mCameraHandler.isOpened()){
                    mCameraHandler.close();
//                    mCaptureButton.setVisibility(View.INVISIBLE);
//                    setCameraButton(false);
                }
                break;
            default:
                break;
        }
    }


}
