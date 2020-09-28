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

import android.animation.Animator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.usbcameratest10.adapter.CommonSelectAdapter;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.CameraViewInterface;
import com.usbcamera.android.bean.FormatBean;
import com.usbcamera.android.bean.FormatsBean;
import com.usbcamera.android.bean.FpsBean;
import com.usbcamera.android.bean.SizeBean;

import java.util.List;

public final class MainActivity extends BaseActivity{
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
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;

	/**
	 * 摄像头视频格式对象
	 * （1）视频格式列表
	 * （2）分辨率列表（包含在视频格式中）
	 * （3）帧率列表（包含在分辨率中）
	 * 选中设备后会刷新
	 * Todo
	 * 考虑移动到libuvccamera
	 */
	private FormatsBean mFormatsBean;

	private Button mStartBtn;
	private Button mStopBtn;

	private Spinner mDevSpinner;
	private CommonSelectAdapter mDeviceListAdapter;


	private Spinner mFormatSpinner;
	private CommonSelectAdapter mFormatListAdapter;

	private Spinner mSizeSpinner;
	private CommonSelectAdapter mSizeListAdapter;

	private Spinner mFpsSpinner;
	private CommonSelectAdapter mFpsListAdapter;

	private View mBrightnessButton, mContrastButton;
	private View mResetButton;
	private View mToolsLayout, mValueLayout;
	private SeekBar mSettingSeekbar;
	protected static final int SETTINGS_HIDE_DELAY_MS = 2500;



	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);

		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		final View view = findViewById(R.id.camera_view);
		view.setOnLongClickListener(mOnLongClickListener);

		mUVCCameraView = (CameraViewInterface)view;
		mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);

		//mUsbDeviceUtil = new UsbDeviceUtil(this);

		//创建 USBMonitor
		// （1）监听热插拔 mOnDeviceChangedListener
		// （2）监听设备连接过程 mOnDeviceConnectListener
		mUSBMonitor = new USBMonitor(this, mOnDeviceChangedListener, mOnDeviceConnectListener);

		//设置 USB设备过滤器（只选择摄像头）
		final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
		mUSBMonitor.setDeviceFilter(filters);

		//创建 CameraHandler
		mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
			2, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

		//开始按钮
		mStartBtn = (Button) findViewById(R.id.start_btn);
		mStartBtn.setOnClickListener(mOnClickListener);

		//停止按钮
		mStopBtn = (Button) findViewById(R.id.stop_btn);
		mStopBtn.setOnClickListener(mOnClickListener);

		final View empty = findViewById(android.R.id.empty);
		//设备列表
		mDevSpinner = (Spinner)findViewById(R.id.dev_spinner);
		mDevSpinner.setEmptyView(empty);

		//视频格式列表
		mFormatSpinner = (Spinner)findViewById(R.id.format_spinner);
		mFormatSpinner.setEmptyView(empty);

		//分辨率列表
		mSizeSpinner = (Spinner)findViewById(R.id.size_spinner);
		mSizeSpinner.setEmptyView(empty);

		//帧率
		mFpsSpinner = (Spinner)findViewById(R.id.fps_spinner);
		mFpsSpinner.setEmptyView(empty);

		mBrightnessButton = findViewById(R.id.brightness_button);
		mBrightnessButton.setOnClickListener(mOnClickListener);
		mContrastButton = findViewById(R.id.contrast_button);
		mContrastButton.setOnClickListener(mOnClickListener);
		mResetButton = findViewById(R.id.reset_button);
		mResetButton.setOnClickListener(mOnClickListener);
		mSettingSeekbar = (SeekBar)findViewById(R.id.setting_seekbar);
		mSettingSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

		mToolsLayout = findViewById(R.id.tools_layout);
		mToolsLayout.setVisibility(View.INVISIBLE);
		mValueLayout = findViewById(R.id.value_layout);
		mValueLayout.setVisibility(View.INVISIBLE);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		mUSBMonitor.register();

		if (mUVCCameraView != null)
			mUVCCameraView.onResume();
	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");

		mUSBMonitor.unregister();

		queueEvent(new Runnable() {
			@Override
			public void run() {
				mCameraHandler.close();
			}
		}, 0);

		if (mUVCCameraView != null)
			mUVCCameraView.onPause();

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

		mCaptureButton = null;
		super.onDestroy();
	}

	/**
	 * 获取全局Activity对象
	 * @return
	 */
	public Context getActivity(){return this;}

	/**
	 * 按钮点击响应
	 * （1）Start：开始预览
	 * （2）Stop：结束预览
	 * （3）Capture：录像/停止录像
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.start_btn: {
				//开始预览
				int devId = (int) mDevSpinner.getSelectedItemId();
				int formatId = (int) mFormatSpinner.getSelectedItemId();
				int sizeId = (int) mSizeSpinner.getSelectedItemId();
				int fpsId = (int) mFpsSpinner.getSelectedItemId();

				FormatBean formatBean = (FormatBean) mFormatsBean.getFormats().get(formatId);
				SizeBean sizeBean = (SizeBean) mFormatsBean.getFormats().get(formatId).getSizeList().get(sizeId);
				FpsBean fpsBean = (FpsBean) mFormatsBean.getFormats().get(formatId).getSizeList().get(sizeId).getFpsList().get(fpsId);

				int width = sizeBean.getWidth();
				int height = sizeBean.getHeight();
				int previewMode = formatBean.getPreviewMode();
				int fps = fpsBean.getFps();

				mUVCCameraView.setAspectRatio((float) width / (float) height);
				mCameraHandler = UVCCameraHandler.createHandler(MainActivity.this, mUVCCameraView,
						2, width, height, previewMode, fps, fps);

				//打开设备
				mUSBMonitor.processConnect(mUSBMonitor.getUsbDevice(devId));
				break;
			}
			case R.id.stop_btn: {
				//结束预览
				mCameraHandler.close();
				stopPreview();
				break;
			}
			case R.id.capture_button: {
				if (mCameraHandler.isOpened()) {
					if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
						if (!mCameraHandler.isRecording()) {
							mCaptureButton.setColorFilter(0xffff0000);    // turn red
							mCameraHandler.startRecording();
						} else {
							mCaptureButton.setColorFilter(0);    // return to default color
							mCameraHandler.stopRecording();
						}
					}
				}
				break;
			}
			case R.id.brightness_button:
				showSettings(UVCCamera.PU_BRIGHTNESS);
				break;
			case R.id.contrast_button:
				showSettings(UVCCamera.PU_CONTRAST);
				break;
			case R.id.reset_button:
				resetSettings();
				break;
		  }
		}
	};

	/**
	 * 长按屏幕进行拍照
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

	//region 亮度对比度
	private boolean isActive() {
		return mCameraHandler != null && mCameraHandler.isOpened();
	}

	private boolean checkSupportFlag(final int flag) {
		return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
	}

	private int getValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
	}

	private int setValue(final int flag, final int value) {
		return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
	}

	private int resetValue(final int flag) {
		return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
	}

	private void updateItems() {
		runOnUiThread(mUpdateItemsOnUITask, 100);
	}

	private final Runnable mUpdateItemsOnUITask = new Runnable() {
		@Override
		public void run() {
			if (isFinishing()) return;
			final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
			mToolsLayout.setVisibility(visible_active);
			mBrightnessButton.setVisibility(
					checkSupportFlag(UVCCamera.PU_BRIGHTNESS)
							? visible_active : View.INVISIBLE);
			mContrastButton.setVisibility(
					checkSupportFlag(UVCCamera.PU_CONTRAST)
							? visible_active : View.INVISIBLE);
		}
	};

	private int mSettingMode = -1;
	/**
	 * 設定画面を表示
	 * @param mode
	 */
	private final void showSettings(final int mode) {
		if (DEBUG) Log.v(TAG, String.format("showSettings:%08x", mode));
		hideSetting(false);
		if (isActive()) {
			switch (mode) {
				case UVCCamera.PU_BRIGHTNESS:
				case UVCCamera.PU_CONTRAST:
					mSettingMode = mode;
					mSettingSeekbar.setProgress(getValue(mode));
					ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);
					break;
			}
		}
	}

	private void resetSettings() {
		if (isActive()) {
			switch (mSettingMode) {
				case UVCCamera.PU_BRIGHTNESS:
				case UVCCamera.PU_CONTRAST:
					mSettingSeekbar.setProgress(resetValue(mSettingMode));
					break;
			}
		}
		mSettingMode = -1;
		ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
	}

	/**
	 * 設定画面を非表示にする
	 * @param fadeOut trueならばフェードアウトさせる, falseなら即座に非表示にする
	 */
	protected final void hideSetting(final boolean fadeOut) {
		removeFromUiThread(mSettingHideTask);
		if (fadeOut) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
				}
			}, 0);
		} else {
			try {
				mValueLayout.setVisibility(View.GONE);
			} catch (final Exception e) {
				// ignore
			}
			mSettingMode = -1;
		}
	}

	protected final Runnable mSettingHideTask = new Runnable() {
		@Override
		public void run() {
			hideSetting(true);
		}
	};

	/**
	 * 設定値変更用のシークバーのコールバックリスナー
	 */
	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			// 設定が変更された時はシークバーの非表示までの時間を延長する
			if (fromUser) {
				runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {
			// シークバーにタッチして値を変更した時はonProgressChangedへ
			// 行かないみたいなのでここでも非表示までの時間を延長する
			runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
			if (isActive() && checkSupportFlag(mSettingMode)) {
				switch (mSettingMode) {
					case UVCCamera.PU_BRIGHTNESS:
					case UVCCamera.PU_CONTRAST:
						setValue(mSettingMode, seekBar.getProgress());
						break;
				}
			}	// if (active)
		}
	};

	private final ViewAnimationHelper.ViewAnimationListener
			mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
		@Override
		public void onAnimationStart(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}

		@Override
		public void onAnimationEnd(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
			final int id = target.getId();
			switch (animationType) {
				case ViewAnimationHelper.ANIMATION_FADE_IN:
				case ViewAnimationHelper.ANIMATION_FADE_OUT:
				{
					final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
					if (id == R.id.value_layout) {
						if (fadeIn) {
							runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
						} else {
							mValueLayout.setVisibility(View.GONE);
							mSettingMode = -1;
						}
					} else if (!fadeIn) {
//					target.setVisibility(View.GONE);
					}
					break;
				}
			}
		}

		@Override
		public void onAnimationCancel(@NonNull final Animator animator, @NonNull final View target, final int animationType) {
//			if (DEBUG) Log.v(TAG, "onAnimationStart:");
		}
	};

	//endregion 亮度对比度



	/**
	 * 预览对象 Surface
	 */
	private Surface mSurface;

	/**
	 * 开始预览
	 */
	private void startPreview() {
		final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
		if (mSurface != null) {
			mSurface.release();
		}
		mSurface = new Surface(st);
		mCameraHandler.startPreview(mSurface);

		//显示预览后可以使用的按钮
		setPreviewButton(true);
	}

	/**
	 * 停止预览
	 */
	private void stopPreview() {
		//隐藏预览后可以使用的按钮
		setPreviewButton(false);
	}


	/**
	 * USB设备变更监听
	 * （1）onChanged
	 */
	private  final USBMonitor.OnDeviceChangedListener mOnDeviceChangedListener = new USBMonitor.OnDeviceChangedListener() {
		@Override
		public void onChanged(boolean bAttached, int nDevCount) {
			if(bAttached){
				Toast.makeText(getActivity(),"when device attached", Toast.LENGTH_SHORT).show();
			}else{
				Toast.makeText(getActivity(),"when device removed", Toast.LENGTH_SHORT).show();
			}

			if(nDevCount > 0){
				//如果设备数量 大于 0 则，初始化设备列表

				//如果有设备在使用，则先关闭设备
				if (mCameraHandler != null) {
					mCameraHandler.release();
					mCameraHandler = null;
				}

				mCaptureButton.setVisibility(View.INVISIBLE);

				//涉及到UI需要操作UI线程
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						//初始化设备列表
						initDevList();
					}
				});
			} else {
				//如果设备数量 等于于 0 则，清空列表
				final View empty = findViewById(android.R.id.empty);
				//设备列表
				mDevSpinner.setEmptyView(empty);
				//视频格式列表
				mFormatSpinner.setEmptyView(empty);
				//分辨率列表
				mSizeSpinner.setEmptyView(empty);
				//帧率
				mFpsSpinner.setEmptyView(empty);
			}
		}
	};

	/**
	 * USB设备连接状态变更监听
	 * （1）onAttach
	 * （2）onPermisson
	 * （3）onConnect
	 * （4）onDisconnect
	 * （5）onDettach
	 * （6）onCancel
	 */
	private final USBMonitor.OnDeviceConnectListener2 mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener2() {
		@Override
		public void onAttach(final UsbDevice device) {
			//设备插入
			//Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onPermisson(final UsbDevice device, boolean hasPermisson) {
			//申请用户权限结果
			//Toast.makeText(getActivity(),"onPermisson:" + hasPermisson, Toast.LENGTH_SHORT).show();
			if(hasPermisson == true){

				USBMonitor.UsbControlBlock ctrlBlock = new USBMonitor.UsbControlBlock(mUSBMonitor, device);

                final UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                String jsonStr = camera.getSupportedSize();
                camera.close();

                mFormatsBean = FormatsBean.convertFromJsonStr(jsonStr);
				initFormatSpinner();

			} else {
				Toast.makeText(getActivity(),"用户没有同意给予权限，因此无法使用。", Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			//设备连接
			if (DEBUG) Log.v(TAG, "onConnect:");
			//Toast.makeText(getActivity(),"onConnect",Toast.LENGTH_SHORT).show();
			mCameraHandler.open(ctrlBlock);
			startPreview();
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			//设备断开连接

			if (DEBUG) Log.v(TAG, "onDisconnect:");
			if (mCameraHandler != null) {
				mCameraHandler.close();
			}
			stopPreview();
		}
		@Override
		public void onDettach(final UsbDevice device) {
			//设备拔出

			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			//设备取消？
			Toast.makeText(getActivity(),"onCancel",Toast.LENGTH_SHORT).show();
		}
	};

	/**
	 * 初始化设备列表
	 */
	private void initDevList(){

		//List<String> devList = mUsbDeviceUtil.getDevStrList();
		List<String> devList = mUSBMonitor.getDevStrList();

		mDeviceListAdapter = new CommonSelectAdapter(getActivity(), R.layout.select_item, devList.toArray(new String[devList.size()]));
		mDevSpinner.setAdapter(mDeviceListAdapter);

		//设备列表选择响应
		mDevSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				int index = (int)id;

				//动态申请设备权限
				List<UsbDevice> list = mUSBMonitor.getDeviceList();
				mUSBMonitor.requestPermission(list.get(index));

//				List<UsbDevice> list = mUsbDeviceUtil.getDeviceList();
//				mUsbDeviceUtil.requestPermission(list.get(index));

				//读取视频格式
				//mUsbCamera.getSupportSize(index);

				//初始化 视频格式列表
				//（1）初始化视频格式列表
				//（2）通过指定视频格式选中项初始化分别率列表
				//（3）通过指定分辨率列表选中项初始化帧率列表
				//initFormatSpinner();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	/**
	 * 初始化 视频格式列表
	 * （1）初始化视频格式列表
	 * （2）通过指定视频格式选中项初始化分别率列表
	 * （3）通过指定分辨率列表选中项初始化帧率列表
	 */
	private void initFormatSpinner(){
		//初始化视频格式列表
		List<String> formatList = mFormatsBean.getFormatStrList();
		mFormatListAdapter = new CommonSelectAdapter(getActivity(), R.layout.select_item, formatList.toArray(new String[formatList.size()]));
		mFormatSpinner.setAdapter(mFormatListAdapter);

		//视频格式列表选择响应
		mFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				//初始化分辨率类别
				initSizeSpinner();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) { }
		});

		//默认选择MJPG
		int index = 0;
		for(int n = 0 ; n < formatList.size(); n ++) {
			String format = formatList.get(n);
			if(format.toUpperCase().equals("MJPG")){
				index = n;
				break;
			}
		}
		mFormatSpinner.setSelection(index);
	}

	/**
	 * 初始化 分辨率列表
	 * （1）初始化分别率列表
	 * （2）通过指定分辨率列表选中项初始化帧率列表
	 */
	private void initSizeSpinner(){
		//获取视频格式编号
		int formatId = (int)mFormatSpinner.getSelectedItemId();

		//初始化分辨率列表
		List<String> sizeList = mFormatsBean.getSizeStrList(formatId);
		mSizeListAdapter = new CommonSelectAdapter(getActivity(), R.layout.select_item, sizeList.toArray(new String[sizeList.size()]));
		mSizeSpinner.setAdapter(mSizeListAdapter);

		//分辨率列表选择响应
		mSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				//初始化Fps
				initFpsSpinner();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) { }
		});

		//默认选择最大分辨率
		int sizeIndex = 0;
		int size = 0;
		for(int n = 0 ; n < sizeList.size(); n ++) {
			String sizeStr = sizeList.get(n);
			String[] sizeArr = sizeStr.split("x");
			if(sizeArr.length == 2){
				int width = Integer.parseInt(sizeArr[0]);
				int height = Integer.parseInt(sizeArr[1]);
				int sizeTmp = width * height;
				if(sizeTmp > size){
					size = sizeTmp;
					sizeIndex = n;
				}
			}
		}
		mSizeSpinner.setSelection(sizeIndex);
	}

	/**
	 * 初始化 帧率列表
	 */
	private void initFpsSpinner(){
		int formatId = (int)mFormatSpinner.getSelectedItemId();

		int sizeId = (int)mSizeSpinner.getSelectedItemId();
		List<String> fpsList = mFormatsBean.getFpsStrList(formatId,sizeId);
		mFpsListAdapter = new CommonSelectAdapter(getActivity(), R.layout.select_item, fpsList.toArray(new String[fpsList.size()]));
		mFpsSpinner.setAdapter(mFpsListAdapter);
	}

	/**
	 * 有的按钮只有在开启预览后可以使用
	 * @param isOn
	 */
	private void setPreviewButton(final boolean isOn) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!isOn) {
					mCaptureButton.setVisibility(View.INVISIBLE);

				} else {
					mCaptureButton.setVisibility(View.VISIBLE);
				}
				updateItems();
			}
		}, 0);
	}




}
