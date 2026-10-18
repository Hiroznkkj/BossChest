import sys, os, json, re, subprocess, difflib, unicodedata
sys.path.insert(0, os.path.dirname(__file__))
from falas_dados import FALAS
voz, palavras_json, res = sys.argv[1], sys.argv[2], sys.argv[3]
whisper = json.load(open(palavras_json))
# arquivos do zip -> nome normalizado
arquivos = {}
for raiz, _, fs in os.walk(voz):
    for f in fs:
        if not f.endswith(".mp3"): continue
        nome = f[:-4].replace("#U00e7", "ç").replace("#U00e3", "ã")
        nome = unicodedata.normalize("NFKD", nome).encode("ascii", "ignore").decode()
        arquivos[nome] = (os.path.join(raiz, f), os.path.relpath(os.path.join(raiz, f), voz))
sons = os.path.join(res, "assets/bauchefe/sounds/marcio"); os.makedirs(sons, exist_ok=True)
sounds_json, tempos = {}, {}
def norm(w): return unicodedata.normalize("NFKD", re.sub(r"[^\w]", "", w.lower())).encode("ascii", "ignore").decode()
def dur(f): return float(subprocess.run(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",f],capture_output=True,text=True).stdout)
for som, tipo, texto in FALAS:
    src, rel = arquivos[som]
    d = dur(src)
    # voz normal: fade de entrada (começo suave) e de saída
    fade = f"afade=t=in:st=0:d=0.12,afade=t=out:st={max(0,d-0.15):.3f}:d=0.15"
    subprocess.run(["ffmpeg","-v","error","-y","-i",src,"-af",fade,"-ac","1","-c:a","libvorbis","-q:a","4",f"{sons}/{som}.ogg"],check=True)
    # versão de longe: abafada e com eco (o rabo do eco ganha 0,8 s de silêncio para não cortar)
    eco = fade + ",apad=pad_dur=0.8,aecho=0.8:0.75:140|290|470:0.45|0.3|0.18,lowpass=f=2800"
    subprocess.run(["ffmpeg","-v","error","-y","-i",src,"-af",eco,"-ac","1","-c:a","libvorbis","-q:a","2",f"{sons}/{som}_eco.ogg"],check=True)
    for n in (som, som + "_eco"):
        sounds_json[f"marcio.{n}"] = {"sounds": [{"name": f"bauchefe:marcio/{n}"}]}
    # tempos: alinha as palavras do texto com as da transcrição; o que não casar é interpolado
    w = whisper.get(rel, [])
    nossas = texto.split()
    a, b = [norm(x) for x in nossas], [norm(x[0]) for x in w]
    t = [None] * len(nossas)
    for bl in difflib.SequenceMatcher(None, a, b, autojunk=False).get_matching_blocks():
        for k in range(bl.size): t[bl.a + k] = w[bl.b + k][1]
    if t and t[0] is None: t[0] = w[0][1] if w else 0.0
    fim = w[-1][2] if w else d
    for i in range(len(t)):
        if t[i] is None:
            j = next((k for k in range(i + 1, len(t)) if t[k] is not None), None)
            prox = t[j] if j is not None else fim
            passo = (prox - t[i - 1]) / ((j if j is not None else len(t)) - i + 1)
            t[i] = t[i - 1] + passo
    for i in range(1, len(t)): t[i] = max(t[i], t[i - 1])
    tempos[som] = {"duracao": round(d, 2), "palavras": [[p, round(x, 2)] for p, x in zip(nossas, t)]}
    print(f"{som:12s} {d:5.2f}s  " + " ".join(f"{p}@{x:.1f}" for p, x in zip(nossas, t)))
json.dump(sounds_json, open(os.path.join(res, "assets/bauchefe/sounds.json"), "w"), ensure_ascii=False, indent=1)
json.dump(tempos, open(os.path.join(res, "assets/bauchefe/marcio_falas.json"), "w"), ensure_ascii=False, indent=1)
