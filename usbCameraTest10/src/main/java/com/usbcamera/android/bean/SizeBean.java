package com.usbcamera.android.bean;

import java.io.Serializable;
import java.util.List;


public class SizeBean implements Serializable {

    private int width;
    private int height;
    private List<FpsBean> fpsList;

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public List<FpsBean> getFpsList() { return fpsList; }

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setFpsList(List<FpsBean> fpsList) { this.fpsList = fpsList; }

    @Override
    public String toString() {
        return String.valueOf(getWidth()) + "x" + String.valueOf(getHeight());
    }
}
