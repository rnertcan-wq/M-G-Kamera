# M-G Kamera

TECNO Camon 20 Pro 4G (CK7n), Android 14 için bağımsız Android Camera2 prototipi. Orijinal kamera uygulamasını değiştirmez. OEM APK kodu, modelleri veya kütüphaneleri dağıtılmaz.

## APK

- [0.3 canlı önizlemeli prototip](downloads/MG-Kamera-0.3.apk)
- [0.2 tanılama sürümü](downloads/MG-Kamera-Tanilama-0.2.apk)
- [SHA-256 kontrol değerleri](downloads/SHA256SUMS)

0.3 sürümü canlı önizleme, 16/64 MP seçimi, çekim öncesi AF/AE/AWB durumlarını bekleme, JPEG yönü ve desteklenen HQ gürültü azaltma/kenar işleme seçeneklerini içerir. Kamera izni dışında izin veya ağ bağlantısı istemez. Gerçek çekim boyutunu JPEG başlığından kaydeder; görüntüyü büyütmez.

0.2, kullanıcının fiziksel CK7n cihazında 4608×3456 ve 9216×6912 JPEG üretmiştir. Bu, 64 MP boyutunda JPEG erişiminin çalıştığını kanıtlar. HAL'in doğal sensör ayrıntısı veya başka bir telefondan daha iyi görüntü kalitesi bu sonuçla kanıtlanmaz. 0.3'ün önizleme+JPEG kombinasyonu fiziksel cihazda henüz denenmemiştir; reddedilirse mod değiştirilerek tekrar denenebilir ve 0.2 tanılama sürümü kullanılabilir.

## Çekim ve raporlar

Fotoğraflar `Pictures/MGKamera`, çekim başına JSON raporları `Download/MGKamera` içine yazılır. Raporda istenen/alınan boyutlar, 3A bekleme sonucu ve uygulanan HQ seçenekleri bulunur. 3A beklemesi en fazla 3 saniyedir; tamamlanmazsa çekim yine yapılır ve `threeAConverged=false` olarak raporlanır. Görüntü kalitesi karşılaştırması için sabit kamera ve aynı sahne kullanın; HQ seçeneğini açıp kapatarak kontrollü karşılaştırma yapın.

Önizleme en-boy oranını koruyarak ekran içine sığdırılır. Arka kameranın en büyük ilan edilmiş standart/yüksek çözünürlük boyutu 16 MP modu, üreticinin özel HD listesi ise 64 MP modu için kullanılır. 64 MP JPEG bu cihazda normal kamera izniyle ve özel CaptureRequest kontrolleri olmadan alınmıştır.

Henüz video kaydı, çok kareli HDR, özel gece algoritması, ön kamera arayüzü ve kalibre edilmiş ayrıntı/gürültü karşılaştırması yoktur. HQ, sürücünün mevcut ISP ayarlarını kullanır; yeni bir algoritmanın kalite üstünlüğü olarak sunulmaz.

## Derleme ve doğrulama

[Tanılama/kurulum notları](TANILAMA.md) içindeki SDK değişkenlerini ayarlayın; `./build.sh` ile derleyin, `./test.sh` ile 10 çözünürlük/format/yön kontrolünü çalıştırın. Geçerli APK imzası geliştirme imzasıdır. Telefon fotoğrafları ve ham servis/JSON raporları depoya yüklenmez.
