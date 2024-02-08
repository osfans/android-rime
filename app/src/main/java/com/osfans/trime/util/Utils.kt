package com.osfans.trime.util

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ToastUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.experimental.InternalSplittiesApi
import splitties.resources.withResolvedThemeAttribute
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

val appContext: Context get() = TrimeApplication.getInstance().applicationContext

@OptIn(ExperimentalContracts::class)
inline fun <T : Any, U> Result<T?>.bindOnNotNull(block: (T) -> Result<U>): Result<U>? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return when {
        isSuccess && getOrThrow() != null -> block(getOrThrow()!!)
        isSuccess && getOrThrow() == null -> null
        else -> Result.failure(exceptionOrNull()!!)
    }
}

suspend fun <T> Result<T>.toast() =
    withContext(Dispatchers.Main.immediate) {
        onSuccess {
            ToastUtils.showShort(R.string.setup__done)
        }
        onFailure {
            ToastUtils.showShort(it.message)
        }
    }

@OptIn(InternalSplittiesApi::class)
fun Context.styledFloat(
    @AttrRes attrRes: Int,
) = withResolvedThemeAttribute(attrRes) {
    when (type) {
        TypedValue.TYPE_FLOAT -> float
        else -> throw IllegalArgumentException("float attribute expected")
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun View.styledFloat(
    @AttrRes attrRes: Int,
) = context.styledFloat(attrRes)

@Suppress("NOTHING_TO_INLINE")
inline fun Fragment.styledFloat(
    @AttrRes attrRes: Int,
) = context!!.styledFloat(attrRes)

fun formatDateTime(timeMillis: Long? = null): String = SimpleDateFormat.getDateTimeInstance().format(timeMillis?.let { Date(it) } ?: Date())

private val iso8601DateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

fun iso8601UTCDateTime(timeMillis: Long? = null): String = iso8601DateFormat.format(timeMillis?.let { Date(it) } ?: Date())

@Suppress("NOTHING_TO_INLINE")
inline fun CharSequence.startsWithAsciiChar(): Boolean {
    val firstCodePoint = this.toString().codePointAt(0)
    return firstCodePoint in 0x20 until 0x80
}

fun RecyclerView.applyNavBarInsetsBottomPadding() {
    clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).also {
            setPadding(paddingLeft, paddingTop, paddingRight, it.bottom)
        }
        windowInsets
    }
}

inline fun <reified T : Serializable> Bundle.serializable(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializable(key) as? T
    }
}

fun Preference.thirdPartySummary(versionCode: String) {
    summary = versionCode
    intent?.let {
        val commitHash =
            if (versionCode.contains("-g")) {
                versionCode.replace("^(.*-g)([0-9a-f]+)(.*)$".toRegex(), "$2")
            } else {
                versionCode.replace("^([^-]*)(-.*)$".toRegex(), "$1")
            }
        it.data = Uri.withAppendedPath(it.data, "commits/$commitHash")
    }
}

fun Preference.optionalPreference() {
    isVisible = summary.isNullOrBlank() || intent?.data == null
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.isStorageAvailable(): Boolean {
    return XXPermissions.isGranted(this, Permission.MANAGE_EXTERNAL_STORAGE) &&
        Environment.getExternalStorageDirectory().absolutePath.isNotEmpty()
}

fun Context.requestExternalStoragePermission() {
    XXPermissions.with(this)
        .permission(Permission.MANAGE_EXTERNAL_STORAGE)
        .request(
            object : OnPermissionCallback {
                override fun onGranted(
                    permissions: List<String>,
                    all: Boolean,
                ) {
                    if (all) {
                        ToastUtils.showShort(R.string.external_storage_permission_granted)
                    }
                }

                override fun onDenied(
                    permissions: List<String>,
                    never: Boolean,
                ) {
                    if (never) {
                        ToastUtils.showShort(R.string.external_storage_permission_denied)
                        XXPermissions.startPermissionActivity(
                            this@requestExternalStoragePermission,
                            permissions,
                        )
                    } else {
                        ToastUtils.showShort(R.string.external_storage_permission_denied)
                    }
                }
            },
        )
}

fun Configuration.isNightMode() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
