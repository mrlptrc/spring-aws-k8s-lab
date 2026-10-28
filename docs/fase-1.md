# Fase 1 — API, testes e container

**Objetivo:** ter uma API que roda igual no notebook, no Docker e (nas próximas fases) no Kubernetes, sem conta AWS.

Ao final desta fase você deve conseguir explicar:

1. o que é o DynamoDB e por que a tabela tem uma *partition key*;
2. como a mesma imagem roda em ambientes diferentes só trocando variáveis de ambiente;
3. por que o Dockerfile tem dois estágios;
4. o que são *liveness* e *readiness* e quem consulta esses endpoints.

---

## 1. Mapa dos arquivos

```
spring-aws-k8s-lab/
├── compose.yaml                     # sobe API + DynamoDB Local
├── app/
│   ├── pom.xml                      # dependências (Maven)
│   ├── mvnw, .mvn/                  # Maven Wrapper: baixa o Maven certo sozinho
│   ├── Dockerfile                   # imagem da API (multi-stage)
│   ├── requests.http                # requisições prontas para a IDE
│   └── src/
│       ├── main/java/dev/mrlptrc/notes/
│       │   ├── NotesApiApplication.java   # ponto de entrada do Spring Boot
│       │   ├── Note.java                  # item da tabela
│       │   ├── NoteController.java        # rotas HTTP /notes
│       │   ├── DynamoDbConfig.java        # cria o cliente do DynamoDB
│       │   └── LocalTableInitializer.java # cria a tabela só no ambiente local
│       ├── main/resources/application.properties
│       └── test/java/.../NotesApiApplicationTests.java
└── docs/
```

O fluxo de uma requisição:

```
curl POST /notes
  → NoteController.create()       valida o corpo (@Valid) e monta a Note
  → DynamoDbTable<Note>.putItem()  Enhanced Client converte Note em item
  → DynamoDbClient                 chamada HTTPS para a API do DynamoDB
  → DynamoDB (Local ou AWS)
```

---

## 2. Conceitos

### DynamoDB

Banco NoSQL gerenciado da AWS, do tipo chave-valor/documento. Diferenças para um banco relacional:

- **Não tem servidor para você gerenciar.** Você cria a tabela e paga por leitura/escrita (modo *on-demand*) ou por capacidade reservada. Uma tabela parada praticamente não custa nada. Por isso ele foi escolhido no lugar do RDS.
- **Partition key** (aqui, `id`) é obrigatória. O DynamoDB usa o hash dela para decidir em qual partição física o item fica. Buscar pela chave (`getItem`) é rápido e barato.
- **`scan` lê a tabela inteira.** O `GET /notes` usa scan: funciona no lab, mas numa tabela grande seria lento e caro. Em produção você modelaria as consultas antes (índices secundários, *sort key*).
- Não há schema fixo além da chave: cada item pode ter atributos diferentes.

**DynamoDB Local** é um jar oficial da AWS que imita a API do DynamoDB. Serve para desenvolver e testar sem conta.

### AWS SDK v2 e Enhanced Client

- `DynamoDbClient` é o cliente "cru": cada chamada é um request HTTP para a API do DynamoDB.
- `DynamoDbEnhancedClient` fica por cima e converte objetos Java em itens (parecido com um ORM, bem mais simples). As anotações `@DynamoDbBean` e `@DynamoDbPartitionKey` em `Note.java` dizem como mapear.

**Credenciais.** Em `DynamoDbConfig.java`, quando não há endpoint local, o SDK usa a *default credentials provider chain*. Ela procura credenciais, em ordem, em:

1. variáveis de ambiente (`AWS_ACCESS_KEY_ID`...);
2. `~/.aws/credentials` (o que o `aws configure` cria);
3. credenciais do container/pod. No EKS (fase 4), o **Pod Identity** injeta credenciais temporárias de uma IAM Role.

Ou seja: **nenhuma chave entra no código ou na imagem.** Esse é o ponto mais importante de segurança na AWS.

### Configuração externa (12-factor)

`application.properties` define valores padrão. Qualquer um pode ser sobrescrito por variável de ambiente, graças ao *relaxed binding* do Spring:

| Propriedade | Variável de ambiente |
|-------------|----------------------|
| `app.dynamodb.endpoint` | `APP_DYNAMODB_ENDPOINT` |
| `app.dynamodb.create-table` | `APP_DYNAMODB_CREATE_TABLE` |
| `app.dynamodb.table` | `APP_DYNAMODB_TABLE` |

O `compose.yaml` usa isso. No Kubernetes, um ConfigMap fará o mesmo papel.

### Actuator: health, probes e métricas

O Spring Boot Actuator expõe endpoints operacionais:

