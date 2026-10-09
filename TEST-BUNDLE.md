# Tek turda fiziksel cihaz testi

0.6 sürümü: Android 12+ / arm64, hedef cihaz CK7n Android 14. Kamera izni gerekir; mikrofon yalnızca sesli video seçildiğinde istenir. Fotoğraflar buluta gönderilmez.

1. APK'yı güncelleyin, uygulamayı açıp telefonu sabit, aydınlık bir sahneye yöneltin.
2. AI denemesi istiyorsanız `AI denoise` kutusunu seçin. Tam çözünürlükte işlem yavaştır; özellikle 64 MP işlem dakikalar alabilir. Toplu test AI aşamasını 16 MP ile çalıştırır. Manuel 64 MP AI seçimi ayrıca mümkündür.
3. Alt ayar panelini kaydırıp `Toplu test` düğmesine basın.
4. Telefonu aynı sahnede tutun; uygulamayı arka plana almayın. Sırayla arka standart JPEG, arka HD JPEG, ön standart JPEG, 5 saniyelik sessiz video, 2× zoom + EV çekimi, 2 saniyelik zamanlayıcı çekimi ve chroma filtresi çekimi çalışır. Ses seçili ve mikrofon izni verilmişse ayrıca sesli video; AI seçiliyse ayrıca arka standart AI çekimi eklenir. Lens otomatik değişir. Chroma filtresi sadece kendi aşamasında açılır; diğer çekimler onunla değiştirilmez.
5. Bittiğinde `Download/MGKamera/MG-batch-....json` tek rapordur. Önce yalnızca bu raporu paylaşın; fotoğraf/video dosyalarının tamamını ilk turda yüklemek gerekmez.

Rapor executed/passed/failed/planned değerlerini ayırır. İptal/arka plana geçiş completed sayılmaz. Video testi, MP4 süresini/boyutunu okuyup bir kareyi decode eder; 30 fps talep edilir, gerçek FPS henüz ölçülmez. Zoom/EV aşamasında isteğin metadata yanıtıyla eşleşmesi, zamanlayıcı aşamasında bekleme süresi, chroma aşamasında ayrı dosyanın başarıyla yazılması kontrol edilir. AI testi, işlenmiş çıktının başarıyla kaydedilmesini gerektirir; yalnızca orijinalin kaydedilmesi AI başarısı sayılmaz. Telefon süreleri ve kalite etkisi fiziksel test sonuçlarıyla değerlendirilmelidir.

Çıktılar: fotoğraflar `Pictures/MGKamera`, videolar `Movies/MGKamera`, JSON'lar `Download/MGKamera`. AI çıktısı `-ai.jpg`, renkli gürültü filtresi çıktısı `-chroma.jpg`; orijinal dosya değiştirilmez.

Manuel kontroller: arka/ön kamera, standart/HD fotoğraf, ilan edilen aralıkta dijital zoom ve EV, 2/5/10 saniye zamanlayıcı, sesli/sessiz H.264 MP4 video (en fazla ilan edilen 1080p, 30 fps istek), odak/AE/AWB bekleme, desteklenen HQ ISP, opsiyonel renkli gürültü azaltma ve offline DnCNN AI.

Gerçek çok kareli HDR, hizalanmış gece birleştirme, portre segmentasyonu, 4K/60 fps ve başka bir telefonun kalite üstünlüğünü geçen sonuçlar bu sürümde uygulanmış veya doğrulanmış değildir. Donanımın ilan etmediği seçenekler varmış gibi sunulmaz. OEM 64 MP örneği bu sahnede daha temiz gölge; M-G 0.3 örneği daha az yumuşamış metin kenarları göstermiştir. Bu gözlem AI sürümünün kalitesini kanıtlamaz.
