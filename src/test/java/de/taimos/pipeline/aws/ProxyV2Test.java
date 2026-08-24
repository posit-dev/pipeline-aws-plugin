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

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.http.apache.ProxyConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@link ProxyTest} for the SDK v2 proxy configuration, so the two can be compared
 * case for case while both SDKs are present.
 */
public class ProxyV2Test {

	private static final String[] PROXY_PROPERTIES = {
			"https.proxyHost", "https.proxyPort", "https.proxyUser", "https.proxyPassword", "http.nonProxyHosts"
	};

	private final Map<String, String> savedProperties = new HashMap<>();

	/**
	 * buildV2ProxyConfiguration deliberately reads the JVM proxy properties, so every case here has
	 * to start from a known state: a machine whose Maven JVM carries -Dhttps.proxyHost (the usual
	 * way to put Maven behind a corporate proxy) would otherwise fail the cases asserting no proxy.
	 */
	@Before
	public void clearProxyProperties() {
		for (String key : PROXY_PROPERTIES) {
			this.savedProperties.put(key, System.getProperty(key));
			System.clearProperty(key);
		}
	}

	@After
	public void restoreProxyProperties() {
		for (Map.Entry<String, String> entry : this.savedProperties.entrySet()) {
			if (entry.getValue() == null) {
				System.clearProperty(entry.getKey());
			} else {
				System.setProperty(entry.getKey(), entry.getValue());
			}
		}
	}

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

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(new EnvVars());

		assertThat(config.host()).isEqualTo("sysprop.corp");
		assertThat(config.port()).isEqualTo(3129);
		assertThat(config.nonProxyHosts()).containsExactlyInAnyOrder("internal.corp", "*.local");
	}

	/**
	 * v1 leaves the port at -1 when only the host property is set and lets Apache resolve it
	 * against the scheme (http, so 80). Defaulting to 443 here would dial http://proxy:443.
	 */
	@Test
	public void systemPropertyProxyWithoutAPortLeavesThePortUnset() throws Exception {
		System.setProperty("https.proxyHost", "sysprop.corp");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(new EnvVars());

		assertThat(config.host()).isEqualTo("sysprop.corp");
		assertThat(config.port()).isEqualTo(-1);
	}

	/**
	 * v1 resolves each proxy field from its own system property, so credentials still apply when
	 * the host came from elsewhere. Without this a controller using HTTPS_PROXY plus
	 * -Dhttps.proxyUser would start getting 407s from the proxy on migrated steps only.
	 */
	@Test
	public void systemPropertyCredentialsApplyToAProxyFromTheEnvironment() throws Exception {
		System.setProperty("https.proxyUser", "sysuser");
		System.setProperty("https.proxyPassword", "syspass");
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://fromenv.corp:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isEqualTo("fromenv.corp");
		assertThat(config.username()).isEqualTo("sysuser");
		assertThat(config.password()).isEqualTo("syspass");
	}

	/**
	 * The system properties are only a fallback: an explicitly configured proxy wins, as in v1.
	 */
	@Test
	public void environmentVariablesWinOverSystemProperties() throws Exception {
		System.setProperty("https.proxyHost", "sysprop.corp");
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTPS_PROXY, "http://fromenv.corp:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isEqualTo("fromenv.corp");
		assertThat(config.port()).isEqualTo(8888);
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

	/**
	 * The netty assembly is asserted in prose to yield "the same one the synchronous clients get", and
	 * the one place it silently did not was this: apache leaves an unset port at -1 and resolves it
	 * against the scheme, while netty's port simply defaults to 0, so an async transfer would have
	 * dialled proxy:0 while the sync clients worked. Deleting the mapping leaves the apache cases green.
	 */
	@Test
	public void nettyGivesAnUnsetPortTheSameDefaultApacheResolvesTo() {
		System.setProperty("https.proxyHost", "proxy.example.com");

		software.amazon.awssdk.http.nio.netty.ProxyConfiguration netty =
				de.taimos.pipeline.aws.ProxyConfiguration.buildV2NettyProxyConfiguration(new EnvVars());

		assertThat(netty.host()).isEqualTo("proxy.example.com");
		assertThat(netty.port()).isEqualTo(80);
	}

	@Test
	public void nettyCarriesAnExplicitPortThrough() {
		System.setProperty("https.proxyHost", "proxy.example.com");
		System.setProperty("https.proxyPort", "8443");

		software.amazon.awssdk.http.nio.netty.ProxyConfiguration netty =
				de.taimos.pipeline.aws.ProxyConfiguration.buildV2NettyProxyConfiguration(new EnvVars());

		assertThat(netty.port()).isEqualTo(8443);
	}
}
