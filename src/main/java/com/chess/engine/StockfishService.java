package com.chess.analyzer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StockfishService {

    @Value("${stockfish.path}")
    private String binaryPath;

    @Value("${stockfish.threads:2}")
    private int threads;

    @Value("${stockfish.hash:4096}")
    private int hash;

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;

    private static final Pattern CP_PATTERN = Pattern.compile("score cp (-?\\d+)");
    private static final Pattern MATE_PATTERN = Pattern.compile("score mate (-?\\d+)");

    @PostConstruct
    public void start() {
        try {
            ProcessBuilder pb = new ProcessBuilder(binaryPath);
            pb.redirectErrorStream(true);
            process = pb.start();

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            sendCommand("uci");
            sendCommand("setoption name Threads value " + threads);
            sendCommand("setoption name Hash value " + hash);
            sendCommand("isready");
            waitFor("readyok");
        } catch (IOException e) {
            System.err.println("Stockfish binary not found at " + binaryPath + ". Local engine start skipped.");
        }
    }

    public synchronized AnalysisResult analyzePosition(String fen, int depth) {
        if (process == null || !process.isAlive()) {
            return new AnalysisResult("e2e4", 0.0);
        }

        sendCommand("stop");
        sendCommand("position fen " + fen);
        sendCommand("go depth " + depth);

        Double scoreInPawns = 0.0;
        String bestMove = null;

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info ") && line.contains("score ")) {
                    Matcher cpMatcher = CP_PATTERN.matcher(line);
                    Matcher mateMatcher = MATE_PATTERN.matcher(line);

                    if (cpMatcher.find()) {
                        scoreInPawns = Integer.parseInt(cpMatcher.group(1)) / 100.0;
                    } else if (mateMatcher.find()) {
                        int mateMoves = Integer.parseInt(mateMatcher.group(1));
                        scoreInPawns = mateMoves > 0 ? 1000.0 - mateMoves : -1000.0 - mateMoves;
                    }
                }

                if (line.startsWith("bestmove")) {
                    String[] tokens = line.split(" ");
                    if (tokens.length >= 2) {
                        bestMove = tokens[1];
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new AnalysisResult(bestMove, scoreInPawns);
    }

    private void sendCommand(String cmd) {
        try {
            writer.write(cmd + "\n");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to Stockfish", e);
        }
    }

    private void waitFor(String expected) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().equals(expected)) {
                break;
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            sendCommand("quit");
        } catch (Exception ignored) {}
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public record AnalysisResult(String bestMove, Double evaluation) {}
}