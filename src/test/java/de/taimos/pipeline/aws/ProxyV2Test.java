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
import software.amazon.awssdk.http.apache.ProxyConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@link ProxyTest} for the SDK v2 proxy configuration, so the two can be compared
 * case for case while both SDKs are present.
 */
public class ProxyV2Test {

	@Test
	public void shouldNotChangeIfNotPresent() throws Exception {
		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(new EnvVars());

		assertThat(config.username()).isNull();
		assertThat(config.password()).isNull();
		assertThat(config.host()).isNull();
	}

	@Test
	public void shouldParseProxy() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.username()).isNull();
		assertThat(config.password()).isNull();
		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.port()).isEqualTo(8888);
	}

	@Test
	public void shouldParseProxyWithoutPort() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.port()).isEqualTo(443);
	}

	@Test
	public void shouldParseProxyLowerCase() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY_LC, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.port()).isEqualTo(8888);
	}

	@Test
	public void shouldParseProxyWithAuth() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://foo:bar@127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.username()).isEqualTo("foo");
		assertThat(config.password()).isEqualTo("bar");
		assertThat(config.host()).isEqualTo("127.0.0.1");
		assertThat(config.port()).isEqualTo(8888);
	}

	/**
	 * v1 joins non-proxy hosts into a pipe-separated string; v2 takes a set.
	 */
	@Test
	public void shouldSetNonProxyHosts() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.NO_PROXY, "127.0.0.1,localhost");
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.nonProxyHosts()).containsExactlyInAnyOrder("127.0.0.1", "localhost");
	}

	@Test
	public void shouldSetNonProxyHostsLowerCase() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.NO_PROXY_LC, "127.0.0.1,localhost");
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.nonProxyHosts()).containsExactlyInAnyOrder("127.0.0.1", "localhost");
	}

	/**
	 * HTTP_PROXY is deliberately ignored: the v1 code branches on a protocol that this plugin never
	 * sets away from the HTTPS default, so its HTTP_PROXY branch is unreachable. Honouring it here
	 * would newly route traffic through a proxy for users who set only that variable.
	 *
	 * This assertion only holds because the builder disables the SDK's own system-property and
	 * environment-variable resolution. With those defaults left on, the SDK fills the host in from
	 * the controller's ambient http_proxy, and this test passes or fails depending on the machine
	 * it runs on. Verified by running this class with http_proxy exported; note the full suite
	 * cannot be run that way, because WithAWSStepTest makes real calls to AWS STS.
	 */
	@Test
	public void shouldIgnoreHttpProxyAsV1Does() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTP_PROXY, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isNull();
	}

	/**
	 * v1 leaves ClientConfiguration's proxy fields unset and lets the Apache layer fall back to the
	 * JVM proxy system properties, so a controller started with -Dhttps.proxyHost proxies even with
	 * no Jenkins proxy and no HTTPS_PROXY set. Reproduced explicitly, since the SDK's own
	 * system-property support resolves against the endpoint scheme rather than v1's https.* names.
	 */
	@Test
	public void fallsBackToTheJvmProxySystemProperties() throws Exception {
		System.setProperty("https.proxyHost", "sysprop.corp");
		System.setProperty("https.proxyPort", "3129");
		System.setProperty("http.nonProxyHosts", "internal.corp|*.local");
		try {
			ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(new EnvVars());

			assertThat(config.host()).isEqualTo("sysprop.corp");
			assertThat(config.port()).isEqualTo(3129);
			assertThat(config.nonProxyHosts()).containsExactlyInAnyOrder("internal.corp", "*.local");
		} finally {
			System.clearProperty("https.proxyHost");
			System.clearProperty("https.proxyPort");
			System.clearProperty("http.nonProxyHosts");
		}
	}

	/**
	 * The system properties are only a fallback: an explicitly configured proxy wins, as in v1.
	 */
	@Test
	public void environmentVariablesWinOverSystemProperties() throws Exception {
		System.setProperty("https.proxyHost", "sysprop.corp");
		try {
			EnvVars vars = new EnvVars();
			vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://fromenv.corp:8888/");

			ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

			assertThat(config.host()).isEqualTo("fromenv.corp");
			assertThat(config.port()).isEqualTo(8888);
		} finally {
			System.clearProperty("https.proxyHost");
		}
	}

	/**
	 * v1 discards the scheme of the proxy URL and connects to the proxy over plain HTTP regardless,
	 * so an https:// proxy URL must not turn into a TLS connection to the proxy here either.
	 */
	@Test
	public void shouldIgnoreProxyUrlScheme() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "https://proxy.corp:8080/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isEqualTo("proxy.corp");
		assertThat(config.port()).isEqualTo(8080);
		assertThat(config.scheme()).isEqualTo("http");
	}
}
