using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace VoiceFlow
{
    static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

        [DllImport("user32.dll")]
        private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        private const int HOTKEY_ID = 9001;
        private const uint MOD_ALT = 0x0001;
        private const uint MOD_CONTROL = 0x0002;
        private const uint MOD_SHIFT = 0x0004;
        private const uint VK_SPACE = 0x20;
        private const uint VK_V = 0x56;

        private static NotifyIcon trayIcon;
        private static Process browserProcess = null;

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // Create Tray Icon
            trayIcon = new NotifyIcon();
            trayIcon.Text = "VoiceFlow - Smart Voice Dictation";
            trayIcon.Icon = SystemIcons.Application;
            trayIcon.Visible = true;

            ContextMenu menu = new ContextMenu();
            menu.MenuItems.Add("Open VoiceFlow", (s, e) => LaunchOrFocusApp());
            menu.MenuItems.Add("Dictate (Alt+V)", (s, e) => LaunchOrFocusApp());
            menu.MenuItems.Add("-");
            menu.MenuItems.Add("Exit", (s, e) => {
                trayIcon.Visible = false;
                Application.Exit();
            });
            trayIcon.ContextMenu = menu;
            trayIcon.DoubleClick += (s, e) => LaunchOrFocusApp();

            // Hidden message window to intercept global hotkeys
            HotKeyForm form = new HotKeyForm();
            RegisterHotKey(form.Handle, HOTKEY_ID, MOD_ALT, VK_V); // Alt + V
            RegisterHotKey(form.Handle, HOTKEY_ID + 1, MOD_CONTROL | MOD_SHIFT, VK_SPACE); // Ctrl + Shift + Space

            // Launch app on startup
            LaunchOrFocusApp();

            Application.Run(form);

            UnregisterHotKey(form.Handle, HOTKEY_ID);
            UnregisterHotKey(form.Handle, HOTKEY_ID + 1);
        }

        public static void LaunchOrFocusApp()
        {
            try
            {
                if (browserProcess != null && !browserProcess.HasExited)
                {
                    SetForegroundWindow(browserProcess.MainWindowHandle);
                    return;
                }

                string url = "http://localhost:5173";
                string edgePath = @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe";
                if (!File.Exists(edgePath))
                {
                    edgePath = @"C:\Program Files\Microsoft\Edge\Application\msedge.exe";
                }

                if (File.Exists(edgePath))
                {
                    ProcessStartInfo psi = new ProcessStartInfo
                    {
                        FileName = edgePath,
                        Arguments = string.Format("--app=\"{0}\" --window-size=440,780", url),
                        UseShellExecute = false
                    };
                    browserProcess = Process.Start(psi);
                }
                else
                {
                    Process.Start(url);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Error launching VoiceFlow: " + ex.Message, "VoiceFlow");
            }
        }

        private class HotKeyForm : Form
        {
            public HotKeyForm()
            {
                this.WindowState = FormWindowState.Minimized;
                this.ShowInTaskbar = false;
                this.Visible = false;
            }

            protected override void SetVisibleCore(bool value)
            {
                base.SetVisibleCore(false);
            }

            protected override void WndProc(ref Message m)
            {
                const int WM_HOTKEY = 0x0312;
                if (m.Msg == WM_HOTKEY)
                {
                    LaunchOrFocusApp();
                }
                base.WndProc(ref m);
            }
        }
    }
}

