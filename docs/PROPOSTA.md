# SinalLens AR — proposta para apresentação

## Para quem?
Estudantes surdos, especialmente crianças e jovens em processos de alfabetização bilíngue Libras–Português, além de professores que desejam transformar o próprio ambiente em material didático.

## O que é?
O SinalLens AR é um aplicativo Android de realidade aumentada baseada em câmera. Ele reconhece objetos reais do cotidiano, mostra o nome do objeto em português, permite recortar e salvar a imagem sem fundo e apresenta o sinal correspondente em Libras por meio de um avatar do VLibras.

## Por quê?
A proposta aproxima a aprendizagem do cotidiano. Em vez de estudar apenas por listas ou cartões prontos, o estudante pode explorar a própria sala, casa, escola ou rua. A associação passa a ser:

OBJETO REAL → IMAGEM → PALAVRA EM PORTUGUÊS → SINAL EM LIBRAS

Isso favorece uma aprendizagem mais visual, contextualizada e concreta.

## Como?
1. O aluno abre a câmera do aplicativo.
2. Aponta para um ambiente real.
3. O app reconhece objetos usando visão computacional.
4. Marcações são desenhadas sobre a imagem, formando a camada de realidade aumentada.
5. O aluno toca no objeto que deseja explorar.
6. O app mostra a palavra em português.
7. O objeto pode ser isolado do fundo e salvo como PNG.
8. Ao tocar em “Ver sinal em Libras”, o VLibras apresenta o sinal com avatar.

## Exemplo
Em uma sala de aula, a câmera pode identificar itens como cadeira, mesa, livro, mochila ou pessoa. Ao tocar em “cadeira”, o estudante vê a imagem isolada da cadeira, a palavra “CADEIRA” e o sinal correspondente em Libras.

## Custo financeiro
O MVP não utiliza API paga nem token comercial:
- Android/Kotlin: gratuito;
- CameraX: gratuito e open source;
- ML Kit usado no dispositivo: sem cobrança por requisição;
- VLibras: software público brasileiro e código aberto.

O VLibras usado no MVP depende de internet para carregar o tradutor/avatar público. Uma versão futura pode estudar o empacotamento de componentes open source para funcionamento offline.

## Escopo realista
A primeira versão não promete reconhecer qualquer objeto existente no mundo. Ela demonstra o conceito com categorias comuns e amplia o vocabulário progressivamente. Essa limitação torna o produto viável e honesto como protótipo acadêmico.

## Evoluções possíveis
- coleção pessoal de objetos escaneados;
- categorias por ambiente: sala, cozinha, escola, rua;
- quiz “encontre o objeto”;
- revisão do vocabulário salvo;
- gravações de pessoas surdas como alternativa ou complemento ao avatar;
- reconhecimento de texto em placas e livros;
- modo professor para criar coleções temáticas.