- `/actuator/health/liveness`: "o processo está vivo?". Se falhar repetidamente, o Kubernetes **reinicia** o container.
- `/actuator/health/readiness`: "posso receber tráfego?". Se falhar, o Kubernetes **tira o pod do Service** mas não reinicia. Isso é útil durante o startup ou quando uma dependência cai.
- `/actuator/prometheus`: métricas (requisições, latência, memória da JVM) no formato que o Prometheus coleta (fase 5).

Só esses três grupos estão expostos (`management.endpoints.web.exposure.include`). Expor tudo vazaria informações internas.

### Docker multi-stage

O `Dockerfile` tem dois `FROM`:

1. **build**: imagem com JDK, que compila e gera o jar;
2. **final**: imagem só com JRE, recebe apenas o jar do estágio anterior (`COPY --from=build`).

Ganhos: imagem menor e sem compilador nem código-fonte dentro. Outros detalhes do arquivo:

- **Cache de camadas:** `pom.xml` é copiado e as dependências são baixadas *antes* de copiar `src/`. Mudou só código? O Docker reaproveita a camada de dependências.
- **Usuário não-root (`USER 10001`):** se alguém explorar a aplicação, não ganha root no container. Clusters Kubernetes costumam exigir isso.
- **`-XX:MaxRAMPercentage=75`:** a JVM calcula o heap a partir do limite de memória do container. Sem isso, um pod com limite de 512Mi pode ser morto por OOM (*OOMKilled*).

### Testcontainers

O teste de integração sobe um container real do DynamoDB Local, roda a API contra ele e destrói o container ao final. Peças-chave em `NotesApiApplicationTests.java`:

- `@Testcontainers` + `@Container`: ciclo de vida do container gerenciado pelo JUnit;
- `@DynamicPropertySource`: a porta do container é aleatória, então o endpoint é injetado depois que ele sobe;
- `MockMvc`: faz requisições HTTP à aplicação sem abrir porta de rede.

Vantagem sobre *mocks*: testa o mapeamento real do Enhanced Client e o protocolo do DynamoDB.

---

## 3. Comandos para praticar

```bash
# testes (Docker precisa estar rodando)
cd app && ./mvnw test

# rodar a API fora do Docker, com só o DynamoDB no compose
docker compose up -d dynamodb
cd app && APP_DYNAMODB_ENDPOINT=http://localhost:8000 APP_DYNAMODB_CREATE_TABLE=true ./mvnw spring-boot:run

# stack completa
docker compose up --build
docker compose logs -f api
docker compose down
```

Exercitando a API:

```bash
curl -i -X POST localhost:8080/notes -H 'Content-Type: application/json' \
  -d '{"title":"estudar EKS","content":"fase 4"}'
curl localhost:8080/notes
curl -i localhost:8080/notes/nao-existe            # 404
curl -i -X POST localhost:8080/notes -H 'Content-Type: application/json' -d '{"title":""}'   # 400
curl localhost:8080/actuator/health/readiness
curl -s localhost:8080/actuator/prometheus | grep http_server_requests
```

Inspecionando o container:

```bash
docker images | grep spring-aws-k8s-lab     # tamanho da imagem
docker compose exec api id                   # confirma uid=10001, não root
docker history spring-aws-k8s-lab-api        # camadas da imagem
```

Olhando o DynamoDB Local direto (precisa do AWS CLI instalado; credenciais podem ser falsas):

```bash
AWS_ACCESS_KEY_ID=x AWS_SECRET_ACCESS_KEY=x aws dynamodb scan \
  --table-name notes --endpoint-url http://localhost:8000 --region us-east-1
```

Repare no formato do item (`{"title": {"S": "..."}}`). É isso que o Enhanced Client esconde de você.

---

## 4. Exercícios

1. **Adicionar `PUT /notes/{id}`** para editar título e conteúdo, com teste. Dica: `table.updateItem(...)`.
2. **Derrubar o DynamoDB com a API no ar** (`docker compose stop dynamodb`) e chamar `GET /notes`. O que acontece? A readiness muda? Por quê? (Spoiler: não muda. Pense se deveria e no risco de a readiness depender de serviço externo.)
3. **Reduzir a imagem.** Hoje ela tem ~480 MB. Teste `eclipse-temurin:25-jre-alpine` e compare com `docker images`.
4. **Quebrar a validação de propósito:** troque `@NotBlank` por nada e rode os testes. Qual falha?

---

## 5. Perguntas de entrevista que esta fase cobre

- Diferença entre liveness e readiness probe.
- Por que usar multi-stage build? Por que não rodar como root?
- Como uma aplicação na AWS obtém credenciais sem access key no código?
- Quando usar DynamoDB em vez de um banco relacional? Qual o problema de `scan`?
- Teste com Testcontainers versus mock: prós e contras.

**Próxima fase:** rodar esta mesma imagem num cluster Kubernetes local (`kind`) com Deployment, Service e probes.
