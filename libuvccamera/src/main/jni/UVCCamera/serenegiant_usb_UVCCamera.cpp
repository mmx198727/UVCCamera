/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: serenegiant_usb_UVCCamera.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#if 0	// デバッグ情報を出さない時
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
		#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
	#undef NDEBUG
#endif

#include <jni.h>
#include <android/native_window_jni.h>

#include "libUVCCamera.h"
#include "UVCCamera.h"

/**
 * set the value into the long field
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 * @param field_name
 * @params val
 */
static jlong setField_long(JNIEnv *env, jobject java_obj, const char *field_name, jlong val) {
#if LOCAL_DEBUG
	LOGV("setField_long:");
#endif

	jclass clazz = env->GetObjectClass(java_obj);
	jfieldID field = env->GetFieldID(clazz, field_name, "J");
	if (LIKELY(field))
		env->SetLongField(java_obj, field, val);
	else {
		LOGE("__setField_long:field '%s' not found", field_name);
	}
#ifdef ANDROID_NDK
	env->DeleteLocalRef(clazz);
#endif
	return val;
}

/**
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 */
static jlong __setField_long(JNIEnv *env, jobject java_obj, jclass clazz, const char *field_name, jlong val) {
#if LOCAL_DEBUG
	LOGV("__setField_long:");
#endif

	jfieldID field = env->GetFieldID(clazz, field_name, "J");
	if (LIKELY(field))
		env->SetLongField(java_obj, field, val);
	else {
		LOGE("__setField_long:field '%s' not found", field_name);
	}
	return val;
}

/**
 * @param env: this param should not be null
 * @param bullet_obj: this param should not be null
 */
jint __setField_int(JNIEnv *env, jobject java_obj, jclass clazz, const char *field_name, jint val) {
	LOGV("__setField_int:");

	jfieldID id = env->GetFieldID(clazz, field_name, "I");
	if (LIKELY(id))
		env->SetIntField(java_obj, id, val);
	else {
		LOGE("__setField_int:field '%s' not found", field_name);
		env->ExceptionClear();	// clear java.lang.NoSuchFieldError exception
	}
	return val;
}

/**
 * set the value into int field
 * @param env: this param should not be null
 * @param java_obj: this param should not be null
 * @param field_name
 * @params val
 */
jint setField_int(JNIEnv *env, jobject java_obj, const char *field_name, jint val) {
	LOGV("setField_int:");

	jclass clazz = env->GetObjectClass(java_obj);
	__setField_int(env, java_obj, clazz, field_name, val);
#ifdef ANDROID_NDK
	env->DeleteLocalRef(clazz);
#endif
	return val;
}

/* This callback function runs once per frame. Use it to perform any
 * quick processing you need, or have it put the frame into your application's
 * input queue. If this function takes too long, you'll start losing frames. */
void cb(uvc_frame_t *frame, void *ptr) {
	uvc_frame_t *bgr;
	uvc_error_t ret;

	LOGD("uvc_callback begin");

	/* We'll convert the image from YUV/JPEG to BGR, so allocate space */
	bgr = uvc_allocate_frame(frame->width * frame->height * 3);
	if (!bgr) {
		printf("unable to allocate bgr frame!");
		return;
	}

	/* Do the BGR conversion */
	ret = uvc_any2bgr(frame, bgr);
	if (ret) {
		uvc_perror(ret, "uvc_any2bgr");
		uvc_free_frame(bgr);
		return;
	}

	/* Call a user function:
     *
     * my_type *my_obj = (*my_type) ptr;
     * my_user_function(ptr, bgr);
     * my_other_function(ptr, bgr->data, bgr->width, bgr->height);
     */

	/* Call a C++ method:
     *
     * my_type *my_obj = (*my_type) ptr;
     * my_obj->my_func(bgr);
     */

	/* Use opencv.highgui to display the image:
     *
     * cvImg = cvCreateImageHeader(
     *     cvSize(bgr->width, bgr->height),
     *     IPL_DEPTH_8U,
     *     3);
     *
     * cvSetData(cvImg, bgr->data, bgr->width * 3);
     *
     * cvNamedWindow("Test", CV_WINDOW_AUTOSIZE);
     * cvShowImage("Test", cvImg);
     * cvWaitKey(10);
     *
     * cvReleaseImageHeader(&cvImg);
     */

    LOGD("uvc_callback end");

	uvc_free_frame(bgr);
}

