/*
 * Copyright 2018 CloudBees, Inc.
 *
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
 */

package de.taimos.pipeline.aws;

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;

@For(S3UploadStep.class)
public class S3UploadStepTransferManagerIntegrationTest {

	private S3TransferManager transferManager;

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Before
	public void setupSdk() throws Exception {
		transferManager = Mockito.mock(S3TransferManager.class);
		AWSClientFactory.setV2FactoryDelegate((x) -> Mockito.mock(S3AsyncClient.class));
		AWSUtilFactory.setV2TransferManagerSupplier(() -> transferManager);
	}

	@org.junit.After
	public void tearDownSdk() {
		AWSClientFactory.setV2FactoryDelegate(null);
		AWSUtilFactory.setV2TransferManagerSupplier(null);
	}

	@Test
	public void useFileListUploaderWhenIncludePathPatternDefined() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  writeFile file: 'work/subdir/test.txt', text: 'Hello!'\n"
				+ "  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.txt', workingDir: 'work')"
				+ "}\n", true)
		);

		FileUpload upload = Mockito.mock(FileUpload.class);
		Mockito.when(upload.completionFuture()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
				CompletedFileUpload.builder().response(PutObjectResponse.builder().build()).build()));
		Mockito.when(transferManager.uploadFile(Mockito.any(UploadFileRequest.class))).thenReturn(upload);

		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// v1 handed the whole file list to uploadFileList; v2 has no such call, so the step submits
		// one uploadFile per resolved file. The key still has to be relative to workingDir, not to
		// the workspace, or the subdirectory would be lost from the object name.
		ArgumentCaptor<UploadFileRequest> captor = ArgumentCaptor.forClass(UploadFileRequest.class);
		Mockito.verify(transferManager).uploadFile(captor.capture());
		Mockito.verify(transferManager).close();
		Mockito.verifyNoMoreInteractions(transferManager);

		Assert.assertEquals("test-bucket", captor.getValue().putObjectRequest().bucket());
		Assert.assertEquals("subdir/test.txt", captor.getValue().putObjectRequest().key());
		assertThat(captor.getValue().source().toString(), matchesRegex("^.*subdir.test.txt$"));
		assertThat(captor.getValue().source().toString(), containsString("work"));
	}

	@Test
	public void shouldNotUploadAnythingWhenPatternDoNotMatchAnyFile() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  writeFile file: 'work/subdir/test.txt', text: 'Hello!'\n"
				+ "  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.no-match', workingDir: 'work')"
				+ "}\n", true)
		);

		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("Nothing to upload", run);

		Mockito.verifyNoMoreInteractions(transferManager);
	}
}
