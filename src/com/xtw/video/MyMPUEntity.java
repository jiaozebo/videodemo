package com.xtw.video;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import util.DES;
import util.MD5;
import vastorager.StreamWriter;
import android.content.Context;
import android.os.Message;
import android.util.OAudioRunnable;
import c7.DC7;
import c7.IResType;
import c7.LoginInfo;
import c7.NC7;
import c7.PUParam;

import com.crearo.mpu.sdk.AudioRunnable;
import com.crearo.mpu.sdk.CameraThread;
import com.crearo.mpu.sdk.GPSHandler;
import com.crearo.mpu.sdk.PreviewThread;
import com.crearo.mpu.sdk.client.ErrorCode;
import com.crearo.mpu.sdk.client.MPUEntity;
import com.crearo.mpu.sdk.client.PUInfo;
import com.crearo.puserver.PUDataChannel;

public class MyMPUEntity extends MPUEntity {

	public MyMPUEntity(Context context) {
		super(context);
		mRecordDirPath = NPUApp.sROOT;
	}

	public NC7 getNC() {
		return sNc;
	}

	public int loginBlock(String addr, int port, boolean fixAddress, String password, PUInfo info)
			throws InterruptedException {
		LoginInfo li = new LoginInfo();
		li.addr = addr;
		li.port = port;
		li.isFixAddr = fixAddress;
		li.password = password;
		li.param = new PUParam();
		li.param.ProducerID = "00005";
		li.param.PUID = info.puid;
		li.param.DevID = info.puid.substring(3);
		li.param.HardwareVer = info.hardWareVer;
		li.param.SoftwareVer = info.softWareVer;
		li.param.puname = info.name;
		li.param.pudesc = info.name;
		li.param.mCamName = info.cameraName;
		li.param.mMicName = info.mMicName;
		li.param.mSpeakerName = info.mSpeakerName;
		li.param.mGPSName = info.mGPSName;

		sNc.setCallback(this);
		for (IResType type : IResType.values()) {
			type.mIsAlive = false;
		}
		li.binPswHash = MD5.encrypt(li.password.getBytes());
		int rst = sNc.create(li, 5000);
		if (rst != 0) {
			rst += ErrorCode.NC_OFFSET;
		} else {
			sNc.sendRpt(li.param);
			mDes = DES.getNativeInstance(sNc.getCryptKey());
		}
		return rst;

	}

