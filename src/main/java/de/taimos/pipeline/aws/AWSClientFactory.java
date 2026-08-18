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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.RetryPolicy;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;


public class AWSClientFactory implements Serializable {

	static final String AWS_PROFILE = "AWS_PROFILE";
	static final String AWS_DEFAULT_PROFILE = "AWS_DEFAULT_PROFILE";
	static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";
	static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
	static final String AWS_REGION = "AWS_REGION";
	static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";
	static final String AWS_SDK_SOCKET_TIMEOUT = "AWS_SDK_SOCKET_TIMEOUT";
	static final String AWS_SDK_RETRIES = "AWS_SDK_RETRIES";
	static final String AWS_PIPELINE_STEPS_FROM_NODE = "AWS_PIPELINE_STEPS_FROM_NODE";
	private static AWSClientFactoryDelegate factoryDelegate;
	private static AWSClientFactoryV2Delegate v2FactoryDelegate;


	private AWSClientFactory() {
		//
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context) {
		if (factoryDelegate != null) {
			return (T) factoryDelegate.create(clientBuilder);
		}
		try {
			return configureBuilder(clientBuilder, context, context.get(EnvVars.class)).build();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context, EnvVars vars) {
		if (factoryDelegate != null) {
			return (T) factoryDelegate.create(clientBuilder);
		}
		return configureBuilder(clientBuilder, context, vars).build();
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, EnvVars vars) {
		return configureBuilder(clientBuilder, null, vars).build();
	}

	public static <B extends AwsSyncClientBuilder<?, ?>> B configureBuilder(final B clientBuilder, StepContext context, final EnvVars vars) {
		if (clientBuilder == null) {
			throw new IllegalArgumentException("ClientBuilder must not be null");
		}
		if (StringUtils.isNotBlank(vars.get(AWS_ENDPOINT_URL))) {
			clientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(vars.get(AWS_ENDPOINT_URL), vars.get(AWS_REGION)));
		} else {
			clientBuilder.setRegion(AWSClientFactory.getRegion(vars).getName());
		}

		clientBuilder.setCredentials(AWSClientFactory.getCredentials(vars, context));

		clientBuilder.setClientConfiguration(AWSClientFactory.getClientConfiguration(vars));
		return clientBuilder;
	}

	private static ClientConfiguration getClientConfiguration(EnvVars vars) {
		ClientConfiguration clientConfiguration = new ClientConfiguration();

		// The default SDK max retry is 3, increasing this to be more resilient to upstream errors
		Integer retries = Integer.valueOf(vars.get(AWS_SDK_RETRIES, "10"));
		clientConfiguration.setRetryPolicy(new RetryPolicy(null, null, retries, false));

		// The default SDK socket timeout is 50000, use as deafult and allow to override via environment variable
		Integer socketTimeout = Integer.valueOf(vars.get(AWS_SDK_SOCKET_TIMEOUT, "50000"));
		clientConfiguration.setSocketTimeout(socketTimeout);

		ProxyConfiguration.configure(vars, clientConfiguration);
		return clientConfiguration;
	}

	private static AWSCredentialsProvider getCredentials(EnvVars vars, StepContext context) {
		AWSCredentialsProvider provider = handleStaticCredentials(vars);
		if (provider != null) {
			return provider;
		}

		provider = handleProfile(vars);
		if (provider != null) {
			return provider;
		}

		if (context != null) {
			if (PluginImpl.getInstance().isEnableCredentialsFromNode() || Boolean.valueOf(vars.get(AWS_PIPELINE_STEPS_FROM_NODE))) {
				try {
					return AWSClientFactory.getCredentialsFromNode(context, vars);
				} catch (Exception e) {
					throw new RuntimeException("Unable to retrieve credentials from node.");
				}
			}
		}

		return new DefaultAWSCredentialsProviderChain();
	}

	private static AWSCredentialsProvider getCredentialsFromNode(StepContext context, EnvVars envVars) throws IOException, InterruptedException {
		FilePath ws = context.get(FilePath.class);
		TaskListener listener = context.get(TaskListener.class);
		SerializableAWSCredentialsProvider serializableAWSCredentialsProvider = ws.act(new AWSCredentialsProviderCallable(listener));
		return serializableAWSCredentialsProvider;
	}

