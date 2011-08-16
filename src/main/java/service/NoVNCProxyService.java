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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.net.PortProber;

public class NoVNCProxyService {

	private static final NoVNCProxyService INSTANCE = new NoVNCProxyService();
	private static Map<String, Process> currentlyViewing = new HashMap<String, Process>();

	private NoVNCProxyService() {
		// killing the python proxies when leaving.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				for (Process p : currentlyViewing.values()) {
					if (p != null) {
						p.destroy();
					}

				}
			}
		});
	}

	// command to launch the vnc proxy
	private static String launch = "/home/euqe/noVNC/utils/launch.sh";

	// start a proxy on the hub.
	public int startProxyForDisplay(String remoteIP, int remotePort, String viewer) throws IOException {
		int port = PortProber.findFreePort();

		List<String> command = new ArrayList<String>();
		command.add(launch);
		command.add("--vnc");
		command.add(remoteIP + ":" + remotePort);
		command.add("--listen");
		command.add("" + port);
		ProcessBuilder vncProxy = new ProcessBuilder(command);
		System.out.println("starting proxy : "+command);
		vncProxy.redirectErrorStream(true);
		Process p = vncProxy.start();
		Process oldp = currentlyViewing.get(viewer);
		if (oldp != null) {
			System.out.println("destroying old proxy for " + viewer);
			oldp.destroy();
		}
		currentlyViewing.put(viewer, p);

		InputStream in = p.getInputStream();
		int c;
		StringBuilder b = new StringBuilder();

		while ((c = in.read()) != -1) {
			b.append((char) c);
			if (b.toString().contains("Press Ctrl-C to exit")) {
				return port;
			}
		}
		in.close();

		throw new RuntimeException("python proxy didn't start ?");

	}

	public static NoVNCProxyService getInstance() {
		return INSTANCE;
	}

}
