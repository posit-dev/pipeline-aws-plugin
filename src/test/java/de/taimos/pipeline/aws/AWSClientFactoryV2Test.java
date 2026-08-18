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

import hudson.EnvVars;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the SDK v2 half of {@link AWSClientFactory}, asserting it reproduces the v1 semantics
 * these environment variables have always had.
 */
public class AWSClientFactoryV2Test {

	@Test
	public void awsDefaultRegionWinsOverAwsRegion() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_DEFAULT_REGION, "eu-central-1");
		vars.put(AWSClientFactory.AWS_REGION, "us-east-1");

		assertThat(AWSClientFactory.getV2Region(vars)).isEqualTo(Region.EU_CENTRAL_1);
	}

	@Test
	public void awsRegionIsUsedWhenDefaultRegionIsAbsent() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "ap-southeast-2");

		assertThat(AWSClientFactory.getV2Region(vars)).isEqualTo(Region.AP_SOUTHEAST_2);
	}

	/**
	 * Regions absent from the v1 Regions enum - the reason this migration was requested - resolve
	 * fine in v2, which treats region ids as opaque strings.
	 */
	@Test
	public void resolvesRegionsUnknownToTheV1Sdk() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "ap-southeast-5");

		assertThat(AWSClientFactory.getV2Region(vars).id()).isEqualTo("ap-southeast-5");
	}

	@Test
	public void staticCredentialsAreUsedWhenBothKeysArePresent() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");

		AwsCredentialsProvider provider = AWSClientFactory.getV2Credentials(vars, null);

		assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
		AwsCredentials credentials = provider.resolveCredentials();
		assertThat(credentials.accessKeyId()).isEqualTo("AKIAEXAMPLE");
		assertThat(credentials.secretAccessKey()).isEqualTo("secret");
		assertThat(credentials).isNotInstanceOf(AwsSessionCredentials.class);
	}

	@Test
	public void aSessionTokenProducesSessionCredentials() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");
		vars.put(AWSClientFactory.AWS_SESSION_TOKEN, "token");

		AwsCredentials credentials = AWSClientFactory.getV2Credentials(vars, null).resolveCredentials();

		assertThat(credentials).isInstanceOf(AwsSessionCredentials.class);
		assertThat(((AwsSessionCredentials) credentials).sessionToken()).isEqualTo("token");
	}

	/**
	 * v1 counts retries after the initial call (maxErrorRetry 10 means 11 calls), v2 counts the
	 * initial call within maxAttempts, so the value carries over as retries + 1 to keep the number
	 * of calls against AWS identical.
	 *
	 * The configuration is applied as a configurator rather than a fixed strategy - matching v1,
	 * which passed nulls for the retry condition and backoff to keep the SDK defaults and overrode
	 * only the count - so the assertion applies it to a strategy builder the way the SDK does.
	 */
	private static int resolveMaxAttempts(ClientOverrideConfiguration configuration) {
		RetryStrategy.Builder<?, ?> builder = AwsRetryStrategy.standardRetryStrategy().toBuilder();
		configuration.retryStrategyConfigurator().get().accept(builder);
		return builder.build().maxAttempts();
	}

	@Test
	public void retryCountKeepsTheV1NumberOfAttempts() {
		assertThat(resolveMaxAttempts(AWSClientFactory.getV2OverrideConfiguration(new EnvVars()))).isEqualTo(11);

		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_RETRIES, "3");
		assertThat(resolveMaxAttempts(AWSClientFactory.getV2OverrideConfiguration(vars))).isEqualTo(4);
	}
}
