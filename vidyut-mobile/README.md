# Vidyut Mobile

React Native application for Android and iOS, managed with Expo SDK 57 and Expo Router.

```powershell
npm install
npm run android
```

Run `npm run ios` on macOS with Xcode. The default backend address is
`http://10.0.2.2:8080/api` on Android emulators and `http://localhost:8080/api`
on iOS simulators. Use `.env.example` when testing on a physical device.

Development builds use mock charger and booking data only when the backend cannot
be reached. Authentication never falls back to mock credentials.

```powershell
npm run typecheck
```
