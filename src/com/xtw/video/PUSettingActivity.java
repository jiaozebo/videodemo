package com.xtw.video;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.StateListDrawable;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

public class PUSettingActivity extends Activity {
	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are shown
	 * as a master/detail two-pane view on tablets. When true, a single pane is
	 * shown on tablets.
	 */
	public static final String TAG = "PUSettingActivity";
	static final String PREVIEW_SIZE_VALUES = "PREVIEW_SIZE_VALUES";
	static final String PREVIEW_SIZE_VALUE = "PREVIEW_SIZE_VALUE";

	public static final String ADDRESS = "address";
	public static final String PORT = "port";

	private OnCheckedChangeListener mOnResolutionChangedListener;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.pu_setting_list);
		View quit = findViewById(R.id.btn_logout);
		quit.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				logoutAndFinish();
			}

		});
		RadioGroup group = (RadioGroup) findViewById(R.id.setting_preview_solutions);

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		String previewSizeStr = preferences.getString(PREVIEW_SIZE_VALUES, null);
		String previewSize = preferences.getString(PREVIEW_SIZE_VALUE, null);
		if (previewSize == null) {// 1920x1088,1280x720,960x720,800x480,768x432,720x480,640x480,576x432,480x320,384x288,352x288,320x240,240x160,176x144
			String[] out = new String[] { null, null };
			initCameraResolution(out);
			Editor editor = preferences.edit();
			editor.putString(PREVIEW_SIZE_VALUE, out[0]).putString(PREVIEW_SIZE_VALUES, out[1])
					.commit();
			previewSizeStr = out[1];
			previewSize = out[0];
		}
		if (previewSize != null) {
			mOnResolutionChangedListener = new OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if (isChecked) {
						String resolution = (String) buttonView.getText();
						PreferenceManager.getDefaultSharedPreferences(PUSettingActivity.this)
								.edit().putString(PREVIEW_SIZE_VALUE, resolution).commit();
					}
				}
			};
			String[] resolutions = previewSizeStr.split(",");

			float density = getResources().getDisplayMetrics().density;
			int dp10 = (int) (10 * density);
			int index = -1;
			for (int i = 0; i < resolutions.length; i++) {
				RadioButton rb = new RadioButton(this);
				rb.setOnCheckedChangeListener(mOnResolutionChangedListener);
				rb.setText(resolutions[i]);
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				// params.setMargins(horizontalMargin, verticalMargin,
				// horizontalMargin,
				// verticalMargin);
				rb.setLayoutParams(params);
				if ((index == -1) && previewSize.equals(resolutions[i])) {
					index = i;
				}
				StateListDrawable left = new StateListDrawable();
				rb.setGravity(Gravity.CENTER_VERTICAL);
				group.addView(rb);
			}
			if (index != -1) {
				RadioButton rb = (RadioButton) group.getChildAt(index + 1);// 要把第一个TextView跳过
				rb.setChecked(true);
			}
		}

		String saddr = preferences.getString(ADDRESS, null);
		EditText addr = (EditText) findViewById(R.id.server_addr);
		addr.setText(saddr);

		int iPort = preferences.getInt(PORT, 0);
		EditText port = (EditText) findViewById(R.id.server_port);
		port.setText(iPort == 0 ? null : String.valueOf(iPort));
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private void initCameraResolution(String[] out) {
		Camera c = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			c = Camera.open(0);
		} else {
			c = Camera.open();
		}
		String sizeStr = c.getParameters().get("preview-size-values");
		out[1] = sizeStr;
		// preferences.edit().putString(PUSettingActivity.PREVIEW_SIZE_VALUES,
		// sizeStr).commit();
		c.release();

		String[] resolutions = sizeStr.split(",");

		// 默认宽度最接近480的分辨率
		int dw = Integer.MAX_VALUE, index = -1;
		for (int i = 0; i < resolutions.length; i++) {
			Pattern p = Pattern.compile("\\d+");
			Matcher matcher = p.matcher(resolutions[i]);
			if (matcher.find()) {
				String width = matcher.group();
				int cdw = Math.abs(Integer.parseInt(width) - 480);
				if (cdw < dw) {
					dw = cdw;
					index = i;
				}
			}
		}
		if (index != -1) {
			out[0] = resolutions[index];
			// preferences.edit().putString(PUSettingActivity.PREVIEW_SIZE_VALUE,
			// previewSize)
			// .commit();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onBackPressed()
	 */
	@Override
	public void onBackPressed() {
		EditText port = (EditText) findViewById(R.id.server_port);
		EditText addr = (EditText) findViewById(R.id.server_addr);
		String address = addr.getText().toString();
		String sport = port.getText().toString();
		addr.setError(null);
		port.setError(null);
		if (TextUtils.isEmpty(address) || TextUtils.isEmpty(sport)) {
			ScrollView sv = (ScrollView) findViewById(R.id.pu_setting_list_root);
			sv.scrollTo(0, port.getTop());
			if (TextUtils.isEmpty(address)) {
				addr.setError("请输入正确的地址");
			} else {
				port.setError("请输入正确的端口");
			}
			return;
		}
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		preferences.edit().putString(ADDRESS, address).putInt(PORT, Integer.parseInt(sport))
				.commit();
		setResult(RESULT_OK);
		super.onBackPressed();
	}

	private void logoutAndFinish() {
		setResult(RESULT_FIRST_USER);
		finish();
	}
}
