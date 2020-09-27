package com.usbcamera.android;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usbcameratest10.R;
import com.serenegiant.utils.BuildCheck;
import com.serenegiant.utils.HandlerThreadHandler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * USB设备操作
 * （1）获取USB设备列表
 * （2）接收USB插拔信息
 *
 * 使用方法
 * （1） 创建UsbDeviceUtil对象
 * （2） 调用register方法
 * （3） 通过回调方法处理
 * （4） 结束时调用unregister方法
 *
 * 技术背景
 * 使用 android.hardware.usb包中：
 * UsbManager 、 UsbDevice 、 UsbDeviceConnection , UsbEndpoint , UsbInterface UsbRequest , UsbConstants
 * （1） 使用 广播方式监听 设备拔出事件（插入设备没有广播）
 * （2） 使用线程判断设备是否插入
 */
public class UsbDeviceUtil {
    // TODO set false on production
    private static final boolean DEBUG = false;
    private static final String TAG = "UsbDeviceUtil";

    private static final String ACTION_USB_PERMISSION_BASE = "com.serenegiant.USB_PERMISSION.";
    private final String ACTION_USB_PERMISSION = ACTION_USB_PERMISSION_BASE + hashCode();

    /**
     * openしているUsbControlBlock
     */
    private final ConcurrentHashMap<UsbDevice, USBMonitor.UsbControlBlock> mCtrlBlocks = new ConcurrentHashMap<UsbDevice, USBMonitor.UsbControlBlock>();
    private final SparseArray<WeakReference<UsbDevice>> mHasPermissions = new SparseArray<WeakReference<UsbDevice>>();

    private final WeakReference<Context> mWeakContext;
    private final UsbManager mUsbManager;

    private PendingIntent mPermissionIntent = null;
    private List<DeviceFilter> mDeviceFilters = new ArrayList<DeviceFilter>();
    private final Handler mAsyncHandler;
    private volatile boolean destroyed;

    /** number of connected & detected devices */
    private volatile int mDeviceCounts = 0;

    /** USB 设备变更回调通知 */
    private OnDeviceChangedListener mOnDeviceChangedListener;

    /**
     * USB 设备变更时的回调通知
     */
    public interface OnDeviceChangedListener {
        /**
         * 插拔设备
         * @param bAttached 是否插入设备
         *                  - ture  插入设备
         *                  - false 拔出设备
         * @param nDevCount 当前设备数量
         */
        public void onChanged(boolean bAttached, int nDevCount);

    }

    public interface OnDeviceStateChangedListener {

    }

    /**
     * 构造函数
     * @param context
     */
    public UsbDeviceUtil(final Context context) {
        mWeakContext = new WeakReference<Context>(context);
        mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        mAsyncHandler = HandlerThreadHandler.createHandler(TAG);
        destroyed = false;

        final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(context, R.xml.device_filter);
        setDeviceFilter(filters);
    }


    /**
     * 注册USB设备监听
     * register BroadcastReceiver to monitor USB events
     * （1）注册BroadcastReceiver以监视USB事件拔出设备
     * （2）通过Runnable以监视USB事件插入设备
     * @param listener USB设备变更监听器
     * @throws IllegalStateException
     */
    public synchronized void register(OnDeviceChangedListener listener) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");

        mOnDeviceChangedListener = listener;

