/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.Project
import org.gradle.api.Task
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun Project.runCmd(cmd: String): String =
    ByteArrayOutputStream().use {
        project.exec {
            commandLine = cmd.split(" ")
            standardOutput = it
        }
        it.toString().trim()
    }

val Project.assetsDir: File
    get() = file("src/main/assets").also { it.mkdirs() }

val Project.cleanTask: Task
    get() = tasks.getByName("clean")

val Project.cmakeVersion
    get() = envOrProp("CMAKE_VERSION", "cmakeVersion") { Versions.DEFAULT_CMAKE }

val Project.ndkVersion
    get() = envOrProp("NDK_VERSION", "ndkVersion") { Versions.DEFAULT_NDK }

val Project.buildAbiOverride
    get() = envOrPropOrNull("BUILD_ABI", "buildABI")

val Project.builder
    get() =
        envOrProp("CI_NAME", "ciName") {
            runCatching { runCmd("git config user.name").ifEmpty { "(Unknown)" } }.getOrElse { "(Unknown)" }
        }

val Project.buildGitRepo
    get() =
        envOrProp("BUILD_GIT_REPO", "buildGitRepo") {
            runCmd("git remote get-url origin")
                .replaceFirst("^git@github\\.com:", "https://github.com/")
                .replaceFirst("\\.git\$", "")
        }

val Project.buildVersionName
    get() =
        envOrProp("BUILD_VERSION_NAME", "buildVersionName") {
            // 构建正式版时过滤掉 nightly 标签
            val cmd =
                if (builder.contains("nightly", ignoreCase = true)) {
                    "git describe --tags --long --always --match nightly"
                } else {
                    "git describe --tags --long --always --match v*"
                }
            runCmd(cmd)
        }

val Project.buildCommitHash
    get() =
        envOrProp("BUILD_COMMIT_HASH", "buildCommitHash") {
            runCmd("git rev-parse HEAD")
        }

val Project.buildTimestamp
    get() =
        envOrProp("BUILD_TIMESTAMP", "buildTimestamp") {
            System.currentTimeMillis().toString()
        }

val Project.signKeyBase64: String?
    get() = envOrPropOrNull("SIGN_KEY_BASE64", "signKeyBase64")

val Project.signKeyStore
    get() = envOrPropOrNull("SIGN_KEY_STORE", "signKeyStore")

val Project.signKeyStorePwd
    get() = envOrPropOrNull("SIGN_KEY_STORE_PWD", "signKeyStorePwd")

val Project.signKeyAlias
    get() = envOrPropOrNull("SIGN_KEY_ALIAS", "signKeyAlias")

val Project.signKeyPwd
    get() = envOrPropOrNull("SIGN_KEY_PWD", "signKeyPwd")

val Project.signKeyFile: File?
    get() {
        signKeyStore?.let {
            val file = File(it)
            if (file.exists()) return file
        }

        @OptIn(ExperimentalEncodingApi::class)
        signKeyBase64?.let {
            val buildDir = layout.buildDirectory.asFile.get()
            buildDir.mkdirs()
            val file = File.createTempFile("sign-", ".ks", buildDir)
            try {
                file.writeBytes(Base64.decode(it))
                return file
            } catch (e: Exception) {
                println(e.localizedMessage ?: e.stackTraceToString())
                file.delete()
            }
        }
        return null
    }
