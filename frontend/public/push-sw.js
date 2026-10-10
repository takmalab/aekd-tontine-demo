/*
 * Service worker dédié aux notifications push de l'AEKD (docs/decisions.md n° 44).
 * Indépendant du service worker Angular (ngsw) : il fonctionne aussi en
 * développement. Il est enregistré avec la portée « /push/ » pour ne pas
 * remplacer ngsw (portée « / ») : il ne contrôle donc aucune page, mais reçoit
 * les push de son abonnement. Le serveur envoie {"title", "body", "url", "tag"}.
 */
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : '' };
  }
  // Prévient les onglets ouverts : ils mettent à jour la cloche et jouent leur son.
  const notifyWindows = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((windows) => windows.forEach((w) => w.postMessage({ type: 'aekd-push' })));
  event.waitUntil(Promise.all([notifyWindows, showNotification(data)]));
});

function showNotification(data) {
  return self.registration.showNotification(data.title || 'AEKD Tontine', {
    body: data.body || '',
    tag: data.tag,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-72x72.png',
    // Le son lui-même dépend des réglages du système (Windows, Android) : une page
    // web ne peut pas le choisir. On demande seulement une alerte non silencieuse.
    silent: false,
    renotify: Boolean(data.tag),
    vibrate: [200, 100, 200],
    data: { url: data.url || '/notifications' },
  });
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const path = event.notification.data?.url || '/notifications';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((w) => new URL(w.url).origin === self.location.origin);
      if (open) {
        // Page non contrôlée par ce worker : elle navigue elle-même (NotificationService).
        open.postMessage({ type: 'aekd-navigate', url: path });
        return open.focus();
      }
      return self.clients.openWindow(new URL(path, self.location.origin).href);
    }),
  );
});
