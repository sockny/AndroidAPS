package app.aaps.ui.compose.preferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.keys.AodBooleanKey
import app.aaps.core.keys.AodIntKey
import app.aaps.core.keys.GlucoseNotificationKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.preference.AdaptivePreferenceItem
import app.aaps.core.ui.compose.preference.Preference
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** Hosted by the existing settings navigation, theme, locale and preference composition locals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlucoseDisplaySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var serviceEnabled by remember { mutableStateOf(aodServiceEnabled(context)) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val generalList = rememberLazyListState()
    val advancedList = rememberLazyListState()
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = aodServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ProvidePreferenceTheme {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = { Text(stringResource(UiStrings.glucose_display_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreUiStrings.back))
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    listOf(UiStrings.glucose_display_general, UiStrings.glucose_display_advanced).forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(stringResource(title)) })
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = if (selectedTab == 0) generalList else advancedList) {
                    if (selectedTab == 0) {
                        item { AdaptivePreferenceItem(GlucoseNotificationKey.Enabled, onShowMessage = {}) }
                        item { AdaptivePreferenceItem(AodBooleanKey.Enabled, onShowMessage = {}) }
                        item {
                            Preference(
                                title = { Text(stringResource(UiStrings.aod_open_accessibility)) },
                                summary = { Text(stringResource(if (serviceEnabled) UiStrings.aod_accessibility_enabled else UiStrings.aod_accessibility_disabled)) },
                                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            )
                        }
                        item { Preference(title = { Text(stringResource(UiStrings.aod_compatibility)) }) }
                        item { Preference(title = { Text(stringResource(UiStrings.glucose_display_data_age)) }) }
                    } else {
                        AodIntKey.entries.forEach { key -> item { AdaptivePreferenceItem(key, onShowMessage = {}) } }
                        item { Preference(title = { Text(stringResource(UiStrings.notification_glucose_icon_tint)) }) }
                        item { Preference(title = { Text(stringResource(UiStrings.aod_burn_in)) }) }
                    }
                }
            }
        }
    }
}

private fun aodServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context.packageName, "app.aaps.aod.AodAccessibilityService")
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
}
