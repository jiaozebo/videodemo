package com.xtw.video;

import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUServerThread;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PUServerService extends Service {
	private PUServerThread mPUThread;

	public PUServerService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO: Return the communication channel to the service.
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
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
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onDestroy() {
		if (mPUThread != null) {
			mPUThread.quit();
			mPUThread = null;
		}
		super.onDestroy();
	}

}
