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

import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateResponse;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the map shape that {@code cfnValidate} returns to the pipeline.
 *
 * The keys here are part of the plugin's public contract: Jenkinsfiles index into this map
 * (for example {@code response.capabilities}). The conversion walks the SDK response's own field
 * metadata ({@code SdkPojo.sdkFields()}), so the key names are derived from the SDK model's member
 * names rather than from any code in this plugin - which means an SDK upgrade can silently rename
 * them. This test exists to make that loud.
 *
 * The nested {@code parameters} keys and the explicit-null behaviour are pinned by
 * {@code CFNValidateStepTests.validateWithUrlSuccess}, which asserts the rendered map end to end
 * through the step; they are deliberately not repeated here.
 */
public class AwsSdkResponseToJsonTest {

	@Test
	public void preservesTopLevelKeyNames() throws Exception {
		ValidateTemplateResponse result = ValidateTemplateResponse.builder()
				.description("myDescription")
				.capabilitiesWithStrings("CAPABILITY_IAM")
				.capabilitiesReason("because")
				.declaredTransforms("AWS::Serverless-2016-10-31")
				// left unpopulated on purpose: the nested keys are pinned by CFNValidateStepTests
				.parameters(TemplateParameter.builder().build())
				.build();

		Map<String, Object> map = AwsSdkResponseToJson.convertToMap(result);

		assertThat(map).containsEntry("description", "myDescription");
		assertThat(map).containsEntry("capabilitiesReason", "because");
		assertThat(map).containsEntry("capabilities", List.of("CAPABILITY_IAM"));
		assertThat(map).containsEntry("declaredTransforms", List.of("AWS::Serverless-2016-10-31"));
		assertThat(map).containsKey("parameters");
	}

	@Test
	public void unsetCollectionsStayNullAsUnderV1() throws Exception {
		// v2's builders fill unset list/map members with an auto-construct empty collection, so a
		// naive conversion would hand the pipeline [] where v1 handed it null.
		ValidateTemplateResponse result = ValidateTemplateResponse.builder()
				.description("myDescription")
				.build();

		Map<String, Object> map = AwsSdkResponseToJson.convertToMap(result);

		assertThat(map).containsEntry("capabilities", null);
		assertThat(map).containsEntry("declaredTransforms", null);
		assertThat(map).containsEntry("parameters", null);
	}
}
