# BossChest
Mod Fabric "Baú Chefe" (bauchefe) para Minecraft 26.3.

- [FALAS.md](FALAS.md): as falas do Márcio pescador.
- [ANIMACOES.md](ANIMACOES.md): como fazer e trocar as animações do Márcio no Blockbench (`/marcioanim`).
- [PROCEDURAL.md](PROCEDURAL.md): como a pose é calculada pelo mundo (IK, camadas) e como criar uma reação nova; testes com `./gradlew testePose`.

## Créditos

- **Uso:** mod sem fins comerciais; não é vendido nem distribuído comercialmente.
- **Animações do Márcio pescador:** inspiradas no resource pack [Fresh Animations](https://modrinth.com/resourcepack/fresh-animations), de **FreshLX**. Ele foi a referência de estilo para os movimentos pequenos e contínuos, a respiração, a piscada, as pupilas que acompanham o olhar e as pernas sentadas no barco. Tudo foi reimplementado em código próprio, sem usar arquivos, modelos (.jem/.jpm) ou texturas do Fresh Animations. Todos os direitos sobre o Fresh Animations são de FreshLX.
- **Voz do Márcio:** as falas (lista em [FALAS.md](FALAS.md)) foram geradas com [ElevenLabs](https://elevenlabs.io), voz de ID `PZb5BTdLd4vD52f25mcy` (Voice Library do ElevenLabs). Créditos à ElevenLabs e a quem criou essa voz.
- **Visual do Márcio:** skin escolhida no [NameMC](https://pt.namemc.com/skin/68f6764193768ba0) (direitos de quem a criou). A camada de fora da skin em 3D foi inspirada no mod [3D Skin Layers](https://github.com/tr7zw/3d-Skin-Layers), de **tr7zw**, reimplementada em código próprio, sem usar código dele.
- **Som da voz:** a forma de calcular o volume pela distância (queda cúbica), pela direção em que o Márcio está virado e pelas paredes no caminho foi inspirada no [Plasmo Voice](https://github.com/plasmoapp/plasmo-voice), de **Plasmo** (licença LGPL-3.0). Reimplementado em código próprio, sem copiar o código do Plasmo Voice.
- **Balões de fala:** o visual e a textura do balão (`assets/bauchefe/textures/entity/balao.png`) foram portados do mod [Notable Bubble Text (NBT)](https://github.com/Mrbysco/NotableBubbleText), de **Mrbysco**, sob a licença MIT:

  > MIT License — Copyright (c) 2023 Mrbysco
  >
  > Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
  >
  > The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
  >
  > THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
