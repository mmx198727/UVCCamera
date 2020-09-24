package com.serenegiant.usbcameratest10.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.serenegiant.usbcameratest10.R;

public class CommonSelectAdapter extends ArrayAdapter<String> {
    private int mResourceId;
    private int mSelecedIndex;

    public CommonSelectAdapter(Context context, int resource, String[] objects) {
        super(context, resource, objects);
        mResourceId = resource;
    }

    public void setSelecedIndex(int selecedIndex) {
        mSelecedIndex = selecedIndex;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        ViewHolder viewHolder;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(mResourceId, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.mText = (TextView) view.findViewById(R.id.mTV);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.mText.setText(getItem(position));

        return view;
    }

    private class ViewHolder {
        public TextView mText;
    }

}