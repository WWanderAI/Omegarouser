const { app, BrowserWindow, session, ipcMain } = require('electron');
const path = require('path');

let mainWindow;

const AD_BLOCK_HOSTS = [
  'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
  'google-analytics.com', 'adservice.google.com', 'adsystem.com',
  'amazon-adsystem.com', 'taboola.com', 'outbrain.com', 'criteo.com',
  'criteo.net', 'moatads.com', 'scorecardresearch.com', 'adnxs.com',
  'pubmatic.com', 'rubiconproject.com', 'mc.yandex.ru', 'mc.yandex.com',
  'an.yandex.ru', 'yandexadexchange.net', 'top-fwz1.mail.ru', 'top.mail.ru',
  'popads.net', 'adcolony.com'
];

global.adblockEnabled = true;
const configuredSessions = new Set();

function setupAdblock(ses) {
  ses.webRequest.onBeforeRequest((details, callback) => {
    if (!global.adblockEnabled) {
      callback({ cancel: false });
      return;
    }
    try {
      const host = new URL(details.url).hostname.toLowerCase();
      const blocked = AD_BLOCK_HOSTS.some((h) => host.includes(h));
      callback({ cancel: blocked });
    } catch (e) {
      callback({ cancel: false });
    }
  });
}

function setupDownloads(ses) {
  ses.on('will-download', (event, item) => {
    const savePath = path.join(app.getPath('downloads'), item.getFilename());
    item.setSavePath(savePath);
    const id = `${Date.now()}-${Math.random()}`;

    const send = () => {
      if (mainWindow) {
        mainWindow.webContents.send('download-update', {
          id,
          fileName: item.getFilename(),
          state: item.getState(),
          receivedBytes: item.getReceivedBytes(),
          totalBytes: item.getTotalBytes(),
          savePath
        });
      }
    };

    item.on('updated', send);
    item.on('done', send);
    send();
  });
}

function setupSession(ses) {
  if (configuredSessions.has(ses)) return;
  configuredSessions.add(ses);
  setupAdblock(ses);
  setupDownloads(ses);
}

app.whenReady().then(() => {
  setupSession(session.defaultSession);

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 760,
    minHeight: 480,
    backgroundColor: '#E6F4EA',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      webviewTag: true
    }
  });

  mainWindow.loadFile('index.html');
});

ipcMain.handle('set-adblock', (event, enabled) => {
  global.adblockEnabled = enabled;
});

ipcMain.handle('setup-private-session', (event, partition) => {
  const ses = session.fromPartition(partition);
  setupSession(ses);
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