// native方面用于测试libuvc
static void nativeTest(JNIEnv *env, jobject thiz,
                       jint vid, jint pid, jint fd,
                       jint busNum, jint devAddr, jstring usbfs_str) {
	ENTER();

	uvc_context_t *ctx;
	uvc_device_t *dev;
	uvc_device_handle_t *devh;
	uvc_stream_ctrl_t ctrl;
	uvc_error_t res;

	/* Initialize a UVC service context. Libuvc will set up its own libusb
     * context. Replace NULL with a libusb_context pointer to run libuvc
     * from an existing libusb context. */
	//res = uvc_init(&ctx, NULL);

	const char *c_usbfs = env->GetStringUTFChars(usbfs_str, JNI_FALSE);
	char* mUsbFs = 0;
	if (mUsbFs)
		free(mUsbFs);
	mUsbFs = strdup(c_usbfs);

    LOGD("uvc_init2");
	res = uvc_init2(&ctx, NULL, mUsbFs);

	if (res < 0) {
		LOGE("uvc_init");
		uvc_perror(res, "uvc_init");
		return;
	}

	puts("UVC initialized");
	LOGD("UVC initialized");

	/* Locates the first attached UVC device, stores in dev */
//	res = uvc_find_device(
//			ctx, &dev,
//			0, 0, NULL); /* filter devices: vendor_id, product_id, "serial_num" */

    LOGD("uvc_get_device_with_fd");
	res = uvc_get_device_with_fd(ctx, &dev, vid, pid, NULL, fd, busNum, devAddr);
    LOGD("uvc_get_device_with_fd finished");

	if (res < 0) {
		uvc_perror(res, "uvc_find_device"); /* no devices found */
		LOGD("no devices found");
	} else {
		puts("Device found");
		LOGD("Device found");

		/* Try to open the device: requires exclusive access */
		LOGD("uvc_open");
		res = uvc_open(dev, &devh);

		if (res < 0) {
			uvc_perror(res, "uvc_open"); /* unable to open device */
			LOGD("unable to open device");
		} else {
			puts("Device opened");
			LOGD("Device opened");

			/* Print out a message containing all the information that libuvc
             * knows about the device */
			uvc_print_diag(devh, stderr);

			/* Try to negotiate a 640x480 30 fps YUYV stream profile */
            LOGD("uvc_get_stream_ctrl_format_size");
			res = uvc_get_stream_ctrl_format_size(
					devh, &ctrl, /* result stored in ctrl */
					UVC_FRAME_FORMAT_MJPEG, /* YUV 422, aka YUV 4:2:2. try _COMPRESSED */
					640, 480, 30 /* width, height, fps */
			);


			/* Print out the result */
			uvc_print_stream_ctrl(&ctrl, stderr);

			if (res < 0) {
				uvc_perror(res, "get_mode"); /* device doesn't provide a matching stream */
                LOGD("device doesn't provide a matching stream");
			} else {
				/* Start the video stream in isochronous mode. The library will
                 * call user function cb: cb(frame, (void*) 12345)
                 */
				//res = uvc_start_iso_streaming(devh, &ctrl, cb, 12345);
				res = uvc_start_streaming_bandwidth(
						devh, &ctrl, cb, 0, 0, 0);

				if (res < 0) {
					uvc_perror(res, "start_streaming"); /* unable to start stream */
				} else {
					puts("Streaming...");

					uvc_set_ae_mode(devh, 1); /* e.g., turn on auto exposure */

					sleep(10); /* stream for 10 seconds */

					/* End the stream. Blocks until last callback is serviced */
					uvc_stop_streaming(devh);
					puts("Done streaming.");
				}
			}

			/* Release our handle on the device */
			uvc_close(devh);
			puts("Device closed");
		}

		/* Release the device descriptor */
		uvc_unref_device(dev);
	}

	/* Close the UVC context. This closes and cleans up any existing device handles,
     * and it closes the libusb context if one was not provided. */
	uvc_exit(ctx);
	puts("UVC exited");



	EXIT();
}

//**********************************************************************
//
//**********************************************************************
jint registerNativeMethods(JNIEnv* env, const char *class_name, JNINativeMethod *methods, int num_methods) {
	int result = 0;

	jclass clazz = env->FindClass(class_name);
	if (LIKELY(clazz)) {
		int result = env->RegisterNatives(clazz, methods, num_methods);
		if (UNLIKELY(result < 0)) {
			LOGE("registerNativeMethods failed(class=%s)", class_name);
		}
	} else {
		LOGE("registerNativeMethods: class'%s' not found", class_name);
	}
	return result;
}

static JNINativeMethod methods[] = {
	{ "nativeTest", 					"(IIIIILjava/lang/String;)V", (void *) nativeTest },

};

int register_uvccamera(JNIEnv *env) {
	LOGV("register_uvccamera:");
	if (registerNativeMethods(env,
		"com/serenegiant/usb/UVCCamera",
		methods, NUM_ARRAY_ELEMENTS(methods)) < 0) {
		return -1;
	}
    return 0;
}
