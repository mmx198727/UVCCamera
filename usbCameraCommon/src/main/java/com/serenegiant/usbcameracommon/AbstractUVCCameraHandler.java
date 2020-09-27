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

package com.serenegiant.usbcameracommon;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.encoder.MediaAudioEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaMuxerWrapper;
import com.serenegiant.encoder.MediaSurfaceEncoder;
import com.serenegiant.encoder.MediaVideoBufferEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
* AbstractUVCCameraHandler 是一个 Handler，在其内部是通过 Android 的消息机制来管理整个相机的生命周期。
* （1）Handler（AbstractUVCCameraHandler） 创建在子线程（CameraThread） 中；
* （2）子线程（CameraThread）对象通过 AbstractUVCCameraHandler 构造函数传入 并在外部 start(开始线程)；
* （3）AbstractUVCCameraHandler实际上不直接使用，一般使用其子类 UVCCameraHandler
* 注：UVCCameraHandler构造函数创建子线程（CameraThread），传入父类构造函数，并开始线程（start）
*
* 1 void handleMessage(Message msg):处理消息
* （1）MSG_OPEN = 0;				打开摄像头
* （2）MSG_CLOSE = 1;			关闭摄像头
* （3）MSG_PREVIEW_START = 2;	开始预览
* （4）MSG_PREVIEW_STOP = 3;		停止预览
* （5）MSG_CAPTURE_STILL = 4;	拍照
* （6）MSG_CAPTURE_START = 5;	开始录制
* （7）MSG_CAPTURE_STOP = 6;		停止录制
* （8）MSG_MEDIA_UPDATE = 7;		刷新媒体库
* （9）MSG_RELEASE = 9;			释放资源
*  注意：（8）（9）实际上Demo未使用
*
* 2 在handleMessage()方法中调用CameraThread处理摄像头
* （1）CameraThread是内部类
* （2）CameraThread调用UVCCamera实现摄像头控制
* （3）CameraThread会触发回调CameraCallback（实际上Demo未涉及）
*
* 3 摄像头操作方法
* 提供方法触发消息（sendMessage)
* （1）open()			打开摄像头
* （2）close()			关闭摄像头
* （3）startPreview()	开始预览
* （4）stopPreview()		停止预览
* （5）captureStill()	拍照
* （6）startRecording()	开始录制
* （7）stopRecording()	停止录制
* （8）updateMedia()		刷新媒体库
* （9）release()			释放资源
* 注意：可以看出与消息对等，实际上是进行了封装，使得消息本身对外透明，用户之间使用方法
*
* 4 摄像头属性设置（如亮度或对比度）
* （1）checkSupportFlag() 询问是否支持属性设置
* （2）getValue()		  获取属性
* （3）setValue()		  设置属性
* （4）resetValue()		  重置属性
*
* 5 属性方法
* （1）getWidth()		获取摄像头宽度
* （2）getHeight()		获取摄像头高度
* （3）isOpened()		摄像头是否开启
* （4）isPreviewing()	摄像头是否开始预览
* （5）isRecording()		摄像头是否开始录制
* （6）isEqual()			是否为同一个设备
* （7）isCameraThread()	是否开启线程
* （8）isReleased()		释放资源
* （9）checkReleased()	检测是否释放资源
*
* 6 摄像头生命周期回调注册方法
* （1）addCallback()		添加生命周期回调注册
* （2）removeCallback()	移除声明周期回调注册
* 摄像头生命周期回调使用方式：
* （1） 继承 CameraCallback接口，处理生命周期；
* （2） 调用 addCallback()方法传入对象；
* （3） 触发过程
* 	AbstractUVCCameraHandler摄像头操作方法 ->
* 	AbstractUVCCameraHandler.SendMessage() ->
* 	AbstractUVCCameraHandler.handleMessage() ->
* 	CameraThread 摄像头操作方法 ->
* 	调用CameraCallback 方法触发回调过程；
*
* 7 参考资料
* （1） Handler相关方法
*  	1） void handleMessage(Message msg):处理消息的方法,通常是用于被重写!
*  	2） sendEmptyMessage(int what):发送空消息
*  	3） sendEmptyMessageDelayed(int what,long delayMillis):指定延时多少毫秒后发送空信息
*  	4） sendMessage(Message msg):立即发送信息
*  	5） sendMessageDelayed(Message msg):指定延时多少毫秒后发送信息
*   6） final boolean hasMessage(int what):检查消息队列中是否包含what属性为指定值的消息 如果是参数为(int what,Object object):除了判断what属性,还需要判断Object属性是否为指定对象的消息
 *
 * （2） Handler写在子线程中
 * 如果是Handler写在了子线程中的话,我们就需要自己创建一个Looper对象了!
 * 创建的流程如下:
 * 	1） 直接调用Looper.prepare()方法即可为当前线程创建Looper对象,而它的构造器会创建配套的MessageQueue;
 *  2） 创建Handler对象,重写handleMessage( )方法就可以处理来自于其他线程的信息了!
 *  3） 调用Looper.loop()方法启动Looper
 *
 */
