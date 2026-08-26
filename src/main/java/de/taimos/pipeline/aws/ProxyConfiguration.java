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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import jenkins.model.Jenkins;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class ProxyConfiguration {

	static final String HTTP_PROXY = "HTTP_PROXY";
	static final String HTTPS_PROXY = "HTTPS_PROXY";
	static final String HTTPS_PROXY_LC = "https_proxy";
	static final String NO_PROXY = "NO_PROXY";
	static final String NO_PROXY_LC = "no_proxy";

	private static final String PROXY_PATTERN = "(https?)://(([^:]+)(:(.+))?@)?([\\da-zA-Z.-]+)(:(\\d+))?/?";

	private static final int HTTP_PORT = 80;
	private static final int HTTPS_PORT = 443;

	private ProxyConfiguration() {
		// hidden constructor
	}

	/**
	 * Builds the proxy configuration for the Apache (synchronous) HTTP client.
	 *
	 * The v1 path branches on {@code ClientConfiguration.getProtocol()}, but nothing in this plugin
	 * ever calls {@code setProtocol} and the v1 default is HTTPS, so the HTTP branch there is
	 * unreachable and only HTTPS_PROXY/https_proxy is ever consulted. That behaviour is preserved
	 * here deliberately: honouring HTTP_PROXY as well would newly route traffic through a proxy for
	 * users who set only that variable.
	 */
	static software.amazon.awssdk.http.apache.ProxyConfiguration buildV2ProxyConfiguration(EnvVars vars) {
		return resolveV2ProxySettings(vars).toProxyConfiguration();
	}

	/**
	 * The same settings expressed for the netty client, which S3TransferManager's asynchronous client
	 * uses. Netty's ProxyConfiguration takes host, port and scheme separately instead of a single
	 * endpoint URI, so the assembly differs, but the resolution above is shared and the resulting
	 * proxy is the same one the synchronous clients get.
	 */
	static software.amazon.awssdk.http.nio.netty.ProxyConfiguration buildV2NettyProxyConfiguration(EnvVars vars) {
		return resolveV2ProxySettings(vars).toNettyProxyConfiguration();
	}

	/**
	 * An opaque value equal for two EnvVars that resolve to the same proxy, for AWSClientFactory to
	 * key its shared synchronous HTTP clients on.
	 *
	 * The SDK's own ProxyConfiguration cannot be used for this: neither the apache nor the netty one
	 * implements equals or hashCode, so two identically-configured instances never compare equal and
	 * a cache keyed on them would miss every time and hand back a fresh pool anyway. The resolved
	 * settings are compared instead, which is also what actually determines the client.
	 */
	static Object v2ProxyIdentity(EnvVars vars) {
		return resolveV2ProxySettings(vars);
	}

	private static V2ProxySettings resolveV2ProxySettings(EnvVars vars) {
		V2ProxySettings settings = new V2ProxySettings();

		useJenkinsProxyV2(settings);

		String env = vars.get(HTTPS_PROXY, vars.get(HTTPS_PROXY_LC));
		if (env != null) {
			configureProxyV2(settings, env, HTTPS_PORT);
		}

		String noProxy = vars.get(NO_PROXY, vars.get(NO_PROXY_LC));
		if (noProxy != null) {
			settings.nonProxyHosts = new HashSet<>(Arrays.asList(noProxy.split(",")));
		}

		useSystemPropertiesV2(settings);

		return settings;
	}

	/**
	 * v1 leaves ClientConfiguration's proxy fields unset and lets the Apache layer
	 * ({@code HttpClientSettings}) fall back to the JVM proxy system properties for whatever the
	 * plugin did not set, so a controller started with -Dhttps.proxyHost still proxies. That
	 * fallback is reproduced explicitly rather than through the SDK's own
	 * {@code useSystemPropertyValues}, because v2 resolves those against the scheme of the endpoint
	 * - which is pinned to http here - and would therefore read http.proxyHost where v1, whose
	 * protocol defaults to HTTPS, reads https.proxyHost.
	 *
	 * These are a fallback only: anything already supplied by the Jenkins proxy configuration or
	 * the environment variables above wins, matching v1's precedence.
	 */
	private static void useSystemPropertiesV2(V2ProxySettings settings) {
		if (settings.host == null || settings.host.isEmpty()) {
			String host = System.getProperty("https.proxyHost");
			if (host != null && !host.isEmpty()) {
				settings.host = host;
				String port = System.getProperty("https.proxyPort");
				// Left unset when the property is absent: v1 leaves ClientConfiguration's port at
				// -1 and lets Apache resolve it against the scheme, which is http here, so port 80.
				// Defaulting to 443 would dial http://proxy:443.
				settings.port = port != null ? Integer.parseInt(port) : UNSET_PORT;
			}
		}
		// v1 resolves each proxy field from its own system property independently of the others, so
		// credentials still apply when the host came from the Jenkins configuration or HTTPS_PROXY.
		if (settings.username == null) {
			settings.username = System.getProperty("https.proxyUser");
		}
		if (settings.password == null) {
			settings.password = System.getProperty("https.proxyPassword");
		}
		if (settings.nonProxyHosts == null) {
			String nonProxyHosts = System.getProperty("http.nonProxyHosts");
			if (nonProxyHosts != null && !nonProxyHosts.isEmpty()) {
				settings.nonProxyHosts = new HashSet<>(Arrays.asList(nonProxyHosts.split("\\|")));
			}
		}
	}

	private static void useJenkinsProxyV2(V2ProxySettings settings) {
		Jenkins jenkins = Jenkins.getInstanceOrNull();
		if (jenkins != null) {
			hudson.ProxyConfiguration proxyConfiguration = jenkins.proxy;
			if (proxyConfiguration != null) {
				settings.host = proxyConfiguration.name;
				settings.port = proxyConfiguration.port;
				settings.username = proxyConfiguration.getUserName();
				settings.password = proxyConfiguration.getPassword();

				if (proxyConfiguration.getNoProxyHost() != null) {
					settings.nonProxyHosts = new HashSet<>(Arrays.asList(proxyConfiguration.getNoProxyHost().split("[ \t\n,|]+")));
				}
			}
		}
	}

	private static void configureProxyV2(V2ProxySettings settings, String env, int defaultPort) {
		Pattern pattern = Pattern.compile(PROXY_PATTERN);
		Matcher matcher = pattern.matcher(env);
		if (matcher.matches()) {
			if (matcher.group(3) != null) {
				settings.username = matcher.group(3);
			}
			if (matcher.group(5) != null) {
				settings.password = matcher.group(5);
			}
			settings.host = matcher.group(6);
			if (matcher.group(8) != null) {
				settings.port = Integer.parseInt(matcher.group(8));
			} else {
				settings.port = defaultPort;
			}
		}
	}

	/** No port configured. Apache resolves -1 against the endpoint scheme, which is http here, so 80. */
	private static final int UNSET_PORT = -1;

	/**
	 * v1 sets host, port, credentials and non-proxy hosts independently; v2 wants a single endpoint
	 * URI, so the parts are collected first and assembled at the end.
	 */
	private static final class V2ProxySettings {
		private String host;
		private int port = UNSET_PORT;
		private String username;
		private String password;
		private Set<String> nonProxyHosts;

		private software.amazon.awssdk.http.apache.ProxyConfiguration toProxyConfiguration() {
			// Both of these default to true. Environment resolution is switched off because v1 does
			// none: leaving it on routes traffic through a proxy this plugin was never told about,
			// including for users who set only HTTP_PROXY, which v1 ignores. The SDK's own
			// system-property resolution is switched off too, but replaced by useSystemPropertiesV2
			// above, because the SDK reads them against the endpoint scheme (http) while v1 reads
			// the https.* ones.
			software.amazon.awssdk.http.apache.ProxyConfiguration.Builder builder =
					software.amazon.awssdk.http.apache.ProxyConfiguration.builder()
							.useSystemPropertyValues(false)
							.useEnvironmentVariableValues(false);

			// The v1 path discards the scheme from the proxy URL and leaves ClientConfiguration's
			// proxy protocol at its HTTP default, so HTTPS_PROXY=https://proxy:8080 means a plain
			// HTTP connection to the proxy. Hard-coding http here keeps that.
			if (this.host != null && !this.host.isEmpty()) {
				String authority = this.port == UNSET_PORT ? this.host : this.host + ":" + this.port;
				builder.endpoint(URI.create("http://" + authority));
			}
			if (this.username != null) {
				builder.username(this.username);
			}
			if (this.password != null) {
				builder.password(this.password);
			}
			if (this.nonProxyHosts != null) {
				builder.nonProxyHosts(this.nonProxyHosts);
			}
			return builder.build();
		}

		private software.amazon.awssdk.http.nio.netty.ProxyConfiguration toNettyProxyConfiguration() {
			// Same reasoning as above: no environment or system-property resolution of the SDK's own,
			// and the scheme pinned to http because v1 connects to the proxy in plain HTTP whatever
			// scheme the proxy URL carried.
			software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder builder =
					software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
							.useSystemPropertyValues(false)
							.useEnvironmentVariableValues(false)
							.scheme("http");

			if (this.host != null && !this.host.isEmpty()) {
				builder.host(this.host);
				// The apache assembly leaves an unset port at -1 and lets apache resolve it against the
				// scheme, which gives 80 for http. Netty has no such resolution with system properties
				// disabled - its port simply defaults to 0 - so the same default is applied here
				// explicitly. Without this an async transfer would dial proxy:0 where the sync clients
				// work, which is exactly the divergence sharing this resolution is meant to prevent.
				builder.port(this.port == UNSET_PORT ? HTTP_PORT : this.port);
			}
			if (this.username != null) {
				builder.username(this.username);
			}
			if (this.password != null) {
				builder.password(this.password);
			}
			if (this.nonProxyHosts != null) {
				builder.nonProxyHosts(this.nonProxyHosts);
			}
			return builder.build();
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof V2ProxySettings)) {
				return false;
			}
			V2ProxySettings that = (V2ProxySettings) other;
			return this.port == that.port
					&& Objects.equals(this.host, that.host)
					&& Objects.equals(this.username, that.username)
					&& Objects.equals(this.password, that.password)
					&& Objects.equals(this.nonProxyHosts, that.nonProxyHosts);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.host, this.port, this.username, this.password, this.nonProxyHosts);
		}

		/** Deliberately omits the credentials, so that a key can never carry a proxy password into a log. */
		@Override
		public String toString() {
			return "V2ProxySettings[host=" + this.host + ",port=" + this.port
					+ ",nonProxyHosts=" + this.nonProxyHosts + "]";
		}
	}

}
