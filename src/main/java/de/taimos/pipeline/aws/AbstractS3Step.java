/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

import java.io.Serializable;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

public abstract class AbstractS3Step extends Step {

	protected boolean pathStyleAccessEnabled = false;
	protected boolean payloadSigningEnabled = false;

	protected AbstractS3Step(final boolean pathStyleAccessEnabled, final boolean payloadSigningEnabled) {
		this.pathStyleAccessEnabled = pathStyleAccessEnabled;
		this.payloadSigningEnabled = payloadSigningEnabled;
	}

	public boolean isPathStyleAccessEnabled() {
		return this.pathStyleAccessEnabled;
	}

	@DataBoundSetter
	public void setPathStyleAccessEnabled(final boolean pathStyleAccessEnabled) {
		this.pathStyleAccessEnabled = pathStyleAccessEnabled;
	}

	public boolean isPayloadSigningEnabled() {
		return this.payloadSigningEnabled;
	}

	@DataBoundSetter
	public void setPayloadSigningEnabled(final boolean payloadSigningEnabled) {
		this.payloadSigningEnabled = payloadSigningEnabled;
	}

	protected S3ClientOptions createS3ClientOptions() {
		S3ClientOptions options = new S3ClientOptions();
		options.setPathStyleAccessEnabled(this.isPathStyleAccessEnabled());
		options.setPayloadSigningEnabled(this.isPayloadSigningEnabled());
		return options;
	}

	public static class S3ClientOptions implements Serializable {
		private boolean pathStyleAccessEnabled = false;
		private boolean payloadSigningEnabled = false;

		public boolean isPathStyleAccessEnabled() {
			return this.pathStyleAccessEnabled;
		}

		public void setPathStyleAccessEnabled(final boolean pathStyleAccessEnabled) {
			this.pathStyleAccessEnabled = pathStyleAccessEnabled;
		}

		public boolean isPayloadSigningEnabled() {
			return this.payloadSigningEnabled;
		}

		public void setPayloadSigningEnabled(final boolean payloadSigningEnabled) {
			this.payloadSigningEnabled = payloadSigningEnabled;
		}

		/**
		 * Still v1, and the only remaining callers are s3Copy, s3Upload and s3Download - all of which
		 * go through TransferManager, which needs the asynchronous client. Removed with them.
		 */
		protected AmazonS3ClientBuilder createAmazonS3ClientBuilder() {
			return AmazonS3ClientBuilder.standard()
					.withPathStyleAccessEnabled(this.isPathStyleAccessEnabled())
					.withPayloadSigningEnabled(this.isPayloadSigningEnabled());
		}

		public S3ClientBuilder createS3ClientBuilder() {
			return this.applyTo(S3Client.builder());
		}

		/**
		 * v1's TransferManagerConfiguration defaults, read off the v1 jar rather than assumed: an
		 * upload became multipart above 16 MiB, a copy above 5 GiB, with a 5 MiB minimum part.
		 *
		 * These matter beyond large-object support. A multipart object's ETag is a hash of part hashes
		 * with a -N suffix rather than the content MD5, so a pipeline comparing an ETag against a
		 * locally computed digest sees a different answer once an object crosses the threshold.
		 * Leaving the SDK's 8 MiB default in place would have moved that boundary for both operations.
		 */
		public static final long MULTIPART_UPLOAD_THRESHOLD_BYTES = 16L * 1024 * 1024;
		public static final long MULTIPART_COPY_THRESHOLD_BYTES = 5L * 1024 * 1024 * 1024;
		private static final long MINIMUM_PART_SIZE_BYTES = 5L * 1024 * 1024;

		/**
		 * Only for S3TransferManager, which has no synchronous form.
		 *
		 * multipartEnabled is not the SDK default, and without it the transfer manager issues a single
		 * PutObject or CopyObject - which S3 rejects above 5 GiB, so objects that worked under v1
		 * would start failing. The threshold is per client, and v1's differed between uploads and
		 * copies, so each step passes its own.
		 */
		public S3AsyncClientBuilder createS3AsyncClientBuilder(long multipartThresholdBytes) {
			return this.applyTo(S3AsyncClient.builder())
					.multipartEnabled(true)
					.multipartConfiguration(c -> c
							.thresholdInBytes(multipartThresholdBytes)
							.minimumPartSizeInBytes(MINIMUM_PART_SIZE_BYTES));
		}

		public S3AsyncClientBuilder createS3AsyncClientBuilder() {
			return this.createS3AsyncClientBuilder(MULTIPART_UPLOAD_THRESHOLD_BYTES);
		}

		/**
		 * v1 had pathStyleAccessEnabled and payloadSigningEnabled directly on the client builder;
		 * v2 moves both under S3Configuration.
		 *
		 * pathStyleAccessEnabled maps across unchanged. payloadSigningEnabled has no exact v2
		 * equivalent: v1 used it to sign the request body in one piece instead of relying on
		 * aws-chunked signing, so the closest v2 expression is to turn chunked encoding off. Left
		 * at the SDK default when the option is off, which is v1's default too.
		 */
		public S3Configuration createS3Configuration() {
			S3Configuration.Builder configuration = S3Configuration.builder()
					.pathStyleAccessEnabled(this.isPathStyleAccessEnabled());
			if (this.isPayloadSigningEnabled()) {
				configuration.chunkedEncodingEnabled(false);
			}
			return configuration.build();
		}

		private <B extends S3BaseClientBuilder<B, ?>> B applyTo(B builder) {
			return builder.serviceConfiguration(this.createS3Configuration());
		}
	}

}
