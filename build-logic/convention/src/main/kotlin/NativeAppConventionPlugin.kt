// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class NativeAppConventionPlugin : NativeBaseConventionPlugin() {
    private val Project.librimeVersion: String
        get() =
            runCmd(
                "git describe --tags --long --always --exclude 'latest'",
                workingDir = file("src/main/jni/librime"),
            )

    private val Project.openccVersion: String
        get() =
            runCmd(
                "git describe --tags --long --always",
                workingDir = file("src/main/jni/OpenCC"),
            )

    override fun apply(target: Project) {
        super.apply(target)

        target.extensions.configure<BaseAppModuleExtension> {
            packaging {
                jniLibs {
                    useLegacyPackaging = true
                }
            }
            defaultConfig {
                buildConfigField("String", "LIBRIME_VERSION", "\"${target.librimeVersion}\"")
                buildConfigField("String", "OPENCC_VERSION", "\"${target.openccVersion}\"")
            }
        }
    }
}
