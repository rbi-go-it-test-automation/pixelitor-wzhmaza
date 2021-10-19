This is a *FORK* of the source code of [Pixelitor](https://pixelitor.sourceforge.io/) - an advanced Java image editor with layers, layer masks, text layers, 110+ image filters and color adjustments, multiple undo etc.

In contrast to the original project, we use Gradle and integrated
Teamscale's JUnit test runner.


## Starting Pixelitor in an IDE

Pixelitor requires Java 16+ to compile. When you start the program from an IDE, 
use **pixelitor.Pixelitor** as the main class.

## Building the Pixelitor jar file from the command line

1. OpenJDK 16+ has to be installed, and the environment variable `JAVA_HOME` 
must point to the OpenJDK installation directory.
2. Execute `./gradlew clean shadowJar` in the main directory (where the `build.gradle` file is), 
this will create an executable `.jar` in the `build/libs` sub-directory. 

## Running Pixelitor

Pixelitor can be started by executing the `.jar` file built in the previous step.
To be usable on today's high resolution displays, a scaling factor has to be
added as a command line argument to java: `java -jar -Dsun.java2d.uiScale=2.5 ./build/libs/Pixelitor-4.3.0-all.jar`.

## Translating the Pixelitor user interface

See [Translating](Translating.md).


