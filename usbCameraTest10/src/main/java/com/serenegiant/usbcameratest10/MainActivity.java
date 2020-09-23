/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest10;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;
import com.usbcamera.android.DeviceListAdapter;
import com.usbcamera.android.UsbCamera;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent{
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = UVCCamera.FRAME_FORMAT_MJPEG;

	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera releated methods sequentially on private thread
	 */
	private UVCCameraHandler mCameraHandler;
	/**
	 * for camera preview display
	 */
	private CameraViewInterface mUVCCameraView;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;


	private UsbCamera mUsbCamera;
	private Button mStartBtn;
	private Button mStopBtn;
	private Spinner mDevSpinner;
	private DeviceListAdapter mDeviceListAdapter;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);
		mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
		mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		final View view = findViewById(R.id.camera_view);
		view.setOnLongClickListener(mOnLongClickListener);
		mUVCCameraView = (CameraViewInterface)view;
		mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

		mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
		mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
			2, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

		//开始按钮
		mStartBtn = (Button) findViewById(R.id.start_btn);
		mStartBtn.setOnClickListener(mOnClickListener);

		//停止按钮
		mStopBtn = (Button) findViewById(R.id.stop_btn);
		mStopBtn.setOnClickListener(mOnClickListener);

		//设备列表
		mDevSpinner = (Spinner)findViewById(R.id.dev_spinner);
		final View empty = findViewById(android.R.id.empty);
		mDevSpinner.setEmptyView(empty);

		//初始化USBCamera
		mUsbCamera = new UsbCamera();
		mUsbCamera.initSDK(this, mUSBMonitor, mCameraHandler);


	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		mUSBMonitor.register();
		if (mUVCCameraView != null)
			mUVCCameraView.onResume();

		//初始化设备列表
		mUsbCamera.initDevs();

		//绑定设备列表
		mDeviceListAdapter = mUsbCamera.getmDeviceListAdapter();
		mDevSpinner.setAdapter(mDeviceListAdapter);

		//设备列表选择
		mDevSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				// TODO
				//Toast.makeText(MainActivity.this,"pos:" + pos + " id:" + id, Toast.LENGTH_SHORT).show();
				int index = (int)id;

				//动态申请设备权限
				mUsbCamera.requestPermission(index);

				//获取设备视频格式和分辨率
				String strSupportSize = mUsbCamera.getSupportSize(index);
				if (DEBUG) Log.i(TAG, "supportedSize:" + strSupportSize );

				Toast.makeText(MainActivity.this, "supportedSize:" + strSupportSize, Toast.LENGTH_LONG).show();



			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// TODO
			}
		});
	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		queueEvent(new Runnable() {
			@Override
			public void run() {
				mCameraHandler.close();
			}
		}, 0);
		if (mUVCCameraView != null)
			mUVCCameraView.onPause();
		setCameraButton(false);
		mCaptureButton.setVisibility(View.INVISIBLE);
		mUSBMonitor.unregister();
		super.onStop();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		if (mCameraHandler != null) {
			mCameraHandler.release();
			mCameraHandler = null;
		}
		if (mUSBMonitor != null) {
			mUSBMonitor.destroy();
			mUSBMonitor = null;
		}
		mUVCCameraView = null;
		mCameraButton = null;
		mCaptureButton = null;
		super.onDestroy();
	}

	protected void checkPermissionResult(final int requestCode, final String permission, final boolean result) {
		super.checkPermissionResult(requestCode, permission, result);
		if (!result && (permission != null)) {
			setCameraButton(false);
		}
	}

	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.start_btn:
				//开始预览
				final Object item = mDevSpinner.getSelectedItem();
				if (item instanceof UsbDevice) {
					//打开设备
					mUSBMonitor.usbCamera_processConnect((UsbDevice)item);
				}
				break;
			case R.id.stop_btn:
				//结束预览
				mCameraHandler.close();
				break;
			case R.id.capture_button:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						if (!mCameraHandler.isRecording()) {
							mCaptureButton.setColorFilter(0xffff0000);	// turn red
							mCameraHandler.startRecording();
						} else {
							mCaptureButton.setColorFilter(0);	// return to default color
							mCameraHandler.stopRecording();
						}
					}
				}
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
			switch (compoundButton.getId()) {
			case R.id.camera_button:
				if (isChecked && !mCameraHandler.isOpened()) {
					CameraDialog.showDialog(MainActivity.this);
				} else {
					mCameraHandler.close();
					mCaptureButton.setVisibility(View.INVISIBLE);
					setCameraButton(false);
				}
				break;
			}
		}
	};

	/**
	 * capture still image when you long click on preview image(not on buttons)
	 */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
			case R.id.camera_view:
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage()) {
						mCameraHandler.captureStill();
					}
					return true;
				}
			}
			return false;
		}
	};

	private void setCameraButton(final boolean isOn) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mCameraButton != null) {
					try {
						mCameraButton.setOnCheckedChangeListener(null);
						mCameraButton.setChecked(isOn);
					} finally {
						mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				if (!isOn && (mCaptureButton != null)) {
					mCaptureButton.setVisibility(View.INVISIBLE);
				}
			}
		}, 0);
	}

	private Surface mSurface;
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
				mCaptureButton.setVisibility(View.VISIBLE);
			}
		});
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			mCameraHandler.open(ctrlBlock);
			startPreview();
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			if (mCameraHandler != null) {
				mCameraHandler.close();
				setCameraButton(false);
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
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			setCameraButton(false);
		}
	}




}
