package com.usbcamera.android;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import java.util.ArrayList;
import java.util.List;

public class DeviceListAdapter extends BaseAdapter {

    private final LayoutInflater mInflater;
    private final List<UsbDevice> mList;

    public DeviceListAdapter(final Context context, final List<UsbDevice>list) {
        mInflater = LayoutInflater.from(context);
        mList = list != null ? list : new ArrayList<UsbDevice>();
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public UsbDevice getItem(final int position) {
        if ((position >= 0) && (position < mList.size()))
            return mList.get(position);
        else
            return null;
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(com.serenegiant.uvccamera.R.layout.listitem_device, parent, false);
        }
        if (convertView instanceof CheckedTextView) {
            final UsbDevice device = getItem(position);
            ((CheckedTextView)convertView).setText(
                    String.format("%s", device.getProductName()));
        }
        return convertView;
    }
}
