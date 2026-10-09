# M-G Kamera Tanılama 0.1

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
