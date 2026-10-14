# Auto Translate (Android)

Do kaam karta hai:

1. **Auto-translate overlay**: WhatsApp, Telegram, Gmail, SMS jaisi apps mein naya message aate hi uska translation usi jagah screen ke upar dikh jata hai (copy-paste nahi).
2. **Floating widget**: bubble dabao, apni language mein likho, translate hokar seedha text box mein bhar jata hai. Send button aap khud dabate ho. Isme ready templates bhi hain (Leave, Follow-up, Invoice reminder, Complaint, Thank you, Meeting).

Translation ML Kit se phone ke andar hota hai (free). Pehli baar har language ka model download hota hai, uske baad internet ke bina chalta hai.

## APK kaise milega

GitHub par is repo ke **Actions** tab mein "Build Auto Translate APK" chalta hai. Run khulne par neeche **Artifacts** mein `AutoTranslate-APK` download karo, zip kholo, `app-debug.apk` phone mein install karo.

## Setup phone par

1. App kholo, **Accessibility ON karo** dabao, "Auto Translate" ON karo.
2. Languages chuno aur **Models download karo** dabao.
3. Floating bubble kisi bhi app ke upar dikhega. Chat app mein text box dabao, phir bubble dabao aur likho.

## Limits

- Hindi ko Devanagari mein likho/bolo. Roman Hindi (Hinglish) translate nahi hota.
- Kuch apps apne text box mein bahar se text bharne nahi deti. Aise mein text copy ho jata hai, paste kar do.
