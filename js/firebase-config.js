/* ============================================================
   HERBALINDO — Firebase web configuration
   Single entry point for the Firebase JS SDK on the web front end.
   The same project backs the Android client (com.altomedia.herbalindo).

   NOTE: the Firebase web API key is a public client identifier, not a
   secret. Access is enforced by Firestore security rules and App Check,
   never by hiding this value.
   ============================================================ */
import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import { getAnalytics, isSupported } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-analytics.js";

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
export const firebaseConfig = {
  apiKey: "AIzaSyC875ajOF1Lj08K-bDGvjesn7Vgfos_uBA",
  authDomain: "altomedia-indonesia.firebaseapp.com",
  databaseURL: "https://altomedia-indonesia-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "altomedia-indonesia",
  storageBucket: "altomedia-indonesia.firebasestorage.app",
  messagingSenderId: "739146490998",
  appId: "1:739146490998:web:32923254160558274eeb57",
  measurementId: "G-87493ELJYY"
};

// Initialize Firebase
export const app = initializeApp(firebaseConfig);

// Analytics needs a browser context that supports it (it is unavailable in some
// embedded webviews and when cookies/storage are blocked), so start it lazily
// and never let its absence break the rest of the page.
export const analyticsReady = isSupported()
  .then((ok) => (ok ? getAnalytics(app) : null))
  .catch(() => null);

// The classic (non-module) store/app/admin scripts read the initialized app
// from here instead of importing the SDK themselves.
window.HERBALINDO_FIREBASE = { app, firebaseConfig };