package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        return ResponseEntity.of(
                Optional.ofNullable(indexingService.startIndexing()));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        return ResponseEntity.of(
                Optional.ofNullable(indexingService.stopIndexing()));
    }

    @PostMapping("indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(String url){
        return ResponseEntity.of(
                Optional.ofNullable(indexingService.indexPage(url)));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(String query,
                                 String site,
                                 int offset,
                                 int limit) {
        return ResponseEntity.of(
                Optional.ofNullable(indexingService.search(query, site, offset, limit)));
    }
}
