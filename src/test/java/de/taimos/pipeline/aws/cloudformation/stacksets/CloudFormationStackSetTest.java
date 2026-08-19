package de.taimos.pipeline.aws.cloudformation.stacksets;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import hudson.model.TaskListener;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

public class CloudFormationStackSetTest {

	private CloudFormationClient client;
	private SleepStrategy sleepStrategy;
	private CloudFormationStackSet stackSet;

	@Before
	public void setup() {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Mockito.when(listener.getLogger()).thenReturn(System.out);
		client = Mockito.mock(CloudFormationClient.class);
		sleepStrategy = Mockito.mock(SleepStrategy.class);
		stackSet = new CloudFormationStackSet(client, "foo", listener, sleepStrategy);
	}

	@Test
	public void stackSetExists() {
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().build()).build()
				);

		Assertions.assertThat(stackSet.exists()).isTrue();
	}

	@Test
	public void stackSetDoesNotExists() {
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("stack set does not exist")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("StackSetNotFoundException").errorMessage("stack set does not exist").build())
				.build();
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		Assertions.assertThat(stackSet.exists()).isFalse();
	}

	@Test(expected = CloudFormationException.class)
	public void stackSetExistsError() {
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("stack set does not exist")
				.awsErrorDetails(AwsErrorDetails.builder().errorMessage("stack set does not exist").build())
				.build();
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		stackSet.exists();
	}

	@Test
	public void createTemplateBody() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void createTemplateUrl() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create(null, "url", Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateURL("url").build()
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNoTemplate() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		stackSet.create(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
	}

	@Test
	public void createAdministratorRoleArn() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), "foo", "baz");
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).administrationRoleARN("foo").executionRoleName("baz").tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void updateTemplateBody() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update("body", null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void updateTemplateUrl() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, "url", UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateURL("url").build()
		);
	}

	@Test
	public void updateTemplateKeepPrevious() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
	}

	@Test
	public void update_OperationInProgressException() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(OperationInProgressException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void update_StaleRequestException() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(StaleRequestException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void update_TooManyOperations_LimitExceeded() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow((LimitExceededException) LimitExceededException.builder()
						.message("StackSet operations cannot involve more than 3500")
						.awsErrorDetails(AwsErrorDetails.builder()
								.errorMessage("StackSet operations cannot involve more than 3500")
								.build())
						.build())
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void waitForStackStateStatus() throws InterruptedException {
		Mockito.when(client.describeStackSet(DescribeStackSetRequest.builder().stackSetName("foo").build()
		)).thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().status(StackSetStatus.ACTIVE).build()
				).build()
		).thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().status(StackSetStatus.DELETED).build()
				).build()
		);
		stackSet.waitForStackState(StackSetStatus.DELETED, Duration.ofMillis(5));

		Mockito.verify(client, Mockito.atLeast(2))
				.describeStackSet(Mockito.any(DescribeStackSetRequest.class));
	}

	@Test
	public void waitForOperationToComplete() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.SUCCEEDED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void waitForOperationToCompleteWithThrottle() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("error")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("Throttling").errorMessage("error").build())
				.build();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenThrow(ex)
		.thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.SUCCEEDED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test(expected = StackSetOperationFailedException.class)
	public void waitForOperationToCompleteFailure() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.FAILED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void delete() {
		stackSet.delete();
		Mockito.verify(client).deleteStackSet(DeleteStackSetRequest.builder().stackSetName("foo").build()
		);
	}
}
