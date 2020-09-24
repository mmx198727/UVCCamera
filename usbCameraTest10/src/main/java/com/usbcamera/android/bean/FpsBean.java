package com.usbcamera.android.bean;

import java.io.Serializable;

public class FpsBean implements Serializable {

    public int fps;

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    @Override
    public String toString() { return String.valueOf(fps); }

}
