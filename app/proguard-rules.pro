# MangaReader R8 rules (v1.0)
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature

# Rhino：脚本运行时大量使用反射与动态类加载
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**

# kotlinx.serialization：保留 @Serializable 生成的序列化器
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.saonte.mangareader.**$$serializer { *; }
-keepclassmembers class io.github.saonte.mangareader.** {
    *** Companion;
}

# Hilt / Room / Compose 由官方 consumer rules 处理；保留崩溃栈可读
-keepnames class io.github.saonte.mangareader.** { *; }

# jsoup 可选 Re2J 支持（未引入该依赖）
-dontwarn com.google.re2j.**
