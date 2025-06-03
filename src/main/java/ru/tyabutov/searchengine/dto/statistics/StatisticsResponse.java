package ru.tyabutov.searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
