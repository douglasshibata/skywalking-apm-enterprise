# Plataforma Global de Observabilidade Enterprise, APM e Tracing Distribuído

Este ecossistema implementa uma plataforma corporativa completa de Observabilidade, APM (Application Performance Monitoring) e Tracing Distribuído baseada no **Apache SkyWalking 9.x / 10.x**. A solução inclui microserviços de exemplo poliglotas/Java totalmente instrumentados de ponta a ponta, integração com armazenamento escalável (Elasticsearch), mecanismos de alerta e dashboards unificados de telemetria.

---

## 🏗️ 1. Arquitetura do Sistema e Fluxo de Observabilidade

Abaixo está o fluxo detalhado de coleta, agregação e visualização de telemetria através das camadas do Apache SkyWalking:

```text
=============================================================================================================
                                  APACHE SKYWALKING ENTERPRISE ARCHITECTURE
=============================================================================================================

  +-----------------------+      +-----------------------+      +-----------------------+
  |     order-service     |      |    payment-service    |      |    Other Services     |
  |  (Java Agent / SDK)   |      |  (Java Agent / SDK)   |      | (Go, Python, Node.js) |
  +-----------+-----------+      +-----------+-----------+      +-----------+-----------+
              |                              |                              |
              | Native SkyWalking Protocol   | Native SkyWalking Protocol   | OpenTelemetry (OTLP) / gRPC
              +------------------------------+------------------------------+
                                             |
                                             v
                             +-------------------------------+
                             |    SkyWalking OAP Server      |
                             | (Observability Analysis Corp) |
                             +---------------+---------------+
                                             |
                  +--------------------------+--------------------------+
                  |                          |                          |
                  v                          v                          v
       +--------------------+     +--------------------+     +--------------------+
       |    OAP Receiver    |     |  Stream Processor  |     |   Aggregator &     |
       |  (gRPC / HTTP)     |     |   & LAL / MAL      |     |   Alarm Engine     |
       +--------------------+     +--------------------+     +--------------------+
                  |                          |                          |
                  +--------------------------+--------------------------+
                                             |
                                             v
                             +-------------------------------+
                             |        Storage Engine         |
                             |   (Elasticsearch / BanyanDB)  |
                             +---------------+---------------+
                                             |
                                             v
                             +-------------------------------+
                             |      SkyWalking Web UI        |
                             |     (Booster Dashboard)       |
                             +-------------------------------+
```

### Conceitos Fundamentais da Plataforma

1. **Trace (Rastreamento):** Representa o caminho de execução completo de uma transação através de múltiplos microserviços distribuídos.
2. **Segment (Segmento de Trace):** Coleção de Spans executados dentro do mesmo processo ou thread (ex: JVM do `order-service`).
3. **Span (Intervalo):** A unidade básica de trabalho. Pode ser um Span de Entrada (*Entry Span* como requisições HTTP), de Saída (*Exit Span* como requisições REST/gRPC externas ou chamadas ao banco), ou Local (*Local Span* para métodos internos).
4. **Service (Serviço):** Uma coleção de instâncias que fornecem a mesma lógica de negócios (ex: `order-service`).
5. **Service Instance (Instância do Serviço):** Um processo individual no cluster de serviços (ex: contêiner Docker rodando o `order-service`).
6. **Endpoint:** Um caminho de rede ou operação específica dentro de um serviço que recebe tráfego (ex: `/orders` via `POST`).
7. **LAL (Log Analysis Language) & MAL (Metrics Analysis Language):** Linguagens de script nativas para filtrar, analisar e calcular métricas complexas com base nos payloads de logs e métricas recebidos.
8. **Topology Graph (Mapa de Dependências):** Visualização gerada dinamicamente pelo OAP Server baseada nas relações de Spans de entrada e saída (`sw8` tracing headers), mostrando gargalos e dependências em tempo real.

---

## 📦 2. Estrutura do Projeto

Abaixo está a árvore completa de diretórios da solução:

