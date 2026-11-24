// GitHub Actions 用：在電腦上直接呼叫 libnihongo_whisper.so，確認 C++ 介面真的能辨識。
// 類別名稱必須和 App 裡的 com.ice.nihongo.whisper.WhisperLib 一樣，JNI 才找得到函式。
package com.ice.nihongo.whisper;

import java.nio.file.Files;
import java.nio.file.Paths;

class WhisperLib {
    static { System.load(System.getProperty("lib")); }

    native long init(String path);
    native byte[] transcribe(long ctx, float[] audio, int threads, int beam, String prompt);
    native void free(long ctx);

    public static void main(String[] args) throws Exception {
        WhisperLib w = new WhisperLib();
        long h = w.init(args[0]);
        if (h == 0) throw new RuntimeException("模型載入失敗");

        // 讀 16kHz 16-bit 單聲道 WAV（跳過 44 位元組檔頭）
        byte[] all = Files.readAllBytes(Paths.get(args[1]));
        int n = (all.length - 44) / 2;
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = ((short) ((all[44 + 2 * i] & 0xff) | (all[45 + 2 * i] << 8))) / 32768f;
        }

        for (int beam : new int[]{1, 5}) {
            long t = System.currentTimeMillis();
            String out = new String(w.transcribe(h, f, 4, beam, "テストです。"), "UTF-8");
            System.out.println("beam=" + beam + "  " + (System.currentTimeMillis() - t) + " ms\n" + out);
            if (out.isBlank()) throw new RuntimeException("沒有辨識結果");
            for (String line : out.split("\n")) {
                if (line.split("\\|", 4).length != 4) throw new RuntimeException("格式錯誤：" + line);
            }
        }
        w.free(h);
        System.out.println("Whisper 介面測試通過");
    }
}
