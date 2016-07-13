#include "AppDelegate.h"
#include "cocos2d.h"
#include "platform/android/jni/JniHelper.h"
#include <jni.h>
#include <android/log.h>
#include "ide-support/SimpleConfigParser.h"
#if PACKAGE_AS
#include "PluginJniHelper.h"
#include "SDKManager.h"
#endif

#define  LOG_TAG    "main"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

using namespace cocos2d;

void cocos_android_app_init (JNIEnv* env) {
    LOGD("cocos_android_app_init");
    AppDelegate *pAppDelegate = new AppDelegate();
#if PACKAGE_AS
    JavaVM* vm;
    env->GetJavaVM(&vm);
    PluginJniHelper::setJavaVM(vm);
#endif
}


extern "C"
{
    bool Java_org_cocos2dx_javascript_AppActivity_nativeIsDebug(JNIEnv *env, jobject thisz)
    {
#if (COCOS2D_DEBUG > 0)
        return true;
#else
        return false;    
#endif
    }

    	void Java_org_cocos2dx_javascript_SDKWrapper_nativeLoadAllPlugins(JNIEnv*  env, jobject thiz)
	{
#if PACKAGE_AS
    	SDKManager::getInstance()->loadAllPlugins();
#endif
	}
}
