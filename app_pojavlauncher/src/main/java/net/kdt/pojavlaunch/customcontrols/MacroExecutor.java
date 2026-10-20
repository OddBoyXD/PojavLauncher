package net.kdt.pojavlaunch.customcontrols;

import android.util.Log;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import org.lwjgl.glfw.CallbackBridge;

public class MacroExecutor {
    private static final String TAG = "MacroExecutor";
    private static volatile boolean sExecuting = false;

    public static void execute(final String command) {
        if (command == null) return;
        final String text = command.trim();
        if (text.isEmpty() || sExecuting) return;

        new Thread(new MacroWorker(text)).start();
    }

    static class MacroWorker implements Runnable {
        private final String mText;

        MacroWorker(String text) {
            this.mText = text;
        }

        @Override
        public void run() {
            sExecuting = true;
            try {
                // Step 1: Open chat
                CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_T);
                Thread.sleep(80);

                // Step 2: Send each character
                for (int i = 0; i < mText.length(); i++) {
                    char c = mText.charAt(i);
                    CallbackBridge.sendChar(c, 0);
                    Thread.sleep(4);
                }
                Thread.sleep(30);

                // Step 3: Send ENTER
                CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER);
            } catch (Throwable th) {
                Log.e(TAG, "Macro execution failed: " + mText, th);
            } finally {
                sExecuting = false;
            }
        }
    }
}
