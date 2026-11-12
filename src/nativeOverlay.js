import { registerPlugin, Capacitor } from '@capacitor/core';

export const isNativeAndroid = () => Capacitor.getPlatform() === 'android';

// Register the native Capacitor plugin with fallback for web/desktop
export const FloatingOverlay = registerPlugin('FloatingOverlay', {
  web: {
    checkPermission: async () => ({ granted: true }),
    requestPermission: async () => {},
    checkAccessibilityPermission: async () => ({ granted: false }),
    requestAccessibilityPermission: async () => {},
    syncSettings: async () => ({ success: true }),
    getServiceStatus: async () => ({ overlayActive: false, accessibilityActive: false }),
    startService: async () => {},
    stopService: async () => {}
  }
});

