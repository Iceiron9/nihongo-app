# 日文學習 App

像 Anki 一樣的日文學習 App，針對 ROG Phone 5（Android、arm64）。

## 分頁

| 分頁 | 功能 |
| --- | --- |
| 影音 | 匯入歌曲或影片，用 Whisper 在手機上離線轉成日文字幕；點單字加進單字卡。辨識不好時，可改匯入 SRT／VTT／LRC 字幕或純文字歌詞 |
| 五十音 | 平假名、片假名（含濁音、拗音）卡片，自動排程複習 |
| 單字 | 從影音挑出或自己新增的單字，自動排程複習 |
| 打字 | 九宮格（12 鍵）日文輸入練習，題目來自匯入的歌詞與單字 |

## 自動校正（離線）

1. 清掉 Whisper 常見的幻聽句子（例如「ご視聴ありがとうございました」）與重複迴圈
2. 信心低或用辭典判斷不通順的句子，切出那段音訊，以前兩句當上下文提示，用 beam search 重新辨識
3. 用 Kuromoji 辭典與全曲上下文，在兩個版本中挑比較好的

## 下載 APK

每次推送到 main，GitHub Actions 會自動：測試 Whisper 介面 → 跑邏輯單元測試 → 編譯 APK → 發佈到 **Releases**。
用手機打開本倉庫的 Releases 頁面，下載 `nihongo-app.apk` 安裝即可（需允許「安裝未知來源的應用程式」）。

## 使用的開源專案

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp)（MIT）
- [Kuromoji](https://github.com/atilika/kuromoji)（Apache 2.0）
- Jetpack Compose、Room
