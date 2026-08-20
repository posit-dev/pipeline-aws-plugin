/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2018 Taimos GmbH
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

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Presigning is computed in process - no request is sent - so these run against a real S3Presigner
 * with throwaway credentials and assert the URL it produces. That is a better trade than the v1
 * version of this test, which mocked generatePresignedUrl and so only proved the step called it.
 *
 * The credentials arrive through the environment because AWSClientFactory resolves them from
 * EnvVars, which is also what withAWS populates.
 */
public class S3PresignUrlStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	private static final String CREDS = "'AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE', "
			+ "'AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY', "
			+ "'AWS_REGION=us-west-2'";

	private Run runPresign(String jobName, String args) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withEnv([" + CREDS + "]) {\n"
				+ "    def url = s3PresignURL(" + args + ")\n"
				+ "    echo \"url=${url}\"\n"
				+ "  }\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void presignsAGetWithTheDefaultExpiration() throws Exception {
		Run run = this.runPresign("s3PresignDefault", "bucket: 'foo', key: 'bar'");

		this.jenkinsRule.assertLogContains("url=https://foo.s3.us-west-2.amazonaws.com/bar?", run);
		this.jenkinsRule.assertLogContains("X-Amz-Expires=60", run);
		this.jenkinsRule.assertLogContains("X-Amz-Credential=AKIAIOSFODNN7EXAMPLE", run);
	}

	@Test
	public void durationInSecondsReachesTheSignature() throws Exception {
		Run run = this.runPresign("s3PresignDuration", "bucket: 'foo', key: 'bar', durationInSeconds: 3600");

		this.jenkinsRule.assertLogContains("X-Amz-Expires=3600", run);
	}

	/**
	 * The four methods v2 can presign. Each produces a different signature, so the assertion is that
	 * every one is accepted and signed rather than quietly falling back to GET.
	 */
	@Test
	public void presignsEachSupportedMethod() throws Exception {
		for (String method : new String[]{"GET", "PUT", "DELETE", "HEAD"}) {
			Run run = this.runPresign("s3Presign" + method, "bucket: 'foo', key: 'bar', httpMethod: '" + method + "'");

			this.jenkinsRule.assertLogContains("url=https://foo.s3.us-west-2.amazonaws.com/bar?", run);
			this.jenkinsRule.assertLogContains("X-Amz-Signature=", run);
		}
	}

	@Test
	public void lowerCaseMethodIsAccepted() throws Exception {
		Run run = this.runPresign("s3PresignLowerCase", "bucket: 'foo', key: 'bar', httpMethod: 'put'");

		this.jenkinsRule.assertLogContains("X-Amz-Signature=", run);
	}

	/**
	 * v1 accepted POST and PATCH because it signed whatever HttpMethod it was handed. v2 presigns per
	 * operation and has no equivalent for either, so the step must say so rather than hand back a URL
	 * signed for the wrong verb.
	 */
	@Test
	public void unsupportedMethodsAreRejected() throws Exception {
		for (String method : new String[]{"POST", "PATCH"}) {
			WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3PresignBad" + method);
			job.setDefinition(new CpsFlowDefinition(""
					+ "node {\n"
					+ "  s3PresignURL(bucket: 'foo', key: 'bar', httpMethod: '" + method + "')\n"
					+ "}\n", true)
			);
			Run run = this.jenkinsRule.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));
			this.jenkinsRule.assertLogContains("httpMethod must be one of [GET, PUT, DELETE, HEAD]", run);
		}
	}

	/**
	 * pathStyleAccessEnabled has to reach the presigner too, or a MinIO-style endpoint gets a
	 * virtual-host URL it cannot serve.
	 */
	@Test
	public void pathStyleAccessChangesTheUrlShape() throws Exception {
		Run run = this.runPresign("s3PresignPathStyle", "bucket: 'foo', key: 'bar', pathStyleAccessEnabled: true");

		this.jenkinsRule.assertLogContains("url=https://s3.us-west-2.amazonaws.com/foo/bar?", run);
	}
}
