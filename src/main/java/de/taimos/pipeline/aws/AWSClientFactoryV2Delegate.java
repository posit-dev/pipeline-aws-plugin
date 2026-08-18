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

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;

/**
 * Test seam for the AWS SDK v2 client path, mirroring {@link AWSClientFactoryDelegate}.
 *
 * It is separate rather than an overload of the v1 setter because a lambda passed to an overloaded
 * method taking two functional interfaces is ambiguous, which would break every existing test that
 * calls {@code AWSClientFactory.setFactoryDelegate((x) -> mock)}. Once the v1 dependencies are
 * removed this can take over the original name.
 */
public interface AWSClientFactoryV2Delegate {
	Object create(AwsClientBuilder<?, ?> clientBuilder);
}
