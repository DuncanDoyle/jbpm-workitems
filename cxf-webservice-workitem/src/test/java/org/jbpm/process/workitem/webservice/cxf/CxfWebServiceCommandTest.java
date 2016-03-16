package org.jbpm.process.workitem.webservice.cxf;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.dynamic.DynamicClientFactory;
import org.junit.Test;
import org.kie.internal.executor.api.CommandContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Test class for the {@link CxfWebServiceCommand}.
 *   
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class CxfWebServiceCommandTest {
	
	private static final String HTTP_CONDUIT_BEAN_NAME = "{http://www.jboss.org/ddoyle/simple-web-service/0.0.1}MySimpleWebServiceBeanPort.http-conduit";

	@Test
	public void testGetDynamicClientFactory() throws Exception {
		CxfWebServiceCommand command = new CxfWebServiceCommand();
		CommandContext ctx = new CommandContext();
		ctx.setData("ClassLoader", this.getClass().getClassLoader());
		
		DynamicClientFactory dcf = command.getDynamicClientFactory(ctx);
		
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
