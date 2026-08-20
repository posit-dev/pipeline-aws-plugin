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
