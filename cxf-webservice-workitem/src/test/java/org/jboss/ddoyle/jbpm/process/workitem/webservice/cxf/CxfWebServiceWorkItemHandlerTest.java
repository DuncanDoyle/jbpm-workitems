package org.jboss.ddoyle.jbpm.process.workitem.webservice.cxf;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Test class for the {@link CxfWebServiceWorkItemHandler}.
 *   
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceWorkItemHandlerTest {

	private static final String HTTP_CONDUIT_BEAN_NAME = "{http://www.jboss.org/ddoyle/simple-web-service/0.0.1}MySimpleWebServiceBeanPort.http-conduit";
	
	@Test
	public void testGetDynamicClientFactory() throws Exception {
		CxfWebServiceWorkItemHandler wih = new CxfWebServiceWorkItemHandler(null, this.getClass().getClassLoader());
		DynamicClientFactory dcf = wih.getDynamicClientFactory();
		
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
