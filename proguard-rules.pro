# R8 rules of both apps (#398). Libraries bring their own (kotlinx.serialization, Room, Hilt, Media3, OkHttp).

# Optimize and shrink, but keep names: the app's log (Export logs, #169) and crashes name real classes.
-dontobfuscate

# File names and line numbers stay in stack traces.
-keepattributes SourceFile,LineNumberTable
