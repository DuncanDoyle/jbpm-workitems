/*
 * Copyright 2010 JBoss Inc
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

package org.jbpm.process.workitem.webservice.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.jbpm.process.workitem.webservice.WebServiceCommand;
import org.kie.api.executor.CommandContext;

/**
 * Extension of the <code>jBPM 6</code> default {@link WebServiceWorkItemCommand}. This implementation allows one to provide a
 * <code>cxf.xml</code> CXF configuration file on the classpath of this command. If the configuration file is found, and if the required
 * <code>Spring</code> libraries are available, the CXF {@link Bus} will be created from the configuration file.
 * <p/>
 * This allows to easily configure the {@link WebServiceCommand} to support things like HTTP BasicAuth, WS-Security, etc.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceCommand extends WebServiceCommand {

	private static final String CXF_CONFIGURATION_FILE_URL = "cxf.xml";

    private DynamicClientFactory dcf = null;
    
    /**
	 * Creates a new {@link DynamicClientFactory}. Uses the {@link CxfDynamicClientFactoryBuilder} to build a new
	 * {@link DynamicClientFactory} from the <code>cxf.xml</code> config file found on the classpath.
	 * 
	 * @return the {@link DynamicClientFactory}
	 */
    @Override
	protected synchronized DynamicClientFactory getDynamicClientFactory(CommandContext ctx) {
		if (this.dcf == null) {
			//Retrieve the classloader from the context.
			ClassLoader cl = (ClassLoader) ctx.getData("ClassLoader");
			if (cl == null) {
				cl = Thread.currentThread().getContextClassLoader();
			}
			this.dcf = new CxfDynamicClientFactoryBuilder().setClassLoader(cl).setCxfConfigurationFile(CXF_CONFIGURATION_FILE_URL).build();
		}
		return this.dcf;
	}
   
}
