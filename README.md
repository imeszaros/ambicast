AmbiCast
=

A small Java application that multicasts Ambilight information acquired from an Ambilight capable device on the local network.

Written in Kotlin. Licensed with [Apache 2.0](LICENSE)

Usage
-

Simply build it and launch as `java -jar ambicast-0.1-all.jar`.

You'll need a configuration file called `config.yml` that contains at least the hostname of the Ambilight device:

```yaml
jointspace:
  host: '192.168.178.24'
  apiVersion: '6'
```

Configuration
-

See [here](src/main/kotlin/Main.kt).