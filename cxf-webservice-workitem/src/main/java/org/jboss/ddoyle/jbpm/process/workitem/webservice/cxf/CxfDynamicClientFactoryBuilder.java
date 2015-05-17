package org.jboss.ddoyle.jbpm.process.workitem.webservice.cxf;

import java.net.URL;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for <code>CXF</code> {@link DynamicClientFactory DynamicClientFactories}.
 * <p/>
 * It is required to first set the {@link ClassLoader} and the <code>cxfConfigurationFile</code> before calling the {@link #build()} method.
 * <p/>
 * This class is not thread-safe.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 *
 */
public class CxfDynamicClientFactoryBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(CxfDynamicClientFactoryBuilder.class);
	
	private ClassLoader cl;
	
	private String cxfConfigurationFile;
	
	
	public CxfDynamicClientFactoryBuilder setClassLoader(ClassLoader cl) {
		this.cl = cl;
		return this;
	}
	
	public CxfDynamicClientFactoryBuilder setCxfConfigurationFile(String cxfConfigurationFile) {
		this.cxfConfigurationFile = cxfConfigurationFile;
		return this;
	}
	
	/**
	 * Builds the {@link DynamicClientFactory}.
	 * 
	 * @return the {@link DynamicClientFactory}
	 */
	public DynamicClientFactory build() {
		DynamicClientFactory dynamicClientFactory = null;
		URL cxfConfigurationUrl = cl.getResource(cxfConfigurationFile);
		
		if (cxfConfigurationUrl != null) {
			LOGGER.debug("Initializing CXF SpringBusFactory with '" + cxfConfigurationFile + "' configuration file.");
			Bus bus = new SpringBusFactory().createBus(cxfConfigurationUrl);
			dynamicClientFactory = JaxWsDynamicClientFactory.newInstance((Bus) bus);
		} else {
			LOGGER.warn("No '" + cxfConfigurationFile + "' CXF configuration file found on the classpath. Falling back to default CXF JaxWsDynamicClient.");
			dynamicClientFactory = JaxWsDynamicClientFactory.newInstance();
		}
		return dynamicClientFactory;
	}
	
}
