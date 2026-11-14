# Çalıştırma ve application server üzerinde yayınlama

Uygulama Spring Boot/standalone HTTP server yerine Jakarta EE application server olan **Payara Micro** üzerinde WAR olarak çalışır. Payara'nın kendi JTA, CDI, JPA ve Faces uygulamaları kullanılır. Java 21 gereklidir; sürümler `pom.xml` içinde sabitlenmiştir.

## Docker Compose

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose logs -f app
```

PostgreSQL sağlıklı olmadan uygulama başlatılmaz. Uygulama ve PostgreSQL portları geliştirmede localhost'a bağlıdır. Değişiklik sonrası `docker compose up -d --build app` ile yeniden dağıtılır. `docker compose down` veriyi korur. Veritabanı eskiyse yeni SQL dosyaları otomatik yeniden çalışmaz; migration gerekir. `down -v` veriyi sileceğinden rutin yeniden başlatma için kullanılmaz.

## IDE / yerel Java ile

Önce PostgreSQL'i çalıştırın: `docker compose up -d db`. Java 21'i `JAVA_HOME` ve `PATH` içinde ayarlayın. Maven ayrıca kurulmak zorunda değildir; Wrapper indirir.

Windows PowerShell:

```powershell
.\scripts\start-local.ps1
```

Script `.env` değerlerini yalnızca o süreç ortamına yükler, `mvnw -Pruntime verify` çalıştırır ve Payara'yı önde başlatır. Durdurmak için Ctrl+C. Port değişikliği için `-Port 8081` verilebilir.

Linux/macOS:

```sh
set -a
. ./.env
set +a
sh mvnw -Pruntime verify
java -jar target/runtime/payara-micro.jar --addlibs target/runtime/postgresql.jar \
  --deploy target/ticket-reservation-system.war --contextroot / --disablephonehome --nocluster
```

`.env` için verilen örnek kabukla uyumludur; shell metakarakterleri içeren değerlerde uygun tırnaklama kullanın. `runtime` profili Payara ve JDBC driver JAR'larını `target/runtime` altına indirir. Normal `verify` yalnızca WAR ve kalite raporlarını üretir.

## Payara Server'a WAR dağıtımı

Java 21/Jakarta EE 11 destekleyen Payara Server kullanın. PostgreSQL JDBC sürücüsünü sunucunun library dizinine ekleyip sunucuyu yeniden başlatın. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` değerlerini sunucu **başlamadan önce** ortamına verin; şemayı SQL dosyalarıyla oluşturun. Sonra:

```sh
sh mvnw verify
asadmin deploy --contextroot / target/ticket-reservation-system.war
```

`WEB-INF/payara-resources.xml`, uygulama kapsamında `java:app/jdbc/TicketDS` pool/resource oluşturur; `persistence.xml` JTA veri kaynağını kullanır. `${ENV=...}` Payara ortam değişkeni çözümlemesidir. Aynı sunucuda farklı uygulamalar için uygulama kapsamı ad çakışmasını azaltır.

## Gerçek hosting için hazır yayın yapılandırması

Henüz bir hosting hesabına dağıtım yapılmadı. Kimlik/yetki özellikleri tamamlandıktan sonra Linux sunucuda Docker ve Compose kurulu olmalı; alan adının DNS A/AAAA kaydı bu sunucuya yönelmeli, 80/443 erişilebilir olmalıdır. `.env` değerlerini oluşturup `APP_DOMAIN=bilet.ornek.edu.tr` ekleyin; örnek alan adını kendi alan adınızla değiştirin.

```sh
docker compose -f compose.yaml -f compose.publish.yaml up -d --build
```

Caddy HTTPS bağlantısını karşılar ve istekleri Payara'ya iletir. PostgreSQL ve Payara'nın doğrudan portları localhost'ta kalır. Yayın sonrası `/api/health`, kullanıcı → rezervasyon → ödeme → iptal demosu ve loglar kontrol edilir. Şema değiştiren smoke testi gerçek kullanıcı veritabanında çalıştırılmaz; ayrı test kurulumunda çalıştırılır. Yayın adresi, sürüm commit'i ve ekran görüntüsü teslim kanıtlarına eklenir.

## Yedekleme

```sh
docker compose exec -T db pg_dump -U tickets -d tickets > tickets-backup.sql
```

Kullanıcı/DB adlarını `.env` ile eşleştirin. `>` ile SQL yedeği alırken UTF-8 destekleyen shell kullanın; Windows PowerShell 5 yerine PowerShell 7 veya `cmd` tercih edilebilir. Geri yüklemeyi boş, ayrı bir test veritabanında deneyin. Otomatik yedekleme, migration ve restore testi Üye 6'nın geliştirme paketidir.

Kaynaklar: [Payara CLI seçenekleri](https://docs.azul.com/payara-community/technical-documentation/payara-micro-documentation/payara-micro-configuration-and-management/micro-management/command-line-options/command-line-options.html), [ortam değişkenleri](https://docs.azul.com/payara-community/technical-documentation/payara-server-documentation/general-administration/configuration-variables-reference), [uygulama resource tanımları](https://docs.azul.com/payara-community/technical-documentation/application-deployment/deployment-descriptor-files).
