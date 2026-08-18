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
