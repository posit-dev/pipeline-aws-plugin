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

package de.taimos.pipeline.aws.utils;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class S3Utils {

	private static final int NOT_FOUND = 404;

	private S3Utils() {
	}

	/**
	 * v2 dropped v1's doesObjectExist convenience method, so this restores it.
	 *
	 * v1 implemented it as getObjectMetadata plus "treat any 404 as absent", which means a missing
	 * bucket answered false rather than throwing, and a 403 propagated. Catching NoSuchKeyException
	 * alone would not reproduce that - a missing bucket raises NoSuchBucketException instead - so the
	 * status code is what is matched on, exactly as v1 did.
	 */
	/**
	 * Waits for a transfer and unwraps the failure.
	 *
	 * CompletableFuture.join wraps whatever went wrong in a CompletionException, which is an artifact
	 * of the transfer manager being asynchronous rather than anything a pipeline should have to know
	 * about. v1's waitForCompletion threw the AmazonS3Exception itself, so a Jenkinsfile could catch
	 * the S3 error; leaving the wrapper in place would break that, and the wrapper's own message
	 * buries the useful one. The cause is rethrown instead.
	 */
	public static <T> T joinTransfer(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw e;
		}
	}

	public static boolean doesObjectExist(S3Client s3Client, String bucket, String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) {
				return false;
			}
			throw e;
		}
	}
}