	@Override
	protected int handleStartStream(LoginInfo info) {
		if (info.resType.mIsAlive && info.resType == IResType.IV) {
			DC7 dc = null;
			CameraThread pt = mCameraThread;
			if (pt != null) {
				dc = pt.getVideoDC();
			}
			if (dc != null) {
				dc.close();
			}
			info.resType.mIsAlive = false;
			// return ErrorCode.ERROR_REOURCE_IN_USE;
		}
		return super.handleStartStream(info);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.os.Handler#handleMessage(android.os.Message)
	 */
	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);
		if (msg.what == 0x3600 || msg.what == 0x3601) {// puserverthread
			int resType = msg.arg1;
			PUDataChannel pdc = (PUDataChannel) msg.obj;

			if (resType == 0) {// iv
				CameraThread cameraThread = mCameraThread;
				if (cameraThread != null)
					if (msg.what == 0x3600) {
						cameraThread.addPUDataChannel(pdc);
					} else {
						cameraThread.removePUDataChannel(pdc);
					}
			} else if (resType == 1) {// ia
				AudioRunnable ar = AudioRunnable.singleton();
				if (msg.what == 0x3600) {
					ar.addPUDataChannel(pdc);
					if (!ar.isActive()) {
						ar.start();
					}
				} else {
					ar.removePUDataChannel(pdc);
				}
			} else if (resType == 3) {
				GPSHandler gpsHandler = mGpsHandler;
				if (gpsHandler == null) {
					gpsHandler = new GPSHandler(mContext, null);
					mGpsHandler = gpsHandler;
				}
				if (msg.what == 0x3600) {
					gpsHandler.addPUDataChannel(pdc);
				} else {
					gpsHandler.removePUDataChannel(pdc);
				}
			}
		}
	}

	/**
	 * 登录服务器
	 * 
	 * @param addr
	 *            地址
	 * @param port
	 *            端口
	 * @param password
	 *            密码
	 * @param info
	 *            设备信息
	 * @return 0说明登录成功，否则为{@link ErrorCode 错误码}里的值。
	 */
	@Override
	public int login(String addr, int port, boolean fixAddr, String password, PUInfo info) {
		LoginInfo li = new LoginInfo();
		li.addr = addr;
		li.port = port;
		li.password = password;
		li.param = new PUParam();
		li.isFixAddr = fixAddr;
		li.param.ProducerID = "00005";
		li.param.PUID = info.puid;
		li.param.DevID = info.puid.substring(3);
		li.param.HardwareVer = info.hardWareVer;
		li.param.SoftwareVer = info.softWareVer;
		li.param.puname = info.name;
		li.param.pudesc = info.name;
		li.param.mCamName = info.cameraName;
		li.param.mMicName = info.mMicName;
		li.param.mSpeakerName = info.mSpeakerName;
		li.param.mGPSName = info.mGPSName;
		return login(li);
	}

	@Override
	public void logout() {
		mRecordCallback = null;
		mRendCallback = null;
		if (mDes != null) {
			mDes.nativeDestory();
			mDes = null;
		}
		// mSpeex.echo_destory();
		// 先把NC断了，这样就不会在关闭了dc后，NC又收到申请流命令了。
		sNc.close();
		if (mBackgroundVideoDC != null) {
			mBackgroundVideoDC.close();
			mBackgroundVideoDC = null;
		}
	}

	/**
	 * 使能音频
	 * 
	 * @param flag
	 *            0表示不使能；1表示使能输入音频（即使能发送音频）；2表示使能输出音频（即使能B端的音频）；其它表示两者都使能
	 */
	public void enableAudio(int flag) {
		OAudioRunnable oa = OAudioRunnable.singleton();
		AudioRunnable ia = AudioRunnable.singleton();
		switch (flag) {
		case 0:
			oa.pause();
			ia.pause();
			break;
		case 2:
			oa.resume();
			ia.pause();
			break;
		case 1:
			oa.pause();
			ia.resume();
			break;
		default:
			oa.resume();
			ia.resume();
			break;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.crearo.mpu.sdk.MPUHandler#startOrStopRecord()
	 */
	@Override
	public boolean startOrStopRecord() {
		final StreamWriter streamWriter = StreamWriter.singleton();
		CameraThread ct = mCameraThread;
		if (streamWriter.isActive()) {
			if (ct != null) {
				ct.setRecorder(null);
			}
			AudioRunnable.singleton().setRecorder(null);
			if (AudioRunnable.singleton().getDc() == null) {
				AudioRunnable.singleton().stop();
			}
			mPath = null;
			streamWriter.close();
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH.mm.ss", Locale.CHINA);
			mPath = String.format("%s/%s.avi", mRecordDirPath, sdf.format(new Date()));
			int recordIntervalInMin = 10;
			int nRet = streamWriter.create(mPath, recordIntervalInMin * 60 * 1000);
			streamWriter.setErrorCallback(new StreamWriter.IVErrorCallback() {

				@Override
				public void onErrorFetched(final int errorCode) {
					streamWriter.setErrorCallback(null);
					Runnable r = new Runnable() {

						@Override
						public void run() {
							startOrStopRecord();
							if (errorCode >= StreamWriter.CRSW_ERROR_CHGFILE_BAD_SEQUENCE
									&& errorCode <= StreamWriter.CRSW_ERROR_CHGFILE_EXCEED_INTERVAL) {
								startOrStopRecord();
							}
							RecordCallback rc = mRecordCallback;
							if (rc != null) {
								rc.onRecordStatusFetched(RecordCallback.STT_RECORD_END);
							}
						}
					};
					post(r);
				}
			});
			if (nRet == 0) {
				if (ct != null) {
					ct.setRecorder(streamWriter);
					if (ct instanceof PreviewThread) {
						DC7 dc = AudioRunnable.singleton().getDc();
						startIAWithDC(dc);
						AudioRunnable.singleton().setRecorder(streamWriter);
					}
				}
			}
			return nRet == 0;
		}
		return false;
	}

	public boolean isStarted() {
		return mCameraThread != null;
	}

	public void setFrameRate(int frameRate) {
		CameraThread ct = mCameraThread;
		if (ct != null) {
			ct.setFrameRate(frameRate);
		}
	}

}
