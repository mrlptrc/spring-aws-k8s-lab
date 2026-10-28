package dev.mrlptrc.notes;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@Configuration
class DynamoDbConfig {

	@Bean
	DynamoDbClient dynamoDbClient(@Value("${app.dynamodb.region}") String region,
			@Value("${app.dynamodb.endpoint}") String endpoint) {
		DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(region));
		if (!endpoint.isBlank()) {
			// DynamoDB Local aceita qualquer credencial. Na AWS não entra aqui:
			// o SDK usa a cadeia padrão (env vars, ~/.aws, Pod Identity no EKS...).
			builder.endpointOverride(URI.create(endpoint))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
		}
		return builder.build();
	}

	@Bean
	DynamoDbTable<Note> notesTable(DynamoDbClient client, @Value("${app.dynamodb.table}") String table) {
		return DynamoDbEnhancedClient.builder()
			.dynamoDbClient(client)
			.build()
			.table(table, TableSchema.fromBean(Note.class));
	}

}
