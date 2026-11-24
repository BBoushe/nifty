# Spring AI 2 with ki:connect NRW

Version: 2.0.x (pinned 2.0.0 in backend/pom.xml)
Sources:
- [1] https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
- [2] https://docs.spring.io/spring-ai/reference/api/chatclient.html
- [3] https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html
- [4] https://docs.spring.io/spring-ai/reference/upgrade-notes.html
- [5] https://github.com/spring-projects/spring-ai (tag v2.0.0 and branch 2.0.x, source files named inline)
- [6] https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html
- [7] Repo files: backend/pom.xml, backend/src/main/resources/application.yml, .env.example, docs/decisions/1-architecture.md (decision 1.6)

Retrieved: 2026-11-24

## Starter and properties
- Use the starter `org.springframework.ai:spring-ai-starter-model-openai`. [1][7]
- Import `spring-ai-bom` at `${spring-ai.version}` to manage the version. [7]
- Spring AI 2.0 calls the OpenAI API through the official `openai-java` SDK. [4]
- The `spring.ai.openai.*` properties stay valid after this change. [4]
- Common keys: `spring.ai.openai.base-url` and `spring.ai.openai.api-key`. [1]
- Chat keys use the prefix `spring.ai.openai.chat`. Example: `spring.ai.openai.chat.model`. [1]
- The key `spring.ai.openai.chat.temperature` sets the temperature. [1]
- Spring AI 2.0 sets no default temperature. Set it explicitly if the code needs one. [4]
- Other keys: `spring.ai.openai.timeout` (60 s default) and `spring.ai.openai.max-retries` (3 default). [1]

```yaml
spring.ai.openai:
  api-key: ${KICONNECT_API_KEY}
  base-url: https://chat.kiconnect.nrw/api/v1
  chat:
    model: OpenAI GPT OSS 120B
    temperature: 0.0
```

## Legacy `.options` keys
- Spring AI 2.0 removes the `.options` segment from most chat property keys. Old `.options` aliases still work. [4]
- `OpenAiChatProperties.getOptions()` carries `@Deprecated(since = "2.0.0", forRemoval = true)`. [5: OpenAiChatProperties.java, v2.0.0]
- The key `spring.ai.openai.chat.options.model` is therefore deprecated. Use `spring.ai.openai.chat.model`. [1][5]
- The project application.yml still uses `chat.options.model`. Migrate it to `chat.model`. [7]

## Base URL and path
- Spring AI has no `completions-path` property for OpenAI in 2.0. The property classes do not define one. [5: OpenAiChatProperties.java, AbstractOpenAiProperties.java]
- Spring AI does not append `/v1`. It passes your `base-url` unchanged to the SDK. [5: OpenAiSetup.calculateBaseUrl]
- If `base-url` is blank, Spring AI uses `https://api.openai.com/v1`. That default already contains `/v1`. [5: OpenAiSetup.OPENAI_URL]
- The reference tells users of local servers to add `/v1` to the base URL when the server expects it. [1]
- Keep the `/api/v1` suffix in the ki:connect base URL. Do not add a second `/v1`. [5][7]
- I could not verify the final path segment from Spring AI sources. The SDK appends it. Test with one real call.
- An empty API key disables authentication. Local servers use this. [1]

## ChatClient
- Inject the auto-configured `ChatClient.Builder` and call `build()` once. [2]

```java
this.chatClient = chatClientBuilder.defaultSystem("You are an evaluator.").build();

String text = chatClient.prompt()
    .system("Rule text")
    .user(userInput)
    .call()
    .content();
```

- The builder has prototype scope. Each injection point gets a new builder. [2]
- Do not use `ChatClient.create(chatModel)` for extra beans. It skips observability and `ChatClientBuilderCustomizer` beans. [2]
- `call()` does not run the model. The model runs on `content()`, `chatResponse()`, or `entity(...)`. [2]
- `content()` returns `null` when the response has no text. [5: DefaultChatClient.java]
- For SSE, use `stream().content()`. It returns `Flux<String>`. [2]

```java
Flux<String> tokens = chatClient.prompt().user(userInput).stream().content();
```

- `stream().chatResponse()` returns `Flux<ChatResponse>` with metadata. [2]
- Streaming has no `entity()` method. Join the chunks, then convert them with a `StructuredOutputConverter`. [2]

## Structured output
- `entity(Class)` builds a `BeanOutputConverter` and parses the model text into your type. [2][3]

```java
record Finding(String criterion, String quote) {}

Finding f = chatClient.prompt().user(p).call().entity(Finding.class);
List<Finding> all = chatClient.prompt().user(p).call()
    .entity(new ParameterizedTypeReference<List<Finding>>() {});
```

