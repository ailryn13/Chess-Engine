package com.chess.analyzer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engine")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final StockfishService stockfishService;

    public AnalysisController(StockfishService stockfishService) {
        this.stockfishService = stockfishService;
    }

    @GetMapping("/analyze")
    public ResponseEntity<StockfishService.AnalysisResult> analyze(
            @RequestParam(defaultValue = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") String fen,
            @RequestParam(defaultValue = "12") int depth) {

        if (depth < 1 || depth > 25) {
            depth = 12;
        }

        return ResponseEntity.ok(stockfishService.analyzePosition(fen, depth));
    }
}