# NetWatch caller intelligence

The Android app still has one network home: **your NetWatch contact center**.

`contact_center.py` adds:
- `/v1/caller/intel` — caller registration/location, carrier/line type/risk aggregation when available
- `/v1/caller/correct` — remembers user-confirmed caller names
- `/v1/calls/incoming` — call decision endpoint
- `/v1/screen/start` — screening session bootstrap
- `/health`

## Optional online lookup

Set `ABSTRACT_PHONE_API_KEY` on the server if you choose to use Abstract's phone-validation API. No provider key is embedded in the phone APK. If the provider is unavailable or not configured, NetWatch falls back to local contacts, call history and numbering-plan information.

## Optional local AI

Run Ollama on your own server and set:

```bash
export NETWATCH_OLLAMA_URL=http://127.0.0.1:11434
export NETWATCH_AI_MODEL=llama3.2:3b
python3 server/contact_center.py
```

The model is used only for a short caller-risk summary. Caller decisions still expose their underlying evidence.

## Location limitation

A phone-number registration location or area code is **not** the caller's live physical location. NetWatch labels it as registration/numbering information rather than pretending it is GPS-style tracking.
