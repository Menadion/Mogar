# R8 rules for the release build. Enabled 2026-10-10; before that the release shipped every class
# and resource of Compose, Firebase and MapLibre whether or not Mogar touched them.
#
# Deliberately short. MapLibre, Firebase and AndroidX each ship their own consumer rules inside
# their AARs and R8 applies those automatically, so the reflection they do on their own classes is
# already covered (MapLibre's proguard.txt keeps RenderingStats, NativeMapOptions, its enums and
# the gson classes its native code reaches for). Nothing here repeats them. Mogar itself never
# hands a class to Firestore to fill in by reflection: every field is read by name with
# doc.getString and friends, and Member is built by hand, so the usual Firebase-under-R8 breakage
# does not apply.

# Keep crash reports readable.
#
# Diagnostics.install stores the first lines of a crash and sends them with the next location, and
# that field is the only way a crash on a phone overseas ever reaches M. Obfuscated class names and
# missing line numbers would make it useless exactly when it matters, so:
#   - SourceFile and LineNumberTable stay, so a trace still names a file and a line.
#   - Mogar's own classes keep their real names, so a trace can be read with no mapping file at
#     hand. They can still be shrunk if unreachable; only the renaming is off. The app's own code
#     is a few dozen KB against 12.5 MB of libraries, so this gives up almost nothing.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepnames class io.github.menadion.magus.** { *; }
