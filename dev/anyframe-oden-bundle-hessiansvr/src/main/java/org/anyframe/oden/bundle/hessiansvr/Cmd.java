/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.anyframe.oden.bundle.hessiansvr;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

/**
 * To connect oden server and control in command line.
 * 
 * @author Junghwan Hong
 */
public class Cmd {
	private static final String HEADER_AUTHORIZATION = "Authorization";
	private final static String CONFIG_FILE = "conf/agent.ini";
	private final static int TIMEOUT = 10000;

	private String odenURL;

	private String user;

	private String home;

	public static void main(String... args) {
		if (args.length < 1) {
			System.err.println("Usage: runc.cmd(sh) <oden-cmd>");
			System.exit(-1);
		}
		try {
			Cmd cmd = new Cmd();
			cmd.loadINI();
			String command = cmd.toCommand(args);
			if(! "".equals(command) || null != command)
				cmd.sendRequest(cmd.toCommand(args));
			else
				System.exit(0);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Cmd() throws Exception {
		// home = new
		// File(Cmd.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getParentFile()
		// + "/../anyframe.oden.bundle";
		home = odenHome().getPath();
	}

	@SuppressWarnings("PMD")
	private String toCommand(String[] ss) {
		StringBuffer buf = new StringBuffer(ss[0] + " ");
		int i = 1;
		// before meeting '-'
		while (i < ss.length) {
			if (ss[i].startsWith("-")) {
				break;
			}
			buf.append(ss[i]);
			buf.append(" ");
			i++;
		}

		// after meeting '-'
		while (i < ss.length) {
			if (!ss[i].startsWith("-")) {
				buf.append("\"" + ss[i] + "\" ");
			} else {
				buf.append(ss[i] + " ");
			}
			i++;
		}
		return buf.substring(0, buf.length() - 1);
	}

	private void loadINI() throws Exception {
		Properties prop = loadINI(CONFIG_FILE);
		if (prop == null) {
			throw new FileNotFoundException(CONFIG_FILE);
		}

		String port = prop.getProperty("http.port");
		if (port == null) {
			throw new IOException("http.port is not defined. See "
					+ CONFIG_FILE);
		}

		odenURL = "http://localhost:" + port + "/agentshell";

		String defaultUser = prop.getProperty("console.user");
		if (defaultUser != null) {
			user = "oden";
		}
	}

//	private String userInfo(String id) {
//		String info = null;
//		File f = new File(home, SecurityHandler.ACCOUNT_FILE);
//		if (f.exists()) {
//			InputStream in = null;
//			try {
//				in = new BufferedInputStream(new FileInputStream(f));
//				Properties prop = new Properties();
//				prop.load(in);
//				info = (String) prop.get(id);
//			} catch (IOException e) {
//				e.printStackTrace();
//			} finally {
//				if (in != null) {
//					try {
//						in.close();
//					} catch (IOException e) {
//					}
//				}
//			}
//		}
//		return info;
//	}

	private Properties loadINI(String ini) throws Exception {
		File iniFile = new File(home, ini);
		InputStream in = null;
		try {
			in = new BufferedInputStream(new FileInputStream(iniFile));
		} catch (FileNotFoundException e) {
			return null;
		}

		Properties prop = null;
		if (in != null) {
			prop = new Properties();
			try {
				prop.load(in);
			} catch (Exception e) {
				throw new IOException("Illegal format error: " + ini);
			} finally {
				in.close();
			}
		}
		return prop;
	}

	private String sendRequest(String msg) throws IOException {
		PrintWriter writer = null;
		String result = null;
		try {
			URLConnection conn = init(odenURL);

			// send
			writer = new PrintWriter(conn.getOutputStream());
			writer.println(msg);
			writer.flush();

			// receive
			result = handleResult(conn);
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
		return result;

	}

	private String handleResult(URLConnection conn) throws IOException {
		String result = null;
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					conn.getInputStream(), "utf-8"));

			result = readAll(reader);
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return result;
	}

	private URLConnection init(String url) throws IOException {
		URLConnection con = new URL(url).openConnection();
		con.setUseCaches(false);
		con.setDoOutput(true);
		con.setDoInput(true);
		con.setConnectTimeout(TIMEOUT);
		if (user != null) {
			con.setRequestProperty(HEADER_AUTHORIZATION, "Basic " + user);
		}
		return con;
	}

	@SuppressWarnings("PMD")
	private String readAll(BufferedReader reader) throws IOException {
		StringBuffer buf = new StringBuffer();
		String line = null;
		while ((line = reader.readLine()) != null) {
			buf.append(line + "\n");
		}
		return buf.toString();
	}

	public static File odenHome() {
		try {
			URL url = new URL(Cmd.class.getProtectionDomain().getCodeSource()
					.getLocation().toString());
			return new File(url.getPath()).getParentFile().getParentFile();
		} catch (Exception e) {
			return new File("..");
		}
	}
}
