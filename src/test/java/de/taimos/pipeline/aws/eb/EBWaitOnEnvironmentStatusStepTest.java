package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class EBWaitOnEnvironmentStatusStepTest {
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentStatusStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentStatusStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentStatus", stepDescriptor.getFunctionName());
    }

    @Test
    public void waitStopImmediatelyAfterFindingReadyStatus() throws Exception {
        EBWaitOnEnvironmentStatusStep step = new EBWaitOnEnvironmentStatusStep("my application", "my-environment");
        EBWaitOnEnvironmentStatusStep.Execution execution = new EBWaitOnEnvironmentStatusStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("Ready")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
