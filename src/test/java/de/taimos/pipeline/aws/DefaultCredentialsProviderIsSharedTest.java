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

import org.junit.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SDK property that decides whether this plugin may close a DefaultCredentialsProvider.
 *
 * create() hands back a shared static instance, so both AWSClientFactory.getV2Credentials and
 * AWSCredentialsProviderCallable are handling an object they do not own. Closing it would tear down
 * the profile-file supplier and the container/IMDS caches for every later caller in the JVM - on an
 * agent, for every subsequent step. Only an instance from builder() is owned and closeable.
 *
 * If a future SDK makes create() return fresh instances, this test fails and the "do not close"
 * comments in those two files can be revisited rather than trusted indefinitely.
 */
public class DefaultCredentialsProviderIsSharedTest {

	@Test
	public void createReturnsASharedInstance() {
		assertThat(DefaultCredentialsProvider.create()).isSameAs(DefaultCredentialsProvider.create());
	}

	@Test
	public void builderReturnsOwnedInstances() {
		DefaultCredentialsProvider first = DefaultCredentialsProvider.builder().build();
		DefaultCredentialsProvider second = DefaultCredentialsProvider.builder().build();

		assertThat(first).isNotSameAs(second);
		assertThat(first).isNotSameAs(DefaultCredentialsProvider.create());
	}
}
