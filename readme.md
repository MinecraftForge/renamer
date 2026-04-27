# Renamer
---
Renamer is a general purpose Java ByteCode renaming tool. Designed to run on Java 8, but supports renaming up to Java 25. It utilizes the [OW2's ASM](https://asm.ow2.io/) library. We are mainly targeting the Minecraft ecosystem so there are some features specific for that. It supports full inheritance traversal while remapping so that you don't have to have a mapping entry for every member.

### Usage
---
Renamer is primarily designed as a command line tool. Basic Usage:
`java -jar renamer.jar --map map.tsrg --output output.jar input.jar`
In order to build a proper inheritance tree, you must either specify the full classpath, or add additional libraries using the `--lib lib.jar` argument, which can be specified as many times as needed. Map files can be any format supported by [SRGUtils](https://github.com/minecraftforge/srgutils/) (ProGuard, TSRG, Tiny v1 or v2+, or even XSRG)

### Gradle
---
We provide a gradle plugin published on under the `net.minecraftforge.renamer` id.

You can see an example in the `renamer-gradle-demo` sub-folder.

```groovy
plugins {
  id 'net.minecraftforge.renamer' version '1.0.17'
}

renamer.classes(tasks.named('jar', Jar)) {
    map = files('mappings.tsrg')
    archiveClassifier = 'renamed'
}
```

The gradle plugin also supports renaming source files using our [Srg2Source](https://github.com/MinecraftForge/Srg2Source/) tool. This is done in two steps, extract and apply. So there are two tasks created.
```groovy
def renamedSources = renamer.sources(sourceSets.main) {
    extract {
        sourceCompatibility = JavaLanguageVersion.of(17)
    }
    apply {
        map = files('libs/sources.tsrg')
        archiveClassifier = 'sources-renamed'
    }
}
```