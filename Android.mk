LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := libpinyin4j

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
	src/com/android/music/IMediaPlaybackService.aidl

LOCAL_PACKAGE_NAME := FPMusic

LOCAL_OVERRIDES_PACKAGES := Music

LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := libpinyin4j:libs/pinyin4j_2.5.0.jar
include $(BUILD_MULTI_PREBUILT)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))

