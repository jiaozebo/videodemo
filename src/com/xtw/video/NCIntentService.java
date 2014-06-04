package com.xtw.video;

import util.E;
import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import c7.NC7;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class NCIntentService extends Service {
	// TODO: Rename actions, choose action names that describe tasks that this
	// IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
	public static final String ACTION_START_NC = "com.xtw.msrd.action.FOO";

	// TODO: Rename parameters
	private static final String EXTRA_PARAM1 = "com.xtw.msrd.extra.PARAM1";
	private static final String EXTRA_PARAM2 = "com.xtw.msrd.extra.PARAM2";

	/**
	 * Starts this service to perform action Foo with the given parameters. If
	 * the service is already performing a task this action will be queued.
	 * 
	 * @see IntentService
	 */
	// TODO: Customize helper method

	private volatile Thread mNCThread;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		final String action = intent.getAction();
		if (ACTION_START_NC.equals(action)) {
			if ((mNCThread != null)) {
				return START_REDELIVER_INTENT;
			}
			final String param1 = intent.getStringExtra(EXTRA_PARAM1);
			final int port = intent.getIntExtra(EXTRA_PARAM2, 0);
			if (port != 0) {
				mNCThread = new Thread("NC") {

					@Override
					public void run() {
						MyMPUEntity entity = NPUApp.sEntity;
						Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
						try {
							int r = 0;
							do {
								LocalBroadcastManager.getInstance(NCIntentService.this)
										.sendBroadcast(new Intent(ACTION_START_NC));
								if (entity == null) {
									return;
								}
								r = entity.loginBlock(param1, port, true, "", NPUApp.sInfo);
								if (r == 0) {
									LocalBroadcastManager.getInstance(NCIntentService.this)
											.sendBroadcast(new Intent(ACTION_START_NC));
									NC7 nc7 = entity.getNC();
									while (mNCThread != null) {
										// @return 0表示成功；2表示成功接收或发送了数据；其它为错误码
										int nRet = nc7.loop();
										if (nRet == E.SUCCESS) {
											try {
												Thread.sleep(50);
											} catch (InterruptedException e) {
												e.printStackTrace();
											}
										} else if (nRet != 2) {
											break;
										}
									}
								} else {
									LocalBroadcastManager.getInstance(NCIntentService.this)
											.sendBroadcast(new Intent(ACTION_START_NC));
									Thread.sleep(5000);
								}
							} while (mNCThread != null);
						} catch (InterruptedException e) {
							e.printStackTrace();
						} finally {
							if (entity != null) {
								entity.logout();
							}
							LocalBroadcastManager.getInstance(NCIntentService.this).sendBroadcast(
									new Intent(ACTION_START_NC));
						}
					}

				};
				mNCThread.start();
			}
		}
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		Thread t = mNCThread;
		if (t != null) {
			mNCThread = null;
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.onDestroy();
	}

	public static void startNC(Context context, String address, int port) {
		Intent intent = new Intent(context, NCIntentService.class);
		intent.setAction(ACTION_START_NC);
		intent.putExtra(EXTRA_PARAM1, address);
		intent.putExtra(EXTRA_PARAM2, port);
		context.startService(intent);
	}

	public static void stopNC(Context context) {
		Intent intent = new Intent(context, NCIntentService.class);
		context.stopService(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
}
