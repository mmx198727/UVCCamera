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
* AbstractUVCCameraHandler 是一个 Handler，在其内部是通过Android的消息机制来管理整个相机的生命周期。
* 1 void handleMessage(Message msg):处理消息
*  （1）MSG_OPEN = 0;
*  （2）MSG_CLOSE = 1;
*  （3）MSG_PREVIEW_START = 2;
*  （4）MSG_PREVIEW_STOP = 3;
*  （5）MSG_CAPTURE_STILL = 4;
*  （6）MSG_CAPTURE_START = 5;
*  （7）MSG_CAPTURE_STOP = 6;
*  （8）MSG_MEDIA_UPDATE = 7;
*  （9）MSG_RELEASE = 9;
*   注意：（8）（9）实际上Demo未使用
*
* 2 在handleMessage()方法中调用CameraThread处理摄像头
*  （1）CameraThread是内部类
*  （2）CameraThread调用UVCCamera实现摄像头控制
*  （3）CameraThread会触发回调CameraCallback（实际上Demo未涉及）
*
* 3 摄像头操作方法
*  提供方法触发消息（sendMessage)
*  （1）open()
*  （2）close()
*  （3）startPreview()
*  （4）stopPreview()
*  （5）captureStill()
*  （6）startRecording()
*  （7）stopRecording()
*  （8）updateMedia()
*  （9）release()
*  注意：可以看出与消息对等，实际上是进行了封装，使得消息本身对外透明，用户之间使用方法
*
* 4 状态方法
*  （1）getWidth()
*  （2）getHeight()
*  （3）isOpened()
*  （4）isPreviewing()
*  （5）isRecording()
*  （6）isEqual()
*  （7）isCameraThread()
*  （8）isReleased()
*  （9）checkReleased()
*
* 5 摄像头生命周期回调注册方法
*  （1）addCallback()
*  （2）removeCallback()
*
* 参考资料——Handler的相关方法
*  （1） void handleMessage(Message msg):处理消息的方法,通常是用于被重写!
*  （2） sendEmptyMessage(int what):发送空消息
*  （3） sendEmptyMessageDelayed(int what,long delayMillis):指定延时多少毫秒后发送空信息
*  （4） sendMessage(Message msg):立即发送信息
*  （5） sendMessageDelayed(Message msg):指定延时多少毫秒后发送信息
*  （6） final boolean hasMessage(int what):检查消息队列中是否包含what属性为指定值的消息 如果是参数为(int what,Object object):除了判断what属性,还需要判断Object属性是否为指定对象的消息
*/
abstract class AbstractUVCCameraHandler extends Handler {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbsUVCCameraHandler";

	/**
	 * 摄像头生命周期回调
	 * 在CameraThread中触发
	 */
	public interface CameraCallback {
		public void onOpen();
		public void onClose();
		public void onStartPreview();
		public void onStopPreview();
		public void onStartRecording();
		public void onStopRecording();
		public void onError(final Exception e);
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
	 *
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
	 * 状态方法-获取宽度
	 * @return
	 */
	public int getWidth() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getWidth() : 0;
	}

	/**
	 * 状态方法-获取高度
	 * @return
	 */
	public int getHeight() {
		final CameraThread thread = mWeakThread.get();
		return thread != null ? thread.getHeight() : 0;
	}

