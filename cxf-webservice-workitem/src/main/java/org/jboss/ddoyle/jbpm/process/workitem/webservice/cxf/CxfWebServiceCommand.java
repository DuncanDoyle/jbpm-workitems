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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.apache.cxf.message.Message;
import org.jbpm.process.workitem.webservice.WebServiceCommand;
import org.kie.api.runtime.process.WorkItem;
import org.kie.internal.executor.api.Command;
import org.kie.internal.executor.api.CommandContext;
import org.kie.internal.executor.api.ExecutionResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialization of the <code>jBPM 6</code> default {@link WebServiceWorkItemCommand}. This implementation allows one to provide a
 * <code>cxf.xml</code> CXF configuration file on the classpath of this command. If the configuration file is found, and if the required
 * <code>Spring</code> libraries are available, the CXF {@link Bus} will be created from the configuration file.
 * <p/>
 * This allows to easily configure the {@link WebServiceCommand} to support things like HTTP BasicAuth, WS-Security, etc.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceCommand implements Command {

	private static final String SPRING_BUS_FACTORY_CLASS_NAME = "org.apache.cxf.bus.spring.SpringBusFactory";
	private static final String SPRING_BUS_FACTORY_CREATE_BUS_METHOD_NAME = "createBus";
	
    private static final Logger logger = LoggerFactory.getLogger(CxfWebServiceCommand.class);
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
	        
	        Client client = getWSClient(workItem, interfaceRef);
	        
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
    
    
    protected synchronized Client getWSClient(WorkItem workItem, String interfaceRef) {
        if (clients.containsKey(interfaceRef)) {
            return clients.get(interfaceRef);
        }
        
        String importLocation = (String) workItem.getParameter("Url");
        String importNamespace = (String) workItem.getParameter("Namespace");
        if (importLocation != null && importLocation.trim().length() > 0 
                && importNamespace != null && importNamespace.trim().length() > 0) {
        	
            Client client = getDynamicClientFactory().createClient(importLocation, new QName(importNamespace, interfaceRef), Thread.currentThread().getContextClassLoader(), null);
            clients.put(interfaceRef, client);
            return client;
        	
        }

        return null;
    }
    
    protected synchronized DynamicClientFactory getDynamicClientFactory() {
    	if (this.dcf == null) {
			JaxWsDynamicClientFactory jaxWsDynamicClientFactory = null;
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			if (cl == null) {
				cl = this.getClass().getClassLoader();
			}
			URL cxfConfigurationUrl = cl.getResource("cxf.xml");
			if (cxfConfigurationUrl != null) {
				try {
					Class<?> springBusFactoryClass = Class.forName(SPRING_BUS_FACTORY_CLASS_NAME);
					Object springBusFactoryInstance = springBusFactoryClass.newInstance();
					Method createBusMethod = springBusFactoryClass.getMethod(SPRING_BUS_FACTORY_CREATE_BUS_METHOD_NAME, String.class);
					Object bus = createBusMethod.invoke(springBusFactoryInstance, cxfConfigurationUrl.getPath());
					jaxWsDynamicClientFactory = JaxWsDynamicClientFactory.newInstance((Bus) bus);
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException
						| IllegalArgumentException | InvocationTargetException e) {
					logger.warn(
							"Unable to load CXF configuration file. Unable to load CXF SpringBusFactory. Falling back to default configuration.",
							e);
					jaxWsDynamicClientFactory = JaxWsDynamicClientFactory.newInstance();
				}
			} else {
				jaxWsDynamicClientFactory = JaxWsDynamicClientFactory.newInstance();
			}
			this.dcf = jaxWsDynamicClientFactory;
		}
		return this.dcf;
    }

}
