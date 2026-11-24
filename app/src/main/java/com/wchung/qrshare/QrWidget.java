package com.wchung.qrshare;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Home screen widget showing the most recent code. */
public class QrWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            manager.updateAppWidget(appWidgetId, build(context));
        }
    }

    /** Called whenever history changes so the widget follows the latest code. */
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return;
        }
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, QrWidget.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        RemoteViews views = build(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_qr);

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);

        List<HistoryStore.Entry> entries = HistoryStore.load(context);
        if (entries.isEmpty()) {
            views.setViewVisibility(R.id.widget_image, View.GONE);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            return views;
        }

        HistoryStore.Entry latest = entries.get(0);
        try {
            Bitmap bitmap = QrEngine.encode(latest.text,
                    QrCapacity.pickEcc(QrCapacity.ECC_M, QrCapacity.utf8Length(latest.text)),
                    QrStyle.foreground(context), QrStyle.background(context),
                    QrStyle.logo(context), QrEngine.WIDGET_SIZE);
            views.setViewVisibility(R.id.widget_image, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty, View.GONE);
            views.setImageViewBitmap(R.id.widget_image, bitmap);
        } catch (Exception e) {
            views.setViewVisibility(R.id.widget_image, View.GONE);
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
        }
        return views;
    }
}