abstract class AbstractUVCCameraHandler extends Handler {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbsUVCCameraHandler";

	/**
	 * 摄像头生命周期回调
	 * 在CameraThread中触发
	 */
	public interface CameraCallback {
		public void onOpen();					/** 打开摄像头 */
		public void onClose();  				/** 关闭摄像头 */
		public void onStartPreview();   		/** 开始预览 */
		public void onStopPreview();			/** 停止预览 */
		public void onStartRecording();			/** 开始录像 */
		public void onStopRecording();			/** 停止录像 */
		public void onError(final Exception e);	/** 返回错误码 */
	}

	/**
	 * 消息-MSG_OPEN:打开设备
	 */
	private static final int MSG_OPEN = 0;

	/**
	 * 消息-MSG_CLOSE:关闭设备
	 */
	private static final int MSG_CLOSE = 1;

	/**
	 * 消息-MSG_PREVIEW_START:开始预览
	 */
	private static final int MSG_PREVIEW_START = 2;

	/**
	 * 消息-MSG_PREVIEW_STOP:结束预览
	 */
	private static final int MSG_PREVIEW_STOP = 3;

	/**
	 * 消息-MSG_CAPTURE_STILL:拍照
	 */
	private static final int MSG_CAPTURE_STILL = 4;

	/**
	 * 消息-MSG_CAPTURE_START:开始录像
	 */
	private static final int MSG_CAPTURE_START = 5;

	/**
	 * 消息-MSG_CAPTURE_STOP:停止录像
	 */
	private static final int MSG_CAPTURE_STOP = 6;

	/**
	 * 消息-MSG_MEDIA_UPDATE:
	 */
	private static final int MSG_MEDIA_UPDATE = 7;

	/**
	 * 消息-MSG_RELEASE:
	 */
	private static final int MSG_RELEASE = 9;

	/**
	 * CameraThread 弱引用
	 * 业务逻辑关键
	 */
	private final WeakReference<AbstractUVCCameraHandler.CameraThread> mWeakThread;

	/**
	 * 是否已经释放
	 */
	private volatile boolean mReleased;

	/**
	 * 构造函数
	 * @param thread CameraThread
	 * 注意：Thread从构造函数传入，因此 Start() 在外部调用
	 */
	protected AbstractUVCCameraHandler(final CameraThread thread) {
		//创建CameraThread 弱引用
		mWeakThread = new WeakReference<CameraThread>(thread);
	}

