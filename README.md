# Remootio Android Auto

An Android Auto app using the Remootio API for controlling Remootio devices.

## Building

To build a signed version of the app, certain secrets must be set. This is because signing keys are
used in the build process and are required by Google Play to publish the app for Android Auto.
The keys to be set are under [build.gradle](./RemootioAndroidAuto/main/build.gradle) and are:

```
signingConfigs {
    release {
        storeFile file("keystore.jks")
        storePassword System.getenv("SIGNING_STORE_PASSWORD")
        keyAlias System.getenv("SIGNING_KEY_ALIAS")
        keyPassword System.getenv("SIGNING_KEY_PASSWORD")
    }
}
```

The variables to set are: `SIGNING_KEY_STORE_BASE64`, `SIGNING_KEY_STORE_PATH`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

Setting this up correctly will allow the project to be built and signed by GitHub Actions. The
generated signed app bundle can then be published to Google Play, so that it can be downloaded and
used with Android Auto.

