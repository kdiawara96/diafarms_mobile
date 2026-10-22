# Règles R8 de la version release (minifyEnabled true).
# Google Play demande un code obscurci : les bibliothèques et le code de l'appli
# sont renommés, sauf ce qui est lu par réflexion ci-dessous.

# Numéros de ligne gardés pour lire les plantages dans la Play Console
# (le fichier mapping est inclus dans l'AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Génériques et annotations : nécessaires à Gson (TypeToken) et Retrofit.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Gson : les noms des champs sont les clés du JSON (API, cache local, saisies
# hors ligne). Les classes peuvent être renommées, pas leurs champs ni leur
# constructeur sans argument.
-keepclassmembers class com.mobile.diafarms.** {
    <fields>;
    <init>();
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Retrofit 2.9 avec R8 en mode complet (corrigé seulement à partir de 2.10).
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Classes lues par Gson seulement à travers un type générique (List<X> dans un
# TypeToken ou une réponse Retrofit) : R8 en mode complet pourrait les supprimer.
# On garde les classes de données (renommables) avec tous leurs champs.
-keep,allowobfuscation class com.mobile.diafarms.models.** { <fields>; <init>(...); }
-keep,allowobfuscation class com.mobile.diafarms.network.** { <fields>; <init>(...); }
-keep,allowobfuscation class com.mobile.diafarms.data.** { <fields>; <init>(...); }

# Énumérations relues par leur nom (SaisieType en base locale, statuts du JSON).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
