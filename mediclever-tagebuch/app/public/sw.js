// Service Worker des MediClever Tagebuchs: zeigt Erinnerungen an. Kein Zwischenspeichern von Tagebuchinhalten.
self.addEventListener('push', (event) => {
  let data = { title: 'MediClever Tagebuch', body: 'Zeit für deinen Eintrag.' };
  try { if (event.data) data = Object.assign(data, event.data.json()); } catch (e) { /* Standardtext */ }
  event.waitUntil(self.registration.showNotification(data.title, { body: data.body, tag: 'mct-reminder', icon: undefined }));
});
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(self.clients.matchAll({ type: 'window' }).then((list) => {
    for (const c of list) { if ('focus' in c) return c.focus(); }
    return self.clients.openWindow('/');
  }));
});
