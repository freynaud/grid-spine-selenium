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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.grid.common.JSONConfigurationUtils;
import org.openqa.grid.common.exception.GridConfigurationException;
import org.openqa.grid.internal.utils.GridHubConfiguration;
import org.openqa.grid.web.Hub;

public class Launcher {

	private static String hub = "localhost:4444";

	public static void main(String[] args) throws Exception {

		// not using the default parsing for the hub. Creating a simple one from the default.
		GridHubConfiguration config = new GridHubConfiguration();

		// adding the VNC capable servlet.
		List<String> servlets = new ArrayList<String>();
		servlets.add("web.ConsoleVNC");
		config.setServlets(servlets);

		// capabilities are dynamic for VMs.
		config.setThrowOnCapabilityNotPresent(false);
		
		// forcing the host from command line param as the hub gets confused by all the VMWare network interfaces.
		for (int i=0;i<args.length;i++){
			if (args[i].startsWith("hubhost=")){
				config.setHost(args[i].replace("hubhost=", ""));		
			}
		}
		
		Hub h = new Hub(config);
		h.start();
		

		// and the nodes.
		
		// load the node templates.
		Map<String, JSONObject> templatesByNameMap = getTemplates("eugrid.json");
		
		// get the template to use for each VM
		Map<String, String> vmsMapping = getVMtoTemplateMapping("nodes.properties");
		
		// register each node using its template.
		for (String id : vmsMapping.keySet()) {
			String templateName = vmsMapping.get(id);
			JSONObject request = templatesByNameMap.get(templateName);
			request.getJSONObject("configuration").put("vm", id);
			
			BasicHttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", "http://" + hub + "/grid/register/");
			r.setEntity(new StringEntity(request.toString()));
			DefaultHttpClient client = new DefaultHttpClient();
			URL hubURL = new URL("http://" + hub);
			HttpHost host = new HttpHost(hubURL.getHost(), hubURL.getPort());
			HttpResponse response = client.execute(host, r);
		}
		
	}

	private static Map<String, JSONObject> getTemplates(String file) throws JSONException{
		// load templates
		JSONObject templates = JSONConfigurationUtils.loadJSON(file);
		JSONArray tps = templates.getJSONArray("templates");

		Map<String, JSONObject> templatesByNameMap = new HashMap<String, JSONObject>();
		for (int i = 0; i < tps.length(); i++) {
			JSONObject obj = tps.getJSONObject(i);
			templatesByNameMap.put(obj.getString("name"), obj.getJSONObject("request"));
		}
		return templatesByNameMap;
	}
	
	private static Map<String, String> getVMtoTemplateMapping(String file) throws FileNotFoundException{
		// load VM mapping
		// format:
		// spine-linux1:421ec33e-4cc6-a747-76f5-cc460ba7f03f:linux
		// name:id:template
		Map<String, String> vmsMapping = new HashMap<String, String>();
		BufferedReader buffreader = new BufferedReader(new FileReader(file));
		String line;
		try {
			while ((line = buffreader.readLine()) != null) {
				String[] pieces = line.split(":");
				if (pieces.length == 3) {
					vmsMapping.put(pieces[1], pieces[2]);
				}
			}
		} catch (IOException e) {
			throw new GridConfigurationException("Cannot read node file " + e.getMessage(), e);
		}
		return vmsMapping;
	}
	
	

}
