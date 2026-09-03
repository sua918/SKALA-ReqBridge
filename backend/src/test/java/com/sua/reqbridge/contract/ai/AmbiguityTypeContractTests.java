package com.sua.reqbridge.contract.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sua.reqbridge.contract.AmbiguityType;

class AmbiguityTypeContractTests {

	private static final Set<String> EXPECTED_TYPES = Set.of(
			"QUANTITY_MISSING",
			"PERFORMANCE_MISSING",
			"CONDITION_MISSING",
			"ACTOR_MISSING",
			"SUCCESS_CRITERIA_MISSING",
			"TERM_AMBIGUOUS",
			"EXCEPTION_MISSING"
	);

	@Test
	@DisplayName("Java AmbiguityType enum은 정확히 7개의 표준 값을 가져야 한다")
	void verifyJavaAmbiguityTypeValues() {
		Set<String> actualNames = Arrays.stream(AmbiguityType.values())
				.map(Enum::name)
				.collect(Collectors.toSet());

		assertThat(actualNames).isEqualTo(EXPECTED_TYPES);
	}

	@Test
	@DisplayName("document-analysis.schema.json의 AmbiguityType enum은 Java enum과 정확히 일치해야 한다")
	void verifyJsonSchemaMatchesAmbiguityType() throws Exception {
		Path schemaPath = Paths.get("../docs/backend/ai/document-analysis.schema.json");
		if (!Files.exists(schemaPath)) {
			schemaPath = Paths.get("docs/backend/ai/document-analysis.schema.json");
		}
		assertThat(Files.exists(schemaPath)).isTrue();

		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(schemaPath.toFile());
		JsonNode enumNode = root.at("/properties/requirements/items/properties/issues/items/properties/type/enum");

		assertThat(enumNode.isArray()).isTrue();
		Set<String> schemaEnumValues = new java.util.HashSet<>();
		enumNode.forEach(node -> schemaEnumValues.add(node.asText()));

		assertThat(schemaEnumValues).isEqualTo(EXPECTED_TYPES);
	}

	@Test
	@DisplayName("prompt-contract.md는 7개 표준 AmbiguityType 값을 모두 명시해야 한다")
	void verifyPromptContractContainsAllTypes() throws Exception {
		Path promptPath = Paths.get("../docs/backend/ai/prompt-contract.md");
		if (!Files.exists(promptPath)) {
			promptPath = Paths.get("docs/backend/ai/prompt-contract.md");
		}
		assertThat(Files.exists(promptPath)).isTrue();

		String content = Files.readString(promptPath);
		for (String expectedType : EXPECTED_TYPES) {
			assertThat(content).contains(expectedType);
		}
	}
}
