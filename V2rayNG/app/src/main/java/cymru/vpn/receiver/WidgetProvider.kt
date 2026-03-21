package cymru.vpn.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import cymru.vpn.AppConfig
import cymru.vpn.R
import cymru.vpn.handler.MmkvManager
import cymru.vpn.handler.SpeedtestManager
import cymru.vpn.handler.V2RayServiceManager
import cymru.vpn.util.CountryUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val isRunning = V2RayServiceManager.isRunning()
        if (isRunning) {
            updateWidgetConnected(context, appWidgetManager, appWidgetIds)
        } else {
            updateWidgetDisconnected(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            AppConfig.BROADCAST_ACTION_WIDGET_CLICK -> {
                if (V2RayServiceManager.isRunning()) {
                    V2RayServiceManager.stopVService(context)
                } else {
                    V2RayServiceManager.startVServiceFromToggle(context)
                }
            }

            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val widgetIds = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
                if (widgetIds.isEmpty()) return

                when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING,
                    AppConfig.MSG_STATE_START_SUCCESS -> {
                        updateWidgetConnected(context, manager, widgetIds)
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING,
                    AppConfig.MSG_STATE_START_FAILURE,
                    AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        updateWidgetDisconnected(context, manager, widgetIds)
                    }
                }
            }
        }
    }

    private fun updateWidgetConnected(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_enhanced)

        // Set click handler
        setToggleClickIntent(context, remoteViews)

        // Connected background
        remoteViews.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_connected)

        // Stop icon
        remoteViews.setImageViewResource(R.id.iv_widget_toggle, R.drawable.ic_stop_24dp)

        // Status text
        remoteViews.setTextViewText(R.id.tv_widget_status, "Connected")

        // Get selected server info
        val serverName = getServerDisplayInfo(context)
        remoteViews.setTextViewText(R.id.tv_widget_server, serverName.first)

        // Country flag
        remoteViews.setTextViewText(R.id.tv_widget_flag, serverName.second)

        // Show "fetching..." for IP initially
        remoteViews.setTextViewText(R.id.tv_widget_ip, "IP: fetching...")
        remoteViews.setViewVisibility(R.id.tv_widget_ip, View.VISIBLE)

        // Push initial update
        for (id in widgetIds) {
            manager.updateAppWidget(id, remoteViews)
        }

        // Fetch IP in background and update
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ipInfo = SpeedtestManager.getRemoteIPInfo()
                val updatedViews = RemoteViews(context.packageName, R.layout.widget_enhanced)
                setToggleClickIntent(context, updatedViews)
                updatedViews.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_connected)
                updatedViews.setImageViewResource(R.id.iv_widget_toggle, R.drawable.ic_stop_24dp)
                updatedViews.setTextViewText(R.id.tv_widget_status, "Connected")
                updatedViews.setTextViewText(R.id.tv_widget_server, serverName.first)
                updatedViews.setTextViewText(R.id.tv_widget_flag, serverName.second)
                updatedViews.setTextViewText(R.id.tv_widget_ip, "IP: ${ipInfo ?: "unavailable"}")
                updatedViews.setViewVisibility(R.id.tv_widget_ip, View.VISIBLE)

                for (id in widgetIds) {
                    manager.updateAppWidget(id, updatedViews)
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Widget: Failed to fetch IP info", e)
            }
        }
    }

    private fun updateWidgetDisconnected(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_enhanced)

        setToggleClickIntent(context, remoteViews)

        // Disconnected background
        remoteViews.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_disconnected)

        // Play icon
        remoteViews.setImageViewResource(R.id.iv_widget_toggle, R.drawable.ic_play_24dp)

        // Status
        remoteViews.setTextViewText(R.id.tv_widget_status, "Not Connected")

        // Get the selected server name to show what will connect
        val serverName = getServerDisplayInfo(context)
        remoteViews.setTextViewText(R.id.tv_widget_server, "Tap to connect")
        remoteViews.setTextViewText(R.id.tv_widget_flag, "\uD83C\uDF10")  // globe emoji

        // Hide IP when disconnected
        remoteViews.setTextViewText(R.id.tv_widget_ip, "IP: ---")
        remoteViews.setViewVisibility(R.id.tv_widget_ip, View.VISIBLE)

        for (id in widgetIds) {
            manager.updateAppWidget(id, remoteViews)
        }
    }

    private fun setToggleClickIntent(context: Context, remoteViews: RemoteViews) {
        val intent = Intent(context, WidgetProvider::class.java)
        intent.action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.layout_widget_toggle, pendingIntent)
    }

    /**
     * Gets the display name and flag for the currently selected server.
     * Returns a pair of (server display name, flag emoji).
     */
    private fun getServerDisplayInfo(context: Context): Pair<String, String> {
        val guid = MmkvManager.getSelectServer()
        if (guid.isNullOrEmpty()) {
            return Pair("No server selected", "\uD83C\uDF10")
        }

        val config = MmkvManager.decodeServerConfig(guid)
        if (config == null) {
            return Pair("Unknown server", "\uD83C\uDF10")
        }

        val remarks = config.remarks
        val countryCode = CountryUtil.extractCountryCode(remarks)
        val flag = if (countryCode != null) {
            CountryUtil.countryCodeToFlag(countryCode)
        } else {
            "\uD83C\uDF10"  // globe emoji
        }

        val displayName = if (countryCode != null) {
            CountryUtil.getCountryName(countryCode)
        } else {
            remarks
        }

        return Pair(displayName, flag)
    }
}
