package com.kanime.app.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kanime.app.MainActivity
import com.kanime.app.R
import com.kanime.app.data.api.AniListApi
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.api.WatchHistoryStore
import java.util.concurrent.TimeUnit

/** Scheduling + posting of "new episode" notifications for anime on the user's watchlist. */
object EpisodeAlerts {
    const val EXTRA_OPEN_MAL_ID = "kanime_open_mal_id"
    const val EXTRA_OPEN_TITLE = "kanime_open_title"
    const val EXTRA_OPEN_COVER = "kanime_open_cover"

    private const val CHANNEL_ID = "new_episodes"
    private const val UNIQUE_WORK_NAME = "kanime_episode_alerts"
    private const val CHECK_INTERVAL_MINUTES = 30L
    private const val MAX_LOOKBACK_SEC = 3L * 24L * 60L * 60L

    fun hasNotificationPermission(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Runs a lightweight background check every ~30 minutes while alerts are on. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<EpisodeAlertWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.alerts_channel_description)
            }
        )
    }

    internal fun notifyEpisode(context: Context, malId: Int, title: String, cover: String?, episode: Int) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_MAL_ID, malId)
            putExtra(EXTRA_OPEN_TITLE, title)
            if (!cover.isNullOrBlank()) putExtra(EXTRA_OPEN_COVER, cover)
        }
        val notificationId = malId * 1000 + episode
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_new_episode)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alerts_episode_aired, episode))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post — nothing to do.
        }
    }

    /** One check: which watchlist anime aired an episode since the last check? */
    internal suspend fun checkAndNotify(context: Context): Boolean {
        EpisodeAlertPreferences.init(context)
        WatchHistoryStore.init(context)
        CatalogConfig.init(context)

        if (!EpisodeAlertPreferences.isEnabled()) return true

        val nowSec = System.currentTimeMillis() / 1000L
        val lastCheck = EpisodeAlertPreferences.lastCheckSec()
        if (lastCheck <= 0L) {
            // First run: just set the baseline, never announce old episodes.
            EpisodeAlertPreferences.setLastCheckSec(nowSec)
            return true
        }

        val watchlistMalIds = WatchHistoryStore.watchlistFlow.value.mapNotNull { it.malId }.toSet()
        if (watchlistMalIds.isEmpty()) {
            EpisodeAlertPreferences.setLastCheckSec(nowSec)
            return true
        }

        val since = maxOf(lastCheck, nowSec - MAX_LOOKBACK_SEC)
        val aired = AniListApi.getAiringSchedule(since, nowSec, useCache = false) ?: return false

        aired
            .filter { it.malId in watchlistMalIds }
            .forEach { entry ->
                if (EpisodeAlertPreferences.markNotified(entry.malId, entry.episode)) {
                    notifyEpisode(context, entry.malId, entry.title, entry.cover, entry.episode)
                }
            }

        EpisodeAlertPreferences.setLastCheckSec(nowSec)
        return true
    }
}

class EpisodeAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            if (EpisodeAlerts.checkAndNotify(applicationContext)) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
