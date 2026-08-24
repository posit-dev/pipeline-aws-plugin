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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a directory download puts files on disk.
 *
 * v1 recreated the object's full key under the destination, so s3Download(path: 'a/b/', file: 'out')
 * produced out/a/b/x.txt. v2's DownloadDirectoryHelper resolves each key relative to the listing
 * prefix instead, which would put the same object at out/x.txt - a silent change: the download
 * succeeds, and only a later step reading the file by its full key path fails.
 *
 * This drives a real S3TransferManager over a stubbed asynchronous client, so the assertion is on
 * actual files rather than on a request object. Mocking the transfer manager, as the other download
 * tests do, cannot observe this at all.
 */
public class S3DownloadLayoutTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Rule
	public Timeout timeout = Timeout.seconds(60);

	private static S3AsyncClient stubClientReturning(List<String> keys) {
		S3AsyncClient client = Mockito.mock(S3AsyncClient.class);

		// stub the underlying call first: the real publisher drives it, so it must already answer
		Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class)))
				.thenReturn(CompletableFuture.completedFuture(ListObjectsV2Response.builder()
						.contents(keys.stream()
								.map(k -> S3Object.builder().key(k).size(4L).build())
								.collect(java.util.stream.Collectors.toList()))
						.isTruncated(false)
						.build()));
		Mockito.when(client.listObjectsV2Paginator(Mockito.any(ListObjectsV2Request.class)))
				.thenAnswer((Answer<ListObjectsV2Publisher>) invocation ->
						new ListObjectsV2Publisher(client, invocation.getArgument(0)));

		Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class), Mockito.any(AsyncResponseTransformer.class)))
				.thenAnswer(invocation -> {
					AsyncResponseTransformer<GetObjectResponse, ?> transformer = invocation.getArgument(1);
					CompletableFuture<?> future = transformer.prepare();
					transformer.onResponse(GetObjectResponse.builder().contentLength(4L).build());
					transformer.onStream(AsyncRequestBody.fromString("data"));
					return future;
				});
		return client;
	}

	private CompletedDirectoryDownload download(String prefix, List<String> keys, File destination) {
		try (S3AsyncClient client = stubClientReturning(keys);
				S3TransferManager mgr = S3TransferManager.builder().s3Client(client).build()) {
			// exactly the request the step issues - layout depends on the destination and the listing
			// prefix together, so a request rebuilt here could stop matching without anyone noticing
			return mgr.downloadDirectory(
					S3DownloadStep.downloadDirectoryRequest("my-bucket", destination, prefix))
					.completionFuture().join();
		}
	}

	/**
	 * The case that changed: an object under a requested prefix keeps its full key path under the
	 * destination, as it did under v1.
	 */
	@Test
	public void aPrefixedDownloadKeepsTheFullKeyPath() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("a/b/",
				Arrays.asList("a/b/x.txt", "a/b/nested/y.txt"), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "a/b/x.txt")).exists();
		assertThat(new File(destination, "a/b/nested/y.txt")).exists();
	}

	/**
	 * Without a prefix the two layouts agree, so this pins that the fix did not shift the no-path case.
	 */
	@Test
	public void anUnprefixedDownloadIsUnchanged() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("",
				Arrays.asList("top.txt", "sub/deeper.txt"), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "top.txt")).exists();
		assertThat(new File(destination, "sub/deeper.txt")).exists();
	}

	/**
	 * The zero-byte "folder marker" objects the S3 console creates, whose key ends in the delimiter.
	 * v2 normalises such a key to an empty relative path, which resolves to the destination directory
	 * itself and cannot be written as a file - so without the filter each one is a failed transfer,
	 * and the step's failed-transfer check would turn a console-created folder into a failed build.
	 */
	@Test
	public void folderMarkerObjectsAreSkippedRatherThanFailingTheDownload() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("a/b/",
				Arrays.asList("a/b/", "a/b/x.txt"), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "a/b/x.txt")).exists();
	}
}