        if (mPermissionIntent == null) {
            if (DEBUG) Log.i(TAG, "register:");
            final Context context = mWeakContext.get();

            // 注册BroadcastReceiver以监视USB事件拔出设备
            if (context != null) {
                mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
                final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                // ACTION_USB_DEVICE_ATTACHED never comes on some devices so it should not be added here
                // 虽然
                filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
                context.registerReceiver(mUsbReceiver, filter);
            }

            // 通过Runnable以监视USB事件插入设备
            // start connection check
            mDeviceCounts = 0;
            mAsyncHandler.postDelayed(mDeviceCheckRunnable, 1000);
        }
    }

    /**
     * 取消 USB 设备监听
     * unregister BroadcastReceiver
     * （1）注册BroadcastReceiver以监视USB事件拔出设备
     * （2）通过Runnable以监视USB事件插入设备
     * @throws IllegalStateException
     */
    public synchronized void unregister() throws IllegalStateException {
        // 通过Runnable以监视USB事件插入设备
        mOnDeviceChangedListener = null;
        mDeviceCounts = 0;
        if (!destroyed) {
            mAsyncHandler.removeCallbacks(mDeviceCheckRunnable);
        }

        // 注册BroadcastReceiver以监视USB事件拔出设备
        if (mPermissionIntent != null) {
			if (DEBUG) Log.i(TAG, "unregister:");
            final Context context = mWeakContext.get();
            try {
                if (context != null) {
                    context.unregisterReceiver(mUsbReceiver);
                }
            } catch (final Exception e) {
                Log.w(TAG, e);
            }
            mPermissionIntent = null;
        }
    }

    /**
     * 判断是否已经注册
     * @return
     */
    public synchronized boolean isRegistered() {
        return !destroyed && (mPermissionIntent != null);
    }

    /**
     * 释放资源
     * Release all related resources,
     * never reuse again
     */
    public void destroy() {
        if (DEBUG) Log.i(TAG, "destroy:");
        unregister();
        if (!destroyed) {
            destroyed = true;

            try {
                mAsyncHandler.getLooper().quit();
            } catch (final Exception e) {
                Log.e(TAG, "destroy:", e);
            }
        }
    }

    /**
     * 是否已经释放资源
     * @apiNote 释放资源后无法使用其他功能，需要重新初始化
     * @return
     */
    public synchronized boolean isDestroyed() {
        return destroyed;
    }

    /**
     * 获取设备名称列表
     * @return
     */
    public List<String> getDevStrList(){
        if (DEBUG) Log.i(TAG, "destroy:");
        List<String> listResult = new ArrayList<>();

        List<UsbDevice> listUsbDev = getDeviceList();
        for (UsbDevice usbDev:listUsbDev) {
            listResult.add(usbDev.getProductName());
        }

        return listResult;
    }

    /**
     * BroadcastReceiver for USB permission
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (destroyed) return;
            final String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                // when received the result of requesting USB permission
                //Toast.makeText(context,"when received the result of requesting USB permission", Toast.LENGTH_SHORT).show();
                synchronized (UsbDeviceUtil.this) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // get permission, call onConnect
                            //processConnect(device);
                        }
                    } else {
                        // failed to get permission
                        //processCancel(device);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Toast.makeText(context,"when device add", Toast.LENGTH_SHORT).show();;
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                //这里不会被调用
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // when device removed
                //Toast.makeText(context,"when device removed", Toast.LENGTH_SHORT).show();
                //设备变更通知——拔出设备
                if(mOnDeviceChangedListener != null) { mOnDeviceChangedListener.onChanged(false,getDeviceCount()); }
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    USBMonitor.UsbControlBlock ctrlBlock = mCtrlBlocks.remove(device);
                    if (ctrlBlock != null) {
                        // cleanup
                        ctrlBlock.close();
                    }
                    mDeviceCounts = getDeviceCount();
                    //processDettach(device);
                }
            }
        }
    };

    /**
     * periodically check connected devices and if it changed, call onAttach
     */
    private final Runnable mDeviceCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            final List<UsbDevice> devices = getDeviceList();
            final int n = devices.size();
            final int hasPermissionCounts;
            final int m;
            synchronized (mHasPermissions) {
                hasPermissionCounts = mHasPermissions.size();
                mHasPermissions.clear();
                for (final UsbDevice device: devices) {
                    hasPermission(device);
                }
                m = mHasPermissions.size();
            }
            if ((n > mDeviceCounts) || (m > hasPermissionCounts)) {
                mDeviceCounts = n;
                //final Context context = mWeakContext.get();
                //Toast.makeText(context,"when device attached", Toast.LENGTH_SHORT).show();
                //设备变更通知——插入设备
                if(mOnDeviceChangedListener != null) { mOnDeviceChangedListener.onChanged(true, n); }

                //if (mOnDeviceConnectListener != null) {
                    for (int i = 0; i < n; i++) {
                        final UsbDevice device = devices.get(i);
                        mAsyncHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //mOnDeviceConnectListener.onAttach(device);


                            }
                        });
                    }
                //}
            }
            mAsyncHandler.postDelayed(this, 500);	// confirm every 2 seconds
        }
    };

    /**
     * 获取设备列表
     * return device list, return empty list if no device matched
     * @return
     * @throws IllegalStateException
     */
    public List<UsbDevice> getDeviceList() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList(mDeviceFilters);
    }

    /**
     * 过滤只保留视频设备
     * return device list, return empty list if no device matched
     * @param filters
     * @return
     * @throws IllegalStateException
     */
    private List<UsbDevice> getDeviceList(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        final List<UsbDevice> result = new ArrayList<UsbDevice>();
        if (deviceList != null) {
            if ((filters == null) || filters.isEmpty()) {
                result.addAll(deviceList.values());
            } else {
                for (final UsbDevice device: deviceList.values() ) {
                    for (final DeviceFilter filter: filters) {
                        if ((filter != null) && filter.matches(device)) {
                            // when filter matches
                            if (!filter.isExclude) {
                                result.add(device);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取设备列表数量
     * return the number of connected USB devices that matched device filter
     * @return
     * @throws IllegalStateException
     */
    private int getDeviceCount() throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return getDeviceList().size();
    }

    /**
     * デバイスキーを整数として取得
     * getDeviceKeyNameで得られる文字列のhasCodeを取得
     * useNewAPI=falseで同種の製品だと同じデバイスキーになるので注意
     * @param device
     * @param useNewAPI
     * @return
     */
    private static final int getDeviceKey(final UsbDevice device, final boolean useNewAPI) {
        return device != null ? getDeviceKeyName(device, null, useNewAPI).hashCode() : 0;
    }

    /**
     * USB機器毎の設定保存用にデバイスキー名を生成する。この機器名をHashMapのキーにする
     * UsbDeviceがopenしている時のみ有効
     * ベンダーID, プロダクトID, デバイスクラス, デバイスサブクラス, デバイスプロトコルから生成
     * serialがnullや空文字でなければserialを含めたデバイスキー名を生成する
     * useNewAPI=trueでAPIレベルを満たしていればマニュファクチャ名, バージョン, コンフィギュレーションカウントも使う
     * @param device nullなら空文字列を返す
     * @param serial	UsbDeviceConnection#getSerialで取得したシリアル番号を渡す, nullでuseNewAPI=trueでAPI>=21なら内部で取得
     * @param useNewAPI API>=21またはAPI>=23のみで使用可能なメソッドも使用する(ただし機器によってはnullが返ってくるので有効かどうかは機器による)
     * @return
     */
    @SuppressLint("NewApi")
    private static final String getDeviceKeyName(final UsbDevice device, final String serial, final boolean useNewAPI) {
        if (device == null) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(device.getVendorId());			sb.append("#");	// API >= 12
        sb.append(device.getProductId());			sb.append("#");	// API >= 12
        sb.append(device.getDeviceClass());			sb.append("#");	// API >= 12
        sb.append(device.getDeviceSubclass());		sb.append("#");	// API >= 12
        sb.append(device.getDeviceProtocol());						// API >= 12
        if (!TextUtils.isEmpty(serial)) {
            sb.append("#");	sb.append(serial);
        }
        if (useNewAPI && BuildCheck.isAndroid5()) {
            sb.append("#");
            if (TextUtils.isEmpty(serial)) {
                sb.append(device.getSerialNumber());	sb.append("#");	// API >= 21
            }
            sb.append(device.getManufacturerName());	sb.append("#");	// API >= 21
            sb.append(device.getConfigurationCount());	sb.append("#");	// API >= 21
            if (BuildCheck.isMarshmallow()) {
                sb.append(device.getVersion());			sb.append("#");	// API >= 23
            }
        }
//		if (DEBUG) Log.v(TAG, "getDeviceKeyName:" + sb.toString());
        return sb.toString();
    }

    /**
     * return whether the specific Usb device has permission
     * @param device
     * @return true: 指定したUsbDeviceにパーミッションがある
     * @throws IllegalStateException
     */
    private final boolean hasPermission(final UsbDevice device) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        return updatePermission(device, device != null && mUsbManager.hasPermission(device));
    }

    /**
     * 内部で保持しているパーミッション状態を更新
     * @param device
     * @param hasPermission
     * @return hasPermission
     */
    private boolean updatePermission(final UsbDevice device, final boolean hasPermission) {
        final int deviceKey = getDeviceKey(device, true);
        synchronized (mHasPermissions) {
            if (hasPermission) {
                if (mHasPermissions.get(deviceKey) == null) {
                    mHasPermissions.put(deviceKey, new WeakReference<UsbDevice>(device));
                }
            } else {
                mHasPermissions.remove(deviceKey);
            }
        }
        return hasPermission;
    }

    /**
     * 获取 USB 设备权限
     * request permission to access to USB device
     * @param device
     * @return true if fail to request permission
     */
    public synchronized boolean requestPermission(final UsbDevice device) {
		if (DEBUG) Log.v(TAG, "requestPermission:device=" + device);
        boolean result = false;
        if (isRegistered()) {
            if (device != null) {
                if (mUsbManager.hasPermission(device)) {
                    // call onConnect if app already has permission
                    //初始化（2）
                    //processConnect(device);
                } else {
                    try {
                        // パーミッションがなければ要求する
                        mUsbManager.requestPermission(device, mPermissionIntent);
                    } catch (final Exception e) {
                        // Android5.1.xのGALAXY系でandroid.permission.sec.MDM_APP_MGMTという意味不明の例外生成するみたい
                        Log.w(TAG, e);
                        //processCancel(device);
                        result = true;
                    }
                }
            } else {
                //processCancel(device);
                result = true;
            }
        } else {
            //processCancel(device);
            result = true;
        }
        return result;
    }

    /**
     * set device filters
     * @param filters
     * @throws IllegalStateException
     */
    private void setDeviceFilter(final List<DeviceFilter> filters) throws IllegalStateException {
        if (destroyed) throw new IllegalStateException("already destroyed");
        mDeviceFilters.clear();
        mDeviceFilters.addAll(filters);
    }
}
