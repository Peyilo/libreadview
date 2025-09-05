#include <jni.h>

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("libreadview");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("libreadview")
//      }
//    }
extern "C"
JNIEXPORT void JNICALL
Java_org_peyilo_libreadview_manager_render_IBookCurlRenderer_nativeInitMeshVerts(
         JNIEnv *env,
         jobject thiz,
         jfloatArray verts,
         jfloat page_width,
         jfloat page_height,
         jint mesh_width,
         jint mesh_height) {
    jfloat* array = env->GetFloatArrayElements(verts, nullptr);
    if (!array) return;

    int index = 0;
    for (int y = 0; y <= mesh_height; ++y) {
        float fy = page_height * y / (float) mesh_height;
        for (int x = 0; x <= mesh_width; ++x) {
            float fx = page_width * x / (float) mesh_width;
            array[index * 2]     = fx;
            array[index * 2 + 1] = fy;
            index++;
        }
    }

    // 通知 JVM 数据已更新
    env->ReleaseFloatArrayElements(verts, array, 0);
}