# disable obfuscation
-dontobfuscate

# preserve the line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# remove kotlin null checks
-processkotlinnullchecks remove

# Tasker plugin library uses reflection to read annotated input/output fields
-keepattributes RuntimeVisible*Annotations
-keep @com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot class * { *; }
-keep @com.joaomgcd.taskerpluginlibrary.input.TaskerInputObject class * { *; }
-keep @com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject class * { *; }
-keepclassmembers class * {
    @com.joaomgcd.taskerpluginlibrary.input.TaskerInputField *;
    @com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable *;
}
