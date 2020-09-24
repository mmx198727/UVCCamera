package com.serenegiant.usbcameratest10.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import com.usbcamera.android.bean.FormatsBean;
import com.usbcamera.android.bean.SizeBean;

import java.util.List;

public class SizeListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final FormatsBean mformats;
    private final int mFormatIndex;
    private final List<SizeBean> mSizeList;

    public SizeListAdapter(final Context context, final FormatsBean formats, final int formatIndex) {
        mInflater = LayoutInflater.from(context);
        mformats = formats;
        mFormatIndex = formatIndex;
        mSizeList = mformats.getFormats().get(mFormatIndex).getSizeList();
    }


    @Override
    public int getCount() { return mSizeList.size(); }

    @Override
    public SizeBean getItem(int position) {
        if ((position >= 0) && (position < mSizeList.size()))
            return mSizeList.get(position);
        else
            return null;
    }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(com.serenegiant.uvccamera.R.layout.listitem_device, parent, false);
        }
        if (convertView instanceof CheckedTextView) {
            final SizeBean size = getItem(position);
            ((CheckedTextView)convertView).setText(
                    String.format("%dx%d", size.getWidth(),size.getHeight()));
        }
        return convertView;
    }
}
