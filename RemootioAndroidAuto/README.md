# remootio-android-auto

An Android Auto app using the Remootio API for controlling Remootio devices.

## Building

The app needs some environment variables set in order for it to be signed when being built. See:

```
signingConfigs {
        release {
            storeFile file(System.getenv("SIGNING_KEY_STORE_PATH"))
            storePassword System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias System.getenv("SIGNING_KEY_ALIAS")
            keyPassword System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
```
