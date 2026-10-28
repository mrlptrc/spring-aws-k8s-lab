package dev.mrlptrc.notes;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Item da tabela DynamoDB. O Enhanced Client exige um bean mutável
 * (construtor vazio + getters/setters) para mapear atributos.
 */
@DynamoDbBean
public class Note {

	private String id;

	private String title;

	private String content;

	private Instant createdAt;

	public Note() {
	}

	Note(String id, String title, String content, Instant createdAt) {
		this.id = id;
		this.title = title;
		this.content = content;
		this.createdAt = createdAt;
	}

	// Partition key: o DynamoDB distribui e busca itens por ela.
	@DynamoDbPartitionKey
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
