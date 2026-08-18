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

package de.taimos.pipeline.aws;

import com.amazonaws.services.organizations.AWSOrganizations;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsForParentRequest;
import com.amazonaws.services.organizations.model.ListAccountsForParentResult;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the map keys that {@code listAWSAccounts} returns to the pipeline, and the pagination
 * contract. The keys are built by hand in the step, but the values and the paging tokens come
 * straight off the SDK model, so this guards the mapping through an SDK upgrade.
 */
public class ListAWSAccountsStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AWSOrganizations organizations;

	@Before
	public void setupSdk() throws Exception {
		this.organizations = Mockito.mock(AWSOrganizations.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.organizations);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private static Account account(String id, String name) {
		return new Account()
				.withId(id)
				.withArn("arn:aws:organizations::123456789012:account/o-exampleorg/" + id)
				.withName(name)
				.withStatus("ACTIVE");
	}

	@Test
	public void listAccountsExposesEveryKey() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any())).thenReturn(new ListAccountsResult()
				.withAccounts(account("111111111111", "My Account"))
		);

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"accountsCount=${accounts.size()}\"\n"
				+ "  echo \"id=${accounts[0].id}\"\n"
				+ "  echo \"arn=${accounts[0].arn}\"\n"
				+ "  echo \"name=${accounts[0].name}\"\n"
				+ "  echo \"safeName=${accounts[0].safeName}\"\n"
				+ "  echo \"status=${accounts[0].status}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("accountsCount=1", run);
		this.jenkinsRule.assertLogContains("id=111111111111", run);
		this.jenkinsRule.assertLogContains("arn=arn:aws:organizations::123456789012:account/o-exampleorg/111111111111", run);
		this.jenkinsRule.assertLogContains("name=My Account", run);
		this.jenkinsRule.assertLogContains("safeName=my-account", run);
		this.jenkinsRule.assertLogContains("status=ACTIVE", run);
	}

	/**
	 * The second page must be requested with the token returned by the first. Asserting only the
	 * call count would stay green if the token stopped being propagated, since the mock returns
	 * the second page regardless of what it is asked for.
	 */
	@Test
	public void listAccountsPropagatesPagingToken() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any()))
				.thenReturn(new ListAccountsResult()
						.withAccounts(account("111111111111", "First"))
						.withNextToken("next"))
				.thenReturn(new ListAccountsResult()
						.withAccounts(account("222222222222", "Second")));

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsPagingTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"ids=${accounts.collect { it.id }.toString()}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("ids=[111111111111, 222222222222]", run);

		ArgumentCaptor<ListAccountsRequest> captor = ArgumentCaptor.forClass(ListAccountsRequest.class);
		Mockito.verify(this.organizations, Mockito.times(2)).listAccounts(captor.capture());
		List<ListAccountsRequest> requests = captor.getAllValues();
		assertThat(requests.get(0).getNextToken()).isNull();
		assertThat(requests.get(1).getNextToken()).isEqualTo("next");
	}

	@Test
	public void listAccountsForParentPropagatesParentAndPagingToken() throws Exception {
		Mockito.when(this.organizations.listAccountsForParent(Mockito.any()))
				.thenReturn(new ListAccountsForParentResult()
						.withAccounts(account("111111111111", "First"))
						.withNextToken("next"))
				.thenReturn(new ListAccountsForParentResult()
						.withAccounts(account("222222222222", "Second")));

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsParentTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts(parent: 'ou-1234')\n"
				+ "  echo \"accountsCount=${accounts.size()}\"\n"
				+ "  echo \"ids=${accounts.collect { it.id }.toString()}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("accountsCount=2", run);
		this.jenkinsRule.assertLogContains("ids=[111111111111, 222222222222]", run);

		ArgumentCaptor<ListAccountsForParentRequest> captor = ArgumentCaptor.forClass(ListAccountsForParentRequest.class);
		Mockito.verify(this.organizations, Mockito.times(2)).listAccountsForParent(captor.capture());
		List<ListAccountsForParentRequest> requests = captor.getAllValues();
		assertThat(requests.get(0).getParentId()).isEqualTo("ou-1234");
		assertThat(requests.get(0).getNextToken()).isNull();
		assertThat(requests.get(1).getParentId()).isEqualTo("ou-1234");
		assertThat(requests.get(1).getNextToken()).isEqualTo("next");
	}
}
