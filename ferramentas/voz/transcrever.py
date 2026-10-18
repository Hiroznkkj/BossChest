import sys, glob, os, subprocess, json
import numpy as np
from faster_whisper import WhisperModel
m = WhisperModel("small", device="cpu", compute_type="int8")
base, out = sys.argv[1], sys.argv[2]
res = {}
for f in sorted(glob.glob(base + "/**/*.mp3", recursive=True)):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-f", "f32le", "-ac", "1", "-ar", "16000", "-"], capture_output=True).stdout
    audio = np.frombuffer(raw, np.float32)
    segs, info = m.transcribe(audio, language="pt", word_timestamps=True)
    words = [(w.word.strip(), round(w.start, 2), round(w.end, 2)) for s in segs for w in (s.words or [])]
    k = os.path.relpath(f, base)
    res[k] = words
    print(k, "|", " ".join(w[0] for w in words), "|", words[0][1] if words else -1, flush=True)
json.dump(res, open(out, "w"), ensure_ascii=False, indent=0)
