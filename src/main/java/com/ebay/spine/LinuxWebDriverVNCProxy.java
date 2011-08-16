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
package com.ebay.spine;

import static org.openqa.grid.common.RegistrationRequest.CLEAN_SNAPSHOT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridException;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.utils.HtmlRenderer;

import service.FindFreeVNCProxyPortService;
import service.NoVNCProxyService;
import service.SSHService;

import com.ebay.spine.vmware.VirtualMachineRemoteProxy;
import com.vmware.vix.VixException;

/**
 * 
 * Proxy controlling a node with a web driver.The proxy is meant to be used on a
 * linux node with an ssh server installed. In addition to the feature inherited
 * from the webdriver regular proxy,this proxy allows ( assuming the browsers
 * can be passed a command line argument when they're launched by webdriver. At
 * the moment only Firefox allows that after a patch) :
 * 
 * 
 * <li>to isolate the running browsers by launching each browser in a separate X
 * display. The display are launch upfront when the proxy first registers.</li>
 * 
 * 
 * 
 * ASSUMPTION : NoVNC is installed on the grid machine so that
 * {@link NoVNCProxyService} can work
 * 
 * @author freynaud
 * 
 */
public class LinuxWebDriverVNCProxy extends VirtualMachineRemoteProxy implements RegistrationListener {

	private static final Logger log = Logger.getLogger(LinuxWebDriverVNCProxy.class);

	public static final String SSH_USER = "sshUser";
	public static final String VNC_PASS = "vncPass";
	public static final String SSH_PASS = "sshPAssword";
	public static final String MAX_DISPLAY = "maxDisplay";
	public static final String SELENIUM_PATH = "seleniumPath";
	public static final String CHROME_SERVER = "webdriver.chrome.driver";
	private static String chromeServer;

	private static String seleniumPath;

	private String sshLogin;
	private String sshPassword;
	// view only password ( ideally ) for VNC
	private String vncPassword;
	private int maxDisplay = 1;
	// keep track of what display is used
	private Map<Integer, Boolean> displayUsage = new HashMap<Integer, Boolean>();
	private HtmlRenderer renderer = new LinuxWebDriverVNCRenderer(this);
	// the SSH session to the node.
	private SSHService service;

	private String snapshot;

