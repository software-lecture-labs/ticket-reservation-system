# Ekip çalışma kuralları

1. Kendi GitHub hesabınızı ve adınızı `docs/TEAM_PLAN.md` tablosuna ekleyin; hocaya GitHub hesabınızı bildirin.
2. Güncel `dev` üzerinden kişisel özellik dalı açın: `feature/member-1-auth`, `feature/member-2-catalog` gibi.
3. Bir issue'da kapsamı ve kabul ölçütlerini yazın. Ortak tablo/API sözleşmesini kodlamadan önce diğer sorumlu ile netleştirin.
4. Her çalışır küçük adımda anlamlı commit oluşturun. Örnek: `feat(catalog): add venue filtering`, `test(reservation): reject concurrent seat claims`.
5. PR açmadan önce Maven doğrulamasını ve özelliğe uygun entegrasyon testini çalıştırın.
6. PR'a ilgili issue, kısa davranış açıklaması ve test kanıtı ekleyin. Planlanan başka üye review yapmalı.
7. CI yeşil ve review tamamlandıktan sonra `dev` dalına birleştirin. Branch protection'ı repository yöneticisi etkinleştirmelidir.

Commit sayısını yapay olarak artırmayın; başka üyenin adına commit yazmayın. Her üye kendi Java kodu, testleri ve dokümantasyonuna düzenli katkı yapmalıdır. Katkı dengesini haftalık `git shortlog -sne --all` ve PR/issue geçmişiyle değerlendirin; sayı tek başına emek ölçüsü değildir.

Ortak entity/SQL/pom değişikliklerini küçük PR'larla yapın. Uygulanmış migration değiştirilmez, yeni sürüm eklenir. Schema değiştiren PR temiz veritabanı ve mevcut veri üzerinde yükseltme testi içermelidir.

`.env`, token, veritabanı parolası, IDE çıktısı, `.tools` ve `target` commit edilmez. Domain/application sınıfları sunum veya JPA adaptörünü import etmez. Para için `double`, işlem güvenliği için JVM içi kilit kullanılmaz.
