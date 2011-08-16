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

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridException;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.utils.HtmlRenderer;

import com.ebay.spine.vmware.VirtualMachineRemoteProxy;

public class WindowsWebDriverVNCProxy extends VirtualMachineRemoteProxy implements RegistrationListener {

	private String snapshot;
	public static final String SELENIUM_PATH = "seleniumPath";
	public static final String CHROME_SERVER = "webdriver.chrome.driver";
	public static final String VNC_PASS = "vncPass";
	private static String seleniumPath;
	private static String chromeServer;
	private String vncPassword;
	
	

	


	public WindowsWebDriverVNCProxy(RegistrationRequest request,Registry registry) {
		super(request,registry);
		snapshot = (String) request.getConfiguration().get(CLEAN_SNAPSHOT);
		seleniumPath = (String) request.getConfiguration().get(SELENIUM_PATH);
		chromeServer = (String)request.getConfiguration().get(CHROME_SERVER);
		vncPassword = (String) request.getConfigAsString(VNC_PASS);
	}

	private static final Logger log = Logger.getLogger(WindowsWebDriverVNCProxy.class);
	private static final String VNC_SERVER = "start /D\"C:\\Program Files (x86)\\TightVNC\\\" tvnserver.exe";
	

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
				log.warn("Error happened when preparing the VM" + e.getMessage(), e);
				if (tries >= maxTries) {
					throw new GridException("Preparation of the VM crashes " + tries + " in a row.", e);
				}
			}
		}
	}
	
	

	private void prepareVM() {
		log.info("node restarting...");
		int nbtry = 0;
		while (nbtry < 2) {
			nbtry++;
			try {
				// start webdriver server.
				getVm().revertToSnapshot(snapshot);
				refreshNetwork();
				log.info("VNC server started : "+VNC_SERVER);
				getVm().getVIXService().runProgramInGuest(null, VNC_SERVER, true);
				System.out.println("reverted to " + snapshot + " and refreshed network.The ip is now " + getRemoteURL());
				totalTestStarted = 0;
				totalTestFinished = 0;
				break;
			} catch (Throwable e) {
				log.error("error preparing the web driver client." + e.getMessage());
				throw new GridException(e.getLocalizedMessage(), e);
			}
		}

		
		startWebDriver(seleniumPath);
		// make sure webdriver is responding before finishing the registration
		// process.
		int maxTries = 20;
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
			

		log.info("node restarted and ready");
		super.hasRestarted();
		System.out.println("PREPARE OK");

	}

	private void refreshNetwork() {
		getVm().getVIXService().runProgramInGuest(null, "ipconfig /release", false);
		getVm().getVIXService().runProgramInGuest(null, "ipconfig /renew", false);
		getVm().setIp(null);
		setRemoteURL(null);

		while (getVm().getIp().startsWith("172") || "unknown".equals(getVm().getIp())) {
			System.out.println("warning, IP " + getVm().getIp());
			getVm().setIp(null);
			setRemoteURL(null);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				//
			}
		}
	}

	private void startWebDriver(String jar) {
		String cmd = "java -jar " + jar + " -port 5555 -Dwebdriver.chrome.driver="+chromeServer;
		log.info("starting "+cmd);
		
		getVm().getVIXService().runProgramInGuest(null, cmd, true);
		boolean ok = false;
		int tries = 0;
		while (!ok) {
			tries++;
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			ok = isAlive();
			if (tries >= 5) {
				throw new RuntimeException("Cannot ping server" + getRemoteURL().toExternalForm() + "/status");
			}
		}

	}

	@Override
	public boolean isAlive() {
		String url = getRemoteURL().toExternalForm() + "/status";
		BasicHttpRequest r = new BasicHttpRequest("GET", url);
		DefaultHttpClient client = new DefaultHttpClient();
		HttpHost host = new HttpHost(getRemoteURL().getHost(), getRemoteURL().getPort());
		HttpResponse response;
		try {
			response = client.execute(host, r);
		} catch (ClientProtocolException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		int code = response.getStatusLine().getStatusCode();
		return code == 200;
	};

	

	HtmlRenderer renderer = new WindowsWebDriverVNCRenderer(this);

	@Override
	public HtmlRenderer getHtmlRender() {
		return renderer;
	}
	
	public String getVncPassword() {
		return vncPassword;
	}

	
	
}
