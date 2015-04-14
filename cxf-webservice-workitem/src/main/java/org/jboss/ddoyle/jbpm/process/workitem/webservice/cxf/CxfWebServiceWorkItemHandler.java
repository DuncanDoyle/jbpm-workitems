/**
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

package org.jboss.ddoyle.jbpm.process.workitem.webservice.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.jbpm.process.workitem.webservice.WebServiceWorkItemHandler;
import org.kie.api.runtime.KieSession;

/**
 * Extension of the <code>jBPM 6</code> default {@link WebServiceWorkItemHandler}. This implementation allows one to provide a
 * <code>cxf.xml</code> CXF configuration file on the classpath of this handler. If the configuration file is found, and if the required
 * <code>Spring</code> libraries are available, the CXF {@link Bus} will be created from the configuration file.
 * <p/>
 * This allows to easily configure the {@link WebServiceWorkItemHandler} to support things like HTTP BasicAuth, WS-Security, etc.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceWorkItemHandler extends WebServiceWorkItemHandler {

	private static final String CXF_CONFIGURATION_FILE_URL = "cxf.xml";

	private DynamicClientFactory dcf = null;

	public CxfWebServiceWorkItemHandler(KieSession ksession) {
		super(ksession);
	}

	public CxfWebServiceWorkItemHandler(KieSession ksession, ClassLoader classloader) {
		super(ksession, classloader);
	}

	public CxfWebServiceWorkItemHandler(KieSession ksession, int timeout) {
		super(ksession, timeout);
	}

	/**
	 * Creates a new {@link DynamicClientFactory}. Uses the {@link CxfDynamicClientFactoryBuilder} to build a new
	 * {@link DynamicClientFactory} from the <code>cxf.xml</code> config file found on the classpath.
	 * 
	 * @return the {@link DynamicClientFactory}
	 */
	@Override
	protected synchronized DynamicClientFactory getDynamicClientFactory() {
		if (this.dcf == null) {
			ClassLoader cl = getClassLoader();
			if (cl == null) {
				cl = this.getClass().getClassLoader();
			}
			this.dcf = new CxfDynamicClientFactoryBuilder().setClassLoader(cl).setCxfConfigurationFile(CXF_CONFIGURATION_FILE_URL).build();
		}
		return this.dcf;
	}

}
