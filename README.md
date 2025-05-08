Kelime Oyunu - README
Proje Hakkında
Bu proje, Türkçe kelimeler kullanarak oynanan çok oyunculu bir Android kelime oyunudur. Scrabble'a benzer mekaniklerle, oyuncular ellerindeki harflerle kelimeler oluşturarak puan toplar. Oyuna eklenen özel jokerler ve mayınlar gibi özellikler sayesinde stratejik derinlik kazandırılmıştır.
Temel Özellikler

Çevrimiçi çok oyunculu oyun
Gerçek zamanlı hamle ve puan güncellemeleri
Türkçe kelime doğrulama
Özel efektler: H2, H3, K2, K3 gibi harf ve kelime çarpanları
Mayın sistemi: Kelime İptali, Puan Transferi, Harf Kaybı vb.
Joker sistemi: Bölge Yasağı, Harf Yasağı, Ekstra Hamle Jokeri
Sürükle-bırak harf yerleştirme
Süreli oyun modları

Kurulum Gereksinimleri
Projeyi çalıştırmak ve geliştirmek için aşağıdaki adımları izlemeniz gerekmektedir:
1. Firebase Yapılandırması
   Bu proje, çevrimiçi oyun deneyimi için Firebase Realtime Database ve Firebase Authentication kullanmaktadır. Projeyi kendi Firebase hesabınızla yapılandırmak için:

Firebase Console adresinde yeni bir proje oluşturun
Android uygulaması ekleyin ve paket adını com.example.kelimeoyunu olarak girin
google-services.json dosyasını indirin
İndirdiğiniz dosyayı app/ klasörüne yerleştirin
Firebase konsolunda Authentication ve Realtime Database hizmetlerini etkinleştirin
Realtime Database Rules'u aşağıdaki şekilde ayarlayın:

{
 "rules": {
  "games": {
   "$gameId": {
    ".read": "auth != null",
    ".write": "auth != null"
   }
 },
 "users": {
  "$userId": {
   ".read": "auth != null",
   ".write": "auth.uid === $userId"
   }
  }
 }
}
2. local.properties Dosyası
   local.properties dosyasında SDK yolunuzu belirtin:
   sdk.dir=//SDK DOSYANIZIN YOLU
3. Kimlik Bilgileri
   Örnek projedeki kullanıcı girişi kontrolleri test amaçlıdır. Gerçek projede, Firebase Authentication kullanmanız önerilir. Test kullanıcı adı ve şifresini değiştirmek için ilgili aktivite dosyasını düzenleyin.
   Proje Yapısı

GameActivity.kt: Ana oyun ekranı ve mantığı
TurkishDictionary.kt: Türkçe kelime doğrulama
FirebaseHelper.kt: Firebase veri işlemleri ve gerçek zamanlı güncellemeler
BoardCell.kt: Oyun tahtası hücresi veri yapısı
SpecialEffect.kt: Özel efektler ve mayınlar için enum sınıfı

Özel Efektler ve Jokerler
Harf ve Kelime Çarpanları

H2: Harf puanını 2 ile çarpar
H3: Harf puanını 3 ile çarpar
K2: Kelime puanını 2 ile çarpar
K3: Kelime puanını 3 ile çarpar

Mayınlar

Kelime İptali: Bu mayına denk gelen kelimeden puan alınmaz
Puan Transferi: Kelimeden elde edilen puan rakibe aktarılır
Puan Bölünmesi: Kelimeden elde edilen puan %30'a düşürülür
Harf Kaybı: Oyuncunun elindeki tüm harfler yenilenir
Ekstra Hamle Engeli: Harf ve kelime çarpanları iptal edilir

Jokerler

Bölge Yasağı: Rakibin bir tur boyunca tahtanın belirli bir bölgesini kullanmasını engeller
Harf Yasağı: Rakibin elindeki iki harfi bir tur boyunca dondurur
Ekstra Hamle: Oyuncuya ek bir hamle hakkı sağlar

Sunucu-İstemci Mimarisi
Bu proje, "Backend as a Service" (BaaS) modelini kullanarak Firebase üzerinden çalışır:

Firebase Realtime Database: Oyun durumu, hamle verileri ve puan güncellemeleri için
Firebase Authentication: Kullanıcı kimlik doğrulama için
Listener'lar: Gerçek zamanlı veri güncellemelerini dinlemek için

Katkıda Bulunma

Projeyi fork edin
Kendi Branch'inizi oluşturun (git checkout -b feature/amazing-feature)
Değişikliklerinizi commit edin (git commit -m 'Add some amazing feature')
Branch'inize push edin (git push origin feature/amazing-feature)
Pull Request açın

İletişim
Sorularınız için issue açabilirsiniz.
