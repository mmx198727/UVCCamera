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

package com.serenegiant.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * change the view size with keeping the specified aspect ratio.
 * if you set this view with in a FrameLayout and set property "android:layout_gravity="center",
 * you can show this view in the center of screen and keep the aspect ratio of content
 * XXX it is better that can set the aspect ratio as xml property
 */
/**
 * 在保持指定的纵横比的情况下更改视图大小。
 * 如果在FrameLayout中使用设置此视图并设置属性 "android:layout_gravity="center"，
 * 您可以在屏幕中央显示此视图，并保持内容的纵横比
 * XXX最好能将纵横比设置为xml属性
 *
 * 继承接口
 * public interface IAspectRatioView {
 *     void setAspectRatio(double var1);
 *     void setAspectRatio(int var1, int var2);
 *     double getAspectRatio();
 * }
 *
 */
public class AspectRatioTextureView extends TextureView	// API >= 14
	implements IAspectRatioView {

	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "AbstractCameraView";

	/**
	 * 宽高比例
	 */
    private double mRequestedAspect = -1.0;


	private CameraViewInterface.Callback mCallback;

	public AspectRatioTextureView(final Context context) {
		this(context, null, 0);
	}

	public AspectRatioTextureView(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public AspectRatioTextureView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	/**
	 * IAspectRatioView接口——设置比例
	 * @param aspectRatio	宽高比
	 */
	@Override
    public void setAspectRatio(final double aspectRatio) {
        if (aspectRatio < 0) {
            throw new IllegalArgumentException();
        }
        if (mRequestedAspect != aspectRatio) {
            mRequestedAspect = aspectRatio;
            requestLayout();
        }
    }

	/**
	 * IAspectRatioView接口——设置宽高比例
	 * 实际调用 void setAspectRatio(final double aspectRatio)
	 * @param width		宽度
	 * @param height 	高度
	 */
	@Override
    public void setAspectRatio(final int width, final int height) {
		setAspectRatio(width / (double)height);
    }

	/**
	 * IAspectRatioView接口——获取宽高比例
	 * @return
	 */
	@Override
	public double getAspectRatio() {
		return mRequestedAspect;
	}

	/**
	 * onMeasure方法的作用是测量控件的大小，
	 * 当我们创建一个View(执行构造方法)的时候不需要测量控件的大小，
	 * 只有将这个view放入一个容器（父控件）中的时候才需要测量，而这个测量方法就是父控件唤起调用的。
	 * @param widthMeasureSpec
	 * @param heightMeasureSpec
	 */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		if (mRequestedAspect > 0) {
			int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
			int initialHeight = MeasureSpec.getSize(heightMeasureSpec);

			final int horizPadding = getPaddingLeft() + getPaddingRight();
			final int vertPadding = getPaddingTop() + getPaddingBottom();
			initialWidth -= horizPadding;
			initialHeight -= vertPadding;

			final double viewAspectRatio = (double)initialWidth / initialHeight;
			final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

			if (Math.abs(aspectDiff) > 0.01) {
				if (aspectDiff > 0) {
					// width priority decision
					initialHeight = (int) (initialWidth / mRequestedAspect);
				} else {
					// height priority decision
					initialWidth = (int) (initialHeight * mRequestedAspect);
				}
				initialWidth += horizPadding;
				initialHeight += vertPadding;
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
			}
		}

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

}
