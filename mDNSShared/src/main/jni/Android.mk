LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES :=  mDNSShared/dnssd_clientlib.c  \
                    mDNSShared/dnssd_clientstub.c \
                    mDNSShared/dnssd_ipc.c \
                    mDNSShared/JNISupport.c

LOCAL_MODULE := jdns_sd
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS := -O2 -g -W -Wall -D__ANDROID__ -D_GNU_SOURCE -DHAVE_IPV6 -DNOT_HAVE_SA_LEN -DUSES_NETLINK -DTARGET_OS_LINUX -fno-strict-aliasing -DHAVE_LINUX -DMDNS_UDS_SERVERPATH=\"/dev/socket/mdnsd\" -DMDNS_DEBUGMSGS=0
LOCAL_SYSTEM_SHARED_LIBRARIES := libc
LOCAL_SHARED_LIBRARIES := libcutils liblog

include $(BUILD_SHARED_LIBRARY)
