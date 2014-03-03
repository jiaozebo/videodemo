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
import c7.PUParam;

import com.crearo.config.StorageOptions;
import com.crearo.mpu.sdk.Common;
import com.crearo.mpu.sdk.client.PUInfo;

public class NPUApp extends Application {

	static PUInfo sInfo;
	static MyMPUEntity sEntity;
	static String sROOT;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Application#onCreate()
	 */
	@Override
	public void onCreate() {
		super.onCreate();

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
		try {
			new ConfigServer(this, "").start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {

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
		});
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

}
