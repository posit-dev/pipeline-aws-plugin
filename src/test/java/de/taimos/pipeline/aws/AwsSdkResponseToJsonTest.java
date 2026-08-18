package de.taimos.pipeline.aws;

import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the map shape that {@code cfnValidate} returns to the pipeline.
 *
 * The keys here are part of the plugin's public contract: Jenkinsfiles index into this map
 * (for example {@code response.description} or {@code response.parameters[0].parameterKey}).
 * The conversion is a blind Jackson round-trip over the SDK response object, so the key names
 * are derived from the SDK model's accessors rather than from any code in this plugin - which
 * means an SDK upgrade can silently rename them. This test exists to make that loud.
 */
public class AwsSdkResponseToJsonTest {

	private static ValidateTemplateResult populatedResult() {
		return new ValidateTemplateResult()
				.withDescription("myDescription")
				.withCapabilities("CAPABILITY_IAM")
				.withCapabilitiesReason("because")
				.withDeclaredTransforms("AWS::Serverless-2016-10-31")
				.withParameters(new TemplateParameter()
						.withDefaultValue("hello")
						.withDescription("myParamDescription")
						.withParameterKey("myParam")
				);
	}

	@Test
	public void preservesTopLevelKeyNames() throws Exception {
		Map<String, Object> map = AwsSdkResponseToJson.convertToMap(populatedResult());

		assertThat(map).containsEntry("description", "myDescription");
		assertThat(map).containsEntry("capabilitiesReason", "because");
		assertThat(map).containsEntry("capabilities", List.of("CAPABILITY_IAM"));
		assertThat(map).containsEntry("declaredTransforms", List.of("AWS::Serverless-2016-10-31"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void preservesNestedParameterKeyNames() throws Exception {
		Map<String, Object> map = AwsSdkResponseToJson.convertToMap(populatedResult());

		List<Map<String, Object>> parameters = (List<Map<String, Object>>) map.get("parameters");
		assertThat(parameters).hasSize(1);
		assertThat(parameters.get(0))
				.containsEntry("parameterKey", "myParam")
				.containsEntry("defaultValue", "hello")
				.containsEntry("description", "myParamDescription");
	}

	/**
	 * Unset members are emitted as explicit nulls rather than omitted. A pipeline doing
	 * {@code response.parameters[0].noEcho == null} depends on the key being present.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void retainsNullMembers() throws Exception {
		Map<String, Object> map = AwsSdkResponseToJson.convertToMap(populatedResult());

		List<Map<String, Object>> parameters = (List<Map<String, Object>>) map.get("parameters");
		assertThat(parameters.get(0)).containsKey("noEcho");
		assertThat(parameters.get(0).get("noEcho")).isNull();
	}
}
