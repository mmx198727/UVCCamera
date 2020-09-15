//
// Created by Max on 2020/9/14.
//

#ifndef UVCCAMERA_LOGHELPER_H
#define UVCCAMERA_LOGHELPER_H

/** @brief   操作Android log辅助类
 *  @note
 *            \li 全局变量 g_logLevel 当前日志级别
 *            \li 全局函数 LogOut(level, cFormat, ...) 打印日志
 *  @file    mzlog.h
 *  @author  北京麦哲科技有限公司
 *  @version V1.0.0
 *  @date    2020-8-28
 *  @since   2020-8-28
 */

#include <android/log.h>
#include <errno.h>
#include <stdarg.h>

/** @brief 日志级别定义
 */
#define UNKNOWN   ANDROID_LOG_UNKNOWN
#define DEFAULT   ANDROID_LOG_DEFAULT
#define VERBOSE   ANDROID_LOG_VERBOSE
#define DEBUG     ANDROID_LOG_DEBUG
#define INFO      ANDROID_LOG_INFO
#define WARN      ANDROID_LOG_WARN
#define ERROR     ANDROID_LOG_ERROR
#define FATAL     ANDROID_LOG_FATAL
#define SILENT    ANDROID_LOG_SILENT

#define __FILENAME__ (strrchr(__FILE__, '/') ? (strrchr(__FILE__, '/') + 1):__FILE__)


#define LOGOUTD(cFormat, ...) \
  logOut(__FILENAME__, __FUNCTION__, __LINE__, DEBUG, cFormat, ##__VA_ARGS__);

/**  Android log打印
 *   调用logOut实现日志打印
 @param level	    输入参数 日志级别，需要大于g_logLevel才会打印日志
 @param format    输入参数 格式化字符串

*/
#define LogOut(level, cFormat, ...) \
  logOut(__FILENAME__, __FUNCTION__, __LINE__, level, cFormat, ##__VA_ARGS__);

/** @brief 当前日志级别
 */
static int g_logLevel = DEBUG;
//int g_logLevel = INFO;

/**  Android log打印
 *   实际上不会直接调用此函数，打印日志调用LogOut宏函数
 @param file	    输入参数
 @param func	    输入参数
 @param line	    输入参数
 @param level	    输入参数 日志级别，需要大于g_logLevel才会打印日志
 @param format    输入参数 格式化字符串

*/
static void logOut(const char* file, const char* func, int line, int level, const char* format, ...)
{
  if (g_logLevel > level)
    return;

  char buf[1024] = {0};
  va_list args;
  va_start (args, format);
  //vsprintf(buf, format, args);
  vsnprintf(buf, sizeof(buf)-4, format, args);

  va_end(args);

  __android_log_print(level, "libuvccamera_tag", "%s_%d(%s):%s\n", file, line, func, buf);
}

#endif //UVCCAMERA_LOGHELPER_H
