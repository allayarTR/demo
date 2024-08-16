package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.EventBridgeRuleSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "uuid_generator",
		roleName = "uuid_generator-role",
		runtime = DeploymentRuntime.JAVA17,
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EventBridgeRuleSource(targetRule = "uuid_trigger")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}}")
})
public class UuidGenerator implements RequestHandler<ScheduledEvent, String> {

	private final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String bucketName = System.getenv("cmtr-d2f4ab85-uuid-storage-test");

	@Override
	public String handleRequest(ScheduledEvent event, Context context) {
		List<String> uuids = generateUUIDs(10);

		try {
			String fileName = Instant.now().toString();
			Map<String, List<String>> idsMap = new HashMap<>();
			idsMap.put("ids", uuids);
			String content = objectMapper.writeValueAsString(idsMap);

			context.getLogger().log("Content: " + content);

			ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType("application/json");
			metadata.setContentLength(content.length());

			context.getLogger().log("Uploading to S3: " + fileName);
			s3Client.putObject(bucketName, fileName, inputStream, metadata);

			context.getLogger().log("Successfully uploaded " + fileName + " to " + bucketName);
			return "Execution completed successfully";
		} catch (Exception e) {
			context.getLogger().log("Error uploading to S3: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private List<String> generateUUIDs(int count) {
		List<String> uuids = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			uuids.add(UUID.randomUUID().toString());
		}
		return uuids;
	}
}