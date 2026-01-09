#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_vibromusic_MainActivity_getNativeStatus(JNIEnv* env, jobject /* this */) {
    // Имитация статуса движка на C++
    return env->NewStringUTF("C++ Audio Engine: Ок");
}
