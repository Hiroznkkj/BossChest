# Voz do Márcio: como adicionar áudios

1. Gere os áudios no ElevenLabs (roteiros em `FALAS.md`) e baixe em mp3, um arquivo por fala (`peixe_7.mp3`, `bebado_1.mp3`...).
2. Transcreva com tempo por palavra (precisa de `pip install faster-whisper` e ffmpeg):
   `python transcrever.py <pasta_dos_mp3> palavras.json`
3. Em `falas_dados.py`, acrescente `(arquivo, TIPO, "texto exatamente como foi falado")`.
4. Gere os .ogg (normal + eco, com fade no começo), o `sounds.json` e os tempos do karaokê:
   `python gerar_audio.py <pasta_dos_mp3> palavras.json ../../src/main/resources`
5. Em `src/main/java/br/hirozn/bauchefe/Falas.java`, coloque a linha no tipo certo: `new Linha("texto", "arquivo")`.
