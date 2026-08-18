package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class EBWaitOnEnvironmentHealthStepTest {
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @After
    public void resetClient() {
        // the factory delegate is static and would otherwise stay installed for whichever test
        // class runs next in the same JVM, handing it a mocked ElasticBeanstalkClient
        EBTestingUtils.resetElasticBeanstalkClient();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentHealthStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentHealthStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentHealth", stepDescriptor.getFunctionName());
    }

    /**
     * "Green" is a modelled EnvironmentHealth, so health().toString() would pass the test below
     * too. Only a value outside the enum distinguishes healthAsString(), and there a regression
     * hangs this step's polling loop rather than failing it.
     */
    @Test
    public void waitStopsOnAHealthOutsideTheEnum() throws Exception {
        EBWaitOnEnvironmentHealthStep step = new EBWaitOnEnvironmentHealthStep("my application", "my-environment");
        step.setHealth("SomeFutureHealth");
        step.setStabilityThreshold(0);
        EBWaitOnEnvironmentHealthStep.Execution execution = new EBWaitOnEnvironmentHealthStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .health("SomeFutureHealth")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        // the step requires the health to hold across polls, so it always describes at least twice;
        // what matters here is that it terminates at all rather than looping forever
        Mockito.verify(client, Mockito.atLeast(1)).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));
    }

    @Test
    public void waitStopImmediatelyAfterFindingGreenHealthForNoThreshold() throws Exception {
        EBWaitOnEnvironmentHealthStep step = new EBWaitOnEnvironmentHealthStep("my application", "my-environment");
        step.setStabilityThreshold(0);
        EBWaitOnEnvironmentHealthStep.Execution execution = new EBWaitOnEnvironmentHealthStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .health("Green")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        Mockito.doReturn(result).when(client).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));

        execution.run();

        Mockito.verify(client, Mockito.atMost(2)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
