// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include "key_table.h"

#include <rime/key_table.h>

#include "jni-utils.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeModifierByName(JNIEnv *env,
                                                      jclass /* thiz */,
                                                      jstring name) {
  return RimeGetModifierByName(CString(env, name));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeKeycodeByName(JNIEnv *env,
                                                     jclass /* thiz */,
                                                     jstring name) {
  return RimeGetKeycodeByName(CString(env, name));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeKeyUnicode(JNIEnv *env, jclass clazz,
                                                  jint keycode) {
  return RimeGetKeyUnicode(keycode);
}
