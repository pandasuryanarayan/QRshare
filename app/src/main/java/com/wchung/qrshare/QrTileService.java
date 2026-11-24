package com.wchung.qrshare;

import android.content.Intent;
import android.service.quicksettings.TileService;

/** Quick Settings tile that jumps straight into the scanner. */
public class QrTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra(ScanActivity.EXTRA_RETURN, false);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivityAndCollapse(intent);
        } catch (Exception e) {
            startActivity(intent);
        }
    }
}
