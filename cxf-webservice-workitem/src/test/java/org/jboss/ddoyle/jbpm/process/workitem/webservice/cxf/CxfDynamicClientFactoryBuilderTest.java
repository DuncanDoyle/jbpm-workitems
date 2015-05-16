package org.jboss.ddoyle.jbpm.process.workitem.webservice.cxf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Test class for the {@link CxfDynamicClientFactoryBuilder}.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfDynamicClientFactoryBuilderTest {
	
	private static final String TEST_CXF_CONFIGURATION_FILE = "testCxf.xml";
	
	private static final String HTTP_CONDUIT_BEAN_NAME = "{http://www.jboss.org/ddoyle/simple-web-service/0.0.1}TestSimpleWebServiceBeanPort.http-conduit";
			

	@Test
	public void testSetClassLoader() throws Exception {
		ClassLoader cl = this.getClass().getClassLoader();
		CxfDynamicClientFactoryBuilder builder = new CxfDynamicClientFactoryBuilder().setClassLoader(cl);
		Field clField = CxfDynamicClientFactoryBuilder.class.getDeclaredField("cl");
		clField.setAccessible(true);
		assertEquals(cl, clField.get(builder));
	}

	@Test
	public void testSetCxfConfigurationFile() throws Exception {
		String cxfConfigurationFile = TEST_CXF_CONFIGURATION_FILE;
		CxfDynamicClientFactoryBuilder builder = new CxfDynamicClientFactoryBuilder().setCxfConfigurationFile(cxfConfigurationFile);
		Field configField = CxfDynamicClientFactoryBuilder.class.getDeclaredField("cxfConfigurationFile");
		configField.setAccessible(true);
		assertEquals(cxfConfigurationFile, configField.get(builder));
	}

	@Test
	public void testBuild() throws Exception {
		DynamicClientFactory dcf = new CxfDynamicClientFactoryBuilder().setClassLoader(this.getClass().getClassLoader())
				.setCxfConfigurationFile(TEST_CXF_CONFIGURATION_FILE).build();
		Field busField = DynamicClientFactory.class.getDeclaredField("bus");
		busField.setAccessible(true);
		Bus bus = (Bus) busField.get(dcf);
		assertTrue(bus instanceof SpringBus);
		
		SpringBus springBus = (SpringBus) bus;
		Field appCtxField = SpringBus.class.getDeclaredField("ctx");
		appCtxField.setAccessible(true);
		
		AbstractApplicationContext appCxt = (AbstractApplicationContext) appCtxField.get(springBus);
		
		boolean contains = false;
		String[] beanDefinitionNames = appCxt.getBeanDefinitionNames();
		for (String nextBeanDefName: beanDefinitionNames) {
			if (HTTP_CONDUIT_BEAN_NAME.equals(nextBeanDefName)) {
				contains = true;
			}
		}
		assertTrue(contains);
	}
}
