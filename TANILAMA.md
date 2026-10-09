# M-G Kamera Tanılama 0.2

TECNO CK7n / Android 14 için bağımsız Camera2 ölçüm uygulaması. Orijinal TECNO uygulamasını değiştirmez ve OEM kodu/kütüphanesi içermez. Yalnızca kamera izni ister; internet izni yoktur.

## Kullanım

1. `build/MG-Kamera-Tanilama.apk` dosyasını ADB ile kurun ve uygulamayı açın.
2. Kamera iznini verin. Özellikler otomatik okunur.
3. Telefonu aydınlık bir sahneye yöneltip standart fotoğraf testini çalıştırın.
4. İlk test tamamlanınca 64 MP erişim denemesini çalıştırın.
5. JSON raporunu kaydedin. Fotoğraflar `Pictures/MGKamera` altına kaydedilir.

Bu bir tanılama sürümüdür: önizleme, video veya yeni HDR algoritması henüz yoktur. Önizlemesiz tek çekimde 3A'nın yerleşmesi beklenmez; fotoğrafları görüntü kalitesi değerlendirmesi için kullanmayın.

64 MP denemesi, uygulamaya görünür `com.transsion.availableHDStreamConfigurations` anahtarından en büyük JPEG boyutunu seçerek normal JPEG-only oturum oluşturmayı dener. Üreticiye özel mod değerlerini tahmin etmez. `session_rejected` sonucu OEM kontrolleriyle yapılacak çekimin imkansız olduğunu kanıtlamaz. Çekim başarılıysa JPEG başlığındaki gerçek genişlik/yükseklik raporlanır; piksel sayısı doğal sensör ayrıntısı veya görüntü kalitesi kanıtı değildir. Uygulama görüntüyü büyütmez.

Telefon raporunda standart arka JPEG 4608×3456 (~15.93 MP), özel HD JPEG 9216×6912 (~63.70 MP). 4608×2592 (~11.94 MP) standart 16:9 seçeneğidir. RAW capability raporda ilan edilmemiştir. FULL düzeyi ve manuel sensör/işleme kontrolleri ilan edilmiştir; çalışma zamanında uygulama erişimi ayrıca ölçülmelidir.

## Derleme

JDK 8+ (`javac`), Python 3, Android API 35 platformu ve build-tools 35.0.0 gerekir. JRE ile Eclipse ECJ 3.39.0 alternatifi desteklenir. Google'ın resmi HTTPS SDK deposundan alınan platform/build-tools ZIP'leri, depo metadatasındaki SHA-1 ile doğrulanmıştır. ECJ Maven Central'dan alınıp depo checksum'ı ile doğrulanmıştır.

```bash
export MG_ANDROID_PLATFORM=/workspace/android-tools/sdk/android-35
export MG_ANDROID_BUILD_TOOLS=/workspace/android-tools/sdk/android-15
# Yalnızca javac yoksa:
export MG_ECJ_JAR=/workspace/android-tools/ecj-3.39.0.jar
./build.sh
```

`android-15` burada resmi build-tools ZIP'inin iç klasörüdür; Android platform seviyesi değildir. Çıktı ve yerel geliştirme imzası `build/` içindedir ve Git tarafından dışlanır. Geliştirme anahtarı üretim imzası değildir.

## Doğrulama sınırı

Cloud makinesinde Java derlemesi, DEX üretimi, manifest paket/izin/başlatıcı kontrolü ve APK imza doğrulaması yapılmıştır. Fiziksel telefon ve Android emülatörü cloud makinesine bağlı değildir; yükleme, UI ve gerçek çekim sonuçları henüz doğrulanmamıştır.

## 0.2 düzeltmesi
Standart JPEG seçiminde getHighResolutionOutputSizes listesi de hesaba katılır. Özel HD metadata içinde HAL BLOB formatı 33, Android JPEG 256 ile eşlenir. Boyut bulunamayan denemeler size_not_advertised olarak kaydedilir. İlk cihaz testinde 3456×3456 JPEG başarılı olmuş; yüksek çözünürlük listesinde 4608×3456 mevcut, HD vendor listesinde 9216×6912 görünür. 64 MP gerçek çekim henüz doğrulanmamıştır.

