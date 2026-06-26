package com.elias.finanx.dto.analytics.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ranking<F> {
    private int count;
    private String title;
    private List<RankingItem<F>> items;
}
