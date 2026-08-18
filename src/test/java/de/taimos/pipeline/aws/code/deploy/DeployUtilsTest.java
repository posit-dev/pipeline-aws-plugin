/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.code.deploy;

import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.DeploymentInfo;
import software.amazon.awssdk.services.codedeploy.model.DeploymentStatus;
import software.amazon.awssdk.services.codedeploy.model.ErrorInformation;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentResponse;

import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * waitDeployment polls until the deployment status matches one of three literals derived from the
 * SDK enum. If those literals stopped matching what the API returns, the loop would never reach a
 * terminal branch and the build would hang rather than fail - which no other test would notice.
 */
public class DeployUtilsTest {

	private CodeDeployClient client;
	private TaskListener listener;

	@Before
	public void setup() {
		this.client = Mockito.mock(CodeDeployClient.class);
		this.listener = Mockito.mock(TaskListener.class);
		Mockito.when(this.listener.getLogger()).thenReturn(Mockito.mock(PrintStream.class));
	}

	/**
	 * The comparison relies on the v2 enum rendering the wire value rather than its Java constant
	 * name. Pinned explicitly because everything else in this class depends on it.
	 */
	@Test
	public void statusEnumsRenderTheWireValues() {
		assertThat(DeploymentStatus.SUCCEEDED.toString()).isEqualTo("Succeeded");
		assertThat(DeploymentStatus.FAILED.toString()).isEqualTo("Failed");
		assertThat(DeploymentStatus.STOPPED.toString()).isEqualTo("Stopped");
	}

	private void stubStatus(String status, String errorMessage) {
		DeploymentInfo.Builder info = DeploymentInfo.builder().status(status);
		if (errorMessage != null) {
			info.errorInformation(ErrorInformation.builder().message(errorMessage).build());
		}
		Mockito.when(this.client.getDeployment(Mockito.any(GetDeploymentRequest.class)))
				.thenReturn(GetDeploymentResponse.builder().deploymentInfo(info.build()).build());
	}

	@Test
	public void returnsOnceTheDeploymentSucceeds() throws Exception {
		this.stubStatus("Succeeded", null);

		new DeployUtils().waitDeployment("d-1", this.listener, this.client);

		Mockito.verify(this.client).getDeployment(Mockito.any(GetDeploymentRequest.class));
	}

	@Test
	public void failsWithTheErrorMessageFromAws() throws Exception {
		this.stubStatus("Failed", "the boom happened");

		assertThatThrownBy(() -> new DeployUtils().waitDeployment("d-1", this.listener, this.client))
				.hasMessageContaining("the boom happened");
	}

	@Test
	public void failsWhenTheDeploymentIsStopped() throws Exception {
		this.stubStatus("Stopped", null);

		assertThatThrownBy(() -> new DeployUtils().waitDeployment("d-1", this.listener, this.client))
				.hasMessageContaining("stopped");
	}

	@Test
	public void passesTheDeploymentIdThrough() throws Exception {
		this.stubStatus("Succeeded", null);

		new DeployUtils().waitDeployment("d-42", this.listener, this.client);

		Mockito.verify(this.client).getDeployment(GetDeploymentRequest.builder().deploymentId("d-42").build());
	}
}
