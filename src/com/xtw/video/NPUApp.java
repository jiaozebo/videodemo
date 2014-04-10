package com.xtw.video;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;

import util.CommonMethod;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import c7.PUParam;

import com.crearo.config.StorageOptions;
import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUServerThread;

public class NPUApp extends Application {

	static PUInfo sInfo;
	private static MyMPUEntity sEntity;
	static String sROOT;

	private static PUServerThread mServer;
	private static ConfigServer sConfigServer;
	public static boolean sUseAsAp = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		try {
			PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
			sUseAsAp = pi.versionName.contains("ap");
		} catch (NameNotFoundException e3) {
			e3.printStackTrace();
		}
		initRootPath();
		PUParam p = new PUParam();
		Common.initParam(p, this);
		sInfo = new PUInfo();
		sInfo.cameraName = p.mCamName;
		sInfo.mGPSName = null;
		sInfo.mMicName = p.mMicName;
		sInfo.mSpeakerName = null;
		sInfo.name = p.puname;
		sInfo.puid = p.PUID;
		sInfo.hardWareVer = p.HardwareVer;
		sInfo.softWareVer = p.SoftwareVer;
		UncaughtExceptionHandler handler = new UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(Thread arg0, Throwable e) {
				e.printStackTrace();

				File file = new File(sROOT, "/log.txt");
				if (CommonMethod.createFile(file)) {
					PrintStream err;
					OutputStream os;
					try {
						java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(
								"yy-MM-dd HH.mm.ss");
						String time = formatter.format(new java.util.Date());
						os = new FileOutputStream(file, true);
						err = new PrintStream(os);
						err.append(time);
						e.printStackTrace(err);
						os.close();
						err.close();
					} catch (FileNotFoundException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} catch (IOException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
					}
				}
				android.os.Process.killProcess(android.os.Process.myPid());
				System.exit(10);
			}
		};
		// Thread.setDefaultUncaughtExceptionHandler(handler);
		startService(new Intent(this, WifiAndPuServerService.class));
	}

	public static void initRootPath() {
		StorageOptions.determineStorageOptions();
		String[] paths = StorageOptions.paths;
		if (paths == null || paths.length == 0) {
			return;
		}
		// 使用版本号作为设备的名称
		sROOT = String.format("%s/%s", paths[0], "NPURecord");

		File f = new File(sROOT);
		f.mkdirs();
	}

	public static void setEntity(MyMPUEntity entity) {
		if (mServer != null) {
			mServer.setCallbackHandler(entity);
		}
		sEntity = entity;
	}

	public static PUServerThread getServer() {
		return mServer;
	}

	public static void startServer(Context context) {
		if (mServer == null) {
			PUServerThread p = new PUServerThread(context, NPUApp.sInfo, 8888);
			p.start();
			p.setCallbackHandler(sEntity);
			mServer = p;
		}
		if (sConfigServer == null) {
			try {
				sConfigServer = new ConfigServer(context, "");
				sConfigServer.start();
			} catch (IOException e) {
				sConfigServer = null;
				e.printStackTrace();
			}
		}

	}

	public static void stopPUServer(Context context) {
		if (mServer != null) {
			mServer.setCallbackHandler(null);
			mServer.quit();
			mServer = null;
		}
		if (sConfigServer != null) {
			sConfigServer.stop();
			sConfigServer = null;
		}
	}

}
