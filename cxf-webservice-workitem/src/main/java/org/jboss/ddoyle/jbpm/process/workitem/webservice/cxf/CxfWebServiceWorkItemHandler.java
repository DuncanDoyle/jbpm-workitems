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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientCallback;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.apache.cxf.message.Message;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.jbpm.bpmn2.core.Bpmn2Import;
import org.jbpm.process.workitem.AbstractLogOrThrowWorkItemHandler;
import org.jbpm.process.workitem.webservice.WebServiceWorkItemHandler;
import org.jbpm.workflow.core.impl.WorkflowProcessImpl;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.internal.runtime.manager.RuntimeManagerRegistry;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialization of the <code>jBPM 6</code> default {@link WebServiceWorkItemHandler}. This implementation allows one to provide a
 * <code>cxf.xml</code> CXF configuration file on the classpath of this handler. If the configuration file is found, and if the required
 * <code>Spring</code> libraries are available, the CXF {@link Bus} will be created from the configuration file.
 * <p/>
 * This allows to easily configure the {@link WebServiceWorkItemHandler} to support things like HTTP BasicAuth, WS-Security, etc.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceWorkItemHandler extends AbstractLogOrThrowWorkItemHandler {

	public static final String WSDL_IMPORT_TYPE = "http://schemas.xmlsoap.org/wsdl/";

	private static final String SPRING_BUS_FACTORY_CLASS_NAME = "org.apache.cxf.bus.spring.SpringBusFactory";
	private static final String SPRING_BUS_FACTORY_CREATE_BUS_METHOD_NAME = "createBus";

	private static Logger logger = LoggerFactory.getLogger(CxfWebServiceWorkItemHandler.class);

	private ConcurrentHashMap<String, Client> clients = new ConcurrentHashMap<String, Client>();
	private DynamicClientFactory dcf = null;
	private KieSession ksession;
	private int asyncTimeout = 10;
	private ClassLoader classLoader;

	enum WSMode {
		SYNC, ASYNC, ONEWAY;
	}

	public CxfWebServiceWorkItemHandler(KieSession ksession) {
		this.ksession = ksession;
	}

	public CxfWebServiceWorkItemHandler(KieSession ksession, ClassLoader classloader) {
		this.ksession = ksession;
		this.classLoader = classloader;
	}

	public CxfWebServiceWorkItemHandler(KieSession ksession, int timeout) {
		this.ksession = ksession;
		this.asyncTimeout = timeout;
	}

public void executeWorkItem(WorkItem workItem, final WorkItemManager manager) {
    	
    	// since JaxWsDynamicClientFactory will change the TCCL we need to restore it after creating client
        ClassLoader origClassloader = Thread.currentThread().getContextClassLoader();
        
    	Object[] parameters = null;
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
        
        String modeParam = (String) workItem.getParameter("Mode");
        WSMode mode = WSMode.valueOf(modeParam == null ? "SYNC" : modeParam.toUpperCase());
            
        try {
             Client client = getWSClient(workItem, interfaceRef);
             if (client == null) {
                 throw new IllegalStateException("Unable to create client for web service " + interfaceRef + " - " + operationRef);
             }
             //Override endpoint address if configured.
             if (endpointAddress != null && !"".equals(endpointAddress)) {
            	 client.getRequestContext().put(Message.ENDPOINT_ADDRESS, endpointAddress) ;
             }
             
             switch (mode) {
                case SYNC:
                    Object[] result = client.invoke(operationRef, parameters);
                    
                    Map<String, Object> output = new HashMap<String, Object>();          
   
                    if (result == null || result.length == 0) {
                      output.put("Result", null);
                    } else {
                        output.put("Result", result[0]);
                    }
                    logger.debug("Received sync response {} completeing work item {}", result, workItem.getId());
                    manager.completeWorkItem(workItem.getId(), output);
                    break;
                case ASYNC:
                    final ClientCallback callback = new ClientCallback();
                    final long workItemId = workItem.getId();
                    final String deploymentId = nonNull(((WorkItemImpl)workItem).getDeploymentId());
                    final long processInstanceId = workItem.getProcessInstanceId();
                    
                    client.invoke(callback, operationRef, parameters);
                    new Thread(new Runnable() {
                       
                       public void run() {
                           
                           try {
                              
                               Object[] result = callback.get(asyncTimeout, TimeUnit.SECONDS);
                               Map<String, Object> output = new HashMap<String, Object>();          
                               if (callback.isDone()) {
                                   if (result == null) {
                                     output.put("Result", null);
                               } else {
                                 output.put("Result", result[0]);
                               }
                           }
                           logger.debug("Received async response {} completeing work item {}", result, workItemId);
                           
                           RuntimeManager manager = RuntimeManagerRegistry.get().getManager(deploymentId);
                           if (manager != null) {
                               RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
                               
                               engine.getKieSession().getWorkItemManager().completeWorkItem(workItemId, output);
                               
                               manager.disposeRuntimeEngine(engine);
                           } else {
                        	   // in case there is no RuntimeManager available use available ksession, 
                        	   // as it might be used without runtime manager at all 
                        	   ksession.getWorkItemManager().completeWorkItem(workItemId, output);
                           }
                       } catch (Exception e) {
                    	   e.printStackTrace();
                           throw new RuntimeException("Error encountered while invoking ws operation asynchronously", e);
                       }
                       
                       
                   }
               }).start();
                break;
            case ONEWAY:
                ClientCallback callbackFF = new ClientCallback();
                
                client.invoke(callbackFF, operationRef, parameters);
                logger.debug("One way operation, not going to wait for response, completing work item {}", workItem.getId());
                manager.completeWorkItem(workItem.getId(),  new HashMap<String, Object>());
                break;
            default:
                break;
            }

         } catch (Exception e) {
             handleException(e);
         } finally {
     		Thread.currentThread().setContextClassLoader(origClassloader);
     	}
    }
    
    @SuppressWarnings("unchecked")
    protected synchronized Client getWSClient(WorkItem workItem, String interfaceRef) {
        if (clients.containsKey(interfaceRef)) {
            return clients.get(interfaceRef);
        }
        
        String importLocation = (String) workItem.getParameter("Url");
        String importNamespace = (String) workItem.getParameter("Namespace");
        if (importLocation != null && importLocation.trim().length() > 0 
        		&& importNamespace != null && importNamespace.trim().length() > 0) {
        	Client client = getDynamicClientFactory().createClient(importLocation, new QName(importNamespace, interfaceRef), getInternalClassLoader(), null);
            clients.put(interfaceRef, client);
            return client;
        }
        
        
        long processInstanceId = ((WorkItemImpl) workItem).getProcessInstanceId();
        WorkflowProcessImpl process = ((WorkflowProcessImpl) ksession.getProcessInstance(processInstanceId).getProcess());
        List<Bpmn2Import> typedImports = (List<Bpmn2Import>)process.getMetaData("Bpmn2Imports");
        
        if (typedImports != null ){
            Client client = null;
            for (Bpmn2Import importObj : typedImports) {
                if (WSDL_IMPORT_TYPE.equalsIgnoreCase(importObj.getType())) {
                    try {
                        client = getDynamicClientFactory().createClient(importObj.getLocation(), new QName(importObj.getNamespace(), interfaceRef), getInternalClassLoader(), null);
                        clients.put(interfaceRef, client);
                        return client;
                    } catch (Exception e) {
                    	logger.error("Error when creating WS Client", e);
                        continue;
                    }
                }
            }
        }
        return null;
    }
	
	/**
	 * Creates a new {@link JaxWsDynamicClientFactory}.
	 * 
	 * If a <code>cxf.xml</code> configuration is found on the classpath, it will be used to load a new Spring-based CXF {@link Bus}. If a
	 * <code>cxf.xml</code> file is not present, a default {@link JaxWsDynamicClientFactor instance} will be returned.
	 * 
	 * @return the {@link JaxWsDynamicClientFactory}
	 */
	protected synchronized DynamicClientFactory getDynamicClientFactory() {
		if (this.dcf == null) {
			JaxWsDynamicClientFactory jaxWsDynamicClientFactory = null;
			ClassLoader cl = getClassLoader();
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

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// Do nothing, cannot be aborted
	}

	private ClassLoader getInternalClassLoader() {
		if (this.classLoader != null) {
			return this.classLoader;
		}

		return Thread.currentThread().getContextClassLoader();
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected String nonNull(String value) {
		if (value == null) {
			return "";
		}
		return value;
	}
}
