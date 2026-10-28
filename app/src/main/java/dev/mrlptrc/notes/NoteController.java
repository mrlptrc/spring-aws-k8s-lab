package dev.mrlptrc.notes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@RestController
@RequestMapping("/notes")
class NoteController {

	record NoteRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10_000) String content) {
	}

	private final DynamoDbTable<Note> table;

	NoteController(DynamoDbTable<Note> table) {
		this.table = table;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Note create(@Valid @RequestBody NoteRequest request) {
		Note note = new Note(UUID.randomUUID().toString(), request.title(), request.content(), Instant.now());
		table.putItem(note);
		return note;
	}

	@GetMapping
	List<Note> list() {
		return table.scan().items().stream().toList();
	}

	@GetMapping("/{id}")
	Note get(@PathVariable String id) {
		Note note = table.getItem(Key.builder().partitionValue(id).build());
		if (note == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return note;
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String id) {
		table.deleteItem(Key.builder().partitionValue(id).build());
	}

}
