package org.waterprinciple.cultivar.ensemble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.curator.RetryPolicy;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.ensemble.exhibitor.ExhibitorEnsembleProvider;
import org.apache.curator.ensemble.exhibitor.ExhibitorRestClient;
import org.apache.curator.retry.RetryNTimes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

@RunWith(MockitoJUnitRunner.class)
public class EnsembleProviderIntegTest {

    private static final String CONNECTION = "localhost:2181;localhost:2182";
    private static final int RETRY_NUMBER = 5;

    @Mock
    private ExhibitorRestClient client;

    private Module module;

    @Before
    public void setUp() {
        module = new EnsembleProviderModule();

        System.setProperty(ConnectionProvider.PROPERTY_NAME, CONNECTION);
    }

    @After
    public void tearDown() {
        System.clearProperty(ConnectionProvider.PROPERTY_NAME);
        System.clearProperty(ExhibitorProvider.PROPERTY_NAME);
    }

    @Test
    public void instance_ConnectionProviderPropertyOnly_SetsConnectionString() {

        Injector inj = Guice.createInjector(module);

        assertEquals(CONNECTION, inj.getInstance(EnsembleProvider.class).getConnectionString());
    }

    @Test
    public void instance_ConnectionAndExhibitorProviderProperties_ReturnsExhibitorProvider() {

        System.setProperty(ExhibitorProvider.PROPERTY_NAME, CONNECTION);

        Injector inj = Guice.createInjector(module);

        assertTrue(inj.getInstance(EnsembleProvider.class) instanceof ExhibitorEnsembleProvider);
    }

    @Test
    public void instance_ConnectionProviderPropertyAndOverrideBound_SetsConnectionStringToOverride() {
        Injector inj = Guice.createInjector(module, new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("Cultivar.zookeeper.connectionString")).to("localhost:2181");

            }
        });

        assertEquals("localhost:2181", inj.getInstance(EnsembleProvider.class).getConnectionString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void instance_BindingOptionalsWithExhibitor_UsesParametersInExhibitorInstance() throws Exception {
        Injector inj = Guice.createInjector(module, new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("Cultivar.zookeeper.connectionString")).to("localhost:2181");
                bindConstant().annotatedWith(Names.named("Cultivar.zookeeper.exhibitorString")).to("localhost");
                bindConstant().annotatedWith(Names.named("Cultivar.properties.exhibitor.pollingTimeMillis")).to(1000);
                bindConstant().annotatedWith(Names.named("Cultivar.properties.exhibitor.restPath")).to("/exhibitor");
                bindConstant().annotatedWith(Names.named("Cultivar.properties.exhibitor.restPort")).to(8081);

                bind(ExhibitorRestClient.class).toInstance(client);
                bind(RetryPolicy.class).annotatedWith(Names.named("Cultivar.properties.exhibitor.retryPolicy"))
                        .toInstance(new RetryNTimes(RETRY_NUMBER, 1));
            }
        });

        ExhibitorEnsembleProvider provider = (ExhibitorEnsembleProvider) inj.getInstance(EnsembleProvider.class);

        provider.pollForInitialEnsemble();

        verify(client, times(RETRY_NUMBER + 1)).getRaw(eq("localhost"), eq(8081), eq("/exhibitor"), anyString());
        verifyNoMoreInteractions(client);
    }
}
