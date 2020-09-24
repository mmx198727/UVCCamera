package com.serenegiant.usbcameratest10.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import com.usbcamera.android.bean.FormatBean;
import com.usbcamera.android.bean.FormatsBean;

public class FormatListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final FormatsBean mformats;

    public FormatListAdapter(final Context context, final FormatsBean formats) {
        mInflater = LayoutInflater.from(context);
        mformats = formats;
    }

    @Override
    public int getCount() { return mformats.getFormats().size(); }

    @Override
    public FormatBean getItem(int position) {
        if ((position >= 0) && (position < mformats.getFormats().size()))
            return mformats.getFormats().get(position);
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
            final FormatBean format = getItem(position);
            ((CheckedTextView)convertView).setText(
                    String.format("%s", format.getGuidFormat()));
        }
        return convertView;
    }
}
