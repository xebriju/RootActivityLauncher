package tk.zwander.rootactivitylauncher.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import tk.zwander.rootactivitylauncher.R
import tk.zwander.rootactivitylauncher.activities.ShortcutLaunchActivity
import tk.zwander.rootactivitylauncher.data.component.ActivityInfo
import tk.zwander.rootactivitylauncher.data.component.BaseComponentInfo
import tk.zwander.rootactivitylauncher.data.component.ReceiverInfo
import tk.zwander.rootactivitylauncher.data.component.ServiceInfo
import tk.zwander.rootactivitylauncher.data.model.AppModel
import tk.zwander.rootactivitylauncher.util.findExtrasForComponent
import tk.zwander.rootactivitylauncher.util.getCoilData
import tk.zwander.rootactivitylauncher.util.launch.launch
import tk.zwander.rootactivitylauncher.util.openAppInfo

sealed class ComponentActionButton<T>(protected val data: T) {
    @Composable
    abstract fun getIconRes(): Int
    @Composable
    abstract fun getLabelRes(): Int

    abstract suspend fun onClick(context: Context)

    override fun equals(other: Any?): Boolean {
        return other != null &&
                other::class == this::class &&
                data == (other as ComponentActionButton<*>).data
    }

    override fun hashCode(): Int {
        return this::class.qualifiedName!!.hashCode() * 31 +
                data.hashCode()
    }

    class ComponentInfoButton(data: Any, private val onClick: (info: Any) -> Unit) :
        ComponentActionButton<Any>(data) {
        @Composable
        override fun getIconRes() = R.drawable.ic_baseline_help_outline_24
        @Composable
        override fun getLabelRes() = R.string.component_info

        override suspend fun onClick(context: Context) {
            onClick(data)
        }
    }

    class IntentDialogButton(data: String, private val onClick: () -> Unit) : ComponentActionButton<String>(data) {
        @Composable
        override fun getIconRes() = R.drawable.tune
        @Composable
        override fun getLabelRes() = R.string.intent

        override suspend fun onClick(context: Context) {
            onClick()
        }
    }

    class AppInfoButton(data: String) : ComponentActionButton<String>(data) {
        @Composable
        override fun getIconRes() = R.drawable.about_outline
        @Composable
        override fun getLabelRes() = R.string.app_info

        override suspend fun onClick(context: Context) {
            context.openAppInfo(data)
        }
    }

    class SaveApkButton(data: AppModel, private val onClick: (AppModel) -> Unit) :
        ComponentActionButton<AppModel>(data) {
        @Composable
        override fun getIconRes() = R.drawable.save
        @Composable
        override fun getLabelRes() = R.string.extract_apk

        override suspend fun onClick(context: Context) {
            onClick(data)
        }
    }

    class CreateShortcutButton(data: BaseComponentInfo) : ComponentActionButton<BaseComponentInfo>(data) {
        @Composable
        override fun getIconRes() = R.drawable.ic_baseline_link_24
        @Composable
        override fun getLabelRes() = R.string.create_shortcut

        @SuppressLint("RestrictedApi")
        override suspend fun onClick(context: Context) {
            ShortcutLaunchActivity.createShortcut(
                context = context,
                label = data.label,
                icon = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(data.getCoilData())
                        .size(Size(Dimension(256), Dimension.Undefined))
                        .build()
                ).drawable?.toBitmap()?.let { IconCompat.createWithBitmap(it) },
                componentKey = data.component.flattenToString(),
                componentType = data.type()
            )
        }
    }

    class LaunchButton(
        data: BaseComponentInfo,
        private val errorCallback: (error: List<Pair<String, Throwable>>) -> Unit
    ) : ComponentActionButton<BaseComponentInfo>(data) {
        @Composable
        override fun getIconRes() = R.drawable.ic_baseline_open_in_new_24
        @Composable
        override fun getLabelRes() = R.string.launch

        override suspend fun onClick(context: Context) {
            val componentKey = data.component.flattenToString()

            val extras = context.findExtrasForComponent(data.component.packageName) +
                    context.findExtrasForComponent(componentKey)

            val result = context.launch(data.type(), extras, componentKey)

            errorCallback(result)
        }
    }

    class FavoriteButton(
        data: BaseComponentInfo
    ) : ComponentActionButton<BaseComponentInfo>(data) {
        @Composable
        override fun getIconRes(): Int {
            val context = LocalContext.current
            val flow = context.flowForType()
            val state by flow.collectAsState(initial = listOf())

            return if (state.contains(data.component.flattenToString())) {
                R.drawable.baseline_favorite_24
            } else {
                R.drawable.outline_favorite_border_24
            }
        }
        @Composable
        override fun getLabelRes(): Int {
            val context = LocalContext.current
            val flow = context.flowForType()
            val state by flow.collectAsState(initial = listOf())

            return if (state.contains(data.component.flattenToString())) {
                R.string.unfavorite
            } else {
                R.string.favorite
            }
        }

        override suspend fun onClick(context: Context) {
            val flow = context.flowForType()
            val current = flow.first().toMutableList()
            val key = data.component.flattenToString()
            val contains = current.contains(key)

            if (contains) {
                current.remove(key)
            } else {
                current.add(key)
            }

            context.updateForType(current)
        }

        private fun Context.flowForType(): Flow<List<String>> {
            return when (data) {
                is ActivityInfo -> prefs.favoriteActivities
                is ServiceInfo -> prefs.favoriteServices
                is ReceiverInfo -> prefs.favoriteReceivers
            }
        }

        private suspend fun Context.updateForType(info: List<String>) {
            when (data) {
                is ActivityInfo -> prefs.updateFavoriteActivities(info)
                is ServiceInfo -> prefs.updateFavoriteServices(info)
                is ReceiverInfo -> prefs.updateFavoriteReceivers(info)
            }
        }
    }
}