	/**
	 * 属性方法-获取宽度
	 * @return
	 */
	public int getWidth() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getWidth() : 0;
	}

	/**
	 * 属性方法-获取高度
	 * @return
	 */
	public int getHeight() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getHeight() : 0;
	}

	/**
	 * 属性方法-是否开启
	 * @return
	 */
	public boolean isOpened() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isCameraOpened();
	}

	/**
	 * 属性方法-是否在预览
	 * @return
	 */
	public boolean isPreviewing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isPreviewing();
	}

	/**
	 * 属性方法-是否自录像
	 * @return
	 */
	public boolean isRecording() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isRecording();
	}

	/**
	 * 属性方法-判断是否同一个设备
	 * @return
	 */
	public boolean isEqual(final UsbDevice device) {
		final CameraThread thread = mWeakThread.get();
		return (thread != null) && thread.isEqual(device);
	}

	/**
	 * 属性方法-是否开启线程
	 * @return
	 */
	protected boolean isCameraThread() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && (thread.getId() == Thread.currentThread().getId());
	}

	/**
	 * 属性方法-是否释放
	 * @return
	 */
	protected boolean isReleased() {
		final CameraThread thread = mWeakThread.get();
		return mReleased || (thread == null);
	}

	/**
	 * 属性方法-检查是否释放
	 * @return
	 */
	protected void checkReleased() {
		if (isReleased()) {
			throw new IllegalStateException("already released");
		}
	}

	/**
	 * 摄像头操作方法-开启摄像头
	 * @param ctrlBlock
	 */
	public void open(final USBMonitor.UsbControlBlock ctrlBlock) {
		checkReleased();
		sendMessage(obtainMessage(MSG_OPEN, ctrlBlock));
	}

	/**
	 * 摄像头操作方法-关闭摄像头
	 */
	public void close() {
		if (DEBUG) Log.v(TAG, "close:");
		if (isOpened()) {
			stopPreview();
			sendEmptyMessage(MSG_CLOSE);
		}
		if (DEBUG) Log.v(TAG, "close:finished");
	}

	/**
	 * 摄像头操作方法-重新设置尺寸
	 * @param width
	 * @param height
	 */
	public void resize(final int width, final int height) {
		checkReleased();
		throw new UnsupportedOperationException("does not support now");
	}

	/**
	 * 摄像头操作方法-开始预览
	 * @param surface
	 */
	protected void startPreview(final Object surface) {
		checkReleased();
		if (!((surface instanceof SurfaceHolder) || (surface instanceof Surface) || (surface instanceof SurfaceTexture))) {
			throw new IllegalArgumentException("surface should be one of SurfaceHolder, Surface or SurfaceTexture");
		}
		sendMessage(obtainMessage(MSG_PREVIEW_START, surface));
	}

	/**
	 * 摄像头操作方法-关闭预览
	 */
	public void stopPreview() {
		if (DEBUG) Log.v(TAG, "stopPreview:");
		removeMessages(MSG_PREVIEW_START);
		stopRecording();
		if (isPreviewing()) {
			final CameraThread thread = mWeakThread.get();
			if (thread == null) return;
			synchronized (thread.mSync) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				if (!isCameraThread()) {
					// wait for actually preview stopped to avoid releasing Surface/SurfaceTexture
					// while preview is still running.
					// therefore this method will take a time to execute
					try {
						thread.mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
			}
		}
		if (DEBUG) Log.v(TAG, "stopPreview:finished");
	}

	/**
	 * 摄像头操作方法-拍照
	 */
	protected void captureStill() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_STILL);
	}

	/**
	 * 摄像头操作方法-拍照
	 * @param path
	 */
	protected void captureStill(final String path) {
		checkReleased();
		sendMessage(obtainMessage(MSG_CAPTURE_STILL, path));
	}

	/**
	 * 摄像头操作方法-开始录像
	 */
	public void startRecording() {
		checkReleased();
		sendEmptyMessage(MSG_CAPTURE_START);
	}

	/**
	 * 摄像头操作方法-停止录像
	 */
	public void stopRecording() {
		sendEmptyMessage(MSG_CAPTURE_STOP);
	}

	/**
	 * 摄像头操作方法-释放资源
	 */
	public void release() {
		mReleased = true;
		close();
		sendEmptyMessage(MSG_RELEASE);
	}

	/**
	 * 摄像头生命周期回调注册方法—注册
	 * @param callback
	 */
	public void addCallback(final CameraCallback callback) {
		checkReleased();
		if (!mReleased && (callback != null)) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.add(callback);
			}
		}
	}

	/**
	 * 摄像头生命周期回调注册方法—注销
	 * @param callback
	 */
	public void removeCallback(final CameraCallback callback) {
		if (callback != null) {
			final CameraThread thread = mWeakThread.get();
			if (thread != null) {
				thread.mCallbacks.remove(callback);
			}
		}
	}

	/**
	 * 摄像头操作方法-更新媒体库
	 * @param path
	 */
	protected void updateMedia(final String path) {
		sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
	}

	/**
	 * 摄像头属性设置—询问是否支持属性设置
	 * @param flag 如 亮度设置， PU_BRIGHTNESS，在UVCCamera.java中定义
	 * @return
	 */
	public boolean checkSupportFlag(final long flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.mUVCCamera != null && thread.mUVCCamera.checkSupportFlag(flag);
	}

	/**
	 * 摄像头属性设置—获取属性
	 * @param flag 如 亮度设置， PU_BRIGHTNESS，在UVCCamera.java中定义
	 * @return
	 */
	public int getValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	/**
	 * 摄像头属性设置—设置属性
	 * @param flag 如 亮度设置， PU_BRIGHTNESS，在UVCCamera.java中定义
	 * @param value 需要设置的值
	 * @return
	 */
	public int setValue(final int flag, final int value) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.setBrightness(value);
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.setContrast(value);
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	/**
	 * 摄像头属性设置—重置属性
	 * @param flag 如 亮度设置， PU_BRIGHTNESS，在UVCCamera.java中定义
	 * @return
	 */
	public int resetValue(final int flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		final UVCCamera camera = thread != null ? thread.mUVCCamera : null;
		if (camera != null) {
			if (flag == UVCCamera.PU_BRIGHTNESS) {
				camera.resetBrightness();
				return camera.getBrightness();
			} else if (flag == UVCCamera.PU_CONTRAST) {
				camera.resetContrast();
				return camera.getContrast();
			}
		}
		throw new IllegalStateException();
	}

	/**
	 * Handler的相关方法
	 * void handleMessage(Message msg):处理消息的方法,通常是用于被重写!
	 * @param msg	消息
	 */
	@Override
	public void handleMessage(final Message msg) {
		final CameraThread thread = mWeakThread.get();
		if (thread == null) return;
		switch (msg.what) {
		case MSG_OPEN:
			Log.d(TAG, "handleMessage: MSG_OPEN");
			thread.handleOpen((USBMonitor.UsbControlBlock)msg.obj);
			break;
		case MSG_CLOSE:
			Log.d(TAG, "handleMessage: MSG_CLOSE");
			thread.handleClose();
			break;
		case MSG_PREVIEW_START:
			Log.d(TAG, "handleMessage: MSG_PREVIEW_START");
			thread.handleStartPreview(msg.obj);
			break;
		case MSG_PREVIEW_STOP:
			Log.d(TAG, "handleMessage: MSG_PREVIEW_STOP");
			thread.handleStopPreview();
			break;
		case MSG_CAPTURE_STILL:
			Log.d(TAG, "handleMessage: MSG_CAPTURE_STILL");
			thread.handleCaptureStill((String)msg.obj);
			break;
		case MSG_CAPTURE_START:
			Log.d(TAG, "handleMessage: MSG_CAPTURE_START");
			thread.handleStartRecording();
			break;
		case MSG_CAPTURE_STOP:
			Log.d(TAG, "handleMessage: MSG_CAPTURE_STOP");
			thread.handleStopRecording();
			break;
		case MSG_MEDIA_UPDATE:
			Log.d(TAG, "handleMessage: MSG_MEDIA_UPDATE");
			thread.handleUpdateMedia((String)msg.obj);
			break;
		case MSG_RELEASE:
			Log.d(TAG, "handleMessage: MSG_RELEASE");
			thread.handleRelease();
			break;
		default:
			throw new RuntimeException("unsupported message:what=" + msg.what);
		}
	}

	/**
	 * 摄像头操作线程
	 *
	 * 1 属性方法
	 * （1）getHandler()				获取绑定的 AbstractUVCCameraHandler 对象
	 * （2）getWidth()				获取摄像头宽度
	 * （3）getHeight()				获取摄像头高度
	 * （4）isCameraOpened()			摄像头是否已开启
	 * （5）isPreviewing()			摄像头是否正在预览
	 * （6）isRecording()			摄像头是否正在录制
	 * （7）isEqual()				是否为同一个设备
	 * 注意：直接在 AbstractUVCCameraHandler中调用
	 *
	 * 2 摄像头控制方法
	 * （1）handleOpen()				打开摄像头
	 * （2）handleClose()			关闭摄像头
	 * （3）handleStartPreview()		开始预览
	 * （4）handleStopPreview()		停止预览
	 * （5）handleCaptureStill()		拍照
	 * （6）handleStartRecording()	开始录制
	 * （7）handleStopRecording()	停止录制
	 * （8）handleUpdateMedia()		刷新媒体库
	 * （9）handleRelease()			释放资源
	 * 注意：
	 * （1）调用UVCCamera实现摄像头控制
	 * （2）在同名方法中会触发CameraCallback，如handleOpen()触发callOnOpen()
	 * （3）在AbstractUVCCameraHandler handleMessage()直接调用，接收到消息
	 *
	 * 3 继承方法
	 * （1）run()，Thread方法重写
	 * （2）finalize()，Object方法重写，GC释放资源时调用
	 *
	 * 4 触发 CameraCallback
	 * （1）callOnOpen()				打开摄像头触发CameraCallback.onOpen()
	 * （2）callOnClose()			关闭摄像头触发CameraCallback.onClose()
	 * （3）callOnStartPreview()		开始预览触发CameraCallback.onStartPreview()
	 * （4）callOnStopPreview()		停止预览触发CameraCallback.onStopPreview()
	 * （5）callOnStartRecording()	开始录像触发CameraCallback.onStartRecording()
	 * （6）callOnStopRecording()	停止录像触发CameraCallback.onStopRecording()
	 * （7）callOnError()			返回错误码触发CameraCallback.onError(final Exception e)
	 *
	 * 5 变量
	 * （1）mSync
	 * （2）mHandlerClass
	 * （3）mWeakParent
	 * （4）mWeakCameraView
	 * （5）mEncoderType
	 * （6）mCallbacks
	 * （7）mWidth, mHeight, mPreviewMode
	 * （8）mBandwidthFactor
	 * （9）mIsPreviewing
	 * （10）mIsRecording
	 * （11）mSoundPool
	 * （12）mSoundId
	 * （13）mHandler
	 * （14）mUVCCamera
	 * （15）mMuxer
	 * （16）mVideoEncoder
	 * （17）mIFrameCallback
	 * （18）mMediaEncoderListener
	 *
	 * 6相关资料
	 * 6.1 Android中Thread的三种使用方式：
	 * （1）继承Thread，重写run()方法。
	 * （2）实现Runnable，重写run()方法来执行任务。
	 * （3）通过Handler启动线程。
	 *
	 * 6.2 MediaScannerConnection
	 * MediaScannerConnection 作用是为应用提供一个媒体扫描服务，当有新创建或者下载的文件时，会从该文件读取元数据并将该文件添加到媒体中去。
	 */
	static final class CameraThread extends Thread {
		private static final String TAG_THREAD = "CameraThread";
		/**
		 * 异步互斥量
		 * 在线程中访问资源
		 */
		private final Object mSync = new Object();

		/**
		 * 记录拥有 CameraThread 对象的 AbstractUVCCameraHandler 对象
		 * 在构造函数赋值
		 * 注意：
		 * Class<? extends T> 这个是定义参数的类型为Class，
		 * 但是这个Class必须是和之前定义的泛型有继承关系的。
		 * ？表示任意类的Class，不过后面跟了extends 表明了条件。
		 */
		private final Class<? extends AbstractUVCCameraHandler> mHandlerClass;

		/**
		 * 记录Activity对象
		 * 在构造函数赋值
		 * 注意：
		 * weak reference 弱引用
		 */
		private final WeakReference<Activity> mWeakParent;

		/**
		 * 记录保持宽高比例窗口接口对象
		 * 在构造函数赋值
		 */
		private final WeakReference<CameraViewInterface> mWeakCameraView;

		/**
		 * Encoder对象创建类型（工厂模式）
		 * 0: use MediaSurfaceEncoder,
		 * 1: use MediaVideoEncoder,
		 * 2: use MediaVideoBufferEncoder
		 */
		private final int mEncoderType;

		/**
		 * CameraCallback集合
		 */
		private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();

		/**
		 * mWidth 视频宽度
		 * mHeight 视频高度
		 * mPreviewMode 视频格式 either UVCCamera.FRAME_FORMAT_YUYV(0) or UVCCamera.FRAME_FORMAT_MJPEG(1)
		 * 在构造函数赋值
		 */
		private int mWidth, mHeight, mPreviewMode, mFpsMin, mFpsMax;

		/**
		 * UVCCamera
		 * 在构造函数赋值
		 */
		private float mBandwidthFactor;

		/**
		 * 是否正在预览
		 */
		private boolean mIsPreviewing;

		/**
		 * 是否正在录像
		 */
		private boolean mIsRecording;

		/**
		 * 拍照音频
		 * shutter sound
		 */
		private SoundPool mSoundPool;
		private int mSoundId;

		/**
		 * 通过反射创建的的对象
		 */
		private AbstractUVCCameraHandler mHandler;

		/**
		 * for accessing UVC camera
		 */
		private UVCCamera mUVCCamera;

		/**
		 * 视频录制
		 * muxer for audio/video recording
		 */
		private MediaMuxerWrapper mMuxer;

		/**
		 * 视频编码器
		 * 视频录制使用
		 */
		private MediaVideoBufferEncoder mVideoEncoder;

		/**
		 * 构造函数
		 * @param clazz Class extends AbstractUVCCameraHandler
		 * @param parent parent Activity
		 * @param cameraView for still capturing
		 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
		 * @param width 视频宽度
		 * @param height 视频高度
		 * @param format either FRAME_FORMAT_YUYV(0) or FRAME_FORMAT_MJPEG(1)
		 * @param bandwidthFactor
		 */
		CameraThread(final Class<? extends AbstractUVCCameraHandler> clazz,
			final Activity parent, final CameraViewInterface cameraView,
			final int encoderType, final int width, final int height, final int format,
			final float bandwidthFactor) {

			super("CameraThread");
			mHandlerClass = clazz;
			mEncoderType = encoderType;
			mWidth = width;
			mHeight = height;
			mFpsMin = 1;
			mFpsMax = 30;
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
			//加载音频
			loadShutterSound(parent);
		}

		CameraThread(final Class<? extends AbstractUVCCameraHandler> clazz,
					 final Activity parent, final CameraViewInterface cameraView,
					 final int encoderType, final int width, final int height, final int format,
					 final float bandwidthFactor, final int fpsMin, final int fpsMax) {

			super("CameraThread");
			mHandlerClass = clazz;
			mEncoderType = encoderType;
			mWidth = width;
			mHeight = height;
			mFpsMin = fpsMin;
			mFpsMax = fpsMax;
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
			//加载音频
			loadShutterSound(parent);
		}

		/**
		 * Object.finalize()方法
		 * finalize()方法是Object类中提供的一个方法，在GC准备释放对象所占用的内存空间之前，它将首先调用finalize()方法
		 * @throws Throwable
		 */
		@Override
		protected void finalize() throws Throwable {
			Log.i(TAG, "CameraThread#finalize");
			super.finalize();
		}

		/**
		 * 属性方法——获取绑定的 AbstractUVCCameraHandler 对象
		 * @return
		 */
		public AbstractUVCCameraHandler getHandler() {
			if (DEBUG) Log.v(TAG_THREAD, "getHandler:");
			synchronized (mSync) {
				if (mHandler == null)
				try {
					mSync.wait();
				} catch (final InterruptedException e) {
				}
			}
			return mHandler;
		}

		/**
		 * 属性方法——获取摄像头宽度
		 * @return
		 */
		public int getWidth() {
			synchronized (mSync) {
				return mWidth;
			}
		}

		/**
		 * 属性方法——获取摄像头高度
		 * @return
		 */
		public int getHeight() {
			synchronized (mSync) {
				return mHeight;
			}
		}

		/**
		 * 属性方法——摄像头是否已开启
		 * @return
		 */
		public boolean isCameraOpened() {
			synchronized (mSync) {
				return mUVCCamera != null;
			}
		}

		/**
		 * 属性方法——摄像头是否正在预览
		 * @return
		 */
		public boolean isPreviewing() {
			synchronized (mSync) {
				return mUVCCamera != null && mIsPreviewing;
			}
		}

		/**
		 * 属性方法——摄像头是否正在录制
		 * @return
		 */
		public boolean isRecording() {
			synchronized (mSync) {
				return (mUVCCamera != null) && (mMuxer != null);
			}
		}

		/**
		 * 属性方法——是否为同一个设备
		 * @param device
		 * @return
		 */
		public boolean isEqual(final UsbDevice device) {
			return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
		}

		/**
		 * 摄像头控制方法——打开摄像头
		 * @param ctrlBlock
		 */
		public void handleOpen(final USBMonitor.UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG_THREAD, "handleOpen:");
			handleClose();
			try {
				final UVCCamera camera = new UVCCamera();
				camera.open(ctrlBlock);
				synchronized (mSync) {
					mUVCCamera = camera;
				}
				callOnOpen();
			} catch (final Exception e) {
				callOnError(e);
			}
			if (DEBUG) Log.i(TAG, "supportedSize:" + (mUVCCamera != null ? mUVCCamera.getSupportedSize() : null));
		}

		/**
		 * 摄像头控制方法——关闭摄像头
		 */
		public void handleClose() {
			if (DEBUG) Log.v(TAG_THREAD, "handleClose:");
			handleStopRecording();
			final UVCCamera camera;
			synchronized (mSync) {
				camera = mUVCCamera;
				mUVCCamera = null;
			}
			if (camera != null) {
				camera.stopPreview();
				camera.destroy();
				callOnClose();
			}
		}

		/**
		 * 摄像头控制方法——开始预览
		 * @param surface
		 */
		public void handleStartPreview(final Object surface) {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
			if ((mUVCCamera == null) || mIsPreviewing) return;
			try {
				mUVCCamera.setPreviewSize(mWidth, mHeight, mFpsMin, mFpsMax, mPreviewMode, mBandwidthFactor);
			} catch (final IllegalArgumentException e) {
				try {
					// fallback to YUV mode
					mUVCCamera.setPreviewSize(mWidth, mHeight, mFpsMin, mFpsMax, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
				} catch (final IllegalArgumentException e1) {
					callOnError(e1);
					return;
				}
			}
			if (surface instanceof SurfaceHolder) {
				mUVCCamera.setPreviewDisplay((SurfaceHolder)surface);
			} if (surface instanceof Surface) {
				mUVCCamera.setPreviewDisplay((Surface)surface);
			} else {
				mUVCCamera.setPreviewTexture((SurfaceTexture)surface);
			}
			mUVCCamera.startPreview();
			mUVCCamera.updateCameraParams();
			synchronized (mSync) {
				mIsPreviewing = true;
			}
			callOnStartPreview();
		}

		/**
		 * 摄像头控制方法——停止预览
		 */
		public void handleStopPreview() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:");
			if (mIsPreviewing) {
				if (mUVCCamera != null) {
					mUVCCamera.stopPreview();
				}
				synchronized (mSync) {
					mIsPreviewing = false;
					mSync.notifyAll();
				}
				callOnStopPreview();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleStopPreview:finished");
		}

		/**
		 * 摄像头控制方法——拍照
		 * @param path
		 */
		public void handleCaptureStill(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleCaptureStill:");
			final Activity parent = mWeakParent.get();
			if (parent == null) return;
			mSoundPool.play(mSoundId, 0.2f, 0.2f, 0, 0, 1.0f);	// play shutter sound
			try {
				final Bitmap bitmap = mWeakCameraView.get().captureStillImage();
				// get buffered output stream for saving a captured still image as a file on external storage.
				// the file name is came from current time.
				// You should use extension name as same as CompressFormat when calling Bitmap#compress.
				final File outputFile = TextUtils.isEmpty(path)
					? MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png")
					: new File(path);
				final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
				try {
					try {
						bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
						os.flush();
						mHandler.sendMessage(mHandler.obtainMessage(MSG_MEDIA_UPDATE, outputFile.getPath()));
					} catch (final IOException e) {
					}
				} finally {
					os.close();
				}
			} catch (final Exception e) {
				callOnError(e);
			}
		}

		/**
		 * 摄像头控制方法——开始录制
		 */
		public void handleStartRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartRecording:");
			try {
				if ((mUVCCamera == null) || (mMuxer != null)) return;
				final MediaMuxerWrapper muxer = new MediaMuxerWrapper(".mp4");	// if you record audio only, ".m4a" is also OK.
				MediaVideoBufferEncoder videoEncoder = null;
				switch (mEncoderType) {
				case 1:	// for video capturing using MediaVideoEncoder
					new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				case 2:	// for video capturing using MediaVideoBufferEncoder
					videoEncoder = new MediaVideoBufferEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				// case 0:	// for video capturing using MediaSurfaceEncoder
				default:
					new MediaSurfaceEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
					break;
				}
				if (true) {
					// for audio capturing
					new MediaAudioEncoder(muxer, mMediaEncoderListener);
				}
				muxer.prepare();
				muxer.startRecording();
				if (videoEncoder != null) {
					mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
				}
				synchronized (mSync) {
					mMuxer = muxer;
					mVideoEncoder = videoEncoder;
				}
				callOnStartRecording();
			} catch (final IOException e) {
				callOnError(e);
				Log.e(TAG, "startCapture:", e);
			}
		}

		/**
		 * 摄像头控制方法——停止录制
		 */
		public void handleStopRecording() {
			if (DEBUG) Log.v(TAG_THREAD, "handleStopRecording:mMuxer=" + mMuxer);
			final MediaMuxerWrapper muxer;
			synchronized (mSync) {
				muxer = mMuxer;
				mMuxer = null;
				mVideoEncoder = null;
				if (mUVCCamera != null) {
					mUVCCamera.stopCapture();
				}
			}
			try {
				mWeakCameraView.get().setVideoEncoder(null);
			} catch (final Exception e) {
				// ignore
			}
			if (muxer != null) {
				muxer.stopRecording();
				mUVCCamera.setFrameCallback(null, 0);
				// you should not wait here
				callOnStopRecording();
			}
		}

		/**
		 * 录制时帧回调处理
		 * UVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21);
		 */
		private final IFrameCallback mIFrameCallback = new IFrameCallback() {
			@Override
			public void onFrame(final ByteBuffer frame) {
				final MediaVideoBufferEncoder videoEncoder;
				synchronized (mSync) {
					videoEncoder = mVideoEncoder;
				}
				if (videoEncoder != null) {
					videoEncoder.frameAvailableSoon();
					videoEncoder.encode(frame);
				}
			}
		};

		/**
		 * 摄像头控制方法——刷新媒体库
		 * @param path
		 */
		public void handleUpdateMedia(final String path) {
			if (DEBUG) Log.v(TAG_THREAD, "handleUpdateMedia:path=" + path);
			final Activity parent = mWeakParent.get();
			final boolean released = (mHandler == null) || mHandler.mReleased;
			if (parent != null && parent.getApplicationContext() != null) {
				try {
					if (DEBUG) Log.i(TAG, "MediaScannerConnection#scanFile");
					MediaScannerConnection.scanFile(parent.getApplicationContext(), new String[]{ path }, null, null);
				} catch (final Exception e) {
					Log.e(TAG, "handleUpdateMedia:", e);
				}
				if (released || parent.isDestroyed())
					handleRelease();
			} else {
				Log.w(TAG, "MainActivity already destroyed");
				// give up to add this movie to MediaStore now.
				// Seeing this movie on Gallery app etc. will take a lot of time.
				handleRelease();
			}
		}

		/**
		 * 摄像头控制方法——释放资源
		 */
		public void handleRelease() {
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:mIsRecording=" + mIsRecording);
			handleClose();
			mCallbacks.clear();
			if (!mIsRecording) {
				mHandler.mReleased = true;
				Looper.myLooper().quit();
			}
			if (DEBUG) Log.v(TAG_THREAD, "handleRelease:finished");
		}

		/**
		 * 视频编码器
		 * new MediaVideoEncoder(muxer, getWidth(), getHeight(), mMediaEncoderListener);
		 */
		private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
			@Override
			public void onPrepared(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
				mIsRecording = true;
				if (encoder instanceof MediaVideoEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaVideoEncoder)encoder);
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
				if (encoder instanceof MediaSurfaceEncoder)
				try {
					mWeakCameraView.get().setVideoEncoder((MediaSurfaceEncoder)encoder);
					mUVCCamera.startCapture(((MediaSurfaceEncoder)encoder).getInputSurface());
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}

			@Override
			public void onStopped(final MediaEncoder encoder) {
				if (DEBUG) Log.v(TAG_THREAD, "onStopped:encoder=" + encoder);
				if ((encoder instanceof MediaVideoEncoder)
					|| (encoder instanceof MediaSurfaceEncoder))
				try {
					mIsRecording = false;
					final Activity parent = mWeakParent.get();
					mWeakCameraView.get().setVideoEncoder(null);
					synchronized (mSync) {
						if (mUVCCamera != null) {
							mUVCCamera.stopCapture();
						}
					}
					final String path = encoder.getOutputPath();
					if (!TextUtils.isEmpty(path)) {
						mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_MEDIA_UPDATE, path), 1000);
					} else {
						final boolean released = (mHandler == null) || mHandler.mReleased;
						if (released || parent == null || parent.isDestroyed()) {
							handleRelease();
						}
					}
				} catch (final Exception e) {
					Log.e(TAG, "onPrepared:", e);
				}
			}
		};

		/**
		 * 加载拍照音效
		 * prepare and load shutter sound for still image capturing
		 */
		@SuppressWarnings("deprecation")
		private void loadShutterSound(final Context context) {
	    	// get system stream type using reflection
	        int streamType;
	        try {
	            final Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");
	            final Field sseField = audioSystemClass.getDeclaredField("STREAM_SYSTEM_ENFORCED");
	            streamType = sseField.getInt(null);
	        } catch (final Exception e) {
	        	streamType = AudioManager.STREAM_SYSTEM;	// set appropriate according to your app policy
	        }
	        if (mSoundPool != null) {
	        	try {
	        		mSoundPool.release();
	        	} catch (final Exception e) {
	        	}
	        	mSoundPool = null;
	        }
	        // load shutter sound from resource
		    mSoundPool = new SoundPool(2, streamType, 0);
		    mSoundId = mSoundPool.load(context, R.raw.camera_click, 1);
		}

		/**
		 * Thread.run()方法
		 * 线程核心逻辑
		 */
		@Override
		public void run() {
			Log.d(TAG, "run: ");
			Looper.prepare();
			AbstractUVCCameraHandler handler = null;
			try {
				final Constructor<? extends AbstractUVCCameraHandler> constructor = mHandlerClass.getDeclaredConstructor(CameraThread.class);
				handler = constructor.newInstance(this);
			} catch (final NoSuchMethodException e) {
				Log.w(TAG, e);
			} catch (final IllegalAccessException e) {
				Log.w(TAG, e);
			} catch (final InstantiationException e) {
				Log.w(TAG, e);
			} catch (final InvocationTargetException e) {
				Log.w(TAG, e);
			}
			if (handler != null) {
				synchronized (mSync) {
					mHandler = handler;
					mSync.notifyAll();
				}
				Log.d(TAG, "run: Looper.loop() ");
				//Looper.loop()会在当前线程执行死循环（没有消息的时候会阻塞），所以正常情况下，后面的代码是执行不了了。
				Looper.loop();
				if (mSoundPool != null) {
					mSoundPool.release();
					mSoundPool = null;
				}
				if (mHandler != null) {
					mHandler.mReleased = true;
				}
			}
			mCallbacks.clear();
			synchronized (mSync) {
				mHandler = null;
				mSync.notifyAll();
			}
			Log.d(TAG, "run: end ");
		}

		/**
		 * 触发 CameraCallback.onOpen()
		 */
		private void callOnOpen() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onOpen();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onClose()
		 */
		private void callOnClose() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onClose();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onStartPreview()
		 */
		private void callOnStartPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onStopPreview()
		 */
		private void callOnStopPreview() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopPreview();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onStartRecording()
		 */
		private void callOnStartRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStartRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onStopRecording()
		 */
		private void callOnStopRecording() {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onStopRecording();
				} catch (final Exception e) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}

		/**
		 * 触发 CameraCallback.onError()
		 * @param e
		 */
		private void callOnError(final Exception e) {
			for (final CameraCallback callback: mCallbacks) {
				try {
					callback.onError(e);
				} catch (final Exception e1) {
					mCallbacks.remove(callback);
					Log.w(TAG, e);
				}
			}
		}
	}
}
