package com.usbcamera.android.bean;

import java.io.Serializable;
import java.util.List;

public class FormatBean implements Serializable {

    private int index;
    private int type;
    private String guidFormat;
    private int defaultSize;
    private List<SizeBean> sizeList;

    public int getIndex() { return index; }
    public int getType() { return type; }
    public String getGuidFormat() { return guidFormat; }
    public int getDefaultSize() { return defaultSize; }
    public List<SizeBean> getSizeList() { return sizeList; }

    public void setIndex(int index) { this.index = index; }
    public void setType(int type) { this.type = type; }
    public void setGuidFormat(String guidFormat) { this.guidFormat = guidFormat; }
    public void setDefaultSize(int defaultSize) { this.defaultSize = defaultSize; }
    public void setSizeList(List<SizeBean> sizeList) { this.sizeList = sizeList; }

    @Override
    public String toString() {
        return guidFormat;
    }

    public int getPreviewMode(){
//        public static final int FRAME_FORMAT_YUYV  = 0;
//        public static final int FRAME_FORMAT_MJPEG = 1;
//        public static final int FRAME_FORMAT_H264 = 2;
//        public static final int FRAME_FORMAT_H265 = 3;
//        public static final int FRAME_FORMAT_NV12 = 4;
//        public static final int FRAME_FORMAT_NV21 = 5;

        int type = 1;
        switch (guidFormat){
            case "YUY2":
                type = 0;
                break;
            case "MJPG":
                type = 1;
                break;
            case "H264":
                type = 2;
                break;
            case "H265":
                type = 3;
                break;
            case "NV12":
                type = 4;
                break;
            case "BV21":
                type = 5;
                break;
        }
        return type;


    }
}