```text
skywalking-apm-enterprise/
├── config/
│   └── alarm-settings.yml       # Regras de Alerta do OAP Server (SLA, latência, SLA, webhooks)
├── order-service/
│   ├── src/                     # Código fonte e testes do Order Microservice
│   ├── Dockerfile               # Pipeline de build e download automático do Java Agent
│   └── pom.xml                  # Dependências do Maven para order-service
├── payment-service/
│   ├── src/                     # Código fonte e testes do Payment Microservice
│   ├── Dockerfile               # Pipeline de build e download automático do Java Agent
│   └── pom.xml                  # Dependências do Maven para payment-service
├── docker-compose.yml           # Orquestração do Elasticsearch, OAP Server, Booster UI e serviços
└── pom.xml                      # POM pai para gerenciamento centralizado de dependências
```

---

## ⚙️ 3. Instrumentação de Microserviços

### A. Instrumentação Automática (Zero-Code)

Configurada através de variáveis ambientais e argumentos de JVM passados nos Dockerfiles ou manifestos de deploy:

```bash
# Execução direta com o jar do agente anexado à JVM
java -javaagent:/path/to/skywalking-agent.jar \
     -DSW_AGENT_NAME=order-service \
     -DSW_AGENT_COLLECTOR_BACKEND_SERVICES=localhost:11800 \
     -jar app.jar
```

### B. Instrumentação Manual e Anotações (`apm-toolkit-trace`)

Utilizada no desenvolvimento dos controladores `OrderController` e `PaymentController` para capturar metadados adicionais, Tags corporativas de segurança/negócio, e monitorar métodos locais:

* **@Trace:** Permite que métodos internos que não são pontos de entrada REST padrão sejam representados como Spans locais no mapa de execução.
* **@Tag e @Tags:** Captura parâmetros dinâmicos de entrada (`arg[0]`) ou valores de retorno (`returnedObj`) no trace final. Exemplo:
  ```java
  @Trace(operationName = "executeExternalPayment")
  @Tags({
      @Tag(key = "userId", value = "arg[0].get('userId')"),
      @Tag(key = "amount", value = "arg[0].get('amount')")
  })
  ```
* **ActiveSpan API:** Adiciona tags em runtime de maneira imperativa e gerencia o status de exceções para alertas rápidos de SRE:
  ```java
  ActiveSpan.setTag("payment.transaction.id", transactionId);
  ActiveSpan.error(e); // Captura a stack trace e marca o Span como com erro.
  ```

### C. Propagação de Contexto Distribuído (gRPC, HTTP `sw8`)

O SkyWalking propaga o contexto de execução entre serviços de forma nativa e automática utilizando os cabeçalhos HTTP em conformidade com o padrão `sw8`.

O cabeçalho `sw8` carrega dados críticos codificados em Base64, incluindo:
* `1` (Versão do Protocolo)
* `Trace ID` (Identificador global da requisição)
* `Parent Service` (Serviço originador)
* `Parent Service Instance` (Instância originadora)
* `Parent Endpoint` (Endpoint de origem)
* `Address` (Endereço do alvo downstream)

---

## 🔔 4. Configuração de Regras de Alerta e Logs

### Regras de Alerta (`alarm-settings.yml`)

O mecanismo de alarmes do OAP avalia métricas agregadas em janelas deslizantes temporais. O arquivo `config/alarm-settings.yml` define regras essenciais de SRE:

1. **service_resp_time_rule:** Alerta caso o tempo médio de resposta ultrapasse `1000ms` em 3 de 10 minutos.
2. **service_sla_rule:** Dispara alertas quando a taxa de sucesso das requisições (SLA) cai abaixo de `99%` (`9900` no SkyWalking).
3. **service_p99_latency_rule:** Monitora picos de latência no P99 superiores a `2000ms`.
4. **webhooks:** Redireciona notificações de forma assíncrona para Slack, MS Teams ou PagerDuty via Webhooks REST.

### Coleta e Correlação de Logs (`logback-spring.xml`)

Ambos os serviços utilizam o appender gRPC do toolkit (`GRPCLogAppender`) associado ao layout `%tid` (Trace ID) para enviar logs diretamente ao OAP Server:
* Os logs gerados no console do Spring Boot e nos arquivos contêm o `Trace ID` ativo (ex: `[TID:123.456.789]`).
* No painel do SkyWalking, o SRE pode selecionar um Trace lento e clicar diretamente na aba "Logs" para visualizar os logs gerados especificamente por aquela requisição em todos os microserviços participantes, reduzindo o tempo de resolução de incidentes (MTTR).

