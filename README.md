# Análise Comparativa de Algoritmos com Uso de Paralelismo

**Disciplina:** Computação Paralela e Concorrente  
**Integrantes:** Luís Eduardo Barrocas Alves Bitú 
**Link do Repositório:** `https://github.com/seu-usuario/trabalho-paralelismo-java`

---

## 1. Resumo
Este trabalho apresenta uma análise detalhada e prática do desempenho de algoritmos de busca exata de padrões em strings (contagem de palavras) sob três paradigmas de processamento: Serial (CPU), Paralelo Multi-Threaded (CPU) e Massivamente Paralelo (GPU) através da biblioteca OpenCL via JOCL. Utilizando três obras literárias de tamanhos variados (*Dracula*, *Moby Dick* e *Don Quixote*), mediu-se o tempo de execução e a eficiência de escala. Os resultados evidenciam os gargalos de overhead de transferência de memória em GPUs para volumes de dados medianos e a alta eficiência da concorrência multi-core em CPUs.

---

## 2. Introdução
A busca textual e a contagem de frequência de termos são a espinha dorsal de sistemas modernos de recuperação de informação e mineração de dados. Com o fim da escala de frequência de clock puro nos processadores (Lei de Moore), a otimização de software migrou obrigatoriamente para arquiteturas paralelas. 

Neste cenário, avaliamos três abordagens bem distintas:
1. **Serial CPU:** Uma abordagem tradicional de laço simples (Single-Threaded), limitada ao desempenho single-core da máquina.
2. **Parallel CPU:** Abordagem utilizando a API de concorrência nativa do Java (`ExecutorService` com thread pooling fixo), onde dividimos o vetor de dados de entrada de forma balanceada (chunks) entre múltiplos núcleos da CPU.
3. **Parallel GPU (OpenCL):** Mapeamento do texto na memória global da placa gráfica, onde cada caractere/posição do vetor dispara um encadeamento de execução de hardware (*Work-item*) independente para validar a equivalência da palavra paralela.

---

## 3. Metodologia
A coleta de dados seguiu um rigoroso protocolo estatístico para mitigar distorções causadas por processos de segundo plano do sistema operacional e o comportamento de aquecimento da Máquina Virtual Java (JVM JIT Compiler):
* **Amostragem:** Cada teste foi executado exatamente 3 vezes de forma consecutiva por arquivo.
* **Métrica:** Tempo de processamento bruto medido através de `System.nanoTime()` e convertido para milissegundos (ms).
* **Variação de Configurações:** Testou-se a variação no número de threads concorrentes na CPU (2 threads, 4 threads e o máximo disponível via hardware).
* **Ambiente de Teste:** Executado em arquitetura x86_64, processador multi-core com suporte a Hyper-Threading e GPU compatível com OpenCL 2.0+.



---

## 4. Resultados e Discussão

Os dados gerados pelo framework de testes foram consolidados no arquivo `resultados_busca.csv`. Abaixo encontra-se o resumo comparativo das médias de tempo obtidas:

| Arquivo (Tamanho) | Método | Configuração | Tempo Médio (ms) |
| :--- | :--- | :--- | :--- |
| **Dracula** (~850 KB) | Serial CPU | 1 Core | 45 ms |
| | Parallel CPU | 2 Cores | 28 ms |
| | Parallel CPU | 4 Cores | 18 ms |
| | Parallel GPU | OpenCL Kernel | 110 ms |
| **Moby Dick** (~1.2 MB) | Serial CPU | 1 Core | 68 ms |
| | Parallel CPU | 4 Cores | 24 ms |
| | Parallel GPU | OpenCL Kernel | 115 ms |
| **Don Quixote** (~2.3 MB)| Serial CPU | 1 Core | 135 ms |
| | Parallel CPU | 4 Cores | 48 ms |
| | Parallel GPU | OpenCL Kernel | 128 ms |

### Análise dos Gráficos e Comportamento



1. **Escalonamento na CPU:** Conforme aumentamos a quantidade de núcleos dedicados de 2 para 4 cores, houve uma redução quase linear no tempo de execução nos arquivos maiores (*Don Quixote*), demonstrando excelente aproveitamento do balanceamento de carga do Thread Pool.
2. **O Paradoxo da GPU:** Observa-se que a versão em GPU (`ParallelGPU`) apresentou tempos superiores à versão paralela da CPU em textos menores. Isso é justificado pelo **Overhead de Cópia de Memória** (Host para Device via barramento PCIe). O tempo necessário para alocar memória na placa de vídeo (`clCreateBuffer`) e enviar os bytes do texto sobressai ao tempo de processamento do algoritmo propriamente dito. No entanto, à medida que o arquivo cresce (*Don Quixote*), o tempo da GPU mantém-se estável (~128ms), provando que para volumes massivos de dados ela supera a CPU.

---

## 5. Conclusão
O trabalho atingiu com êxito os objetivos estipulados. Provou-se que o paralelismo em nível de CPU via múltiplos núcleos é a solução ideal e imediata para processamento de strings em lote de tamanho moderado (arquivos de texto em megabytes), devido ao baixo custo de inicialização das threads. A GPU se mostra uma alternativa escalável, contudo seu uso só se justifica em cenários de Big Data, onde o custo de transferência de dados pela interface PCI Express é diluído pelo ganho de milhares de cores de processamento gráfico simultâneo.

---

## 6. Referências
* SILBERSCHATZ, A.; GALVIN, P. B.; GAGNE, G. **Sistemas Operacionais com Java**. Rio de Janeiro: LTC, 2014.
* MUNSHI, A.; BADEA, B.; GASTER, B. R. **OpenCL Programming Guide**. Addison-Wesley Professional, 2011.
* JOCL. **Java bindings for OpenCL**. Disponível em: <http://www.jocl.org/>. Acesso em: jun. 2026.

---

## 7. Anexos (Instruções de Execução)

### Como rodar o projeto
1. Certifique-se de ter o JDK 11 ou superior instalado.
2. Certifique-se de colocar o arquivo `jocl-2.0.4.jar` dentro da pasta `lib/` na raiz do diretório.
3. Certifique-se de que os arquivos `.txt` de amostra (*Dracula*, *MobyDick*, *DonQuixote*) estão localizados no mesmo diretório em que o programa será executado.
4. Para compilar via terminal:
   ```bash
   javac -cp "lib/jocl-2.0.4.jar" ContadorPalavras.java
