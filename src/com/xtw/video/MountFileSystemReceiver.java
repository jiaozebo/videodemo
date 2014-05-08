package com.xtw.video;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

public class MountFileSystemReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent service = new Intent(context, WifiAndPuServerService.class);
		if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
			NPUApp.initRootPath();
			if (TextUtils.isEmpty(NPUApp.sROOT)) {
				Toast.makeText(context, "初始化存储路径失败！", Toast.LENGTH_LONG).show();
				return;
			}
			context.startService(service);
		} else {
		}
	}
}
