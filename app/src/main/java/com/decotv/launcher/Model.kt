package com.decotv.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.drawable.toBitmap

/** The three looks. Morning 5 AM–12 PM, afternoon 12–5 PM, evening 5 PM–5 AM. */
enum class TimeTheme(
    val greeting: String,
    val background: Int,
    val blurred: Int,
    val font: FontFamily,
    val glassTint: Color,
    val cream: Color,
    val muted: Color,
) {
    MORNING(
        "Good Morning", R.drawable.bg_morning, R.drawable.bg_morning_blur,
        FontFamily(Font(R.font.federo)),
        Color(0x1F7896D2), Color(0xFFEFE7D3), Color(0xFFB9C0D0),
    ),
    AFTERNOON(
        "Good Afternoon", R.drawable.bg_afternoon, R.drawable.bg_afternoon_blur,
        FontFamily(Font(R.font.poiret_one)),
        Color(0x1F3CA078), Color(0xFFF1E6CC), Color(0xFFC9D2C4),
    ),
    EVENING(
        "Good Evening", R.drawable.bg_evening, R.drawable.bg_evening_blur,
        FontFamily(Font(R.font.limelight)),
        Color(0x1AD4AF6A), Color(0xFFF3E9D2), Color(0xFFCDBF9F),
    );

    companion object {
        fun forHour(hour: Int): TimeTheme = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            else -> EVENING
        }
    }
}

val Gold = Color(0xFFE0B866)
val GoldLight = Color(0xFFF6DFA6)
val BodyFont = FontFamily(Font(R.font.josefin_sans))

data class TvApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap,
    val hasRealBanner: Boolean,
)

object Apps {
    /** Every app that has a TV launcher entry, alphabetically, minus this launcher. */
    fun load(context: Context): List<TvApp> {
        val pm = context.packageManager
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val infos: List<ResolveInfo> = pm.queryIntentActivities(leanback, 0)
        return infos
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val ai = ri.activityInfo
                val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                val drawable = banner ?: ai.loadIcon(pm)
                val bmp = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(144, 144)
                TvApp(ai.packageName, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), banner != null)
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: TvApp) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return
        Actions.start(context, intent)
    }
}

data class TvInput(val id: String, val label: String)

object Actions {
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun startFirst(context: Context, vararg actions: String): Boolean =
        actions.any { start(context, Intent(it)) }

    fun openWifi(context: Context) {
        startFirst(context, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS)
    }

    fun openSettings(context: Context) {
        startFirst(context, Settings.ACTION_SETTINGS)
    }

    /** Tries the TV's own screen-mirroring page. Returns false if this TV has none. */
    fun openMirroring(context: Context): Boolean =
        startFirst(context, "android.settings.CAST_SETTINGS", "android.settings.WIFI_DISPLAY_SETTINGS")

    fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: "this TV"

    fun inputs(context: Context): List<TvInput> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return emptyList()
        val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            tim.tvInputList
                .filter { it.isPassthroughInput }
                .map { TvInput(it.id, it.loadLabel(context).toString()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun switchToInput(context: Context, input: TvInput): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
}