	/**
	 * 状态方法-是否开启
	 * @return
	 */
	public boolean isOpened() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isCameraOpened();
	}

	/**
	 * 状态方法-是否在预览
	 * @return
	 */
	public boolean isPreviewing() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isPreviewing();
	}

	/**
	 * 状态方法-是否自录像
	 * @return
	 */
	public boolean isRecording() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.isRecording();
	}

	/**
	 * 状态方法-判断是否同一个设备
	 * @return
	 */
	public boolean isEqual(final UsbDevice device) {
		final CameraThread thread = mWeakThread.get();
		return (thread != null) && thread.isEqual(device);
	}

	/**
	 * 状态方法-是否开启线程
	 * @return
	 */
	protected boolean isCameraThread() {
		final CameraThread thread = mWeakThread.get();
		return thread != null && (thread.getId() == Thread.currentThread().getId());
	}

	/**
	 * 状态方法-是否释放
	 * @return
	 */
	protected boolean isReleased() {
		final CameraThread thread = mWeakThread.get();
		return mReleased || (thread == null);
	}

	/**
	 * 状态方法-检查是否释放
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
	 * 添加 CameraCallback
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
	 * 移除 CameraCallback
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

	protected void updateMedia(final String path) {
		sendMessage(obtainMessage(MSG_MEDIA_UPDATE, path));
	}

	public boolean checkSupportFlag(final long flag) {
		checkReleased();
		final CameraThread thread = mWeakThread.get();
		return thread != null && thread.mUVCCamera != null && thread.mUVCCamera.checkSupportFlag(flag);
	}

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
	 * 3 CameraCallback 触发
	 * （1）callOnOpen
	 * （2）callOnClose
	 * （3）callOnStartPreview
	 * （4）callOnStopPreview
	 * （5）callOnStartRecording
	 * （6）callOnStopRecording
	 * （7）callOnError
	 *
	 * 4 Android中Thread的三种使用方式：
	 * （1）继承Thread，重写run()方法。
	 * （2）实现Runnable，重写run()方法来执行任务。
	 * （3）通过Handler启动线程。
	 */
	static final class CameraThread extends Thread {
		private static final String TAG_THREAD = "CameraThread";
		/**
		 * 互斥量
		 * 注意：在外部调用（AbstractUVCCameraHandler）
		 */
		private final Object mSync = new Object();

		/**
		 * 记录拥有 CameraThread 对象的 AbstractUVCCameraHandler 对象
		 * 构造函数赋值
		 * 注意：
		 * Class<? extends T> 这个是定义参数的类型为Class，
		 * 但是这个Class必须是和之前定义的泛型有继承关系的。
		 * ？表示任意类的Class，不过后面跟了extends 表明了条件。
		 */
		private final Class<? extends AbstractUVCCameraHandler> mHandlerClass;

		/**
		 * 记录Activity对象
		 * 构造函数赋值
		 *
		 */
		private final WeakReference<Activity> mWeakParent;

		private final WeakReference<CameraViewInterface> mWeakCameraView;

		private final int mEncoderType;

		/**
		 * CameraCallback集合
		 */
		private final Set<CameraCallback> mCallbacks = new CopyOnWriteArraySet<CameraCallback>();

		private int mWidth, mHeight, mPreviewMode;
		private float mBandwidthFactor;
		private boolean mIsPreviewing;
		private boolean mIsRecording;

		/**
		 * shutter sound
		 */
		private SoundPool mSoundPool;
		private int mSoundId;
		private AbstractUVCCameraHandler mHandler;
		/**
		 * for accessing UVC camera
		 */
		private UVCCamera mUVCCamera;
		/**
		 * muxer for audio/video recording
		 */
		private MediaMuxerWrapper mMuxer;
		private MediaVideoBufferEncoder mVideoEncoder;

		/**
		 *
		 * @param clazz Class extends AbstractUVCCameraHandler
		 * @param parent parent Activity
		 * @param cameraView for still capturing
		 * @param encoderType 0: use MediaSurfaceEncoder, 1: use MediaVideoEncoder, 2: use MediaVideoBufferEncoder
		 * @param width
		 * @param height
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
			mPreviewMode = format;
			mBandwidthFactor = bandwidthFactor;
			mWeakParent = new WeakReference<Activity>(parent);
			mWeakCameraView = new WeakReference<CameraViewInterface>(cameraView);
			loadShutterSound(parent);
		}

		@Override
		protected void finalize() throws Throwable {
			Log.i(TAG, "CameraThread#finalize");
			super.finalize();
		}

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

		public int getWidth() {
			synchronized (mSync) {
				return mWidth;
			}
		}

		public int getHeight() {
			synchronized (mSync) {
				return mHeight;
			}
		}

		public boolean isCameraOpened() {
			synchronized (mSync) {
				return mUVCCamera != null;
			}
		}

		public boolean isPreviewing() {
			synchronized (mSync) {
				return mUVCCamera != null && mIsPreviewing;
			}
		}

		public boolean isRecording() {
			synchronized (mSync) {
				return (mUVCCamera != null) && (mMuxer != null);
			}
		}

		public boolean isEqual(final UsbDevice device) {
			return (mUVCCamera != null) && (mUVCCamera.getDevice() != null) && mUVCCamera.getDevice().equals(device);
		}

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

		public void handleStartPreview(final Object surface) {
			if (DEBUG) Log.v(TAG_THREAD, "handleStartPreview:");
			if ((mUVCCamera == null) || mIsPreviewing) return;
			try {
				mUVCCamera.setPreviewSize(mWidth, mHeight, 1, 31, mPreviewMode, mBandwidthFactor);
			} catch (final IllegalArgumentException e) {
				try {
					// fallback to YUV mode
					mUVCCamera.setPreviewSize(mWidth, mHeight, 1, 31, UVCCamera.DEFAULT_PREVIEW_MODE, mBandwidthFactor);
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
		 * 录像时帧回调处理
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
