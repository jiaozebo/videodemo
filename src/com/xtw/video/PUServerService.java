package com.xtw.video;

import java.util.Iterator;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.crearo.config.Wifi;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUServerThread;

public class PUServerService extends Service {
	private PUServerThread mPUThread;

	private Thread mWatchThread;
	public static final String TAG = "WATCH_DOG";
	/**
	 * 外界可以发该命令。(比如wifi更改时)使watchdog马上查询一次wifi状态
	 */
	public static final String EXTRA_CHECK_WIFI_NOW = "extra_check_wifi_now";

	public PUServerService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int result = super.onStartCommand(intent, flags, startId);
		if (intent == null) {
			return result;
		}
		boolean queryNow = intent.getBooleanExtra(EXTRA_CHECK_WIFI_NOW, false);
		if (queryNow && mWatchThread != null) {
			mWatchThread.interrupt();
		}
		PUInfo info = new PUInfo();
		info.puid = NPUApp.sInfo.puid;
		info.name = "audio";
		info.cameraName = "camera";
		info.mMicName = "camera";
		info.mSpeakerName = null;
		info.mGPSName = null;
		mPUThread = new PUServerThread(this, info, 8866);
		mPUThread.start();
		mPUThread.setCallbackHandler(NPUApp.sEntity);
		if (mWatchThread == null) {

			mWatchThread = new Thread("WATCHER") {

				@Override
				public void run() {
					String prevValid = null;
					android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					while (mWatchThread != null) {
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
						final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
						if (connectionInfo == null) {// unavailable
							Log.d(TAG, "wifi unavailable");
							continue;
						}
						// wifi可用
						boolean equalDefault = false, validWifi = false;

						String ssid = connectionInfo.getSSID();

						if (connectionInfo.getNetworkId() != -1 && connectionInfo.getLinkSpeed() != -1 && !TextUtils.isEmpty(ssid)) { // valid
							// wifi
							if (ssid.startsWith("\"")) {
								ssid = ssid.substring(1);
							}
							if (ssid.endsWith("\"")) {
								ssid = ssid.substring(0, ssid.length() - 1);
							}
							if (NPUApp.DEFAULT_SSID.equals(ssid)) {
								equalDefault = true;
							}

							validWifi = true;
						}
						if (equalDefault) { // same as default
							Log.d(TAG, "wifi same as default");
							continue;
						}

						// 非默认wifi
						// save current valid(not default) wifi as prev wifi
						if (validWifi) {
							prevValid = ssid;
						}
						Log.d(TAG, "wifi " + (validWifi ? ("valid," + ssid) : "invalid"));

						Iterable<ScanResult> iterable = Wifi.getConfiguredNetworks(PUServerService.this);
						if (iterable == null) {
							Log.d(TAG, "wifi scan result null");
							continue;
						}
						// 有扫描到wifi
						boolean foundDefault = false;
						Iterator<ScanResult> it = iterable.iterator();
						while (it.hasNext()) {
							ScanResult cfg = (ScanResult) it.next();
							String sSID = cfg.SSID;
							if (sSID.startsWith("\"")) {
								sSID = sSID.substring(1);
							}
							if (sSID.endsWith("\"")) {
								sSID = sSID.substring(0, sSID.length() - 1);
							}

							if (sSID.equals(NPUApp.DEFAULT_SSID)) {
								PreferenceManager.getDefaultSharedPreferences(PUServerService.this).edit().putString(WifiStateReceiver.KEY_DEFAULT_SSID, NPUApp.DEFAULT_SSID)
										.putString(WifiStateReceiver.KEY_DEFAULT_SSID_PWD, NPUApp.DEFAULT_SSID_PWD).commit();
								Wifi.connectWifi(PUServerService.this, NPUApp.DEFAULT_SSID, NPUApp.DEFAULT_SSID_PWD);
								foundDefault = true;
								Log.d(TAG, "we found and try connect default wifi!!!");
								break;
							}
						}
						if (!foundDefault) {
							Log.d(TAG, "default wifi no found.");
							if (!validWifi) {
								Log.d(TAG, "current wifi invalid,try to reconnect old wifi : " + prevValid);
								if (!TextUtils.isEmpty(prevValid)) {
									Wifi.connectWifi(PUServerService.this, prevValid, null);
									prevValid = null;
								} else {
									// we can't do noting,wait for default
								}
							}
						}
					}
				}

			};
			mWatchThread.start();
		}
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		if (mPUThread != null) {
			mPUThread.quit();
			mPUThread = null;
		}
		Thread thread = mWatchThread;
		if (thread != null) {
			mWatchThread = null;
			thread.interrupt();
			try {
				thread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		super.onDestroy();
	}

}
