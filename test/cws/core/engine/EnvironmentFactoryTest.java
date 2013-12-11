package cws.core.engine;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import cws.core.cloudsim.CloudSimWrapper;
import cws.core.simulation.StorageCacheType;
import cws.core.simulation.StorageSimulationParams;
import cws.core.simulation.StorageType;

/**
 * Tests for {@link EnvironmentFactory} class.
 */
public class EnvironmentFactoryTest {
    private StorageSimulationParams storageParams;
    private CloudSimWrapper cloudsim;

    @Before
    public void before() {
        cloudsim = new CloudSimWrapper();
        cloudsim.init();
        storageParams = new StorageSimulationParams(StorageType.VOID, null, StorageCacheType.FIFO);
    }

    @Test
    public void testCreateStorageAwareEnvironment() {
        Environment environment = EnvironmentFactory.createEnvironment(cloudsim, storageParams, true);
        assertTrue(environment instanceof StorageAwareEnvironment);
    }

    @Test
    public void testCreateStorageUnawareEnvironment() {
        Environment environment = EnvironmentFactory.createEnvironment(cloudsim, storageParams, false);
        assertTrue(environment instanceof StorageUnawareEnvironment);
    }
}
