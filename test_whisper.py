import whisper
import os
from gtts import gTTS

# 生成测试音频文件（中文语音，用于测试翻译）
text = "你好，世界！这是Whisper模型的测试音频。"
audio_file = "test_audio.wav"

if not os.path.exists(audio_file):
    print("生成测试音频文件...")
    tts = gTTS(text=text, lang='zh')
    tts.save(audio_file)
    print(f"音频文件 {audio_file} 已生成。")

print("开始测试Whisper模型可用性...")

# 测试Whisper tiny模型
print("\n=== 测试Whisper tiny模型 ===")
try:
    model_tiny = whisper.load_model("tiny")
    print("Tiny模型加载成功。")
    result_tiny = model_tiny.transcribe(audio_file, task="transcribe")
    print(f"Tiny转写结果: {result_tiny['text']}")
    # 测试翻译（翻译为英语）
    result_tiny_translate = model_tiny.transcribe(audio_file, task="translate")
    print(f"Tiny翻译结果 (to English): {result_tiny_translate['text']}")
    print("Tiny模型测试通过。")
except Exception as e:
    print(f"Tiny模型测试失败: {e}")

# 测试Whisper large-v3模型
print("\n=== 测试Whisper large-v3模型 ===")
try:
    model_large = whisper.load_model("large-v3")
    print("Large-v3模型加载成功。")
    result_large = model_large.transcribe(audio_file, task="transcribe")
    print(f"Large-v3转写结果: {result_large['text']}")
    # 测试翻译
    result_large_translate = model_large.transcribe(audio_file, task="translate")
    print(f"Large-v3翻译结果 (to English): {result_large_translate['text']}")
    print("Large-v3模型测试通过。")
except Exception as e:
    print(f"Large-v3模型测试失败: {e}")

print("\n测试完成。如果模型加载成功且输出合理文本，则模型可用；否则检查网络/依赖。")

print("开始测试Whisper模型可用性...")

# 测试Whisper tiny模型
print("\n=== 测试Whisper tiny模型 ===")
try:
    model_tiny = whisper.load_model("tiny")
    print("Tiny模型加载成功。")
    result_tiny = model_tiny.transcribe(audio_file, task="transcribe")
    print(f"Tiny转写结果: {result_tiny['text']}")
    # 测试翻译（假设音频是中文，翻译为英语）
    result_tiny_translate = model_tiny.transcribe(audio_file, task="translate")
    print(f"Tiny翻译结果 (to English): {result_tiny_translate['text']}")
    print("Tiny模型测试通过。")
except Exception as e:
    print(f"Tiny模型测试失败: {e}")

# 测试Whisper large-v3模型
print("\n=== 测试Whisper large-v3模型 ===")
try:
    model_large = whisper.load_model("large-v3")
    print("Large-v3模型加载成功。")
    result_large = model_large.transcribe(audio_file, task="transcribe")
    print(f"Large-v3转写结果: {result_large['text']}")
    # 测试翻译
    result_large_translate = model_large.transcribe(audio_file, task="translate")
    print(f"Large-v3翻译结果 (to English): {result_large_translate['text']}")
    print("Large-v3模型测试通过。")
except Exception as e:
    print(f"Large-v3模型测试失败: {e}")

print("\n测试完成。如果模型加载成功且输出合理文本，则模型可用；否则检查网络/依赖。")