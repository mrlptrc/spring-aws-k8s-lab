# spring-aws-k8s-lab

Lab de estudo: uma API de notas em **Spring Boot** levada do notebook até a **AWS**, com **Terraform**, **Kubernetes (EKS)** e **CI/CD no GitHub Actions**.

Cada fase é um passo pequeno, com anotações em [`docs/`](docs/) explicando os conceitos, os comandos e o porquê de cada decisão.

## Arquitetura alvo

```
GitHub Actions (OIDC, sem access keys)
  ├─ testes + build da imagem Docker
  ├─ push → Amazon ECR
  └─ deploy → Amazon EKS
                 └─ Pod notes-api ──(IAM via Pod Identity)──> DynamoDB
                 └─ exposto por Load Balancer
Terraform: VPC, EKS, ECR, DynamoDB, IAM, OIDC do GitHub, alarme de budget
```

## Roteiro

| Fase | Conteúdo | Custo AWS | Status |
|------|----------|-----------|--------|
| 1 | API Spring Boot + testes + Docker + compose com DynamoDB Local | zero | ✅ [anotações](docs/fase-1.md) |
| 2 | Kubernetes local com `kind`: Deployment, Service, probes, ConfigMap | zero | ⏳ |
| 3 | Terraform na AWS: budget, ECR, DynamoDB, IAM, OIDC do GitHub | ~zero | ⏳ |
| 4 | EKS via Terraform + pipeline de deploy | por hora; `destroy` ao final | ⏳ |
| 5 | Extras: HPA, Prometheus/Grafana, staging | por hora | ⏳ |

## Rodando localmente

Requisitos: Java 25 (LTS) e Docker. Não precisa instalar Maven: o projeto usa o wrapper `./mvnw`.

```bash
# API + DynamoDB Local
docker compose up --build

# em outro terminal
curl -X POST localhost:8080/notes -H 'Content-Type: application/json' \
  -d '{"title":"primeira nota","content":"oi"}'
curl localhost:8080/notes
```

Testes (sobem o DynamoDB Local via Testcontainers, então precisam do Docker rodando):

```bash
cd app && ./mvnw test
```

## API

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/notes` | cria nota (`title` obrigatório, até 200 caracteres) |
| `GET` | `/notes` | lista notas |
| `GET` | `/notes/{id}` | busca nota; `404` se não existir |
| `DELETE` | `/notes/{id}` | remove nota |
| `GET` | `/actuator/health/liveness` | liveness probe |
| `GET` | `/actuator/health/readiness` | readiness probe |
| `GET` | `/actuator/prometheus` | métricas |

## Stack

Java 25 (LTS) · Spring Boot 4 · AWS SDK v2 (DynamoDB Enhanced Client) · JUnit 5 + Testcontainers · Docker
