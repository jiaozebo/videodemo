package com.xtw.video;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.ScanResult;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.crearo.config.NanoHTTPD;
import com.crearo.config.NanoHTTPD.Response.Status;
import com.crearo.config.Wifi;
import com.crearo.mpu.sdk.client.VideoParam;
import com.crearo.puserver.PUCommandChannel;

public class ConfigServer extends NanoHTTPD {
	private String mRoot;
	private Context mContext;

	/**
	 * Constructs an HTTP server on given port.
	 * 
	 * @throws IOException
	 */
	public ConfigServer(Context c, String fileRoot) throws IOException {
		super(8080);
		mContext = c;
		mRoot = fileRoot;
	}

	@Override
	public Response serve(String uri, Method method, Map<String, String> header,
			Map<String, String> parms, Map<String, String> files) {
		if (uri.equals("/")) {
			if (parms.containsKey("address")) {
				StringBuilder sb = new StringBuilder();
				String addr = parms.get("address");
				String port = parms.get("port");
				boolean highQuality = !TextUtils.isEmpty(parms.get("quality"));
				int nPort = -1;
				if (TextUtils.isEmpty(addr) || TextUtils.isEmpty(port)) {
					sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>配置参数不合法。</body></html>");
				} else {
					try {
						nPort = Integer.parseInt(port);
					} catch (NumberFormatException e) {
					}
					if (nPort == -1) {
						sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>端口不合法</body></html>");
					} else {
						Editor edit = PreferenceManager.getDefaultSharedPreferences(mContext)
								.edit();
						edit.putString(PUSettingActivity.ADDRESS, addr)
								.putInt(PUSettingActivity.PORT, nPort).commit();
						sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=/' />\n<body>参数修改成功!</body></html>");
					}
				}
				return new Response(sb.toString());
			} else if (parms.containsKey("login") || parms.containsKey("query_login_status")) {
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body>");
				sb.append("<br /><a href='?query_login_status=1'>查询登录状态</a><hr /></body></html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("logout")) {
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=' />\n<body>已退出登录.</body></html>");
				return new Response(sb.toString());
			} else if (parms.containsKey("list_files")) {
				return serveFile(uri, header, mRoot, true);
			} else if (parms.containsKey("list_allfiles_xml")) {
				String xml = null;
				try {
					DocumentBuilder builder = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder();
					Document doc = builder.newDocument();
					File rootFile = new File(mRoot);
					Element root = doc.createElement("File");
					addFiles2Node(rootFile, root, doc);
					doc.appendChild(root);
					xml = PUCommandChannel.node2String(doc);
				} catch (ParserConfigurationException e) {
					e.printStackTrace();
					xml = "error: " + e.getMessage();
				} catch (TransformerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					xml = "error: " + e.getMessage();
				}
				return new Response(Status.OK, "text/xml", xml);
			} else if (parms.containsKey("wifi")) {
				final String ssid = parms.get("ssid");
				final String pwd = parms.get("password");
				new Thread() {
					@Override
					public void run() {
						Wifi.connectWifi(mContext, ssid, pwd);
						super.run();
					}

				}.start();
				PreferenceManager.getDefaultSharedPreferences(mContext).edit()
						.putString(WifiStateReceiver.KEY_DEFAULT_SSID, ssid)
						.putString(WifiStateReceiver.KEY_DEFAULT_SSID_PWD, pwd).commit();
				return new Response(
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=' />\n<body>正在处理...请切换至新的WIFI再连接。</body></html>");
			} else if (parms.containsKey("camera")) {
				int id = 0;
				if ("back".equals(parms.get("camera"))) {
					id = 0;
				} else {
					id = 1;
				}
				SharedPreferences preferences = PreferenceManager
						.getDefaultSharedPreferences(mContext);
				preferences.edit().putInt(VideoParam.KEY_INT_CAMERA_ID, id).commit();
				StringBuilder sb = new StringBuilder();
				sb.append("<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body>");
				sb.append(String.format("已切换为%s.", id == 0 ? "后置" : "前置"));
				sb.append("</body></html>");
				return new Response(sb.toString());
			}
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
			StringBuilder sb = new StringBuilder();
			sb.append("<html xmlns='http://www.w3.org/1999/xhtml'>\n");
			sb.append("<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n");
			sb.append("\t<body>\n");
			sb.append("\t\t<h1 align='center'>配置与管理</h1>\n");
			sb.append("\t\t\t<form  method='get' action=''>\n");
			sb.append(String.format(
					"\t\t\t\tAddress: <input type='text' name='address' value='%s'/>\n",
					preferences.getString(PUSettingActivity.ADDRESS, "")));
			sb.append(String.format(
					"\t\t\t\tPort: <input type='number' name='port' value='%d'/><br />\n",
					preferences.getInt(PUSettingActivity.PORT, 0)));
			sb.append("\t\t\t\t<input type='submit' value='配置' />\n");
			sb.append("\t\t\t</form>\n");

			sb.append("<form method='get' action=''>\n");
			int cid = preferences.getInt(VideoParam.KEY_INT_CAMERA_ID, 0);
			if (cid == 0) {
				sb.append("<input name='camera' value='back' type='radio' checked='1'>后置</input>\n");
				sb.append("<input name='camera' value='front' type='radio' >前置<br />\n");
			} else {
				sb.append("<input name='camera' value='back' type='radio' >后置</input>\n");
				sb.append("<input name='camera' value='front' type='radio' checked='1'>前置<br />\n");
			}
			sb.append("<input type='submit' value='配置' />\n");
			sb.append("</form>\n");

			String cSSID = Wifi.getCurrentSSID(mContext);
			Iterable<ScanResult> configuredNetworks = Wifi.getConfiguredNetworks(mContext);
			if (configuredNetworks != null) {
				sb.append("\t\t\t<form method='post' action=''>\n");
				sb.append("\t\t<h3 style='margin:0;padding:0'>周边WIFI接入点名称:</h3>\n");
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
					sb.append(String.format("%s(%d)", sSID, 100 + cfg.level));
					sb.append("<input type='radio' name='ssid' ");
					String valueString = String.format("value='%s'", sSID);
					sb.append(valueString);
					if (cSSID.equals(sSID) || cSSID.equals(String.format("\"%s\"", sSID))) {
						sb.append("checked='checked'");
					}
					sb.append("/><br />");
				}
				sb.append("\t\t\t\t密码: <input type='password' name='password' value=''/>\n");
				sb.append("\t\t\t\t<input type='submit' name='wifi' value='连接'>\n");
				sb.append("\t\t\t</form>\n");
			}

			sb.append("\t</body>\n");
			sb.append("</html>\n");

			return new Response(sb.toString());
		} else {
			if (parms.containsKey("delete")) {
				File f = new File(mRoot, uri);
				if (f.isDirectory()) {
					deleteDir(f);
				} else {
					f.delete();
				}
				String parentUri = uri.subSequence(0, uri.lastIndexOf("/")).toString();
				if (parentUri.equals("")) {
					parentUri = "/?list_files=1";
				}
				return new Response(
						String.format(
								"<html><meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='1;url=%s' />\n<body>%s%s.</body></html>",
								parentUri, f.getName(), f.exists() ? "未能删除" : "已删除"));
			} else if (parms.containsKey("download_all")) {
				uri = uri.substring(0, uri.lastIndexOf(".zip"));
				File f = new File(mRoot, uri);
				if (f.isDirectory()) {
					try {
						zipDir(f.getPath(), null);
						final String zip = uri + ".zip";
						Response r = serveFile(zip, header, mRoot, true);
						r.callback = new Runnable() {

							@Override
							public void run() {
								new File(mRoot, zip).delete();
							}
						};
						return r;
					} catch (IOException e) {
						e.printStackTrace();
						String parentUri = uri.subSequence(0, uri.lastIndexOf("/")).toString();
						if (parentUri.equals("")) {
							parentUri = "/?list_files=1";
						}
						return new Response(
								String.format(
										"<html><meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<meta http-equiv='refresh' content='3;url=%s' />\n<body>%s(%s).</body></html>",
										parentUri, "下载失败", e.getMessage()));
					}
				} else {
					return serveFile(uri, header, mRoot, true);
				}
			} else if (parms.containsKey("download_and_delete")) {
				Response r = serveFile(uri, header, mRoot, true);
				final String furi = uri;
				r.callback = new Runnable() {

					@Override
					public void run() {
						File file = new File(mRoot, furi);
						file.delete();
					}
				};
				return r;
			} else {
				return serveFile(uri, header, mRoot, true);
			}
		}
	}

	private void addFiles2Node(File rootFile, Node root, Document doc) {
		File[] fils = rootFile.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory() || pathname.getPath().endsWith(".aac")) {
					return true;
				}
				return false;
			}
		});
		for (int i = 0; i < fils.length; i++) {
			File f = fils[i];
			if (f.isDirectory()) {
				Element subRoot = doc.createElement("File");
				subRoot.setAttribute("Name", f.getName());
				addFiles2Node(f, subRoot, doc);
				root.appendChild(subRoot);
			} else {
				Element fnode = doc.createElement("File");
				fnode.setAttribute("Name", f.getName());
				root.appendChild(fnode);
			}
		}
	}

	/**
	 * 
	 * 
	 * @param file
	 * @return
	 */
	private String file2Msg(File file) {
		String length = "";
		String name = file.getName();
		if (file.isDirectory()) {
			length = "/";
		} else {
			long len = file.length();
			if (len < 1024)
				length += len + " bytes";
			else if (len < 1024 * 1024)
				length += len / 1024 + "." + (len % 1024 / 10 % 100) + " KB";
			else
				length += len / (1024 * 1024) + "." + len % (1024 * 1024) / 10 % 100 + " MB";
			length = String.format("&nbsp(%s)", length);
		}
		String result = String
				.format("<a href='%s?open_or_download=1'>%s</a>", name, name + length);
		if (file.isDirectory()) {
			result += String
					.format("&nbsp&nbsp&nbsp<a href='%s.zip?download_all=1'>打包下载</a>", name);
		}
		result += String.format("&nbsp&nbsp&nbsp<a href='%s?delete=1'>删除</a><br>\n", name);
		return result;
	}

	/**
	 * 如果是"/"，那么一定要加上list_files参数，才会列出文件 Serves file from homeDir and its'
	 * subdirectories (only). Uses only URI, ignores all headers and HTTP
	 * parameters.
	 */
	public Response serveFile(String uri, Map<String, String> header, String root,
			boolean allowDirectoryListing) {
		Response res = null;
		File homeDir = new File(root);
		// Make sure we won't die of an exception later
		if (!homeDir.isDirectory())
			res = new Response(Status.INTERNAL_ERROR, MIME_PLAINTEXT,
					"INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");

		if (res == null) {
			// Remove URL arguments
			uri = uri.trim().replace(File.separatorChar, '/');
			if (uri.indexOf('?') >= 0)
				uri = uri.substring(0, uri.indexOf('?'));

			// Prohibit getting out of current directory
			if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
				res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT,
						"FORBIDDEN: Won't serve ../ for security reasons.");
		}

		File f = new File(homeDir, uri);
		if (res == null && !f.exists())
			res = new Response(Status.NOT_FOUND, MIME_PLAINTEXT, "Error 404, file not found.");

		// List the directory, if necessary
		if (res == null && f.isDirectory()) {
			// Browsers get confused without '/' after the
			// directory, send a redirect.
			if (!uri.endsWith("/")) {
				uri += "/";
				res = new Response(
						Status.REDIRECT,
						MIME_HTML,
						"<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body>Redirected: <a href=\""
								+ uri + "\">" + uri + "</a></body></html>");
				res.addHeader("Location", uri);
			}

			if (res == null) {
				// First try index.html and index.htm
				if (new File(f, "index.html").exists())
					f = new File(homeDir, uri + "/index.html");
				else if (new File(f, "index.htm").exists())
					f = new File(homeDir, uri + "/index.htm");
				// No index file, list the directory if it is readable
				else if (allowDirectoryListing && f.canRead()) {
					String[] files = f.list();
					String msg = "<html>\n<meta http-equiv='Content-Type' content='text/html; charset=utf-8' />\n<body><h1>Directory "
							+ uri + "</h1><br/>\n";
					// msg += "<form  action='' method='get'>\n";
					// msg += "<fieldset>\n<legend>文件信息</legend>";
					if (uri.length() > 1) {
						String u = uri.substring(0, uri.length() - 1);
						int slash = u.lastIndexOf('/');
						if (slash >= 0 && slash < u.length()) {
							String parentUri = uri.substring(0, slash + 1);
							if (parentUri.equals("/")) {
								parentUri += "?list_files=1";
							}
							msg += "<b><a href=\"" + parentUri + "\">..</a></b><br/>\n";
						}
					}

					if (files != null) {
						for (int i = 0; i < files.length; ++i) {
							File curFile = new File(f, files[i]);
							msg += file2Msg(curFile);
						}
					}
					// msg += "<input type='text' name='fname' /><br/\n>";
					// msg += "</fieldset>";
					// msg +=
					// "<input type='submit' id='download' value='下载全部'><br/>\n";
					// msg += "</form>";
					msg += "</body></html>";
					res = new Response(Status.OK, MIME_HTML, msg);
				} else {
					res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT,
							"FORBIDDEN: No directory listing.");
				}
			}
		}

		try {
			if (res == null) {
				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = f.getCanonicalPath().lastIndexOf('.');
				if (dot >= 0)
					mime = (String) theMimeTypes.get(f.getCanonicalPath().substring(dot + 1)
							.toLowerCase());
				if (mime == null)
					mime = MIME_DEFAULT_BINARY;

				// Calculate etag
				String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f
						.length()).hashCode());

				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = header.get("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0, minus));
								endAt = Long.parseLong(range.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {
						}
					}
				}

				// Change return code and add Content-Range header when skipping
				// is requested
				long fileLen = f.length();
				if (range != null && startFrom >= 0) {
					if (startFrom >= fileLen) {
						res = new Response(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "");
						res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
						res.addHeader("ETag", etag);
					} else {
						if (endAt < 0)
							endAt = fileLen - 1;
						long newLen = endAt - startFrom + 1;
						if (newLen < 0)
							newLen = 0;

						final long dataLen = newLen;
						FileInputStream fis = new FileInputStream(f) {
							public int available() throws IOException {
								return (int) dataLen;
							}
						};
						fis.skip(startFrom);

						res = new Response(Status.PARTIAL_CONTENT, mime, fis);
						res.addHeader("Content-Length", "" + dataLen);
						res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/"
								+ fileLen);
						res.addHeader("filename", f.getName());
						res.addHeader("ETag", etag);
					}
				} else {
					if (etag.equals(header.get("if-none-match")))
						res = new Response(Status.NOT_MODIFIED, mime, "");
					else {
						res = new Response(Status.OK, mime, new FileInputStream(f));
						res.addHeader("Content-Length", "" + fileLen);
						res.addHeader("filename", f.getName());
						res.addHeader("ETag", etag);
					}
				}
			}
		} catch (IOException ioe) {
			res = new Response(Status.FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed.");
		}

		res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
		// server accepts partial content requestes
		return res;
	}

	private static void deleteDir(File f) {
		File[] children = f.listFiles();
		for (File file : children) {
			if (file.isDirectory()) {
				deleteDir(file);
			} else {
				file.delete();
			}
		}
		f.delete();
	}

	private static void zipDir(String dir, ZipOutputStream out) throws IOException {
		boolean is_i_create = out == null;
		if (is_i_create) {
			String dstPath = String.format("%s.zip", dir);
			FileOutputStream dest = new FileOutputStream(dstPath);
			out = new ZipOutputStream(new BufferedOutputStream(dest));
		}
		int BUFFER = 1024;
		File f = new File(dir);
		File files[] = f.listFiles();

		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isDirectory()) {
				ZipEntry entry = new ZipEntry(file.getName());
				out.putNextEntry(entry);
				zipDir(file.getPath(), out);
				continue;
			}
			byte data[] = new byte[BUFFER];
			ZipEntry entry = new ZipEntry(file.getName());
			out.putNextEntry(entry);
			FileInputStream fi = new FileInputStream(file);
			BufferedInputStream origin = new BufferedInputStream(fi, BUFFER);
			int count;
			while ((count = origin.read(data, 0, BUFFER)) != -1) {
				out.write(data, 0, count);
			}
			origin.close();
		}
		if (is_i_create) {
			out.close();
		}
	}

	public static long storageAvailable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			String sROOT = NPUApp.sROOT;
			if (TextUtils.isEmpty(sROOT)) {
				return -1;
			}
			File f = new File(sROOT);
			if (!f.exists()) {
				return -1;
			}
			StatFs sf = new StatFs(sROOT);
			long blockSize = sf.getBlockSize();
			long availCount = sf.getAvailableBlocks();
			return availCount * blockSize;
		}
		return -1;
	}

	public static float getBaterryPecent(Context context) {
		Intent batteryInfoIntent = context.getApplicationContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

		int level = batteryInfoIntent.getIntExtra("level", 50);
		int scale = batteryInfoIntent.getIntExtra("scale", 100);
		return level * 1f / scale;
	}
}