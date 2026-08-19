# AISearch

AISearch is a deliberately small Android TV app for live AI-assisted
web search.

## M1

- One-screen TV interface
- D-pad friendly
- Text search
- Android voice input
- Groq `groq/compound-mini`
- Only the `web_search` Compound tool is enabled
- Every query is instructed to search the live web before answering
- The response is marked unverified if `executed_tools` does not
  confirm a search tool was used
- Groq API key is encrypted with Android Keystore and stored only on
  the device
- No account, history, sidebar or unrelated features

## First run

1. Install the APK.
2. Enter a Groq API key once.
3. Search.

Package: `com.nudroid12.aisearch`
