package com.ebay.spine;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
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
import org.openqa.grid.selenium.proxy.WebRemoteProxy;
import org.openqa.grid.web.utils.BrowserNameUtils;
import org.openqa.selenium.remote.DesiredCapabilities;

public class WindowsWebDriverVNCRenderer implements HtmlRenderer {
	private WindowsWebDriverVNCProxy proxy;
	private Registry registry;
	public static boolean debug = false;

	public WindowsWebDriverVNCRenderer() {
		throw new RuntimeException("No.");
	}

	public WindowsWebDriverVNCRenderer(WindowsWebDriverVNCProxy proxy) {
		this.proxy = proxy;
		this.registry = proxy.getRegistry();
	}

	@Override
	public String renderSummary() {

		StringBuilder builder = new StringBuilder();

		builder.append("<fieldset>")
				.append("<legend>")
				.append("<img width='40' src='../resources/images/windows_logo.jpg' style='vertical-align:middle;' title='" + proxy.getVm().getName()
						+ "'/>").append(proxy.getClass().getSimpleName()).append("</legend>");

		builder.append("listening on ").append(proxy.getRemoteURL());

		if (((WebRemoteProxy) proxy).isDown()) {
			builder.append("<b> (down)</b>");
		}

		builder.append("<br>");
		if (proxy.getTimeOut() > 0) {
			int inSec = proxy.getTimeOut() / 1000;
			builder.append("test session time out after ").append(inSec).append(" sec.<br>");
		}
		builder.append("test started / max before restart : " + proxy.getTotalTestStarted() + "/" + proxy.getMaxTestBeforeClean() + "<br>");
		builder.append("Supports up to <b>").append(proxy.getMaxNumberOfConcurrentTestSessions()).append("</b> concurrent tests from : </u><br>");

		builder.append("<div id='browsers'>");
		for (TestSlot slot : proxy.getTestSlots()) {
			TestSession session = slot.getSession();
			builder.append("<a href='#' ");

			if (session != null) {
				builder.append(" class='vncCapable' hub='"+proxy.getRegistry().getHub().getHost()+"' pwd='"+proxy.getVncPassword()+"' ip='" + proxy.getRemoteURL().getHost() + "' display='0' ");
				builder.append(" title='").append(session.get("lastCommand")).append("' ");
			} else {
				builder.append(" title='").append(slot.getCapabilities()).append("' ");
			}
			builder.append(" >");
			String icon = BrowserNameUtils.consoleIconName(new DesiredCapabilities(slot.getCapabilities()), registry);
			builder.append("<img src='../resources/images/" + icon + ".png' height='20px' ");
			if (session != null) {
				builder.append(" class='busy' >");
			} else {
				builder.append(" class='free'>");
			}

			builder.append(slot.getCapabilities().get("version"));

			builder.append("</a>");
		}

		builder.append("</div>");
		builder.append("</fieldset>");

		return builder.toString();
	}

}
