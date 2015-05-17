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

package org.jboss.ddoyle.jbpm.process.workitem.webservice.cxf;

import java.lang.reflect.Array;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.message.Message;
import org.jbpm.process.workitem.webservice.WebServiceCommand;
import org.kie.api.runtime.process.WorkItem;
import org.kie.internal.executor.api.Command;
import org.kie.internal.executor.api.CommandContext;
import org.kie.internal.executor.api.ExecutionResults;
import org.kie.internal.runtime.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of the <code>jBPM 6</code> default {@link WebServiceWorkItemCommand}. This implementation allows one to provide a
 * <code>cxf.xml</code> CXF configuration file on the classpath of this command. If the configuration file is found, and if the required
 * <code>Spring</code> libraries are available, the CXF {@link Bus} will be created from the configuration file.
 * <p/>
 * This allows to easily configure the {@link WebServiceCommand} to support things like HTTP BasicAuth, WS-Security, etc.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceCommand implements Command, Cacheable {

	private static final String CXF_CONFIGURATION_FILE_URL = "cxf.xml";
	
	private static final Logger logger = LoggerFactory.getLogger(WebServiceCommand.class);
    private volatile static ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<String, Client>();
    private DynamicClientFactory dcf = null;

    @Override
    public ExecutionResults execute(CommandContext ctx) throws Exception {
    	// since JaxWsDynamicClientFactory will change the TCCL we need to restore it after creating client
        ClassLoader origClassloader = Thread.currentThread().getContextClassLoader();
        try {
	    	Object[] parameters = null;
	        WorkItem workItem = (WorkItem) ctx.getData("workItem");
	        
	        String interfaceRef = (String) workItem.getParameter("Interface");
	        String operationRef = (String) workItem.getParameter("Operation");
	        String endpointAddress = (String) workItem.getParameter("Endpoint");
	        if ( workItem.getParameter("Parameter") instanceof Object[]) {
	        	parameters =  (Object[]) workItem.getParameter("Parameter");
	        } else if (workItem.getParameter("Parameter") != null && workItem.getParameter("Parameter").getClass().isArray()) {
	        	int length = Array.getLength(workItem.getParameter("Parameter"));
	            parameters = new Object[length];
	            for(int i = 0; i < length; i++) {
	            	parameters[i] = Array.get(workItem.getParameter("Parameter"), i);
	            }            
	        } else {
	        	parameters = new Object[]{ workItem.getParameter("Parameter")};
	        }
	        
	        Client client = getWSClient(workItem, interfaceRef, ctx);
	        
	        //Override endpoint address if configured.
	        if (endpointAddress != null && !"".equals(endpointAddress)) {
	       	 client.getRequestContext().put(Message.ENDPOINT_ADDRESS, endpointAddress) ;
	        }
	        
	        Object[] result = client.invoke(operationRef, parameters);
	        
	        ExecutionResults results = new ExecutionResults();       
	
	        if (result == null || result.length == 0) {
	            results.setData("Result", null);
	        } else {
	            results.setData("Result", result[0]);
	        }
	        logger.debug("Received sync response {}", result);
	        
	        
	        return results;
        }finally {
    		Thread.currentThread().setContextClassLoader(origClassloader);
    	}
    }
    
    
    protected synchronized Client getWSClient(WorkItem workItem, String interfaceRef, CommandContext ctx) {
        if (clients.containsKey(interfaceRef)) {
            return clients.get(interfaceRef);
        }
        
        String importLocation = (String) workItem.getParameter("Url");
        String importNamespace = (String) workItem.getParameter("Namespace");
        if (importLocation != null && importLocation.trim().length() > 0 
                && importNamespace != null && importNamespace.trim().length() > 0) {
        	
            Client client = getDynamicClientFactory(ctx).createClient(importLocation, new QName(importNamespace, interfaceRef), Thread.currentThread().getContextClassLoader(), null);
            clients.put(interfaceRef, client);
            return client;
        	
        }

        return null;
    }
    
    /**
	 * Creates a new {@link DynamicClientFactory}. Uses the {@link CxfDynamicClientFactoryBuilder} to build a new
	 * {@link DynamicClientFactory} from the <code>cxf.xml</code> config file found on the classpath.
	 * 
	 * @return the {@link DynamicClientFactory}
	 */
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
    
	@Override
	public void close() {
		if (clients != null) {
			for (Client client : clients.values()) {
				client.destroy();
			}
		}
	}

	
}
