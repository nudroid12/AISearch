# TV AI Search M1

A deliberately small Android TV search client.

## M1 behaviour

- One-screen TV UI
- D-pad friendly
- Text search
- Android voice input
- Groq `groq/compound-mini`
- Web search is the only enabled Compound tool
- System instruction requires a web search for every query
- API key is encrypted with Android Keystore and stored only on the device
- No account, history, sidebar or unrelated features

## First run

1. Create a Groq API key in the Groq console.
2. Install the APK.
3. Enter the key once.
4. Search.

## Current live-data guard

The app inspects the returned `executed_tools` field. If it cannot confirm that
a search tool was executed, it puts a warning above the answer instead of
silently presenting the response as live information.

## Build

The included GitHub Actions workflow installs Gradle 9.3.1 and builds the debug APK.

Android configuration:
- AGP 9.1.1
- compileSdk 36
- targetSdk 36
- minSdk 23

## Important

Groq web-search availability, free-tier limits and pricing can change. The app
does not assume that a current free allowance is permanent.