## CK7n üzerinde 0.2 fiziksel cihaz sonucu

Kullanıcının Android 14 cihazından alınan 0.2 JSON raporunda iki test de `jpeg_received` olarak tamamlandı:

- Standart JPEG: istenen ve alınan 4608×3456, 15.925248 MP, 5.122.037 bayt.
- Özel HD JPEG: istenen ve alınan 9216×6912, 63.700992 MP, 14.929.440 bayt.

Her iki çıktı boyutu isteğe eşleşti. Kamera ID 0, normal uygulama kamera izni ile kullanıldı. Denemede üreticiye özel CaptureRequest kontrolleri uygulanmadı. Uygulama ImageReader JPEG baytlarını büyütmeden MediaStore'a kaydetti. Bu sonuç 64 MP boyutunda JPEG almanın mümkün olduğunu gösterir; HAL'in doğal sensör ayrıntısı, görüntü kalitesi, video ve HDR geliştirmeleri henüz doğrulanmadı. Kaynak rapor kullanıcı cihazına ait olduğundan depoya yüklenmedi.

## 0.3 prototip

Launcher artık CameraActivity'dir: canlı TextureView önizleme + JPEG ImageReader oturumu; 16/64 MP seçimi; AF/AE/AWB bekleme (en çok 3 saniye); destekleniyorsa HQ ISP noise reduction ve edge seçenekleri; JPEG_ORIENTATION. Tanılama ekranı ayrı düğmeyle açılır. Önizleme+64 MP oturumu 0.2 JPEG-only oturumundan farklıdır ve fiziksel cihazda ayrıca doğrulanmalıdır. APK derlemesi/imzası ve CameraMath için 10 regresyon kontrolü cloud ortamında başarılıdır. Fotoğraf kalite artışı ölçülmüş değildir.

Kullanıcının yüklediği 0.2 JPEG'leri başlık ve EXIF düzeyinde doğrulanmıştır. 64 MP fotoğraf 9216×6912 / 14.929.440 bayt; 16 MP fotoğraf 4608×3456 / 5.122.037 bayt. İkisinde ISO 167, 0.010006 saniye pozlama, f/1.7, 5.249 mm odak uzunluğu. Sahne/kadraj değişmiştir ve stock kamera karşılaştırması yoktur; netlik veya kalite üstünlüğü sonucu çıkarılmadı. Fotoğraflar repo dışında tutuldu.

## 0.3 fiziksel cihaz ve fotoğraf doğrulaması

Kullanıcının 0.3 raporu ve aynı ada ait JPEG dosyası birlikte incelendi. Önizleme + HD JPEG oturumu çalışmış: istenen 9216×6912, alınan 6912×9216 (JPEG yön isteği 90°), dosya 16.171.368 bayt, sizeMatched=true. Raporda AF_STATE=2 (PASSIVE_FOCUSED), AE_STATE=2 (CONVERGED), AWB_STATE=2 (CONVERGED), threeAConverged=true. HQ noise reduction ve HQ edge destek kontrolünden geçerek isteğe eklenmiş; bu bayraklar sürücünün algoritmasını ölçmez. Fotoğraf fiziksel olarak dik yönlüdür; EXIF Orientation değeri 0'dır (geçerli 1..8 aralığının dışında), görüntü okuyucusu piksel yönünü esas almıştır.

JPEG EXIF: ISO 300, 0.010006 saniye, f/1.7, 5.249 mm. Tam boyuta ait takvim yazısı ve gölge kırpımları görsel inceleme amacıyla repo dışında üretildi. Takvim yazısı okunaklı; koyu bölgelerde renkli gürültü görülüyor. İlk çekimlerden farklı kadraj ve ISO nedeniyle bu örneklerle kalite artışı miktarı veya doğal sensör ayrıntısı ölçülemez. Orijinal kamera 64 MP ve aynı sabit sahnede 16/64 MP kontrollü eşleştirmesi kalite karşılaştırması için halen eksik. Ham fotoğraflar/raporlar GitHub'a yüklenmedi.
