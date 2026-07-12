#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_sentinel_guardian_MainActivity1_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}