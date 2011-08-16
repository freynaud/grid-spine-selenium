/*
Copyright eBay Inc., Spine authors, and other contributors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SSHService {
	private String login;
	private String password;
	private String host;

	private Session session;
	private Channel channel;
	private OutputStream out;
	private InputStream in;

	private boolean upAndRunning = false;

	private String prompt;

	@SuppressWarnings("unused")
	private SSHService() {
		// don't use
	}

	public SSHService(String login, String password, String host) {
		this.login = login;
		this.password = password;
		this.host = host;
		this.prompt = login + "@euqe-linux1" + ":~$";
	}

	public String start() throws Exception {
		int cpt = 0;
		while (!upAndRunning && cpt < 20) {
			cpt++;
			try {
				JSch jsch = new JSch();
				session = jsch.getSession(login, host, 22);
				session.setPassword(password);
				session.setConfig("StrictHostKeyChecking", "no");
				session.connect(10000);
				channel = session.openChannel("shell");
				out = channel.getOutputStream();
				in = channel.getInputStream();
				channel.connect(10 * 1000);
				StringBuilder log = new StringBuilder();
				log.append(waitForReplyContaing(prompt, in, 5000));
				upAndRunning = true;
				return log.toString();
			} catch (Throwable e) {
				System.out.println("error "+e.getMessage()+".Trying again.");
			}
		}
		if (!upAndRunning){
			throw new RuntimeException("Cannot connect to SSH server on "+host);
		}
		return null;
	}

	public void stop() {
		upAndRunning = false;
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
	}

	private void checkServiceStarted() {
		if (!upAndRunning) {
			throw new RuntimeException("SSH session not up. Did the service start properly?");
		}
	}

	/**
	 * @param nbDisplay
	 *            number of display to start. If nbDisplay = 5, it will execute
	 *            tightvncserver :1 , ... , tightvncserver :5
	 * @return the ssh session log.
	 * @throws Exception
	 */
	public String startTightVNC(int nbDisplay) throws Exception {
		checkServiceStarted();
		StringBuilder log = new StringBuilder();
		for (int i = 1; i <= nbDisplay; i++) {
			send("tightvncserver :" + i + "\n", out);
			log.append(waitForReplyContaing("Log file is /home/euqe/.vnc/euqe-linux1:" + i + ".log", in, 5000));
			log.append(waitForReplyContaing(prompt, in, 5000));
		}
		return log.toString();
	}
	
	

	public String startWebDriverServer(String webdriverJarAbsolutePath, String chromeServer, int port) throws Exception {
		checkServiceStarted();
		StringBuilder log = new StringBuilder();
		send("export DISPLAY=:0\n", out);
		log.append(waitForReplyContaing(login + "@euqe-linux", in, 5000));
		//System.out.println("export display done. Res was " + log.toString());

		String cmd = "nohup java -jar " + webdriverJarAbsolutePath + " -port " + port + " -Dwebdriver.chrome.driver="+chromeServer+" &\n";
		send(cmd, out);
		log.append(waitForReplyContaing("nohup.out", in, 15000));
		return log.toString();
	}
	
	public void mount(String from, String to) throws Exception {
		StringBuilder log = new StringBuilder();
		send("sudo mount -t smbfs "+from+" "+to+"\n", out);
		log.append(waitForReplyContaing(login + "@euqe-linux", in, 5000));
	}

	private static void send(String s, OutputStream out) throws UnsupportedEncodingException, IOException {
		//System.out.println("sending ssh command : " + s);
		String enc = "UTF8";
		out.write(s.getBytes(enc));
		out.flush();
	}

	/**
	 * wait for the result of the command to contains the given pattern
	 * 
	 * @param pattern
	 *            the pattern to look for
	 * @param in
	 * @param timeoutMilliSec
	 *            the time out for the search. In milli sec
	 * @return the log of the command session.
	 * @throws TimeoutException
	 *             if the time out is reached
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private static String waitForReplyContaing(final String pattern, final InputStream in, final long timeoutMilliSec) throws TimeoutException, IOException, InterruptedException, ExecutionException {
		FutureTask<String> theTask = null;
		final StringBuffer buff = new StringBuffer();
		try {
			// create new task
			theTask = new FutureTask<String>(new Callable<String>() {
				public String call() {
					int c;
					try {
						while ((c = in.read()) != -1) {
							buff.append((char) c);
							if (buff.toString().endsWith(pattern)) {
								break;
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					return buff.toString();
				}
			});

			// start task in a new thread
			new Thread(theTask).start();

			// wait for the execution to finish, timeout after timeoutMilliSec
			// secs
			String log = theTask.get(timeoutMilliSec, TimeUnit.MILLISECONDS);
			return log;
		} catch (TimeoutException e) {
			System.out.println("Cannot find :" + buff.toString());
			throw new TimeoutException("Time out (after " + (timeoutMilliSec / (1000 * 60)) + "min),waiting for " + pattern + "in the session log : " + "<pre>" + buff.toString() + "</pre>");
		}

	}

}
