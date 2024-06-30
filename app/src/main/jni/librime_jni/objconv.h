// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#ifndef TRIME_OBJCONV_H
#define TRIME_OBJCONV_H

#include <rime_api.h>

#include "jni-utils.h"

inline jobject rimeCommitToJObject(JNIEnv *env, const RimeCommit &commit) {
  return env->NewObject(GlobalRef->RimeCommit, GlobalRef->RimeCommitInit,
                        *JString(env, commit.text));
}

inline jobject rimeContextToJObject(JNIEnv *env, const RimeContext &context) {
  auto jcomposition = JRef<>(
      env,
      env->NewObject(GlobalRef->RimeComposition, GlobalRef->RimeCompositionInit,
                     context.composition.length, context.composition.cursor_pos,
                     context.composition.sel_start, context.composition.sel_end,
                     *JString(env, context.composition.preedit)));

  const auto &menu = context.menu;

  size_t numSelectKeys =
      menu.select_keys ? std::strlen(context.menu.select_keys) : 0;
  bool hasLabel = RIME_STRUCT_HAS_MEMBER(context, context.select_labels) &&
                  context.select_labels;
  auto selectLabels = JRef<jobjectArray>(
      env,
      env->NewObjectArray(menu.num_candidates, GlobalRef->String, nullptr));
  auto candidates = JRef<jobjectArray>(
      env, env->NewObjectArray(menu.num_candidates,
                               GlobalRef->CandidateListItem, nullptr));
  for (int i = 0; i < menu.num_candidates; ++i) {
    std::string label;
    if (i < menu.page_size && hasLabel) {
      label = context.select_labels[i];
    } else if (i < numSelectKeys) {
      label = std::string(1, menu.select_keys[i]);
    } else {
      label = std::to_string((i + 1) % 10);
    }
    label.append(" ");
    env->SetObjectArrayElement(selectLabels, i, JString(env, label));
    auto &candidate = context.menu.candidates[i];
    auto jcandidate = JRef<>(
        env, env->NewObject(
                 GlobalRef->CandidateListItem, GlobalRef->CandidateListItemInit,
                 *JString(env, candidate.comment ? candidate.comment : ""),
                 *JString(env, candidate.text ? candidate.text : "")));
    env->SetObjectArrayElement(candidates, i, jcandidate);
  }

  auto jmenu = JRef<>(
      env, env->NewObject(GlobalRef->RimeMenu, GlobalRef->RimeMenuInit,
                          menu.page_size, menu.page_no, menu.is_last_page,
                          menu.highlighted_candidate_index, menu.num_candidates,
                          *candidates));

  return env->NewObject(
      GlobalRef->RimeContext, GlobalRef->RimeContextInit, *jcomposition, *jmenu,
      *JString(env, context.commit_text_preview), *selectLabels);
}

inline jobject rimeStatusToJObject(JNIEnv *env, const RimeStatus &status) {
  return env->NewObject(GlobalRef->RimeStatus, GlobalRef->RimeStatusInit,
                        *JString(env, status.schema_id),
                        *JString(env, status.schema_name), status.is_disabled,
                        status.is_composing, status.is_ascii_mode,
                        status.is_full_shape, status.is_simplified,
                        status.is_traditional, status.is_ascii_punct);
}

inline jobjectArray rimeSchemaListToJObjectArray(JNIEnv *env,
                                                 RimeSchemaList &list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size),
                                           GlobalRef->SchemaListItem, nullptr);
  for (int i = 0; i < list.size; i++) {
    auto item = list.list[i];
    auto obj = JRef<>(env, env->NewObject(GlobalRef->SchemaListItem,
                                          GlobalRef->SchemaListItemInit,
                                          *JString(env, item.schema_id),
                                          *JString(env, item.name)));
    env->SetObjectArrayElement(array, i, obj);
  }
  return array;
}

#endif  // TRIME_OBJCONV_H
