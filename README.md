# SinalLens AR

Protótipo Android de material didático bilíngue em **realidade aumentada pela câmera** para estudantes surdos.

## Para quem?
Estudantes surdos, com foco em alfabetização bilíngue e associação entre Libras, objetos reais e português escrito.

## O que é?
Um aplicativo Android inspirado na lógica do Google Lens. A pessoa aponta a câmera para o ambiente, escaneia objetos do cotidiano, toca em um objeto reconhecido e acessa:
- nome em português;
- recorte automático do objeto;
- opção de salvar PNG sem fundo;
- visualização do sinal em Libras com avatar do VLibras.

## Por quê?
O projeto aproxima o vocabulário de situações reais e concretas. Em vez de começar por uma lista abstrata de palavras, o estudante explora o próprio ambiente: cadeira, mesa, livro, mochila, planta etc.

## Como funciona?
1. CameraX exibe a câmera;
2. ML Kit detecta objetos;
3. Image Labeling identifica o objeto;
4. Subject Segmentation remove o fundo;
5. a interface desenha marcações sobre a imagem, criando a camada de realidade aumentada;
6. o objeto selecionado é enviado como palavra para o VLibras, que sinaliza usando avatar 3D.

## Custo
Não há API paga nem token comercial no MVP. A stack usa bibliotecas Android/Google gratuitas e o VLibras, software público brasileiro.

## Observação
A tradução automática em Libras não substitui validação linguística por pessoas surdas/profissionais de Libras. Para uso pedagógico final, o vocabulário e os sinais apresentados devem ser revisados com a comunidade surda.

## Stack
- Android / Kotlin
- Jetpack Compose
- CameraX
- Google ML Kit Object Detection
- Google ML Kit Image Labeling
- Google ML Kit Subject Segmentation
- VLibras
