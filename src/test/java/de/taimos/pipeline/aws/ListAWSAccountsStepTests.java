package de.taimos.pipeline.aws;

import com.amazonaws.services.organizations.AWSOrganizations;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsForParentResult;
import com.amazonaws.services.organizations.model.ListAccountsResult;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

/**
 * Pins the map keys that {@code listAWSAccounts} returns to the pipeline. The keys are built
 * by hand in the step, but the values come straight off the SDK model, so this guards the
 * mapping through an SDK upgrade.
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
	public void listAccounts() throws Exception {
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

	@Test
	public void listAccountsForParentFollowsPagination() throws Exception {
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
		Mockito.verify(this.organizations, Mockito.times(2)).listAccountsForParent(Mockito.any());
	}
}
