# AISearch

Personal Android TV AI search app.

AISearch is intentionally small. It searches current web results first,
then uses Groq to summarise those live sources for easy reading on TV.

## Current version

`0.4.0`

## Search flow

1. Tavily keyless search fetches current web results.
2. AISearch requires usable live sources.
3. Groq `openai/gpt-oss-120b` summarises only the supplied sources.
4. Current facts are not allowed to silently fall back to model memory.

## TV behaviour

- Text search
- Voice search
- D-pad focus navigation
- Page scrolling for long answers
- Back returns to the search field before exiting
- Groq API key is encrypted with Android Keystore
- No account, history, sidebar, updater, store integration or public
  distribution features

## Build

Run the `AISearch Personal Build` workflow manually.

Artifact:
`AISearch-personal-debug`

Package:
`com.nudroid12.aisearch`
