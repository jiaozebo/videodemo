package com.xtw.video;

import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

import com.crearo.config.Connectivity;
import com.crearo.mpu.sdk.client.VideoParam;
import com.crearo.puserver.PUServerThread;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

	private static final int REQUEST_PARAM = 0x1000;
	protected static final String tag = "MainActivity";
	protected static final String KEY_QUIT = "key_quit";
	private int mWidth, mHeight;
	SurfaceView mSurface;
	// MyMPUEntity mEntity;
	protected VideoParam mVideoParam;
	private String mAddress;
	private int mPort;
	private boolean mQuit;

	private Toast mQuitTipToast = null;
	private Runnable mResetQuitFlagRunnable = null;
	/**
	 * 不为null说明可以切换镜头
	 */
	private Button mSwitchCameraButton;

	private boolean mRecordStart;

	@SuppressLint("ShowToast")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		if (getIntent().getBooleanExtra(KEY_QUIT, false)) {
			finish();
			return;
		}
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

		mQuitTipToast = Toast.makeText(this, "再按一次退出", Toast.LENGTH_LONG);
		mResetQuitFlagRunnable = new Runnable() {

			@Override
			public void run() {
				mQuit = false;
				mQuitTipToast.cancel();
			}
		};

		if (initParams()) {
			startWithParamValid();
			tryEnableMobileData();
			if (mPort != 0) {
				NCIntentService.startNC(this, mAddress, mPort);
			}
		} else {
			Intent i = new Intent(this, PUSettingActivity.class);
			startActivityForResult(i, REQUEST_PARAM);
		}
	}

	private void tryEnableMobileData() {
		if (!Connectivity.isMobileDataEnabled((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))) {
			Connectivity.setMobileDataEnable((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE), true);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (intent.getBooleanExtra(KEY_QUIT, false)) {
			finish();
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private void startWithParamValid() {
		setContentView(R.layout.activity_main);
		mSwitchCameraButton = (Button) findViewById(R.id.switch_camera);
		if (VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			ViewGroup parent = (ViewGroup) mSwitchCameraButton.getParent();
			parent.removeView(mSwitchCameraButton);
			mSwitchCameraButton = null;
		} else if (Camera.getNumberOfCameras() < 2) {
			ViewGroup parent = (ViewGroup) mSwitchCameraButton.getParent();
			parent.removeView(mSwitchCameraButton);
			mSwitchCameraButton = null;
		} else {
			mSwitchCameraButton.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
					int newId = 1 - preferences.getInt(VideoParam.KEY_INT_CAMERA_ID, 0);
					preferences.edit().putInt(VideoParam.KEY_INT_CAMERA_ID, newId).commit();
				}
			});
		}

		mSurface = (SurfaceView) findViewById(R.id.fake_render);

		fixSurfaceRatio();
		mVideoParam = new VideoParam();
		mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_WIDTH, mWidth);
		mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_HEIGHT, mHeight);
		mVideoParam.putParam(VideoParam.KEY_BOOLEAN_ENCODE_COMPATIBILITY, !(isS4() || isOmate() || isS3()));
		mVideoParam.putParam(VideoParam.KEY_INT_FRAME_RATE, 8);
		int id = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).getInt(VideoParam.KEY_INT_CAMERA_ID, 0);
		mVideoParam.putParam(VideoParam.KEY_INT_CAMERA_ID, id);
		final Callback callback = new Callback() {

			@Override
			public void surfaceDestroyed(final SurfaceHolder holder) {
				MyMPUEntity entity = NPUApp.sEntity;
				if (entity == null) {
					return;
				}
				if (mRecordStart) {
					entity.startOrStopRecord();
					mRecordStart = false;
				}
				entity.stop();
			}

			@Override
			public void surfaceCreated(final SurfaceHolder holder) {
				MyMPUEntity entity = NPUApp.sEntity;
				if (entity == null) {
					return;
				}
				entity.start(mSurface, mVideoParam);
				mRecordStart = entity.startOrStopRecord();
			}

			@Override
			public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
			}
		};
		mSurface.getHolder().addCallback(callback);

		Button ipBtn = (Button) findViewById(R.id.ips);
		ipBtn.setText("本机IP： ");
		List<String> ips = PUServerThread.getAllIpAddress();
		for (Iterator<String> iterator = ips.iterator(); iterator.hasNext();) {
			String ip = (String) iterator.next();
			ipBtn.append("\n" + ip);
		}
	}

	private void fixSurfaceRatio() {
		final Rect outRect = new Rect();
		getWindow().getDecorView().getWindowVisibleDisplayFrame(outRect);

		final float ratioW = (float) mWidth / (float) outRect.width();
		final float ratioH = (float) mHeight / (float) outRect.height();
		final LayoutParams params = (LayoutParams) mSurface.getLayoutParams();
		if (ratioW > ratioH) {// 比率更高的更改为view的尺寸
			params.height = (int) (mHeight / ratioW);
			params.width = outRect.width();
		} else {
			params.height = outRect.height();
			params.width = (int) (mWidth / ratioH);
		}
		mSurface.requestLayout();
	}

	private boolean isOmate() {
		return Build.MODEL.equals("OMATE");
	}

	private boolean isS4() {
		return Build.MODEL.equals("SCH-I959") || Build.MODEL.equals("GT-I9500") || Build.MODEL.equals("SCH-I9502") || Build.MODEL.equals("SCH-I9508")
				|| Build.MODEL.equals("GT-I9508V") || Build.MODEL.equals("Nexus 7");
	}

	private boolean isS3() {
		return Build.MODEL.equals("SCH-I939D");
	}

	private boolean initParams() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String previewSize = preferences.getString(PUSettingActivity.PREVIEW_SIZE_VALUE, null);
		mAddress = preferences.getString(PUSettingActivity.ADDRESS, null);
		mPort = preferences.getInt(PUSettingActivity.PORT, -1);
		if (previewSize == null || mPort == -1 || TextUtils.isEmpty(mAddress)) {
			return false;
		} else {
			int pos = previewSize.indexOf('x');
			if (pos != -1) {
				String width = previewSize.substring(0, pos);
				String height = previewSize.substring(pos + 1);
				mWidth = Integer.parseInt(width);
				mHeight = Integer.parseInt(height);
			}
			return true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_settings) {
			Intent i = new Intent(this, PUSettingActivity.class);
			startActivity(i);
		}
		return super.onOptionsItemSelected(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_PARAM) {
			if (initParams()) {
				startWithParamValid();
				tryEnableMobileData();
			} else {
				finish();
			}
		}
	}

	@Override
	public void onBackPressed() {
		MyMPUEntity entity = NPUApp.sEntity;
		if (entity == null) {
			super.onBackPressed();
		}
		if (mQuit) {
			entity.removeCallbacks(mResetQuitFlagRunnable);
			mResetQuitFlagRunnable.run();
			super.onBackPressed();
		} else {
			mQuit = true;
			mQuitTipToast.show();
			entity.postDelayed(mResetQuitFlagRunnable, 1500);
		}
	}

	@Override
	protected void onDestroy() {
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
		mResetQuitFlagRunnable = null;
		mQuitTipToast = null;
		if (mVideoParam != null) {
			Editor edit = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
			edit.putInt(VideoParam.KEY_INT_CAMERA_ID, mVideoParam.getIntParam(VideoParam.KEY_INT_CAMERA_ID, 0)).commit();
			mVideoParam = null;
		}
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(VideoParam.KEY_INT_CAMERA_ID) && mSwitchCameraButton != null) {
			int newId = sharedPreferences.getInt(key, 0);
			MyMPUEntity entity = NPUApp.sEntity;
			if (entity != null) {
				entity.switchCamera(newId, mVideoParam);
				mVideoParam.putParam(VideoParam.KEY_INT_CAMERA_ID, newId);
			}
		} else if (key.equals(VideoParam.KEY_INT_FRAME_RATE)) {
			mVideoParam.putParam(VideoParam.KEY_INT_FRAME_RATE, sharedPreferences.getInt(key, 8));
		} else if (key.equals(PUSettingActivity.PREVIEW_SIZE_VALUE)) {
			String[] size = sharedPreferences.getString(key, "352x288").split("x");
			if (size != null && size.length == 2) {
				try {

					int w = Integer.parseInt(size[0]);
					int h = Integer.parseInt(size[1]);
					mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_WIDTH, w);
					mVideoParam.putParam(VideoParam.KEY_INT_PREVIEW_HEIGHT, h);
					MyMPUEntity entity = NPUApp.sEntity;
					if (entity != null) {
						if (entity.isStarted()) {
							if (mRecordStart) {
								entity.startOrStopRecord();
								mRecordStart = false;
							}
							entity.stop();
							entity.start(mSurface, mVideoParam);
							mRecordStart = entity.startOrStopRecord();
						}
					}
					mWidth = w;
					mHeight = h;
				} catch (Exception e) {
					// TODO: handle exception
				}

			}
		}
	}

}
