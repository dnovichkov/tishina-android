# ===========================================================================
# :core:data — consumer ProGuard rules
# ===========================================================================
#
# Rules in this file are packaged into the AAR and automatically applied by
# R8 in any module that depends on `:core:data` (currently only `:app`). The
# DRY benefit: keep rules describing this module's reflection surface live
# next to the code they protect, not far away in `app/proguard-rules.pro`.
#
# What we protect here:
#   * Room database + entities + DAOs (generated `_Impl` classes call into
#     reflective lookups on @Entity fields and @Query parameters).
#   * `OssLicensesParser.Dto` — a private `@Serializable` class decoded from
#     the bundled `assets/oss_licenses.json` by kotlinx.serialization.
# ===========================================================================

# Room generated implementations reach into our entities by name.
-keep class ru.dmdp.tishina.core.data.db.entity.** { *; }
-keep class ru.dmdp.tishina.core.data.db.dao.** { *; }
-keep class ru.dmdp.tishina.core.data.db.TishinaDatabase { *; }
-keep class ru.dmdp.tishina.core.data.db.TishinaDatabase_Impl { *; }

# Preserve the kotlinx.serialization $serializer companion of the OSS-license DTO.
-keepclassmembers class ru.dmdp.tishina.core.data.licenses.OssLicensesParser$Dto {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ru.dmdp.tishina.core.data.licenses.OssLicensesParser$Dto$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
