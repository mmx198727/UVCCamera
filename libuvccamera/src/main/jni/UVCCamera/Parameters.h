/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2015-2017 saki t_saki@serenegiant.com
 *
 * File name: Parameters.h
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
#ifndef PARAMETERS_H_
#define PARAMETERS_H_

#pragma interface

#include "libUVCCamera.h"

/**
 * 获取设备参数
 */
class UVCDiags {
private:
public:
	UVCDiags();
	~UVCDiags();
	char *getDescriptions(const uvc_device_handle_t *deviceHandle);
	char *getCurrentStream(const uvc_stream_ctrl_t *ctrl);

	/**
	 * 获取设备支持的视频格式，以及视频格式包含的分辨率
	 * @param deviceHandle
	 * @return	视频格式字符串（需要用户自行拆分）
	 * 			格式如：{"formats":[
	 * 			{"index":1,"type":4,"guidFormat":"NV21","default":1,"size":["640x360"]},
	 * 			{"index":2,"type":6,"guidFormat":"MJPG","default":1,"size":["640x360","1280x720","1920x1080","3840x2160"]},
	 * 			{"index":3,"type":16,"guidFormat":"H264","default":1,"size":["640x360","1280x720","1920x1080","3840x2160"]}
	 * 			]
	 * @see uvc_device_handle_t
	 */
	char *getSupportedSize(const uvc_device_handle_t *deviceHandle);

	/**
	 * 从 Guid 获取 视频格式名称
	 * @param fmt_desc
	 * @return
	 * @see uvc_format_desc_t
	 */
	static char* getGuidFormat(uvc_format_desc_t *fmt_desc);

};

#endif /* PARAMETERS_H_ */
