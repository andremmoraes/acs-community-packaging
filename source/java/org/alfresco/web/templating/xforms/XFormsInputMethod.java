/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.web.templating.xforms;

import java.io.*;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletContext;

import org.alfresco.web.templating.*;
import org.chiba.xml.util.DOMUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.alfresco.web.app.servlet.FacesHelper;
import org.chiba.xml.xforms.exception.XFormsException;

public class XFormsInputMethod
    implements TemplateInputMethod
{
    private static final Log LOGGER = LogFactory.getLog(XFormsInputMethod.class); 

    public XFormsInputMethod()
    {
    }

    /**
     * Generates html text which bootstraps the JavaScript code that will
     * call back into the XFormsBean and get the xform and build the ui.
     */
    public void generate(final InstanceData instanceData, 
			 final TemplateType tt,
			 final Writer out)
    {
	final TemplatingService ts = TemplatingService.getInstance();
	final FacesContext fc = FacesContext.getCurrentInstance();
	//make the XFormsBean available for this session
	final XFormsBean xforms = (XFormsBean)
	    FacesHelper.getManagedBean(fc, "XFormsBean");
	xforms.setInstanceData(instanceData);
	xforms.setTemplateType(tt);
	try
        {
	    xforms.init();
	}
	catch (XFormsException xfe)
        {
	    LOGGER.error(xfe);
	}
 
	final String cp = fc.getExternalContext().getRequestContextPath();

	final Document result = ts.newDocument();

	// this div is where the ui will write to
	final Element div = result.createElement("div");
	div.setAttribute("id", "alf-ui");
	result.appendChild(div);

	// a script with config information and globals.
	Element e = result.createElement("script");
        e.setAttribute("type", "text/javascript");
	e.appendChild(result.createTextNode("\ndjConfig = { isDebug: " + LOGGER.isDebugEnabled() +
					    " };\n" +
					    "var WEBAPP_CONTEXT = \"" + cp + "\";\n"));
	div.appendChild(e);
	final String[] scripts = 
	{
           "/scripts/tiny_mce/" + (LOGGER.isDebugEnabled() 
                                   ? "tiny_mce_src.js" 
                                   : "tiny_mce.js"),
	    "/scripts/ajax/dojo/" + (LOGGER.isDebugEnabled() 
                                     ? "dojo.js.uncompressed.js" 
                                     : "dojo.js"),
	    "/scripts/ajax/xforms.js"
	};
	    
	// include all our scripts, order is significant
	for (int i = 0; i < scripts.length; i++)
	{
	    e = result.createElement("script");
	    e.setAttribute("type", "text/javascript");
	    e.setAttribute("src", cp + scripts[i]);
	    e.appendChild(result.createTextNode("\n"));
	    div.appendChild(e);
	}
 
	ts.writeXML(result, out);
    }
}
