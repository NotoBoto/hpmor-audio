package io.github.notoboto.unison

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Daily goal, streaks and the evening reminder. */
object Habits {
    fun todayMin(app: UnisonApp) = ((app.store.s.statsDays[LocalDate.now().toString()] ?: 0) / 60000).toInt()

    /** consecutive days (ending today or yesterday) with at least the goal (or 1 minute without a goal) */
    fun streak(app: UnisonApp): Int {
        val need = maxOf(1, app.store.s.settings.goalMin) * 60000L
        var d = LocalDate.now(); var n = 0
        if ((app.store.s.statsDays[d.toString()] ?: 0) < need) d = d.minusDays(1)
        while ((app.store.s.statsDays[d.toString()] ?: 0) >= need) { n++; d = d.minusDays(1) }
        return n
    }

    fun schedule(ctx: Context) {
        val s = ctx.app().store.s.settings
        val wm = WorkManager.getInstance(ctx)
        if (!s.reminderOn) { wm.cancelUniqueWork("reminder"); return }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atStartOfDay().plusMinutes(s.reminderAt.toLong())
        if (!at.isAfter(now)) at = at.plusDays(1)
        val delay = java.time.Duration.between(now, at).toMinutes()
        wm.enqueueUniqueWork("reminder", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MINUTES).build())
    }
}

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app()
        val s = app.store.s.settings
        val today = Habits.todayMin(app)
        val goal = s.goalMin
        if (s.reminderOn && (goal == 0 && today == 0 || goal > 0 && today < goal)) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("reminder", "Напоминания", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(applicationContext, 5, Intent(applicationContext, MainActivity::class.java).setAction("io.github.notoboto.unison.RESUME"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val streak = Habits.streak(app)
            val text = if (goal > 0) "Сегодня $today из $goal мин" + (if (streak > 0) " • серия $streak дн." else "") else "Сегодня вы ещё не слушали"
            runCatching {
                nm.notify(3997, NotificationCompat.Builder(applicationContext, "reminder").setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Время послушать").setContentText(text).setContentIntent(open).setAutoCancel(true).build())
            }
        }
        Habits.schedule(applicationContext)   // next day
        return Result.success()
    }
}
