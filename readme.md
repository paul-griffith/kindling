# Kindling

A standalone utility project for Ignition users. Features various tools to help with troubleshooting Ignition systems.

## Usage

![](assets/threads.gif)
![](assets/logs.gif)
![](assets/cache.gif)

Download either the installer or portable executable from the releases tab. Once running, you can use Kindling to open
various Ignition related files.

## Development

Kindling is a Java Swing application, but written almost exclusively in Kotlin, an alternate JVM language. Gradle is
used as the build tool, and will automatically download the appropriate Gradle and Java version (via the Gradle wrapper)
. Most IDEs (Eclipse, IntelliJ should "figure it out" automatically). You can directly run the main class in your
IDE ([MainPanel](src/main/kotlin/io/github/paulgriffith/MainPanel.kt)), or you can run the application
via`./gradlew run` at the command line.

## Contribution

Contributions of any kind (additional tools, polish to existing tools) are welcome.

## Releases

`./gradlew jpackage` will generate a `jpackage` folder in `build/`. The outer level `support-tools-$version.exe` can be
used as an installer, or you can zip the `support-tools` folder and distribute it directly as a portable executable.

## Acknowledgements

- [Ignition](https://inductiveautomation.com/)
- [BoxIcons](https://github.com/atisawd/boxicons)
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf)
- [SerializationDumper](https://github.com/NickstaDB/SerializationDumper)

## Disclaimer

This is **not** an official Inductive Automation product and is not affiliated with, supported by, maintained by, or
otherwise associated with Inductive Automation in any way. This software is provided with no warranty.