	private static AWSCredentialsProvider handleProfile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return new ProfileCredentialsProvider(profile);
		}
		return null;
	}

	private static AWSCredentialsProvider handleStaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return new AWSStaticCredentialsProvider(new BasicSessionCredentials(accessKey, secretAccessKey, sessionToken));
			}
			return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretAccessKey));
		}
		return null;
	}

	private static Region getRegion(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_DEFAULT_REGION)));
		}
		if (vars.get(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_REGION)));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_DEFAULT_REGION)));
		}
		if (System.getenv(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_REGION)));
		}
		Region currentRegion = Regions.getCurrentRegion();
		if (currentRegion != null) {
			return currentRegion;
		}
		return Region.getRegion(Regions.DEFAULT_REGION);
	}

	private static final long serialVersionUID = 1L;

	@Restricted(NoExternalUse.class)
	public static void setFactoryDelegate(AWSClientFactoryDelegate factoryDelegate) {
		AWSClientFactory.factoryDelegate = factoryDelegate;
	}

	@Restricted(NoExternalUse.class)
	public static void setV2FactoryDelegate(AWSClientFactoryV2Delegate v2FactoryDelegate) {
		AWSClientFactory.v2FactoryDelegate = v2FactoryDelegate;
	}

	// ---------------------------------------------------------------------------------------------
	// AWS SDK v2. These overloads sit beside the v1 ones above while services are migrated one at a
	// time; the v1 half is deleted once the last step has moved. They resolve by argument type: v1
	// builders are AwsSyncClientBuilder, v2 builders are AwsClientBuilder, so there is no ambiguity.
	// ---------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	public static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, C>, C> C create(B clientBuilder, StepContext context) {
		if (v2FactoryDelegate != null) {
			return (C) v2FactoryDelegate.create(clientBuilder);
		}
		try {
			return configureV2Builder(clientBuilder, context, context.get(EnvVars.class)).build();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, C>, C> C create(B clientBuilder, StepContext context, EnvVars vars) {
		if (v2FactoryDelegate != null) {
			return (C) v2FactoryDelegate.create(clientBuilder);
		}
		return configureV2Builder(clientBuilder, context, vars).build();
	}

	@SuppressWarnings("unchecked")
	public static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, C>, C> C create(B clientBuilder, EnvVars vars) {
		if (v2FactoryDelegate != null) {
			return (C) v2FactoryDelegate.create(clientBuilder);
		}
		return configureV2Builder(clientBuilder, null, vars).build();
	}

	public static <B extends software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<B, C>, C> B configureV2Builder(final B clientBuilder, StepContext context, final EnvVars vars) {
		if (clientBuilder == null) {
			throw new IllegalArgumentException("ClientBuilder must not be null");
		}

		// v1 treats region and endpoint as mutually exclusive, but v2 needs a region for request
		// signing even when the endpoint is overridden, so the region is always resolved here.
		String endpointUrl = vars.get(AWS_ENDPOINT_URL);
		if (StringUtils.isNotBlank(endpointUrl)) {
			clientBuilder.region(getV2RegionForEndpoint(vars, endpointUrl));
			clientBuilder.endpointOverride(URI.create(endpointUrl));
		} else {
			clientBuilder.region(getV2Region(vars));
		}

		clientBuilder.credentialsProvider(getV2Credentials(vars, context));
		clientBuilder.overrideConfiguration(getV2OverrideConfiguration(vars));

		if (clientBuilder instanceof SdkSyncClientBuilder) {
			((SdkSyncClientBuilder<?, ?>) clientBuilder).httpClient(getV2SyncHttpClient(vars));
		} else {
			// Async builders need their HTTP client configured through a different interface, and
			// with a different dependency (netty). Nothing here builds one yet; failing loudly
			// means the first service that does cannot silently lose the socket timeout and proxy
			// settings applied above.
			throw new IllegalStateException("Asynchronous AWS clients are not supported yet: " + clientBuilder.getClass().getName());
		}

		return clientBuilder;
	}

	static ClientOverrideConfiguration getV2OverrideConfiguration(EnvVars vars) {
		// v1 counts retries after the initial call (maxErrorRetry = 10 means 11 total attempts),
		// while v2 maxAttempts includes it, so the value is carried over as retries + 1 to keep the
		// number of calls against AWS identical.
		int retries = Integer.parseInt(vars.get(AWS_SDK_RETRIES, "10"));
		return ClientOverrideConfiguration.builder()
				.retryStrategy(b -> b.maxAttempts(retries + 1))
				.build();
	}

	static software.amazon.awssdk.http.SdkHttpClient getV2SyncHttpClient(EnvVars vars) {
		int socketTimeout = Integer.parseInt(vars.get(AWS_SDK_SOCKET_TIMEOUT, "50000"));
		return ApacheHttpClient.builder()
				.socketTimeout(Duration.ofMillis(socketTimeout))
				.proxyConfiguration(ProxyConfiguration.buildV2ProxyConfiguration(vars))
				.build();
	}

	static AwsCredentialsProvider getV2Credentials(EnvVars vars, StepContext context) {
		AwsCredentialsProvider provider = handleV2StaticCredentials(vars);
		if (provider != null) {
			return provider;
		}

		provider = handleV2Profile(vars);
		if (provider != null) {
			return provider;
		}

		if (context != null) {
			if (PluginImpl.getInstance().isEnableCredentialsFromNode() || Boolean.valueOf(vars.get(AWS_PIPELINE_STEPS_FROM_NODE))) {
				try {
					return AWSClientFactory.getV2CredentialsFromNode(context, vars);
				} catch (Exception e) {
					throw new RuntimeException("Unable to retrieve credentials from node.");
				}
			}
		}

		return DefaultCredentialsProvider.create();
	}

	private static AwsCredentialsProvider getV2CredentialsFromNode(StepContext context, EnvVars envVars) throws IOException, InterruptedException {
		FilePath ws = context.get(FilePath.class);
		TaskListener listener = context.get(TaskListener.class);
		// SerializableAWSCredentialsProvider implements both SDKs' provider interfaces.
		return ws.act(new AWSCredentialsProviderCallable(listener));
	}

	private static AwsCredentialsProvider handleV2Profile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider.create(profile);
		}
		return null;
	}

	private static AwsCredentialsProvider handleV2StaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretAccessKey, sessionToken));
			}
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretAccessKey));
		}
		return null;
	}

	/**
	 * v1 passes the endpoint and {@code AWS_REGION} to {@code EndpointConfiguration}. When no region
	 * is set there, the v1 SDK derives the signing region from the endpoint host
	 * ({@code AwsHostNameUtils.parseRegion}), so {@code withAWS(endpointUrl:
	 * 'https://s3.eu-west-1.amazonaws.com')} signs for eu-west-1 without the user naming a region -
	 * and the plugin documents region and endpointUrl as mutually exclusive, so that is the normal
	 * way to use it. Resolving through the usual chain instead would sign for whatever the profile
	 * or instance metadata yields, or us-west-2, and fail in a way that is hard to trace.
	 *
	 * Hosts that are not AWS endpoints (a MinIO server, say) yield nothing here, exactly as they
	 * yield null in v1; those fall through to the normal chain, and the region is arbitrary for
	 * such endpoints anyway.
	 */
	static software.amazon.awssdk.regions.Region getV2RegionForEndpoint(EnvVars vars, String endpointUrl) {
		if (vars.get(AWS_DEFAULT_REGION) != null || vars.get(AWS_REGION) != null) {
			return getV2Region(vars);
		}
		software.amazon.awssdk.regions.Region parsed = parseRegionFromEndpoint(endpointUrl);
		if (parsed != null) {
			return parsed;
		}
		return getV2Region(vars);
	}

	private static final Pattern AWS_ENDPOINT_REGION = Pattern.compile(
			"^(?:.+\\.)?([a-z]{2}(?:-gov)?(?:-[a-z]+)+-\\d+)\\.amazonaws\\.com(?:\\.cn)?$");

	private static software.amazon.awssdk.regions.Region parseRegionFromEndpoint(String endpointUrl) {
		final String host;
		try {
			host = URI.create(endpointUrl).getHost();
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (host == null) {
			return null;
		}
		Matcher matcher = AWS_ENDPOINT_REGION.matcher(host);
		if (matcher.matches()) {
			return software.amazon.awssdk.regions.Region.of(matcher.group(1));
		}
		if (host.endsWith(".amazonaws.com")) {
			// Legacy global endpoints such as sns.amazonaws.com; v1 resolves these to us-east-1.
			return software.amazon.awssdk.regions.Region.US_EAST_1;
		}
		return null;
	}

	static software.amazon.awssdk.regions.Region getV2Region(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(vars.get(AWS_DEFAULT_REGION));
		}
		if (vars.get(AWS_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(vars.get(AWS_REGION));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(System.getenv(AWS_DEFAULT_REGION));
		}
		if (System.getenv(AWS_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(System.getenv(AWS_REGION));
		}
		try {
			// v1's Regions.getCurrentRegion() returns null off-EC2; the v2 chain throws instead.
			return new DefaultAwsRegionProviderChain().getRegion();
		} catch (RuntimeException e) {
			// v1 falls back to Regions.DEFAULT_REGION, which is us-west-2.
			return software.amazon.awssdk.regions.Region.US_WEST_2;
		}
	}
}
