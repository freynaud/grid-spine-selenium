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
package web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import service.NoVNCProxyService;

import com.google.common.io.ByteStreams;

public class ConsoleVNC extends RegistryBasedServlet {

	private static final long serialVersionUID = -2792745375550370310L;
	private static final Logger log = Logger.getLogger(ConsoleVNC.class.getName());
	private static String coreVersion;
	private static String coreRevision;

	private void getVersion() {
		final Properties p = new Properties();

		InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("VERSION.txt");
		if (stream == null) {
			log.severe("Couldn't determine version number");
			return;
		}
		try {
			p.load(stream);
		} catch (IOException e) {
			log.severe("Cannot load version from VERSION.txt" + e.getMessage());
		}
		coreVersion = p.getProperty("selenium.core.version");
		coreRevision = p.getProperty("selenium.core.revision");
		if (coreVersion == null) {
			log.severe("Cannot load selenium.core.version from VERSION.txt");
		}
	}

	public ConsoleVNC() {
		this(null);
	}

	public ConsoleVNC(Registry registry) {
		super(registry);
		getVersion();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		process(request, response);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		process(request, response);
	}

	protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {

		
		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(200);

		StringBuilder builder = new StringBuilder();

		if (request.getMethod().equalsIgnoreCase("POST")) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			try {
				String ip = request.getParameter("ip");
				int x = Integer.parseInt(request.getParameter("x"));
				String remote = request.getRemoteAddr();
				int port = NoVNCProxyService.getInstance().startProxyForDisplay(ip, 5900 + x, remote);
				builder.append(port);
			} catch (Throwable t) {
				response.setStatus(500);
				builder.append(t.getClass() + ":" + t.getMessage());
			}
		} else {

			builder.append("<html>");
			builder.append("<head>");

			builder.append("<title>Grid overview</title>");

			builder.append("<script src='http://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js'></script>");
			builder.append("<script src='../resources/euqe.js'></script>");
			builder.append("<script src='../resources/noVNCJS/vnc.js'></script>");
			builder.append("<script src='../resources/noVNCJS/ConsoleVNC.js'></script>");
			builder.append("<style>");

			builder.append(".busy {");
			builder.append(" text-decoration: blink;");
			builder.append("}\n");

			builder.append(".free {");
			builder.append(" opacity : 0.4;");
			builder.append("filter: alpha(opacity=40);");
			builder.append("}\n");

			builder.append("h1 {");
			builder.append("	font-size: 25px;");
			builder.append("			font-family:Verdana;");
			builder.append("	font-weight:200;");
			builder.append("}\n");

			builder.append("fieldset {");
			builder.append("	margin-bottom:20px;");
			builder.append("}\n");

			builder.append("legend {");
			builder.append("font-weight: bold;");
			builder.append("color: #006600;");
			builder.append("}\n");

			builder.append("#browsers {");
			builder.append("	margin-top: 5px;");
			builder.append("}\n");

			builder.append("#browsers img {");
			builder.append("	border-style: none;");
			builder.append("}\n");

			builder.append("#browsers a:link,#browsers  a:visited,#browsers  a:hover,#browsers  a:active {");
			builder.append("	color: #006600;");
			builder.append("	font-size:9pt;");
			builder.append("	margin-right:3px;");
			builder.append("	font-weight:700;");
			builder.append("}\n");

			builder.append("#screen	{");
			builder.append("position: absolute;");
			builder.append("left: 0;");
			builder.append("top: 0;");
			builder.append("background: #000;}\n");

			builder.append("</style>");

			builder.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"http://include.ebaystatic.com/v4css/nl_BE/e705i/GH-ZAM_ReskinEbayNoAutoComplete_e705i12640609_nl_BE.css\">");

			builder.append("</head>");
			builder.append("<body>");
			builder.append("<div id='screen'></div>");
			builder.append("<div style='margin-top:10px;font-weight:900;'>");
			builder.append("	<img src='http://pics.ebaystatic.com/aw/pics/logos/logoEbay_x45.gif' class='floatLeft' /><br />");
			builder.append("	EU Quality Engineering : Grid "+coreVersion+coreRevision+" Hub");
			builder.append("</div>");

			builder.append("<div class='gh-col'>");
			builder.append("<b class='gh-c1'></b><b class='gh-c2'></b><b class='gh-c3'></b><b class='gh-c4'></b><b class='gh-c5'></b><b class='gh-c6'></b><b class='gh-c7'></b>");
			builder.append("<div class='gh-clr'>");
			builder.append("</div></div><br/>");

		
			for (RemoteProxy proxy : getRegistry().getAllProxies()) {
				builder.append(proxy.getHtmlRender().renderSummary());
			}

			builder.append("<div id='vnc' ></div>");

			builder.append("<div id=\"VNC_screen\"  style='position: fixed; left:100px; top: 100px;' > ");

			builder.append("<div id=\"VNC_status_bar\" class=\"VNC_status_bar\" style=\"margin-top: 0px; \" > ");
			builder.append(" <table border=0 width=\"100%\"><tr> ");
			builder.append(" 	<td><b><a id='closeVNC' href='#'>close<a/></td><td><div id=\"VNC_status\"   >Loading</div></b></td>");
			builder.append("	<td width=\"1%\">");
			builder.append("		<div id=' VNC_buttons' > ");
			builder.append("  			<input type=button value='Send CtrlAltDel' id='sendCtrlAltDelButton' style='display:none' > ");
			builder.append("   		</div></td> ");
			builder.append("</tr></table> ");
			builder.append(" </div> ");

			builder.append(" <canvas id=\"VNC_canvas\" width='640px' height='20px'> ");
			builder.append("     Canvas not supported.");
			builder.append("  </canvas> ");

			builder.append("</body>");
			builder.append("</html>");

		}
		InputStream in = new ByteArrayInputStream(builder.toString().getBytes("UTF-8"));
		try {
			ByteStreams.copy(in, response.getOutputStream());
		} finally {
			in.close();
			response.getOutputStream().close();
		}
	}

}
