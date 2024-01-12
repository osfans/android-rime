import Versions.cmakeVersion
import Versions.ndkVersion
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.task

open class NativeBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
        target.extensions.configure<CommonExtension<*, *, *, *, *>>("android") {
            ndkVersion = target.ndkVersion
            // Use prebuilt JNI library if the "app/prebuilt" exists
            //
            // Steps to generate the prebuilt directory:
            // $ ./gradlew app:assembleRelease
            // $ cp --recursive app/build/intermediates/stripped_native_libs/universalRelease/out/lib app/prebuilt
            if (target.file("prebuilt").exists()) {
                sourceSets.getByName("main").jniLibs.srcDirs(setOf("prebuilt"))
            } else {
                externalNativeBuild {
                    cmake {
                        version = target.cmakeVersion
                        path("src/main/jni/CMakeLists.txt")
                    }
                }
            }

            if (ApkRelease.run { target.buildApkRelease }) {
                // in this case, the version code of arm64-v8a will be used for the single production,
                // unless `buildABI` is specified
                defaultConfig {
                    ndk {
                        abiFilters.add("armeabi-v7a")
                        abiFilters.add("arm64-v8a")
                        abiFilters.add("x86")
                        abiFilters.add("x86_64")
                    }
                }
            } else {
                splits {
                    abi {
                        isEnable = true
                        reset()
                        include(target.buildABI)
                        isUniversalApk = false
                    }
                }
            }
        }
        registerCleanCxxTask(target)
    }

    private fun registerCleanCxxTask(project: Project) {
        project.task<Delete>("cleanCxxIntermediates") {
            delete(project.file(".cxx"))
        }.also {
            project.cleanTask.dependsOn(it)
        }
    }
}
