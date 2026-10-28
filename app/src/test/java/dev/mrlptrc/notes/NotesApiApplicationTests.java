package dev.mrlptrc.notes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração: sobe o DynamoDB Local num container (Testcontainers)
 * e exercita a API inteira, sem conta AWS.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NotesApiApplicationTests {

    @Container
    static GenericContainer<?> dynamodb = new GenericContainer<>("amazon/dynamodb-local:3.3.2")
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb")
            .withExposedPorts(8000);

    // Porta do container é aleatória: injeta o endpoint depois que ele sobe.
    @DynamicPropertySource
    static void dynamoProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "app.dynamodb.endpoint", () -> "http://" + dynamodb.getHost() + ":" + dynamodb.getMappedPort(8000));
        registry.add("app.dynamodb.create-table", () -> "true");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void createGetListDelete() throws Exception {
        String body = mvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"estudar EKS\",\"content\":\"fase 4\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/notes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("estudar EKS"));
        mvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());
        mvc.perform(delete("/notes/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/notes/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mvc.perform(post("/notes").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesProbes() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