- `BeanOutputConverter` adds a JSON Schema (draft 2020-12) format instruction to the prompt. [3]
- Parsing is strict. Prose before a fenced JSON block makes the parse fail. [3]
- The converter ignores unknown JSON properties. [5: BeanOutputConverter.getJsonMapper]
- The docs do not name the exception for a plain parse failure. [3]
- The source calls Jackson 3 `JsonMapper.readValue` and does not catch errors. Expect a `tools.jackson.core.JacksonException`. [5: BeanOutputConverter.convert, v2.0.0]
- Catch the exception at the service boundary. Do not guess a fallback value.
- `entity()` returns `null` when the response has no text. Check for `null`. [5: DefaultChatClient.java]
- A filtered response with no usable text throws `ContentFilteredException`. [2]
- `responseEntity()` returns a `null` entity for a filtered response. It does not throw. [2]
- The second argument `spec -> spec.validateSchema()` validates JSON against the schema and retries with error feedback. [2]
- The option `useProviderStructuredOutput()` sends the schema as an API constraint. Check that ki:connect supports it first. [2][3]
- To accept fenced JSON, wrap `BeanOutputConverter` in a custom `StructuredOutputConverter`. The docs show an example. [3]

## Testing
- Spring AI ships no mock `ChatModel`. Its `spring-ai-test` module has evaluators and base test classes only. [5: spring-ai-test/README.md]
- `ChatModel` is an interface with `ChatResponse call(Prompt)` and a default `stream(Prompt)`. [5: ChatModel.java]
- `ChatClient` calls `chatModel.call(prompt)` through its advisor chain. [5: ChatModelCallAdvisor.java]
- Mock `ChatModel`, then build a real `ChatClient` with `ChatClient.create(mock)`. This tests the real `entity()` parsing.

```java
ChatModel model = mock(ChatModel.class);
when(model.call(any(Prompt.class))).thenReturn(
    new ChatResponse(List.of(new Generation(new AssistantMessage("{\"criterion\":\"a\",\"quote\":\"b\"}")))));
ChatClient client = ChatClient.create(model);
```

- `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`) replaces a bean with a Mockito mock. [6]
- Use `@MockitoBean ChatModel` in Spring context tests to replace the real model bean.
- Spring AI own tests mock the SDK client with Mockito. [5: OpenAiExtraBodyTests.java]

## Breaking changes from 1.x
- `ChatModel.call()` no longer runs tools. `ChatClient` runs tools through `ToolCallingAdvisor`. [4]
- `internalToolExecutionEnabled` is removed. `ToolCallAdvisor` is now `ToolCallingAdvisor`. [4]
- Renames: `FunctionCallback` is `ToolCallback`. `defaultFunctions(...)` is `defaultTools(...)`. `.functions(...)` is `.tools(...)`. [4]
- `ChatClient.prompt().options(...)` takes the options builder. Do not call `.build()` on it. [4]
- Options are immutable. `ChatOptions#copy()` is removed. Use `mutate()`. [4]
- `ChatModel#getDefaultOptions()` is deprecated. Use `getOptions()`. [4]
- The OpenAI builder method `N(...)` is now `n(...)`. [4]
- The modules `spring-ai-azure-openai` and `spring-ai-openai-sdk` are removed. Use `spring-ai-openai`. [4]
- `BeanOutputConverter` uses Jackson 3 (`tools.jackson.*`). Do not import `com.fasterxml.jackson` types for it. [5: BeanOutputConverter.java]
- `JsonParser` is deprecated. Use `JsonHelper` from `spring-ai-commons`. [4]

## ki:connect NRW
- Base URL: `https://chat.kiconnect.nrw/api/v1`. Env var: `AI_EVALUATOR_BASE_URL`. [7]
- API key env var: `KICONNECT_API_KEY`. The app starts with the placeholder `not-configured` when it is unset. [7]
- Model env var: `AI_EVALUATOR_MODEL`. Default: `OpenAI GPT OSS 120B`. [7]
- Allowed models: `OpenAI GPT OSS 120B`, `Qwen 3.8 27B`, `Mistral Small 4 119B`. [7]
- The provider is an EU host in Aachen, NRW. Use it only for the LLM evaluator. [7]
- Decision 1.6: do not build an offline stub gateway. Mock or replay Spring AI `ChatClient` in tests. [7]
- LLMs never compute scores, totals, or rankings. See AGENTS.md section 2 and `.agents/rules/spec-driven.md`.
