package org.jbpm.process.workitem.infinispan;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.core.process.instance.WorkItem;
import org.drools.core.process.instance.impl.DefaultWorkItemManager;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jbpm.bpmn2.handler.WorkItemHandlerRuntimeException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.runtime.process.WorkItemManager;
import org.mockito.Mockito;

/**
 * JUnit tests for the {@link InfinispanWorkItemHandler}.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class InfinispanWorkItemHandlerTest {

	// Configure JBoss Logging provider to use SLF4J.
	static {
		System.setProperty("org.jboss.logging.provider", "slf4j");
	}

	private static final String INFINISPAN_CONFIG_FILE_NAME = "infinispan/infinispan.xml";

	private static EmbeddedCacheManager cacheManager;

	private static InfinispanWorkItemHandler ispnWih = new InfinispanWorkItemHandler();

	@BeforeClass
	public static void getCacheManager() {

		cacheManager = InfinispanTestUtil.getCacheManager(INFINISPAN_CONFIG_FILE_NAME);
	}

	@AfterClass
	public static void disposeCacheManager() {
		InfinispanTestUtil.disposeCacheManager(cacheManager);
	}

	// @Test
	public void testInfinispanPut() {

		// TODO: implement test
		assertTrue(false);

	}

	// @Test
	public void testInfinispanGet() {

		// TODO: implement test
		assertTrue(false);
	}

	/**
	 * Test passing an incorrect or not-supported ISPN operation. We should get an {@link IllegalArgumentException}, which will cause the
	 * WIH to blow up on first usage. It's basically an incorrect configuration in the process, so blowing up quickly and not handling the
	 * exception is actually a proper way to deal with these situaions.
	 */
	@Test
	public void testIncorrectOperationName() {
		WorkItem workItem = new WorkItemImpl();

		// Set parameters
		Map<String, Object> testParams = new HashMap<String, Object>();
		testParams.put("operation", "FAIL");
		testParams.put("key", "testKey1");
		testParams.put("value", "testValue1");

		workItem.setParameters(testParams);
		((WorkItemImpl) workItem).setId(1);

		WorkItemManager wiManager = Mockito.mock(DefaultWorkItemManager.class);
		try {
			ispnWih.executeWorkItem(workItem, wiManager);
		} catch (WorkItemHandlerRuntimeException wihre) {
			// We expect an IllegalArgumentException wrapped in a WorkItemHandlerRuntimeException.
			assertEquals(IllegalArgumentException.class, wihre.getCause().getClass());
			return;
		}
		assertTrue(false);
	}

	/**
	 * Test that we can specify the ISPN operation name in upper-case.
	 */
	@Test
	public void testOperationUpperCase() throws Exception {
		String testEndpoint = "testEndpointUpperCase";

		WorkItem workItem = new WorkItemImpl();

		// Set parameters
		Map<String, Object> testParams = new HashMap<String, Object>();
		testParams.put("endpoint", testEndpoint);
		testParams.put("operation", "PUT");
		testParams.put("key", "testKey1");
		testParams.put("value", "testValue1");

		workItem.setParameters(testParams);
		((WorkItemImpl) workItem).setId(1);

		WorkItemManager wiManager = Mockito.mock(DefaultWorkItemManager.class);

		/*
		 * We need to inject a BasicCacheContainer for our testendpoint inside the WIH to prevent the unit-test to try to access an actual
		 * ISPN instance over HotRod.
		 */
		Map<String, BasicCacheContainer> cacheContainers = new ConcurrentHashMap<String, BasicCacheContainer>();
		cacheContainers.put(testEndpoint, cacheManager);
		Field cacheContainersField = ispnWih.getClass().getDeclaredField("cacheContainers");
		cacheContainersField.setAccessible(true);
		cacheContainersField.set(ispnWih, cacheContainers);

		ispnWih.executeWorkItem(workItem, wiManager);

		// Verify that the workitem has been completed.
		verify(wiManager).completeWorkItem(anyInt(), anyMap());

		// Verify that the test values have been stored in ISPN.
		assertEquals("testValue1", cacheManager.getCache().get("testKey1"));
	}

	/**
	 * Test that we can specify the ISPN operation name in lowercase.
	 */
	@Test
	public void testOperationLowerCase() throws Exception {
		String testEndpoint = "testEndpointLowerCase";

		WorkItem workItem = new WorkItemImpl();

		// Set parameters
		Map<String, Object> testParams = new HashMap<String, Object>();
		testParams.put("endpoint", testEndpoint);
		testParams.put("operation", "put");
		testParams.put("key", "testKey1");
		testParams.put("value", "testValue1");

		workItem.setParameters(testParams);
		((WorkItemImpl) workItem).setId(1);

		WorkItemManager wiManager = Mockito.mock(DefaultWorkItemManager.class);

		/*
		 * We need to inject a BasicCacheContainer for our testendpoint inside the WIH to prevent the unit-test to try to access an actual
		 * ISPN instance over HotRod.
		 */
		Map<String, BasicCacheContainer> cacheContainers = new ConcurrentHashMap<String, BasicCacheContainer>();
		cacheContainers.put(testEndpoint, cacheManager);
		setCacheContainers(ispnWih, cacheContainers);

		ispnWih.executeWorkItem(workItem, wiManager);

		// Verify that the workitem has been completed.
		verify(wiManager).completeWorkItem(anyInt(), anyMap());

		// Verify that the test values have been stored in ISPN.
		assertEquals("testValue1", cacheManager.getCache().get("testKey1"));
	}

	/**
	 * Tests usage of different ISPN endpoints in one WorkItemHandler instance.
	 * <p>
	 * Only one WIH is registered per session. That means that one WIH can be used in different processes, which might use different ISPN
	 * instances. Furthermore, a single WIH can be used at multiple points in a process, where different nodes can use different ISPN
	 * endpoints. So the WIH needs to be able to deal with different ISPN endpoints.
	 */
	@Test
	public void testMultipleIspnEndpoints() throws Exception {
		
		
		WorkItemManager wiManager = Mockito.mock(DefaultWorkItemManager.class);

		// Set parameters
		WorkItem workItem1 = new WorkItemImpl();
		Map<String, Object> testParams1 = new HashMap<String, Object>();
		testParams1.put("endpoint", "localhost:11222");
		testParams1.put("operation", "GET");
		testParams1.put("key", "testKey1");
		testParams1.put("value", "testValue1");
		workItem1.setParameters(testParams1);

		WorkItem workItem2 = new WorkItemImpl();
		Map<String, Object> testParams2 = new HashMap<String, Object>();
		testParams2.put("endpoint", "remote:11222");
		testParams2.put("operation", "GET");
		testParams2.put("key", "testKey2");
		testParams2.put("value", "testValue2");
		workItem2.setParameters(testParams2);

		/*
		 * Configure 2 ISPN caches. We therefore need to load another cacheManager.
		 * We're going to use a Mock for this one as we can't register 2 ISPN nodes in one JVM (we get problems with JMX).
		 */
		EmbeddedCacheManager secondCacheManager = Mockito.mock(EmbeddedCacheManager.class);
		Cache secondCache = Mockito.mock(Cache.class);
		when(secondCacheManager.getCache()).thenReturn(secondCache);
		
		try {
			Map<String, BasicCacheContainer> cacheContainers = new ConcurrentHashMap<String, BasicCacheContainer>();
			cacheContainers.put("localhost:11222", cacheManager);
			cacheContainers.put("remote:11222", secondCacheManager);
			setCacheContainers(ispnWih, cacheContainers);
			
			ispnWih.executeWorkItem(workItem1, wiManager);
			ispnWih.executeWorkItem(workItem2, wiManager);
			
			//Verify the result in the cachemanagers
			assertEquals("testValue1", cacheManager.getCache().get("testKey1"));
			verify(secondCache).get("testKey2");
		} finally {
			InfinispanTestUtil.disposeCacheManager(secondCacheManager);
		}
	}

	private void setCacheContainers(InfinispanWorkItemHandler ispnWih, Map<String, BasicCacheContainer> cacheContainers) throws Exception {
		Field cacheContainersField = ispnWih.getClass().getDeclaredField("cacheContainers");
		cacheContainersField.setAccessible(true);
		cacheContainersField.set(ispnWih, cacheContainers);
	}

	/**
	 * Tests the retrieval of CacheContainers for endpoints.
	 * <p>
	 * TODO: test thread-safety of creating the CacheContainers for the same endpoint.
	 */
	public void testGetCacheContainer() {

	}

}
