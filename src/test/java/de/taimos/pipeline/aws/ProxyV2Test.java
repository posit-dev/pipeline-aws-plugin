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
	 */
	@Test
	public void shouldIgnoreHttpProxyAsV1Does() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(de.taimos.pipeline.aws.ProxyConfiguration.HTTP_PROXY, "http://127.0.0.1:8888/");

		ProxyConfiguration config = de.taimos.pipeline.aws.ProxyConfiguration.buildV2ProxyConfiguration(vars);

		assertThat(config.host()).isNull();
	}
}