---

## 🚀 5. Guia Operacional, Performance e Tuning

### Dimensionamento de Memória e Storage (Alta Vazão)

Para cenários corporativos reais que processem milhares de requisições por segundo (RPS), utilize os seguintes parâmetros recomendados de dimensionamento:

| Componente | CPU (Cores) | Memória RAM | Storage Recomendado | JVM Heap Settings |
| :--- | :--- | :--- | :--- | :--- |
| **OAP Server (Réplica)** | 4 - 8 vCPUs | 8GB - 16GB | SSD Local de Baixa Latência | `-Xms4g -Xmx4g` |
| **Elasticsearch Node** | 8 - 16 vCPUs | 16GB - 32GB | NVMe / EBS gp3 (Mínimo 3000 IOPS) | `-Xms8g -Xmx8g` (50% da RAM) |

### Amostragem de Traces (Trace Sampling)

Para otimizar o uso de I/O de disco e economizar espaço no Elasticsearch sem comprometer a visibilidade de anomalias, ajuste a taxa de amostragem na inicialização do agente através de variáveis ambientais:

```env
# Amostrar apenas 10% de traces de sucesso, mas amostrar 100% dos traces com erros/exceções
SW_AGENT_SAMPLE_N_IN_10_SECS=100
SW_AGENT_FORCE_SAMPLE_ERROR_TRACES=true
```

No OAP Server, você também pode limitar a taxa global de amostragem de traces definindo a propriedade no arquivo `application.yml` do OAP:
* `receiver-trace/default/sampleRate: 10000` (Amostragem em formato de taxa base de 10.000, onde 10000 representa 100%).

### Políticas de Retenção de Dados (TTL)

No ambiente de produção, ajuste o tempo de vida (TTL) dos dados históricos dentro do OAP Server para que o Elasticsearch realize limpezas rotineiras e evite discos cheios:

```yaml
# Configurações passadas nas variáveis ambientais do OAP (docker-compose)
SW_CORE_RECORD_DATA_TTL: 7   # TTL em dias para dados detalhados (Traces, logs individuais, eventos)
SW_CORE_METRICS_DATA_TTL: 14 # TTL em dias para dados de métricas agregadas (SLA, percentis de performance)
```

---

## 🛠️ 6. Como Executar o Ecossistema Localmente

### Pré-requisitos
* Java 21 SDK instalado
* Apache Maven 3.9+ instalado
* Docker & Docker Compose v2+ instalados

### Passo 1: Compilar o Projeto
Execute a compilação e execute todos os testes unitários/integração do Maven para garantir que a suite de testes está 100% funcional:
```bash
cd skywalking-apm-enterprise
mvn clean verify
```

### Passo 2: Inicializar o Ambiente
Suba o ecossistema completo utilizando o Docker Compose:
```bash
docker compose up -d --build
```

### Passo 3: Testar e Gerar Telemetria
Envie requisições de teste para gerar tráfego e telemetria distribuída:

1. **Criar uma transação bem-sucedida:**
   ```bash
   curl -X POST http://localhost:8081/orders \
     -H "Content-Type: application/json" \
     -d '{"userId": "user-corp-99", "amount": 120.50}'
   ```

2. **Simular uma falha / exceção de limite de crédito (Payment Rejected):**
   ```bash
   curl -X POST http://localhost:8081/orders \
     -H "Content-Type: application/json" \
     -d '{"userId": "user-corp-99", "amount": 15000.00}'
   ```

3. **Simular latência elevada (P99 Latency Spike):**
   ```bash
   curl http://localhost:8081/orders/slow
   ```

### Passo 4: Acessar os Painéis de Controle
* **SkyWalking Booster UI:** Acesse http://localhost:8080 para visualizar a topologia de rede distribuída em tempo real, examinar a árvore de spans do trace, conferir os logs correlacionados pelo `%tid` e os gráficos de SLA.
* **Elasticsearch Engine Endpoint:** Acesse http://localhost:9200 para verificar os índices criados e a saúde do storage.