	public LinuxWebDriverVNCProxy(RegistrationRequest request, Registry registry) {
		super(request, registry);

		log.debug(getVm().getName() + " : Creating a LinuxWebDriverVNCProxy");

		// LinuxWebDriverVNCProxy specific arguments.
		maxDisplay = request.getConfigAsInt(MAX_DISPLAY, 1);

		sshLogin = (String) request.getConfigAsString(SSH_USER);
		sshPassword = (String) request.getConfigAsString(SSH_PASS);
		vncPassword = (String) request.getConfigAsString(VNC_PASS);
		if (sshLogin == null || sshPassword == null) {
			throw new InvalidParameterException("You need to specify  both " + SSH_USER + " and " + SSH_PASS);
		}

		for (int i = 1; i <= maxDisplay; i++) {
			displayUsage.put(i, false);
		}

		snapshot = (String) request.getConfiguration().get(CLEAN_SNAPSHOT);
		seleniumPath = (String) request.getConfiguration().get(SELENIUM_PATH);
		chromeServer = (String) request.getConfiguration().get(CHROME_SERVER);

		// killing the python proxies when leaving.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				for (Process p : vncProxies.values()) {
					p.destroy();
				}
			}
		});
		log.debug(getVm().getName() + " : proxy built");
	}

	/**
	 * Get a free X display on the node, and mark that display busy.
	 * 
	 * @return the first free X display, -1 if nothing is available.
	 */
	private synchronized int getFreeDisplay() {
		for (Integer x : displayUsage.keySet()) {
			if (!isBusy(x)) {
				setBusy(x, true);
				return x;
			}
		}
		System.err.println("Error finding a free display in " + displayUsage);
		return -1;
	}

	/**
	 * @param display
	 * @return true is the display X is busy. false otherwise.
	 */
	private boolean isBusy(int display) {
		return new Boolean(displayUsage.get(display));
	}

	/**
	 * 
	 * @param display
	 *            the display to update
	 * @param busy
	 *            true to flag the display busy, false to flag is idle.
	 */
	private void setBusy(int display, boolean busy) {
		displayUsage.put(display, busy);
	}

	@Override
	public void beforeRegistration() {
		boolean ok = false;

		int tries = 0;
		int maxTries = 5;
		while (!ok) {
			try {
				tries++;
				prepareVM();
				ok = true;
			} catch (RuntimeException e) {
				log.warn(getVm().getName() + " : Error, " + e.getMessage() + " on VM " + getVm().getName());
				if (tries >= maxTries) {
					throw new GridException("Preparation of the VM crashes " + tries + " in a row.", e);
				}
			}
		}

	}

	private void prepareVM() {

		for (TestSlot slot : getTestSlots()) {
			slot.forceRelease();
		}

		log.info(getVm().getName() + " node restarting...");
		long start = System.currentTimeMillis();

		int nbtry = 0;
		while (nbtry < 2) {
			nbtry++;
			try {
				// start webdriver server.
				getVm().revertToSnapshot(snapshot);

				for (int i = 1; i <= maxDisplay; i++) {
					displayUsage.put(i, false);
				}

				refreshNetwork();

				totalTestStarted = 0;
				totalTestFinished = 0;
				break;
			} catch (Throwable e) {
				log.warn(getVm().getName() + " : error preparing the web driver client." + e.getMessage());
				throw new GridException(e.getLocalizedMessage(), e);
			}
		}

		try {
			service = new SSHService(sshLogin, sshPassword, getRemoteURL().getHost());
			service.start();
			log.debug(getVm().getName() + ": service started. Starting tightVNC for " + maxDisplay + " display.");
			service.startTightVNC(maxDisplay);
			log.debug(getVm().getName() + " : TightVNC OK. Starting webdriver on port " + 5555);
			service.startWebDriverServer(seleniumPath, chromeServer, 5555);
		} catch (Throwable t) {
			throw new RuntimeException("Error preparing the VM " + t.getMessage(), t);
		} finally {
			if (service != null) {
				service.stop();
			}
		}

		// make sure webdriver is responding before finishing the registration
		// process.
		int maxTries = 25;
		int tries = 0;
		while (!isAlive()) {
			tries++;
			if (tries >= maxTries) {
				throw new RuntimeException("Server didn't come up after " + tries + " tries.");
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
		}
		super.hasRestarted();
		log.info(getVm().getName() + "node reset ok in " + (System.currentTimeMillis() - start) / 1000 + "sec.");
	}

	private void refreshNetwork() throws VixException, Exception {
		long start = System.currentTimeMillis();
		getVm().getVIXService().runProgramInGuest("/bin/bash", "sudo -n /etc/init.d/networking restart", false);
		log.debug(getVm().getName() + " networking restated");
		getVm().setIp(null);
		setRemoteURL(null);

		String ip = getRealIp("eth1");
		log.debug("real ip : " + ip);
		getVm().setIp(ip);
		setRemoteURL(null);
		while (ip == null || ip.startsWith("172")) {

			ip = getRealIp("eth1");
			getVm().setIp(ip);
			setRemoteURL(null);

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				//
			}
		}
		log.debug(getVm().getName() + " network refreshed for " + getVm().getName() + " new IP : " + getVm().getIp() + " - "
				+ (System.currentTimeMillis() - start) / 1000 + " sec.");
	}

	private String getRealIp(String ethX) {
		StringBuffer b = new StringBuffer();
		getVm().getVIXService().runProgramInGuest("/bin/bash", "rm -f /home/euqe/ip.txt", false);
		getVm().getVIXService().runProgramInGuest("/bin/bash", "ifconfig " + ethX + " | grep \"inet addr\" > /home/euqe/ip.txt", false);

		String tmpDir = System.getProperty("java.io.tmpdir");
		File ipFile = new File(tmpDir, "pass" + UUID.randomUUID());
		ipFile.deleteOnExit();
		try {
			getVm().getVIXService().copyFileFromGuestToHost("/home/euqe/ip.txt", ipFile.getAbsolutePath());
			BufferedReader in = new BufferedReader(new FileReader(ipFile.getAbsolutePath()));
			String str;
			while ((str = in.readLine()) != null) {
				b.append(str);
			}
			in.close();
		} catch (IOException e) {
			// ignore e.printStackTrace();
		} catch (VixException e) {
			// ignore e.printStackTrace();
		}

		String s = b.toString();
		try {
			int begin = s.indexOf("inet addr:") + 10;
			int end = s.indexOf("Bcast:");
			s = s.substring(begin, end).trim();
			return s;
		} catch (Throwable e) {
			// ignore e.printStackTrace();
			return null;
		}

	}

	/*
	 * @Override public boolean isAlive() { String url =
	 * getRemoteURL().toExternalForm() + "/status"; BasicHttpRequest r = new
	 * BasicHttpRequest("GET", url); DefaultHttpClient client = new
	 * DefaultHttpClient(); HttpHost host = new
	 * HttpHost(getRemoteURL().getHost(), getRemoteURL().getPort());
	 * HttpResponse response; try { response = client.execute(host, r); } catch
	 * (ClientProtocolException e) { //System.out.println(e.getMessage());;
	 * return false; } catch (IOException e) {
	 * //System.out.println(e.getMessage()); return false; } int code =
	 * response.getStatusLine().getStatusCode();
	 * //System.out.println("return : "+code); return code == 200; };
	 */

	@Override
	public void beforeSession(TestSession session) {
		super.beforeSession(session);

		// find a free display.
		int x = getFreeDisplay();
		if (x == -1) {
			log.warn(getVm().getName() + "Error routing the test");
			return;
		}
		log.debug(getVm().getName() + "routing test " + session + "to : " + x);
		// assign the test to it.
		session.getRequestedCapabilities().put("noVNCPort", getProxyPort(x));
		session.getRequestedCapabilities().put("x", x);

		if (session.getRequestedCapabilities().get("browserName").equals("chrome")) {
			session.getRequestedCapabilities().put("chrome.switches", new String[] { "--display=:" + x });
		} else if (session.getRequestedCapabilities().get("browserName").equals("firefox")) {
			session.getRequestedCapabilities().put("moz.switches", new String[] { "--display=:" + x });
		} else {
			session.getRequestedCapabilities().put("opera.display", x);

		}

	};

	@Override
	public void afterSession(TestSession session) {
		super.afterSession(session);
		Object o = session.getRequestedCapabilities().get("x");
		if (o != null) {
			Integer x = (Integer) o;
			displayUsage.put(x, false);
		}
	};

	private Map<Integer, Process> vncProxies = new HashMap<Integer, Process>();

	public List<Integer> getVNCsPort() {
		List<Integer> res = new ArrayList<Integer>();
		for (int i = 0; i < maxDisplay; i++) {
			res.add(getProxyPort(i));
		}
		return res;
	}

	
	

	public int getMaxDisplay() {
		return maxDisplay;
	}

	private Map<Integer, Integer> displayToPortMap = new HashMap<Integer, Integer>();

	int getProxyPort(int display) {
		Integer port = displayToPortMap.get(display);
		if (port == null) {
			port = FindFreeVNCProxyPortService.getPort();
			displayToPortMap.put(display, port);
		}
		return port;
	}

	@Override
	public HtmlRenderer getHtmlRender() {
		return renderer;
	}

	public String getVncPassword() {
		return vncPassword;
	}

	public String getSshLogin() {
		return sshLogin;
	}

	public String getSshPassword() {
		return sshPassword;
	}
}
