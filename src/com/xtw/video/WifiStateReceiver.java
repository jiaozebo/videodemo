package com.xtw.video;

import java.util.Iterator;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crearo.config.Wifi;

public class WifiStateReceiver extends BroadcastReceiver {

	private static final String tag = "WifiStateReceiver";
	public static final String KEY_DEFAULT_SSID = "key_default_ssid";
	public static final String KEY_DEFAULT_SSID_PWD = "key_default_ssid_pwd";

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.e(tag, context.toString() + intent.toString());

		if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {// 这个监听wifi的打开与关闭，与wifi的连接无关
			int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
			int prevState = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, 0);
			if (prevState == WifiManager.WIFI_STATE_ENABLED
					&& wifiState != WifiManager.WIFI_STATE_ENABLED) {
				//
				// wifi 关闭了

				WifiManager mng = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				if (!Wifi.isWifiApEnabled(mng)) {
					NPUApp.stopPUServer(context);
				}
			} else if (prevState != WifiManager.WIFI_STATE_ENABLED
					&& wifiState == WifiManager.WIFI_STATE_ENABLED) {
				// wifi 开启了

			}
		}
		//
		// //
		// 这个监听wifi的连接状态即是否连上了一个有效无线路由，当上边广播的状态是WifiManager.WIFI_STATE_DISABLING，和WIFI_STATE_DISABLED的时候，根本不会接到这个广播。
		// //
		// 在上边广播接到广播是WifiManager.WIFI_STATE_ENABLED状态的同时也会接到这个广播，当然刚打开wifi肯定还没有连接到有效的无线
		if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) { // 这时候通知底层关闭或者开启3G
			Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

			if (null != parcelableExtra) {
				NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;

				State state = networkInfo.getState();
				// 关闭3G
				if (state == State.CONNECTED) {// WIFI 连接，关闭3G
					String ssid = null;
					if (VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						WifiInfo w = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
						ssid = w.getSSID();
					} else {
						ssid = Wifi.getCurrentSSID(context);
					}
					boolean needChangeWifi = false;
					// 自动连接一个默认的WIFI.
					String defaultSSID = PreferenceManager.getDefaultSharedPreferences(context)
							.getString(KEY_DEFAULT_SSID, "123456");
					if (defaultSSID.equals(ssid)
							|| String.format("\"%s\"", defaultSSID).equals(ssid)) {
					} else {
						String defaultPwd = PreferenceManager.getDefaultSharedPreferences(context)
								.getString(KEY_DEFAULT_SSID_PWD, null);
						if ("123456".equals(defaultSSID)) {
							defaultPwd = "58894436";
						}
						Iterable<ScanResult> configuredNetworks = Wifi
								.getConfiguredNetworks(context);
						if (configuredNetworks != null) {
							Iterator<ScanResult> it = configuredNetworks.iterator();
							while (it.hasNext()) {
								ScanResult cfg = (ScanResult) it.next();
								String sSID = cfg.SSID;
								if (sSID.startsWith("\"")) {
									sSID = sSID.substring(1);
								}
								if (sSID.endsWith("\"")) {
									sSID = sSID.substring(0, sSID.length() - 1);
								}

								if (sSID.equals(defaultSSID)) {
									// 连接该wifi
									Wifi.connectWifi(context, sSID, defaultPwd);
									needChangeWifi = true;
									break;
								}
							}
						}
					}
					if (!needChangeWifi) {
						NPUApp.startServer(context);
					}
				} else { // WIFI关闭
					NPUApp.stopPUServer(context);
				}
			}
		}
		//
		// // 这个监听网络连接的设置，包括wifi和移动数据的打开和关闭。.
		// // 最好用的还是这个监听。wifi如果打开，关闭，以及连接上可用的连接都会接到监听。见log
		// // 这个广播的最大弊端是比上边两个广播的反应要慢，如果只是要监听wifi，我觉得还是用上边两个配合比较合适
		// else if
		// (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
		// {
		// }
	}
}
