package com.mvcframework.usbcameratest11;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    /**
     * for accessing USB
     */
    private USBMonitor mUSBMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //创建 USBMonitor
        // （1）监听热插拔 mOnDeviceChangedListener
        // （2）监听设备连接过程 mOnDeviceConnectListener
        mUSBMonitor = new USBMonitor(this, mOnDeviceChangedListener, mOnDeviceConnectListener);

        //设置 USB设备过滤器（只选择摄像头）
        final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(this, R.xml.device_filter);
        mUSBMonitor.setDeviceFilter(filters);

    }

    @Override
    protected void onStart() {
        super.onStart();
        //openUsbDevice();
        mUSBMonitor.register();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mUSBMonitor.unregister();
    }

    //region 使用USBMonitor
    /**
     * USB设备变更监听
     * （1）onChanged
     */
    private  final USBMonitor.OnDeviceChangedListener mOnDeviceChangedListener = new USBMonitor.OnDeviceChangedListener() {
        @Override
        public void onChanged(boolean bAttached, int nDevCount) {
            if(bAttached){
                Toast.makeText(MainActivity.this,"when device attached", Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(MainActivity.this,"when device removed", Toast.LENGTH_SHORT).show();
            }

            if(nDevCount > 0){
                //如果设备数量 大于 0 则，初始化设备列表

                //涉及到UI需要操作UI线程
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUsbDeviceList = mUSBMonitor.getDeviceList();
                        mUSBMonitor.requestPermission(mUsbDeviceList.get(0));
                    }
                });
            } else {
                //如果设备数量 等于于 0 则，清空列表
                final View empty = findViewById(android.R.id.empty);
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
            //Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED" + device.getProductName(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPermisson(final UsbDevice usbDevice, boolean hasPermisson) {
            //申请用户权限结果
            //Toast.makeText(getActivity(),"onPermisson:" + hasPermisson, Toast.LENGTH_SHORT).show();
            if(hasPermisson == true){

                UsbDeviceConnection connection = mUSBMonitor.getmUsbManager().openDevice(usbDevice);
                //add your operation code here

                int venderId =  0;
                int productId = 0;
                int fd = 0;

                String name = usbDevice.getDeviceName();
                int busnum = 0;
                int devnum = 0;

                String usbfsName = null;

                if (connection != null) {
                    venderId = usbDevice.getVendorId();
                    productId = usbDevice.getProductId();
                    fd = connection.getFileDescriptor();

                    String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;

                    if (v != null) {
                        busnum = Integer.parseInt(v[v.length - 2]);
                        devnum = Integer.parseInt(v[v.length - 1]);
                    }

                    int desc = connection.getFileDescriptor();
                    byte[] rawDesc = connection.getRawDescriptors();
                    //Log.i(TAG, String.format(Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=", name, desc, busnum, devnum) + rawDesc);

                    Toast.makeText(MainActivity.this,
                            String.format(Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=", name, desc, busnum, devnum) + rawDesc,
                            Toast.LENGTH_LONG).show();


                    if ((v != null) && (v.length > 2)) {
                        final StringBuilder sb = new StringBuilder(v[0]);
                        for (int i = 1; i < v.length - 2; i++)
                            sb.append("/").append(v[i]);
                        usbfsName = sb.toString();
                    }
                    if (TextUtils.isEmpty(usbfsName)) {
                        Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
                        usbfsName = "/dev/bus/usb";
                    }

                    UVCCamera.testlibuvc(venderId, productId,
                            fd,
                            busnum,
                            devnum,
                            usbfsName);
                }
            } else {
                Toast.makeText(MainActivity.this,"用户没有同意给予权限，因此无法使用。", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            //设备连接
            //Toast.makeText(MainActivity.this,"onConnect",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            //设备断开连接
            //if (DEBUG) Log.v(TAG, "onDisconnect:");
        }
        @Override
        public void onDettach(final UsbDevice device) {
            //设备拔出
            //Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
            //设备取消？
            Toast.makeText(MainActivity.this,"onCancel",Toast.LENGTH_SHORT).show();
        }
    };

    List<UsbDevice> mUsbDeviceList;
    //endregion

    //region 获得 usb 权限
    /**
     * 获得 usb 权限
     */
    private void openUsbDevice(){
        //before open usb device
        //should try to get usb permission
        tryGetUsbPermission();
    }
    UsbManager mUsbManager;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private void tryGetUsbPermission(){
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbPermissionActionReceiver, filter);

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        //here do emulation to ask all connected usb device for permission
        for (final UsbDevice usbDevice : mUsbManager.getDeviceList().values()) {
            //add some conditional check if necessary
            //if(isWeCaredUsbDevice(usbDevice)){
            if(mUsbManager.hasPermission(usbDevice)){
                //if has already got permission, just goto connect it
                //that means: user has choose yes for your previously popup window asking for grant perssion for this usb device
                //and also choose option: not ask again
                afterGetUsbPermission(usbDevice);
            }else{
                //this line will let android popup window, ask user whether to allow this app to have permission to operate this usb device
                mUsbManager.requestPermission(usbDevice, mPermissionIntent);
            }
            //}
        }
    }


    private void afterGetUsbPermission(UsbDevice usbDevice){
        //call method to set up device communication
        //Toast.makeText(this, String.valueOf("Got permission for usb device: " + usbDevice), Toast.LENGTH_LONG).show();
        //Toast.makeText(this, String.valueOf("Found USB device: VID=" + usbDevice.getVendorId() + " PID=" + usbDevice.getProductId()), Toast.LENGTH_LONG).show();

        doYourOpenUsbDevice(usbDevice);
    }

    private void doYourOpenUsbDevice(UsbDevice usbDevice){
        //now follow line will NOT show: User has not given permission to device UsbDevice
        UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
        //add your operation code here

        int venderId =  0;
        int productId = 0;
        int fd = 0;

        String name = usbDevice.getDeviceName();
        int busnum = 0;
        int devnum = 0;

        String usbfsName = null;

        if (connection != null) {
            venderId =  usbDevice.getVendorId();
            productId = usbDevice.getProductId();
            fd = connection.getFileDescriptor();

            String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;

            if (v != null) {
                busnum = Integer.parseInt(v[v.length-2]);
                devnum = Integer.parseInt(v[v.length-1]);
            }

            int desc = connection.getFileDescriptor();
            byte[] rawDesc = connection.getRawDescriptors();
            //Log.i(TAG, String.format(Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=", name, desc, busnum, devnum) + rawDesc);

            Toast.makeText(MainActivity.this,
                    String.format(Locale.US, "name=%s,desc=%d,busnum=%d,devnum=%d,rawDesc=", name, desc, busnum, devnum) + rawDesc,
                    Toast.LENGTH_LONG).show();


            if ((v != null) && (v.length > 2)) {
                final StringBuilder sb = new StringBuilder(v[0]);
                for (int i = 1; i < v.length - 2; i++)
                    sb.append("/").append(v[i]);
                usbfsName = sb.toString();
            }
            if (TextUtils.isEmpty(usbfsName)) {
                Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
                usbfsName = "/dev/bus/usb";
            }

            UVCCamera.testlibuvc(venderId, productId,
                    fd,
                    busnum,
                    devnum,
                    usbfsName);


        } else {
            //Log.e(TAG, "could not connect to device " + name);
            Toast.makeText(MainActivity.this,
                    "could not connect to device " + name,
                    Toast.LENGTH_LONG).show();
        }

    }

    private final BroadcastReceiver mUsbPermissionActionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        //user choose YES for your previously popup window asking for grant perssion for this usb device
                        if(null != usbDevice){
                            afterGetUsbPermission(usbDevice);
                        }
                    }
                    else {
                        //user choose NO for your previously popup window asking for grant perssion for this usb device
                        Toast.makeText(context, String.valueOf("Permission denied for device" + usbDevice), Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    };
    //endregion
}
