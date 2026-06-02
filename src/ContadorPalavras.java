import org.jocl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ContadorPalavras {

    private static String programSource =
        "__kernel void buscarPalavra(__global const char* texto, __global const char* palavra, " +
        "                            const int tamanhoTexto, const int tamanhoPalavra, __global int* resultados) {" +
        "    int gid = get_global_id(0);" +
        "    if (gid <= tamanhoTexto - tamanhoPalavra) {" +
        "        int igual = 1;" +
        "        for (int i = 0; i < tamanhoPalavra; i++) {" +
        "            if (texto[gid + i] != palavra[i]) {" +
        "                igual = 0;" +
        "                break;" +
        "            }" +
        "        }" +
        "        resultados[gid] = igual;" +
        "    } else {" +
        "        resultados[gid] = 0;" +
        "    }" +
        "}";

    public static void main(String[] args) {
        String[] arquivos = {
            "Dracula-165307.txt",
            "MobyDick-217452.txt",
            "DonQuixote-388208.txt"
        };
        
        String palavraAlvo = "the"; 
        int repeticoes = 3;

        System.out.println("=== Iniciando Benchmarks de Computacao Paralela ===");
        
        List<String[]> linhasCsv = new ArrayList<>();
        linhasCsv.add(new String[]{"Arquivo", "Tamanho(KB)", "Metodo", "Execucao", "Contagem", "Tempo(ms)", "Nucleos_Threads"});

        for (String arq : arquivos) {
            File file = new File(arq);
            if (!file.exists()) {
                System.out.println("Arquivo nao encontrado na raiz: " + arq + ". Pulando...");
                continue;
            }

            try {
                String conteudo = new String(Files.readAllBytes(Paths.get(arq)), StandardCharsets.UTF_8).toLowerCase();
                long tamanhoKB = file.length() / 1024;
                String[] palavras = conteudo.split("\\s+");

                System.out.println("\nProcessando: " + arq + " (" + tamanhoKB + " KB)");

                // 1. SERIAL CPU
                for (int i = 1; i <= repeticoes; i++) {
                    long inicio = System.nanoTime();
                    int contagem = metodoSerialCPU(palavras, palavraAlvo);
                    long fim = System.nanoTime();
                    long tempoMs = (fim - inicio) / 1_000_000;
                    System.out.println("SerialCPU [Volta " + i + "]: " + contagem + " ocorrencias em " + tempoMs + " ms");
                    linhasCsv.add(new String[]{arq, String.valueOf(tamanhoKB), "SerialCPU", String.valueOf(i), String.valueOf(contagem), String.valueOf(tempoMs), "1"});
                }

                // 2. PARALLEL CPU (Variando Threads: 2, 4 e o maximo disponivel)
                int[] configsThreads = {2, 4, Runtime.getRuntime().availableProcessors()};
                for (int cores : configsThreads) {
                    for (int i = 1; i <= repeticoes; i++) {
                        long inicio = System.nanoTime();
                        int contagem = metodoParallelCPU(palavras, palavraAlvo, cores);
                        long fim = System.nanoTime();
                        long tempoMs = (fim - inicio) / 1_000_000;
                        System.out.println("ParallelCPU (" + cores + " Cores) [Volta " + i + "]: " + contagem + " ocorrencias em " + tempoMs + " ms");
                        linhasCsv.add(new String[]{arq, String.valueOf(tamanhoKB), "ParallelCPU", String.valueOf(i), String.valueOf(contagem), String.valueOf(tempoMs), String.valueOf(cores)});
                    }
                }

                // 3. PARALLEL GPU (OpenCL)
                for (int i = 1; i <= repeticoes; i++) {
                    long inicio = System.nanoTime();
                    int contagem = metodoParallelGPU(conteudo, palavraAlvo);
                    long fim = System.nanoTime();
                    long tempoMs = (fim - inicio) / 1_000_000;
                    System.out.println("ParallelGPU [Volta " + i + "]: " + contagem + " ocorrencias em " + tempoMs + " ms");
                    linhasCsv.add(new String[]{arq, String.valueOf(tamanhoKB), "ParallelGPU", String.valueOf(i), String.valueOf(contagem), String.valueOf(tempoMs), "GPU_Chunks"});
                }

            } catch (Exception e) {
                System.err.println("Erro ao processar o arquivo " + arq + ": " + e.getMessage());
            }
        }

        salvarCSV(linhasCsv);
        System.out.println("\n=== Benchmark Concluido! Arquivo 'resultados/resultados_busca.csv' gerado. ===");
    }

    private static int metodoSerialCPU(String[] palavras, String alvo) {
        int contador = 0;
        for (String p : palavras) {
            if (p.equals(alvo)) {
                contador++;
            }
        }
        return contador;
    }

    private static int metodoParallelCPU(String[] palavras, String alvo, int numThreads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int tamanhoChunk = palavras.length / numThreads;
        List<Future<Integer>> futuros = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int inicio = i * tamanhoChunk;
            final int fim = (i == numThreads - 1) ? palavras.length : (inicio + tamanhoChunk);

            futuros.add(executor.submit(() -> {
                int parcial = 0;
                for (int j = inicio; j < fim; j++) {
                    if (palavras[j].equals(alvo)) {
                        parcial++;
                    }
                }
                return parcial;
            }));
        }

        int total = 0;
        for (Future<Integer> f : futuros) {
            total += f.get();
        }
        executor.shutdown();
        return total;
    }

    private static int metodoParallelGPU(String texto, String alvo) {
        byte[] textoBytes = texto.getBytes(StandardCharsets.UTF_8);
        byte[] alvoBytes = alvo.getBytes(StandardCharsets.UTF_8);
        int tamanhoTexto = textoBytes.length;
        int tamanhoAlvo = alvoBytes.length;
        int[] resultados = new int[tamanhoTexto];

        CL.setExceptionsEnabled(true);
        cl_platform_id[] platforms = new cl_platform_id[1];
        CL.clGetPlatformIDs(1, platforms, null);
        cl_platform_id platform = platforms[0];

        cl_device_id[] devices = new cl_device_id[1];
        CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_GPU, 1, devices, null);
        cl_device_id device = devices[0];

        cl_context context = CL.clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
        cl_command_queue commandQueue = CL.clCreateCommandQueueWithProperties(context, device, null, null);

        cl_mem memTexto = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR, Sizeof.cl_char * tamanhoTexto, Pointer.to(textoBytes), null);
        cl_mem memAlvo = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR, Sizeof.cl_char * tamanhoAlvo, Pointer.to(alvoBytes), null);
        cl_mem memResultados = CL.clCreateBuffer(context, CL.CL_MEM_WRITE_ONLY, Sizeof.cl_int * tamanhoTexto, null, null);

        cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);
        CL.clBuildProgram(program, 0, null, null, null, null);

        cl_kernel kernel = CL.clCreateKernel(program, "buscarPalavra", null);
        CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memTexto));
        CL.clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memAlvo));
        CL.clSetKernelArg(kernel, 2, Sizeof.cl_int, Pointer.to(new int[]{tamanhoTexto}));
        CL.clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{tamanhoAlvo}));
        CL.clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(memResultados));

        long[] globalWorkSize = new long[]{tamanhoTexto};

        CL.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        CL.clEnqueueReadBuffer(commandQueue, memResultados, CL.CL_TRUE, 0, Sizeof.cl_int * tamanhoTexto, Pointer.to(resultados), 0, null, null);

        CL.clReleaseKernel(kernel);
        CL.clReleaseProgram(program);
        CL.clReleaseMemObject(memTexto);
        CL.clReleaseMemObject(memAlvo);
        CL.clReleaseMemObject(memResultados);
        CL.clReleaseCommandQueue(commandQueue);
        CL.clReleaseContext(context);

        int totalOcorrencias = 0;
        for (int v : resultados) {
            if (v == 1) totalOcorrencias++;
        }
        return totalOcorrencias;
    }

    private static void salvarCSV(List<String[]> dados) {
        try (PrintWriter writer = new PrintWriter(new File("resultados/resultados_busca.csv"))) {
            for (String[] linha : dados) {
                writer.println(String.join(",", linha));
            }
        } catch (FileNotFoundException e) {
            System.err.println("Erro ao salvar arquivo CSV: " + e.getMessage());
        }
    }
}