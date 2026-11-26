# SR AI — Android Assistant

এই ZIP-এ SR AI-এর Android Studio source project আছে। এখানে Gemini API key সংরক্ষণ, বাংলা voice input, Gemini chat/code generation, app launch, web search এবং ব্যবহারকারীর বোতাম চাপার পর focused text field-এ AI উত্তর বসানোর সুবিধা রয়েছে।

## APK তৈরি

1. ZIP extract করুন।
2. Android Studio-তে `SR_AI_Android_Project` folder খুলুন।
3. Android Studio-তে JDK 17, Android SDK Platform 35 এবং Android Gradle Plugin-এর প্রয়োজনীয় components install হতে দিন।
4. Gradle Sync শেষ হলে **Build > Build Bundle(s) / APK(s) > Build APK(s)** চাপুন।
5. APK সাধারণত `app/build/outputs/apk/debug/app-debug.apk`-এ তৈরি হবে।

## প্রথমবার চালু

1. SR AI খুলে Google AI Studio থেকে তৈরি Gemini API key বসিয়ে **Save API Key** চাপুন।
2. Voice ব্যবহারের জন্য Android-এর speech recognition service লাগবে; ফোন চাইলে microphone permission দিন।
3. অন্য অ্যাপের focused text box-এ উত্তর বসাতে চাইলে **Enable screen typing permission** চাপুন, Accessibility settings-এ SR AI enable করুন।
4. অন্য অ্যাপে text field focus করুন, SR AI-তে ফিরে **Type latest AI answer into focused field** চাপুন।

## Termux

শুধু Termux দিয়ে APK build করতে Android SDK/Build Tools ও JDK 17 সঠিকভাবে configure থাকতে হবে। Android SDK ছাড়া Termux একা APK compile করতে পারে না। Android Studio build-ই সহজ ও নির্ভরযোগ্য পথ। `termux_build.sh` কেবল আগে থেকে configure করা SDK/Gradle environment-এর জন্য।

## গুরুত্বপূর্ণ নিরাপত্তা

- API key এই ডিভাইসের SharedPreferences-এ plain text হিসেবে থাকে। ফোন share করলে বা APK distribute করলে ব্যক্তিগত key embed করবেন না; production app-এ backend/proxy ব্যবহার করুন।
- Accessibility permission শক্তিশালী। SR AI স্বয়ংক্রিয়ভাবে স্ক্রিনে নজরদারি বা ক্লিক করে না; কেবল আপনার চাপা Type বোতামে বর্তমানে focused editable field-এ উত্তর বসানোর চেষ্টা করে।
- Android version, app restrictions বা target app-এর input field-এর কারণে text insertion কাজ নাও করতে পারে।
- App launch/search কাজ করার জন্য সংশ্লিষ্ট অ্যাপ/ব্রাউজার থাকতে হবে।
- Gemini API model/endpoint ভবিষ্যতে পরিবর্তিত হতে পারে; Google AI Studio-এর বর্তমান API documentation দেখে model name যাচাই করুন।
