# M-G Kamera

TECNO Camon 20 Pro 4G (CK7n), Android 14 için bağımsız Android Camera2 prototipi. Orijinal kamera uygulamasını değiştirmez. OEM APK kodu, modelleri veya kütüphaneleri dağıtılmaz.

## Güncel APK ve tek test turu

- [0.6 offline AI ve toplu test APK](downloads/MG-Kamera-AI-0.6.apk)
- [0.6 ZIP](downloads/MG-Kamera-AI-0.6.zip)
- [Tek tur test talimatları](TEST-BUNDLE.md)
- [SHA-256 kontrol değerleri](downloads/SHA256SUMS)

Bir araya getirilen özellikler: canlı önizleme, arka/ön kamera, standart ve cihazın açtığı HD fotoğraf boyutları, 3A bekleme, doğru JPEG yönü, dijital zoom, EV, 2/5/10 saniye zamanlayıcı, desteklenen HQ ISP seçenekleri, opsiyonel renkli gürültü filtresi, H.264 MP4 video (ilan edilen en fazla 1080p boyut, 30 fps istek), isteğe bağlı AAC ses, offline DnCNN AI denoise ve tek raporlu otomatik toplu test. Ön kamera HD boyutu uygulamaya ilan edilmiyorsa mevcut olduğu iddia edilmez.

AI gerçek bir pretrained DnCNN modelidir; [kaynak/lisans/doğrulama](third_party/SOURCES.md). Fotoğraf internete gönderilmez. İsteğe bağlı AI ve renkli gürültü işlemi orijinal JPEG'i korur, ayrıca aynı piksel boyutunda çıktı kaydeder. Model tam çözünürlükte 256'lık parçalarda, 20 piksel halo ile çalışır; çözünürlük büyütülmez. 64 MP işleme yavaştır ve dakikalar sürebilir. Orijinal + AI karşılaştırması kalite kararının temelidir; modeli çalıştırmak başka bir telefondan daha iyi kaliteyi kanıtlamaz. 16 MP ile AI aşaması toplu teste isteğe bağlı eklenir; manuel 64 MP AI mümkündür.

Bellek yetmez, işlem iptal edilir veya runtime başarısız olursa orijinal korunur ve AI başarısız/atlandı olarak raporlanır. CPU iki thread ile çalışır. ARM64 yerel runtime dahil; diğer işlemci mimarileri paketlenmemiştir. Geniş heap talep edilir, başlangıç bellek kontrolü ve parça sınırında iptal vardır. Öğrenilmiş model gürültüyü bastırırken ayrıntı kaybedebilir; 35% orijinal + 65% model çıktısı harmanı kullanılır. Renkli gürültü filtresi AI değildir; ayrı seçenektir.

## Cihaz üzerinde doğrulananlar

0.2: kullanıcının fiziksel CK7n cihazında 4608×3456 ve 9216×6912 JPEG üretildi. 0.3: canlı önizleme + HD JPEG çalıştı, 3A hazır raporlandı ve fotoğraf boyutu dosyadan doğrulandı. Son karşılaştırmada üç çekimin EXIF pozlaması aynı (ISO 445, 0.010005 s, f/1.7). Orijinal TECNO 64 MP çekimi gölgelerde belirgin biçimde daha az renkli gürültü; M-G 0.3 metin kenarları daha az yumuşama gösteriyor. Bu tek sahne tüm kaliteyi ölçmez ve AI sürümünün başarısını kanıtlamaz.

0.6: cloud makinesinde derleme, APK imzası, paket/model/native-libraries/lisans bütünlüğü, çözünürlük-format-yön regresyon kontrolleri, sentetik chroma testi ve DnCNN PyTorch/ONNX/tiling referans kontrolleri geçti. 0.6 video, ön kamera, toplu test, bellek/performance ve gerçek fotoğrafta AI kalitesi henüz fiziksel telefonda doğrulanmadı. Toplu test başarısız/iptal/eksik aşamaları geçmiş gibi saymaz.

Gerçek çok kareli HDR, hizalanmış gece birleştirme, portre segmentasyonu, 4K/60 fps ve bir iPhone'a üstünlük bu sürümde uygulanmış veya doğrulanmış değildir. Donanımın açmadığı özellikler varmış gibi sunulmaz. Bunlar kalite hedefine yönelik sonraki geliştirmelerdir.

## Çıktılar ve geliştirme

Fotoğraflar `Pictures/MGKamera`; videolar `Movies/MGKamera`; çekim ve `MG-batch-...json` raporları `Download/MGKamera`. AI çıktısı `-ai.jpg`, renk filtresi `-chroma.jpg`. Yeni kamera kontrolleri alt kaydırılabilir paneldedir.

[Tanılama/kurulum notları](TANILAMA.md) içindeki SDK değişkenlerini ayarlayın; `./build.sh` ile derleyin, `./test.sh` ile unit/regresyon kontrollerini çalıştırın. Native runtime/model assets repoda hazırdır. SDK/ECJ sistem kurulumu dışındaki uygulama bağımlılıkları ağdan çalışma anında indirilmez. İmza geliştirme imzasıdır. Telefon fotoğrafları ve ham servis/JSON raporları depoya yüklenmez.
