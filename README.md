# AISearch

AISearch is a small Android TV app for current, live AI-assisted
web search.

## M2 architecture

Search flow:

1. Tavily keyless Search fetches live web results.
2. AISearch requires at least one usable live result.
3. Groq `openai/gpt-oss-120b` receives only those results plus the
   user's question.
4. The model is instructed to answer only from supplied live sources.
5. The answer includes source URLs.
6. If current information is not supported by search results,
   AISearch must say it cannot confirm it.

## Cost design

- Tavily keyless Search is free and rate-limited.
- Groq Free Plan is used for `openai/gpt-oss-120b`.
- Groq paid built-in Web Search is not used.
- Only a Groq API key is required on the device.

## Reliability

- Fails closed if no usable live web result exists.
- Uses up to five live sources.
- Uses low-temperature synthesis.
- Retries Groq once for HTTP 429 and 5xx errors.
- Sends the current date into search and synthesis context.

Package: `com.nudroid12.aisearch`
