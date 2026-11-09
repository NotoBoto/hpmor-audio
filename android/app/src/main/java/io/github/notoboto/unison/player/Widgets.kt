package io.github.notoboto.unison.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import io.github.notoboto.unison.MainActivity
import io.github.notoboto.unison.R
import io.github.notoboto.unison.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home-screen player widget: cover, chapter, rewind / play-pause / forward. */
/** Large widget: the same controls plus the sentence being spoken. */
class PlayerWidgetBig : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx)
}

class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.update(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val app = ctx.app()
        when (intent.action) {
            ACT_PLAY -> app.player.toggleOrResume()
            ACT_REW -> app.player.skip(-app.store.s.settings.skipBack)
            ACT_FWD -> app.player.skip(app.store.s.settings.skipForward)
        }
    }

    companion object {
        const val ACT_PLAY = "io.github.notoboto.unison.W_PLAY"
        const val ACT_REW = "io.github.notoboto.unison.W_REW"
        const val ACT_FWD = "io.github.notoboto.unison.W_FWD"
    }
}

object Widgets {
    private var coverUrl: String? = null
    private var coverBmp: Bitmap? = null

    private fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(ctx, code,
        Intent(ctx, PlayerWidget::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun update(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        render(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidget::class.java)), R.layout.widget_player)
        render(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidgetBig::class.java)), R.layout.widget_player_big)
        TileService.requestListeningState(ctx, ComponentName(ctx, PlayerTile::class.java))
    }

    private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, layout: Int) {
        val app = ctx.app()
        val np = app.player.now.value
        val playing = app.player.isPlaying.value
        val wantCover = np?.book?.coverSmUrl
        if (ids.isNotEmpty()) {
            val v = RemoteViews(ctx.packageName, layout)
            if (layout == R.layout.widget_player_big) v.setTextViewText(R.id.w_sentence, app.player.sentence.value.ifBlank { "▶ — продолжить слушать" })
            if (np != null) {
                v.setTextViewText(R.id.w_title, if (app.store.s.settings.fullTitles) np.title else "Глава ${np.n}")
                v.setTextViewText(R.id.w_sub, np.book.shortTitle)
            } else {
                val last = app.store.s.history.firstOrNull()
                v.setTextViewText(R.id.w_title, last?.let { h -> app.repo.book(h.book)?.let { "${it.shortTitle}, глава ${h.n}" } } ?: "Unison")
                v.setTextViewText(R.id.w_sub, "Нажмите ▶, чтобы продолжить")
            }
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            if (coverBmp != null && coverUrl == wantCover) v.setImageViewBitmap(R.id.w_cover, coverBmp)
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, PlayerWidget.ACT_PLAY, 1))
            v.setOnClickPendingIntent(R.id.w_rew, pi(ctx, PlayerWidget.ACT_REW, 2))
            v.setOnClickPendingIntent(R.id.w_fwd, pi(ctx, PlayerWidget.ACT_FWD, 3))
            v.setOnClickPendingIntent(R.id.w_cover, PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java).putExtra("openPlayer", true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            mgr.updateAppWidget(ids, v)
            if (wantCover != null && wantCover != coverUrl) app.scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        val r = coil.request.ImageRequest.Builder(ctx).data(wantCover).size(160, 230).allowHardware(false).build()
                        (coil.ImageLoader(ctx).execute(r).drawable as? BitmapDrawable)?.bitmap
                    }.getOrNull()
                }
                if (bmp != null) { coverUrl = wantCover; coverBmp = bmp; update(ctx) }
            }
        }
    }
}

/** Quick Settings tile: play / pause the current (or last) chapter. */
class PlayerTile : TileService() {
    override fun onStartListening() { refresh() }
    override fun onClick() { applicationContext.app().player.toggleOrResume(); refresh() }
    private fun refresh() {
        val t = qsTile ?: return
        val app = applicationContext.app()
        val np = app.player.now.value
        t.state = if (app.player.isPlaying.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (app.player.isPlaying.value) "Пауза" else "Слушать"
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = np?.let { "Глава ${it.n}" } ?: "Unison"
        t.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        t.updateTile()
    }
}
