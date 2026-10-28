package dev.mrlptrc.notes;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

/**
 * Cria a tabela no DynamoDB Local ao subir a aplicação.
 * Só existe com app.dynamodb.create-table=true; na AWS quem cria é o Terraform.
 */
@Configuration
@ConditionalOnProperty(name = "app.dynamodb.create-table", havingValue = "true")
class LocalTableInitializer {

	@Bean
	ApplicationRunner createNotesTable(DynamoDbTable<Note> table) {
		return args -> {
			try {
				table.createTable();
			}
			catch (ResourceInUseException alreadyExists) {
				// Tabela já existe: nada a fazer.
			}
		};
	}

}
